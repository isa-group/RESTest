/*
 * Copyright 2026 ISA Research Group, Universidad de Sevilla.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.restest.report;

import io.restest.core.event.RunEvent;
import io.restest.core.event.RunListener;
import io.restest.core.exec.EngineStatistics;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionOutcome;
import io.restest.core.json.InteractionDocument;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.model.OperationId;
import io.restest.core.oracle.FaultCategory;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.WfcFault;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Writes what a run found as one JSON file, for anything that reads a run rather than looks at it.
 *
 * <p>The report on the screen is for a person; this one is for a build server deciding whether to
 * fail, a spreadsheet counting faults across versions, a script opening the ones that are new.
 *
 * <p>An API that is broken is broken every time it is asked, so a run of any length finds the same
 * fault over and over. Writing all of them out produces a file nobody can open, made almost entirely
 * of copies. What this report does instead, in three layers:
 *
 * <ul>
 *   <li><b>Every fault is counted exactly</b>, and every operation and kind of fault that went wrong
 *       is listed with its own count. Nothing is estimated and nothing is left out of the counting,
 *       because that list is as long as the API has operations rather than as long as the run.
 *   <li><b>The first few of each kind, on each operation, are written out whole</b> - the request as
 *       it was sent, the reply as it came back, where every value came from, and a {@code curl}
 *       command that does it again. Written in the same shape a stored run uses, so a fault in a
 *       report and the same fault in a stored run read identically.
 *   <li><b>The rest are named one by one</b>, by the request that caused them, so a reader can still
 *       see the spread of what was tried without the file carrying every reply.
 * </ul>
 *
 * <p>An allowance per operation and kind, rather than the first so many faults found, is the whole
 * point. Faults arrive in whatever order the API produces them, and the kind that happens to repeat
 * first would otherwise take the entire file - leaving a kind first seen towards the end of a run
 * with nothing written about it at all.
 *
 * <p>None of this decides that two faults are the same problem. Two faults on the same operation of
 * the same kind are two faults, counted separately and named separately. The allowance decides how
 * much room a kind gets, never whether two of them share a cause.
 *
 * <p>Every limit is written into the file beside what it applies to, and the file says when a limit
 * stopped it writing, so nothing is quietly missing.
 *
 * <p>Fault kinds are named by number from a catalogue several testing tools share, and the report
 * says which version of that catalogue it used, because a fault code only means something next to
 * the list it was taken from.
 */
public final class JsonReport implements RunListener {

    /**
     * How many faults of one kind, on one operation, are written out whole.
     *
     * <p>The first proves the fault is real and can be repeated. A second and a third show what else
     * was being sent when it happened, which is the part a reader learns from. By the fifth, another
     * near-identical copy teaches nobody anything, and every one of them carries a whole request and
     * reply.
     */
    static final int FULL_EVIDENCE_PER_OPERATION_AND_KIND = 5;

    /**
     * And how many bytes of them the file will hold, however many kinds went wrong.
     *
     * <p>An allowance counted in faults is not an allowance at all when one reply can be a megabyte:
     * a limit on how many unbounded things are kept is not a limit. This is the one that actually
     * binds against an API with large replies, and it is roughly the size the file used to be, so
     * nothing downstream meets a surprise.
     */
    static final long FULL_EVIDENCE_BYTES = 2L * 1024 * 1024;

    /**
     * How many faults of one kind, on one operation, are named after their allowance is spent.
     *
     * <p>Naming exists to show the spread of what was being sent - whether one value broke the API
     * or every value did. A handful is too few to see that; a thousand near-identical lines is a
     * wall. The exact count beside them carries the rest.
     */
    static final int NAMES_PER_OPERATION_AND_KIND = 100;

    /** And the bytes those names may take, for the same reason the evidence has one. */
    static final long NAMES_BYTES = 1024L * 1024;

    /**
     * One operation, one kind of fault: the pair everything here is allowanced by.
     *
     * <p>The kind is its catalogue number rather than the object describing it. A number is what
     * this report writes out, and two objects saying "F500" are the same kind of fault whether or
     * not they happen to be the same object - which matters because what describes a kind of fault
     * is an interface anybody can implement, and one that made a fresh object per fault would turn
     * the allowance below into no allowance at all.
     */
    private record Kind(OperationId operation, int code) {
    }

    /**
     * What is known about one such pair. The count is exact and unbounded; the other two are what
     * this report had room for.
     */
    private static final class Tally {

        /** The first object seen describing this kind, for the words that go beside the number. */
        private final FaultCategory category;

        private int count;
        private int writtenInFull;
        private int named;

        /**
         * Set once the file has no room left for this kind, so that the next fault of it is not
         * turned into JSON only to be thrown away. Without this, every fault after the file filled
         * up would be built and measured and discarded, which costs far more than writing it.
         */
        private boolean noRoomToWriteMore;
        private boolean noRoomToNameMore;

        private Instant firstSeenAt;
        private Instant lastSeenAt;

        Tally(FaultCategory category) {
            this.category = category;
        }

        void saw(Instant when) {
            count++;
            if (firstSeenAt == null || when.isBefore(firstSeenAt)) {
                firstSeenAt = when;
            }
            if (lastSeenAt == null || when.isAfter(lastSeenAt)) {
                lastSeenAt = when;
            }
        }
    }

    private final Optional<Path> file;
    private final Clock clock;

    /**
     * One entry per operation and kind that went wrong. This is the structure that is not bounded by
     * a constant, and deliberately: it is what makes the counting exact. It is bounded by the API
     * instead - an operation and a kind of fault each come from a fixed list - so it does not grow
     * with how long a run is given.
     */
    private final Map<Kind, Tally> tallies = new LinkedHashMap<>();

    /** Written-out faults and named faults, in the order they were found. Already JSON, and sized. */
    private final List<JsonValue> full = new ArrayList<>();
    private final List<JsonValue> named = new ArrayList<>();
    private long fullBytes;
    private long namedBytes;

    /**
     * How many faults were turned into JSON. Bounded by the number of operations and kinds that went
     * wrong, never by how long the run was given - which is the difference between a report that
     * costs nothing past its allowance and one that quietly does all the work anyway.
     */
    private int converted;
    private boolean fullEvidenceStoppedEarly;
    private boolean namingStoppedEarly;

    private final Set<OperationId> operations = new LinkedHashSet<>();
    private String api = "";
    private String baseUrl = "";
    private int attempts;
    private int faults;
    private Duration elapsed = Duration.ZERO;
    private EngineStatistics engine = EngineStatistics.none();
    private JsonValue written;

    private JsonReport(Optional<Path> file, Clock clock) {
        this.file = file;
        this.clock = clock;
    }

    /** A report written to this file when the run finishes. */
    public static JsonReport to(Path file) {
        return new JsonReport(Optional.of(Objects.requireNonNull(file, "file")), Clock.systemUTC());
    }

    /** A report that is built but written nowhere, for anyone who only wants to read it back. */
    public static JsonReport inMemory() {
        return new JsonReport(Optional.empty(), Clock.systemUTC());
    }

    /** The same, with the clock the report's timestamp comes from, so a test can fix it. */
    public static JsonReport inMemory(Clock clock) {
        return new JsonReport(Optional.empty(), Objects.requireNonNull(clock, "clock"));
    }

    /** How many faults this report turned into JSON, which is what it costs the run. */
    int converted() {
        return converted;
    }

    /** What was written, once the run has finished. Empty before that. */
    public Optional<JsonValue> document() {
        return Optional.ofNullable(written);
    }

    /** What was written, as the text that went into the file. Empty before the run has finished. */
    public Optional<String> asText() {
        return document().map(JsonText::write);
    }

    @Override
    public void on(RunEvent event) {
        switch (event) {
            case RunEvent.RunStarted started -> {
                api = started.api();
                baseUrl = started.baseUrl();
            }
            case RunEvent.InteractionCompleted completed -> {
                attempts++;
                operations.add(completed.interaction().testCase().operation());
            }
            case RunEvent.FaultFound found -> took(found.finding());
            case RunEvent.RunFinished finished -> {
                elapsed = finished.elapsed();
                engine = finished.engine();
                finish();
            }
            case RunEvent.TestCasePlanned ignored -> {
                // What was planned and then not sent leaves no evidence to report on.
            }
        }
    }

    /**
     * Counts one fault, then writes it out whole if its kind still has room, and names it if not.
     *
     * <p>The counting happens first and always. Everything after it is about how much of the file
     * this one fault gets, and never about whether it happened.
     */
    private void took(Finding finding) {
        faults++;
        Tally tally = tallies.computeIfAbsent(
                new Kind(finding.operation(), finding.category().code()),
                ignored -> new Tally(finding.category()));
        tally.saw(finding.interaction().sentAt());
        if (!writtenWhole(finding, tally)) {
            name(finding, tally);
        }
    }

    /**
     * Writes the whole attempt out, if this kind of fault on this operation still has room for one
     * and the file has room for it.
     *
     * @return whether it was written
     */
    private boolean writtenWhole(Finding finding, Tally tally) {
        if (tally.writtenInFull >= FULL_EVIDENCE_PER_OPERATION_AND_KIND || tally.noRoomToWriteMore) {
            // Either its kind has had its share, or the file had no room for the last one of this
            // kind and will have none for this one either. Those are different things and a reader
            // is told which, but neither is worth building a whole attempt to discover again.
            return false;
        }
        // Turned into JSON only here, and at most once more per kind after the file fills up. A
        // fault past its kind's allowance is never converted at all, which is why a run finding
        // faults by the hundred thousand costs almost nothing.
        JsonValue entry = findingOf(finding);
        converted++;
        long size = sizeOf(entry);
        if (fullBytes + size > FULL_EVIDENCE_BYTES) {
            fullEvidenceStoppedEarly = true;
            tally.noRoomToWriteMore = true;
            return false;
        }
        full.add(entry);
        fullBytes += size;
        tally.writtenInFull++;
        return true;
    }

    /** Names a fault by the request that caused it, when there is no room to write it out whole. */
    private void name(Finding finding, Tally tally) {
        if (tally.named >= NAMES_PER_OPERATION_AND_KIND || tally.noRoomToNameMore) {
            return;
        }
        JsonValue entry = nameOf(finding);
        long size = sizeOf(entry);
        if (namedBytes + size > NAMES_BYTES) {
            namingStoppedEarly = true;
            tally.noRoomToNameMore = true;
            return;
        }
        named.add(entry);
        namedBytes += size;
        tally.named++;
    }

    /**
     * How many bytes this will take in the file.
     *
     * <p>Measured by writing it, rather than by a second way of counting the same thing: two ways
     * would eventually disagree, and then every limit in this file would be a lie. Only what is
     * actually kept is ever measured, so this happens a few hundred times in a run, not once per
     * fault.
     */
    private static long sizeOf(JsonValue value) {
        return JsonText.write(value).getBytes(StandardCharsets.UTF_8).length;
    }

    private void finish() {
        written = build();
        file.ifPresent(path -> {
            try {
                Files.writeString(path, JsonText.write(written));
            } catch (IOException e) {
                throw new UncheckedIOException("the report could not be written to " + path, e);
            }
        });
    }

    private JsonValue build() {
        Map<String, JsonValue> report = new LinkedHashMap<>();
        report.put("tool", JsonValue.object(Map.of("name", JsonValue.of("RESTest"),
                "version", JsonValue.of(toolVersion()))));
        report.put("faultCatalogue", JsonValue.object(Map.of(
                "name", JsonValue.of(WfcFault.CATALOGUE_NAME),
                "version", JsonValue.of(WfcFault.CATALOGUE_VERSION))));
        report.put("createdAt", JsonValue.of(clock.instant().toString()));
        report.put("api", JsonValue.object(Map.of("title", JsonValue.of(api),
                "baseUrl", JsonValue.of(baseUrl))));
        report.put("totals", totals());
        report.put("limits", limits());
        report.put("engine", engineStatistics());
        report.put("faultsByCategory", faultsByCategory());
        report.put("faultsByOperation", faultsByOperation());
        report.put("findings", JsonValue.array(List.copyOf(full)));
        report.put("otherFaults", JsonValue.array(List.copyOf(named)));
        return JsonValue.object(report);
    }

    /** One row a build server can read, in the order the kinds were first seen. */
    private JsonValue faultsByCategory() {
        Map<FaultCategory, Integer> byCategory = new LinkedHashMap<>();
        tallies.forEach((kind, tally) -> byCategory.merge(tally.category, tally.count, Integer::sum));
        return JsonValue.array(byCategory.entrySet().stream()
                .map(entry -> {
                    Map<String, JsonValue> counted = new LinkedHashMap<>(
                            ((JsonValue.JsonObject) categoryOf(entry.getKey())).members());
                    counted.put("count", JsonValue.of(entry.getValue()));
                    return (JsonValue) JsonValue.object(counted);
                })
                .toList());
    }

    /**
     * Every operation and kind that went wrong, with its exact count.
     *
     * <p>This is the part of the file that is complete. It says how many of each pair's faults were
     * written out, how many were named and how many are only counted, and when the first and last of
     * them happened - so a kind that only started going wrong late in a run is visible as such rather
     * than being indistinguishable from one that never did.
     */
    private JsonValue faultsByOperation() {
        return JsonValue.array(tallies.entrySet().stream()
                .map(entry -> {
                    Kind kind = entry.getKey();
                    Tally tally = entry.getValue();
                    Map<String, JsonValue> row = new LinkedHashMap<>();
                    row.put("operation", JsonValue.of(kind.operation().value()));
                    row.put("code", JsonValue.of(kind.code()));
                    row.put("label", JsonValue.of(tally.category.label()));
                    row.put("count", JsonValue.of(tally.count));
                    row.put("writtenInFull", JsonValue.of(tally.writtenInFull));
                    row.put("named", JsonValue.of(tally.named));
                    row.put("countedOnly",
                            JsonValue.of(tally.count - tally.writtenInFull - tally.named));
                    row.put("firstSeenAt", JsonValue.of(tally.firstSeenAt.toString()));
                    row.put("lastSeenAt", JsonValue.of(tally.lastSeenAt.toString()));
                    return (JsonValue) JsonValue.object(row);
                })
                .toList());
    }

    /**
     * What this report was allowed to keep, and whether a limit actually stopped it.
     *
     * <p>Written into the file so that a reader finding a fault counted but not described knows why,
     * and can tell "this kind had its share" from "the file ran out of room".
     */
    private JsonValue limits() {
        Map<String, JsonValue> limits = new LinkedHashMap<>();
        limits.put("fullEvidencePerOperationAndKind",
                JsonValue.of(FULL_EVIDENCE_PER_OPERATION_AND_KIND));
        limits.put("fullEvidenceBytes", JsonValue.of(FULL_EVIDENCE_BYTES));
        limits.put("namesPerOperationAndKind", JsonValue.of(NAMES_PER_OPERATION_AND_KIND));
        limits.put("namesBytes", JsonValue.of(NAMES_BYTES));
        limits.put("fullEvidenceStoppedEarly", JsonValue.of(fullEvidenceStoppedEarly));
        limits.put("namingStoppedEarly", JsonValue.of(namingStoppedEarly));
        return JsonValue.object(limits);
    }

    private JsonValue totals() {
        Map<String, JsonValue> totals = new LinkedHashMap<>();
        totals.put("requests", JsonValue.of(attempts));
        totals.put("operations", JsonValue.of(operations.size()));
        totals.put("faults", JsonValue.of(faults));
        // The two numbers whose absence made a report of a thousand copies of one fault look like a
        // report of a thousand faults.
        totals.put("faultKinds", JsonValue.of(
                tallies.keySet().stream().map(Kind::code).distinct().count()));
        totals.put("operationsWithFaults", JsonValue.of(
                tallies.keySet().stream().map(Kind::operation).distinct().count()));
        totals.put("faultsWrittenInFull", JsonValue.of(full.size()));
        totals.put("faultsNamed", JsonValue.of(named.size()));
        totals.put("faultsCountedOnly", JsonValue.of(faults - full.size() - named.size()));
        totals.put("elapsed", JsonValue.of(elapsed.toString()));
        return JsonValue.object(totals);
    }

    private JsonValue engineStatistics() {
        Map<String, JsonValue> statistics = new LinkedHashMap<>();
        statistics.put("requestsSent", JsonValue.of(engine.requestsSent()));
        statistics.put("wallClock", JsonValue.of(engine.wallClock().toString()));
        statistics.put("idle", JsonValue.of(engine.idle().toString()));
        statistics.put("idleFraction", JsonValue.of(BigDecimal.valueOf(
                Math.round(engine.idleFraction() * 1000) / 1000.0)));
        statistics.put("meanResponseTime", JsonValue.of(engine.meanResponseTime().toString()));
        statistics.put("peakConcurrency", JsonValue.of(engine.peakConcurrency()));
        statistics.put("concurrencyLimit", JsonValue.of(engine.concurrencyLimit()));
        return JsonValue.object(statistics);
    }

    private JsonValue findingOf(Finding finding) {
        Map<String, JsonValue> entry = new LinkedHashMap<>();
        entry.put("category", categoryOf(finding.category()));
        finding.context().ifPresent(context -> entry.put("context", JsonValue.of(context)));
        entry.put("operation", JsonValue.of(finding.operation().value()));
        entry.put("summary", JsonValue.of(finding.summary()));
        entry.put("details", JsonValue.array(
                finding.details().stream().map(JsonValue::of).map(JsonValue.class::cast).toList()));
        entry.put("curl", JsonValue.of(CurlCommand.of(finding.interaction().request())));
        entry.put("interaction", InteractionDocument.of(finding.interaction()));
        return JsonValue.object(entry);
    }

    /**
     * A fault named rather than written out: which operation, which kind, and the request that
     * caused it.
     *
     * <p>The request is what earns this line its place. The identifier of the attempt is here too,
     * which is the way to find the whole thing in a run that was kept - but a run is only kept when
     * it was asked for, so a line that carried nothing else would be useless in most runs.
     */
    private static JsonValue nameOf(Finding finding) {
        Map<String, JsonValue> entry = new LinkedHashMap<>();
        entry.put("operation", JsonValue.of(finding.operation().value()));
        entry.put("code", JsonValue.of(finding.category().code()));
        entry.put("interactionId", JsonValue.of(finding.interactionId().value()));
        entry.put("method", JsonValue.of(finding.interaction().request().method().name()));
        entry.put("url", JsonValue.of(finding.interaction().request().url()));
        // Absent rather than zero when nothing came back: a status code that was never sent is not
        // a fact about the API, and inventing one would be.
        statusOf(finding.interaction())
                .ifPresent(status -> entry.put("status", JsonValue.of(status)));
        return JsonValue.object(entry);
    }

    /** The status code the API answered with, when it answered at all. */
    private static Optional<Integer> statusOf(Interaction interaction) {
        return switch (interaction.outcome()) {
            case InteractionOutcome.Answered answered ->
                    Optional.of(answered.response().statusCode());
            case InteractionOutcome.MalformedResponse malformed ->
                    malformed.statusLine().map(line -> line.statusCode());
            case InteractionOutcome.TransportFailure ignored -> Optional.empty();
        };
    }

    private static JsonValue categoryOf(FaultCategory category) {
        Map<String, JsonValue> written = new LinkedHashMap<>();
        written.put("code", JsonValue.of(category.code()));
        written.put("descriptiveName", JsonValue.of(category.descriptiveName()));
        written.put("testCaseLabel", JsonValue.of(category.testCaseLabel()));
        written.put("label", JsonValue.of(category.label()));
        return JsonValue.object(written);
    }

    /**
     * Which RESTest wrote this, taken from the packaged build. A copy built from source and run
     * straight out of its build directory carries no such stamp, and says so rather than inventing
     * one.
     */
    private static String toolVersion() {
        String version = JsonReport.class.getPackage().getImplementationVersion();
        return version == null ? "unknown" : version;
    }
}

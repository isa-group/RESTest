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
import io.restest.core.execution.InteractionId;
import io.restest.core.json.InteractionDocument;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.model.OperationId;
import io.restest.core.oracle.FaultCategory;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.WfcFault;
import io.restest.core.settings.ReportSettings;
import io.restest.core.settings.Settings;
import io.restest.core.settings.SettingsInEffect;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
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

    /** How many exact status codes are listed beside the families, commonest first. */
    private static final int STATUS_CODES_LISTED = 10;

    /** One kind of fault, and the class of status code the API answered with when it happened. */
    private record ByStatus(int code, String statusClass) {
    }

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

    /** The faults written out whole, in the order they were found. Already JSON. */
    private final List<JsonValue> writeUps = new ArrayList<>();

    /** How many faults of each kind happened on a reply of each class of status code. */
    private final Map<ByStatus, Integer> faultsByStatus = new LinkedHashMap<>();

    /** How every attempt ended, whether or not anything turned out to be wrong with it. */
    private final Map<String, Integer> repliesByClass = new LinkedHashMap<>();

    /** How many operations made the API fall over, worked out in one place for every report. */
    private final ServerErrors serverErrors = new ServerErrors();
    private final Map<Integer, Integer> repliesByStatus = new LinkedHashMap<>();
    private int attemptsWithNothingWrong;

    /**
     * The attempt currently being judged, and whether anything has been found wrong with it yet.
     *
     * <p>This is how the number of clean attempts is counted without remembering every attempt: what
     * an oracle found in an attempt is announced before the next attempt is, so an attempt is
     * finished with as soon as a different one arrives. Compared by identifier rather than taken on
     * trust from the order, so a fault announced late is simply not counted against the wrong
     * attempt.
     */
    private InteractionId beingJudged;
    private boolean somethingWrongWithIt;

    private final Set<OperationId> operations = new LinkedHashSet<>();
    private String api = "";
    private String baseUrl = "";
    private int attempts;
    private int faults;
    private Duration elapsed = Duration.ZERO;
    private EngineStatistics engine = EngineStatistics.none();
    private JsonValue written;

    private final ReportSettings limits;
    private final SettingsInEffect configuration;

    private JsonReport(Optional<Path> file, Clock clock, SettingsInEffect configuration) {
        this.file = file;
        this.clock = clock;
        this.configuration = configuration;
        this.limits = configuration.settings().report();
    }

    /** A report written to this file when the run finishes, with the limits RESTest ships. */
    public static JsonReport to(Path file) {
        return to(file, SettingsInEffect.of(Settings.defaults()));
    }

    /**
     * A report written to this file when the run finishes.
     *
     * @param file where to write it
     * @param configuration how the run was configured: the report's own limits are read from it,
     *     and the whole of it is written into the file so that a directory of results carries the
     *     settings that produced it
     * @return the report
     */
    public static JsonReport to(Path file, SettingsInEffect configuration) {
        return new JsonReport(Optional.of(Objects.requireNonNull(file, "file")), Clock.systemUTC(),
                Objects.requireNonNull(configuration, "configuration"));
    }

    /** A report that is built but written nowhere, for anyone who only wants to read it back. */
    public static JsonReport inMemory() {
        return new JsonReport(Optional.empty(), Clock.systemUTC(),
                SettingsInEffect.of(Settings.defaults()));
    }

    /** The same, with the clock the report's timestamp comes from, so a test can fix it. */
    public static JsonReport inMemory(Clock clock) {
        return new JsonReport(Optional.empty(), Objects.requireNonNull(clock, "clock"),
                SettingsInEffect.of(Settings.defaults()));
    }

    /** The same, configured, for anyone who wants to read back what a configured run would write. */
    public static JsonReport inMemory(Clock clock, SettingsInEffect configuration) {
        return new JsonReport(Optional.empty(), Objects.requireNonNull(clock, "clock"),
                Objects.requireNonNull(configuration, "configuration"));
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
            case RunEvent.InteractionCompleted completed -> attempted(completed.interaction());
            case RunEvent.FaultFound found -> took(found.finding());
            case RunEvent.RunFinished finished -> {
                // The last attempt is still open when the run ends; nothing else will be said about
                // it, so this is where it is counted.
                closeOffTheAttemptBeingJudged();
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
     * Counts one attempt, however it ended.
     *
     * <p>Every attempt is counted here, not only the ones something was wrong with. A run where nine
     * replies in ten are refusals is a run whose requests are the problem rather than the API, and a
     * report that only ever mentions faults cannot tell anybody that.
     */
    private void attempted(Interaction interaction) {
        attempts++;
        operations.add(interaction.testCase().operation());
        closeOffTheAttemptBeingJudged();
        beingJudged = interaction.id();
        somethingWrongWithIt = false;
        repliesByClass.merge(classOf(interaction), 1, Integer::sum);
        interaction.statusCode().ifPresent(status -> repliesByStatus.merge(status, 1, Integer::sum));
        serverErrors.note(interaction);
    }

    /** Counts the attempt just finished with as clean, if nothing was found wrong with it. */
    private void closeOffTheAttemptBeingJudged() {
        if (beingJudged != null && !somethingWrongWithIt) {
            attemptsWithNothingWrong++;
        }
        beingJudged = null;
    }

    /**
     * Counts one fault, then writes it out whole if its kind still has room.
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
        faultsByStatus.merge(
                new ByStatus(finding.category().code(), classOf(finding.interaction())),
                1, Integer::sum);
        if (finding.interactionId().equals(beingJudged)) {
            somethingWrongWithIt = true;
        }
        writtenWhole(finding, tally);
    }

    /**
     * Which family the API's answer belongs to: {@code 2xx} and the rest, or no answer at all.
     *
     * <p>By family rather than by exact code on purpose. As more kinds of fault are looked for, an
     * exact-code list becomes a long tail of 401, 403, 404, 409, 422 that summarises nothing. The
     * exact codes are still counted, and written beside this.
     *
     * <p>An attempt that got nothing back - a timeout, a closed connection, a reply that was not
     * HTTP - has no status code, and is probably the worst thing an API can do. A count that only
     * knew about codes would lose it, so it has a name of its own here.
     */
    private static String classOf(Interaction interaction) {
        return interaction.statusCode().map(status -> (status / 100) + "xx").orElse("noReply");
    }

    /** Writes the whole attempt out, if this kind of fault on this operation still has room. */
    private void writtenWhole(Finding finding, Tally tally) {
        if (tally.writtenInFull >= limits.writeUpsPerOperationAndKind()
                || writeUps.size() >= limits.writeUpsInTotal()) {
            // Its kind has had its share, or the file has had its. Either way the fault is never
            // turned into JSON at all, which is why a run finding faults by the hundred thousand
            // costs this report almost nothing.
            return;
        }
        writeUps.add(findingOf(finding));
        tally.writtenInFull++;
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

    /**
     * An object of exactly two members, in the order they are written here.
     *
     * <p>Worth a method of its own only because the short way of writing this is wrong.
     * {@code Map.of} decides its order afresh in every process, so the same run written twice
     * produced the same report with its lines rearranged - and comparing this run against last
     * week's, which is much of what a report a machine can read is for, would have shown differences
     * that were not differences.
     */
    private static JsonValue pair(String firstName, JsonValue first, String secondName,
            JsonValue second) {
        Map<String, JsonValue> members = new LinkedHashMap<>();
        members.put(firstName, first);
        members.put(secondName, second);
        return JsonValue.object(members);
    }

    private JsonValue build() {
        Map<String, JsonValue> report = new LinkedHashMap<>();
        report.put("tool", pair("name", JsonValue.of("RESTest"),
                "version", JsonValue.of(toolVersion())));
        report.put("faultCatalogue", pair("name", JsonValue.of(WfcFault.CATALOGUE_NAME),
                "version", JsonValue.of(WfcFault.CATALOGUE_VERSION)));
        report.put("createdAt", JsonValue.of(clock.instant().toString()));
        report.put("api", pair("title", JsonValue.of(api), "baseUrl", JsonValue.of(baseUrl)));
        report.put("totals", totals());
        report.put("limits", limits());
        report.put("settings", settings());
        report.put("engine", engineStatistics());
        report.put("replies", replies());
        report.put("serverErrors", serverErrors());
        report.put("faultsByCategory", faultsByCategory());
        report.put("faultsByStatus", faultsByStatus());
        report.put("faultsByOperation", faultsByOperation());
        report.put("findings", JsonValue.array(List.copyOf(writeUps)));
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
                    row.put("countedOnly", JsonValue.of(tally.count - tally.writtenInFull));
                    row.put("firstSeenAt", JsonValue.of(tally.firstSeenAt.toString()));
                    row.put("lastSeenAt", JsonValue.of(tally.lastSeenAt.toString()));
                    return (JsonValue) JsonValue.object(row);
                })
                .toList());
    }

    /**
     * What this report was allowed to keep.
     *
     * <p>Written into the file so that a reader finding a fault counted but not described knows why,
     * without having to know which version of RESTest wrote it.
     */
    private JsonValue limits() {
        Map<String, JsonValue> written = new LinkedHashMap<>();
        written.put("writeUpsPerOperationAndKind",
                JsonValue.of(limits.writeUpsPerOperationAndKind()));
        written.put("writeUpsInTotal", JsonValue.of(limits.writeUpsInTotal()));
        written.put("mostBodyBytesKept", JsonValue.of(limits.mostBodyBytesKept()));
        return JsonValue.object(written);
    }

    /**
     * How this run was configured, and where each of its settings came from.
     *
     * <p>Every setting there is, not only the ones somebody changed, so that two runs can be
     * compared line by line without either of them having to be re-derived from a shell history.
     * A reader that does not know about settings ignores this.
     */
    private JsonValue settings() {
        return JsonValue.array(configuration.rows().stream()
                .map(row -> {
                    Map<String, JsonValue> stated = new LinkedHashMap<>();
                    stated.put("key", JsonValue.of(row.key().fullName()));
                    stated.put("value", JsonValue.of(row.value()));
                    stated.put("source", JsonValue.of(row.source().written()));
                    return (JsonValue) JsonValue.object(stated);
                })
                .toList());
    }

    /**
     * How every attempt ended, faulty or not.
     *
     * <p>This counts attempts, where everything else in this file counts faults. The two never add
     * up, and are not meant to: one attempt can be found wrong in more than one way, and most
     * attempts are found wrong in no way at all.
     *
     * <p>It is here because it answers a question no list of faults can. If most of a run came back
     * refused, the requests were the problem rather than the API, and knowing that is the difference
     * between fixing the tool and filing bugs against somebody else's service.
     */
    private JsonValue replies() {
        Map<String, JsonValue> replies = new LinkedHashMap<>();
        replies.put("total", JsonValue.of(attempts));
        replies.put("withNothingWrong", JsonValue.of(attemptsWithNothingWrong));
        Map<String, JsonValue> byClass = new LinkedHashMap<>();
        repliesByClass.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> byClass.put(entry.getKey(), JsonValue.of(entry.getValue())));
        replies.put("byClass", JsonValue.object(byClass));
        replies.put("commonest", JsonValue.array(repliesByStatus.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(STATUS_CODES_LISTED)
                .map(entry -> {
                    Map<String, JsonValue> row = new LinkedHashMap<>();
                    row.put("status", JsonValue.of(entry.getKey()));
                    row.put("count", JsonValue.of(entry.getValue()));
                    return (JsonValue) JsonValue.object(row);
                })
                .toList()));
        return JsonValue.object(replies);
    }

    /**
     * How many operations the API fell over on, and how that was counted.
     *
     * <p>Separate from the list of faults, and counted differently on purpose. A fault is a
     * judgement some rule made, and which rules run changes between releases and between tools. A
     * 5xx is something the API did, and it counts whether or not any rule had an opinion about it.
     *
     * <p>How it was counted is written beside the number, because a fault count means nothing next
     * to somebody else's unless both say what they counted. These two are counted the way the
     * benchmarks count them: over replies rather than over faults, and distinct by operation.
     */
    private JsonValue serverErrors() {
        Map<String, JsonValue> counted = new LinkedHashMap<>();
        counted.put("operationsAnswering500",
                JsonValue.of(serverErrors.operationsAnswering500()));
        counted.put("operationsAnsweringAny5xx",
                JsonValue.of(serverErrors.operationsAnsweringAny5xx()));
        counted.put("countedOver", JsonValue.of("replies"));
        counted.put("distinctBy", JsonValue.of("operation"));
        return JsonValue.object(counted);
    }

    /**
     * Faults crossed with the kind of answer that carried them.
     *
     * <p>The table a developer reads first, because a status code is what they already work in. It
     * makes one thing visible at a glance that a list of fault kinds cannot: how many of the faults
     * came back with a perfectly ordinary {@code 200}. Somebody watching their own logs for 500s is
     * not seeing those at all.
     *
     * <p>This counts faults, not attempts - see {@code replies} for the other one.
     */
    private JsonValue faultsByStatus() {
        return JsonValue.array(faultsByStatus.entrySet().stream()
                .map(entry -> {
                    Map<String, JsonValue> row = new LinkedHashMap<>();
                    row.put("code", JsonValue.of(entry.getKey().code()));
                    row.put("label", JsonValue.of(labelOf(entry.getKey().code())));
                    row.put("statusClass", JsonValue.of(entry.getKey().statusClass()));
                    row.put("count", JsonValue.of(entry.getValue()));
                    return (JsonValue) JsonValue.object(row);
                })
                .toList());
    }

    /** The words that go with a catalogue number, taken from the first fault that carried it. */
    private String labelOf(int code) {
        return tallies.values().stream()
                .filter(tally -> tally.category.code() == code)
                .map(tally -> tally.category.label())
                .findFirst()
                .orElse("F" + code);
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
        totals.put("faultsWrittenInFull", JsonValue.of(writeUps.size()));
        totals.put("faultsCountedOnly", JsonValue.of(faults - writeUps.size()));
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
        finding.interaction().statusCode()
                .ifPresent(status -> entry.put("status", JsonValue.of(status)));
        entry.put("statusClass", JsonValue.of(classOf(finding.interaction())));
        entry.put("interaction",
                InteractionDocument.of(finding.interaction(), limits.mostBodyBytesKept()));
        return JsonValue.object(entry);
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

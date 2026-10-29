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

import io.restest.core.auth.Place;
import io.restest.core.event.RunEvent;
import io.restest.core.event.RunListener;
import io.restest.core.event.Unauthorized;
import io.restest.core.event.Waited;
import io.restest.core.exec.EngineStatistics;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionId;
import io.restest.core.json.InteractionDocument;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.SpecificationIssue;
import io.restest.core.oracle.FaultCategory;
import io.restest.core.oracle.FaultReading;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.Severity;
import io.restest.core.oracle.WfcFault;
import io.restest.core.settings.ReportSettings;
import io.restest.core.settings.Settings;
import io.restest.core.settings.SettingsInEffect;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

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
 * <p>It lists every operation the document describes, with what happened to it - tested, never
 * reached, not testable, or left alone by the plan - and how its requests ended, so that an
 * operation that answered every request well can be told from one nobody sent anything to. And it
 * says in one word what the run amounts to ({@link Verdict}), by the rule the number the command ends
 * with follows, so that the file and the number never disagree.
 *
 * <p>It also names every operation the run said it would not try, each with the reason, so that a
 * run which found nothing wrong says what it did not look at - and every list of values the run
 * held, beside any it could not read, so that a run made without the values somebody meant it to
 * have can be told from one made with them.
 *
 * <p>It says whether the run was stopped from outside before it finished - with Ctrl-C, say - since
 * every number in it is then what the run found until it was stopped. It says what the API asked
 * the run to leave alone, and for how long, so that time the run spent sending nothing because the
 * API asked is never read as the tool's own slowness. And it is never half there:
 * it is written in full under another name and then given its own, so a file called by the report's
 * name is always a whole report.
 *
 * <p>Fault kinds are named by number from a catalogue several testing tools share, and the report
 * says which version of that catalogue it used, because a fault code only means something next to
 * the list it was taken from. Beside the number, every fault says whether it is an error or a
 * warning, and what RESTest calls its kind; what to look at is said once for each kind
 * ({@link FaultReading}). The totals count errors and warnings apart, since a build fails on the
 * first and, unless told otherwise, not on the second.
 */
public final class JsonReport implements RunListener {

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

    /**
     * What the command that started the run was handed, beyond its settings: which plan, which file
     * of settings, whether the run is being kept, and which keys the document asks for that nobody
     * handed over.
     *
     * <p>Handed to the report when it is made, as the settings are, rather than announced during the
     * run: it is what the command was told, not something the run did. Never the command line
     * itself, which can carry a key or a token where nothing would know to hide it.
     *
     * @param plan the plan as it was named on the command line, or nothing when the run followed the
     *     plan RESTest carries
     * @param settingsFile the file of settings as it was named on the command line, or nothing
     * @param stored whether every request and reply is being kept, in a file beside the report
     * @param keysNotGiven each key the document asks for that nobody handed over
     * @param keysHeld where each key handed over goes, for the operations the run can test: said
     *     beside a run the API refused, so that whoever reads it knows which key to look at
     */
    public record Invocation(Optional<String> plan, Optional<String> settingsFile, boolean stored,
            List<KeyNotGiven> keysNotGiven, List<Place> keysHeld) {

        public Invocation {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(settingsFile, "settingsFile");
            keysNotGiven = List.copyOf(keysNotGiven);
            keysHeld = List.copyOf(keysHeld);
        }

        /** The same, for a run handed no key. */
        public Invocation(Optional<String> plan, Optional<String> settingsFile, boolean stored,
                List<KeyNotGiven> keysNotGiven) {
            this(plan, settingsFile, stored, keysNotGiven, List.of());
        }

        /** A run started with nothing beyond its settings: RESTest's own plan, kept nowhere. */
        public static Invocation none() {
            return new Invocation(Optional.empty(), Optional.empty(), false, List.of());
        }
    }

    /**
     * A key the API's document asks for that nobody handed over, and how many of the operations
     * the run can test ask for it. Those operations are expected to be refused, which is worth
     * saying beside the faults, so that their refusals are not read as the API's doing.
     *
     * @param scheme the name the document gives the key
     * @param place where it would go in a request
     * @param operations how many of the operations that can be tested ask for it
     */
    public record KeyNotGiven(String scheme, Place place, long operations) {

        public KeyNotGiven {
            Objects.requireNonNull(scheme, "scheme");
            Objects.requireNonNull(place, "place");
        }
    }

    private final Optional<Path> file;
    private final Clock clock;
    private final Invocation invocation;

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

    /**
     * What describes each catalogue number, taken from the first fault that carried it: the one
     * place the key and the words that go beside a number come from, wherever faults are counted by
     * number.
     */
    private final Map<Integer, FaultCategory> kinds = new LinkedHashMap<>();

    /** How many operations made the API fall over, worked out in one place for every report. */
    private final ServerErrors serverErrors = new ServerErrors();

    /** How many replies could not be checked, and why, worked out in one place for every report. */
    private final NotChecked notChecked = new NotChecked();
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

    /**
     * How every attempt ended, whether or not anything turned out to be wrong with it, by the
     * operation it went to: every operation a request went to, in the order the first one went, and
     * how many of its requests ended in each class of status code. The whole run's count is their
     * sum. As long as the document has operations, never as long as the run.
     */
    private final Map<OperationId, Map<String, Integer>> sentTo = new LinkedHashMap<>();

    /** How many attempts came back with a reply, rather than failing on the way. */
    private int answered;

    /** Every operation the document describes that could be read, once the run has said so. */
    private List<Operation> described = List.of();

    /** Every part of the document that could not be used as it is written. */
    private List<SpecificationIssue> issues = List.of();

    /** Every operation the plan asked the run to leave alone, in the order it said so. */
    private final Set<OperationId> setAside = new LinkedHashSet<>();

    /**
     * Every operation the run said it would not try, and why, in the order it said so.
     *
     * <p>Not bounded by a constant, for the same reason the tallies are not: it is as long as the
     * description has operations, never as long as the run.
     */
    private final List<RunEvent.OperationSkipped> skipped = new ArrayList<>();

    /** The lists of values the run holds, in the order it said so. */
    private final List<RunEvent.DictionaryRead> dictionariesRead = new ArrayList<>();

    /** The lists that were handed over and could not be read, in the order the run said so. */
    private final List<RunEvent.DictionaryRefused> dictionariesRefused = new ArrayList<>();
    private String api = "";
    private String baseUrl = "";
    private int attempts;
    private int faults;
    private Duration elapsed = Duration.ZERO;
    private EngineStatistics engine = EngineStatistics.none();
    private boolean cutShort;
    private Optional<Unauthorized> unauthorized = Optional.empty();
    private Optional<Waited> waited = Optional.empty();
    private JsonValue written;
    private String text;

    /** What each stretch of the run achieved, worked out in one place for every report. */
    private final Phases phases = new Phases();

    /** How the run went over the time it was given, worked out in one place for every report. */
    private final Timeline timeline;

    private final ReportSettings limits;
    private final SettingsInEffect configuration;

    private JsonReport(Optional<Path> file, Clock clock, SettingsInEffect configuration,
            Invocation invocation) {
        this.file = file;
        this.clock = clock;
        this.configuration = configuration;
        this.invocation = invocation;
        this.limits = configuration.settings().report();
        this.timeline = new Timeline(limits.timelineIntervals());
    }

    /** A report written to this file when the run finishes, with the limits RESTest ships. */
    public static JsonReport to(Path file) {
        return to(file, SettingsInEffect.of(Settings.defaults()));
    }

    /**
     * A report written to this file when the run finishes, for a run handed nothing beyond its
     * settings.
     *
     * @param file where to write it
     * @param configuration how the run was configured: the report's own limits are read from it,
     *     and the whole of it is written into the file so that a directory of results carries the
     *     settings that produced it
     * @return the report
     */
    public static JsonReport to(Path file, SettingsInEffect configuration) {
        return to(file, configuration, Invocation.none());
    }

    /**
     * A report written to this file when the run finishes.
     *
     * @param file where to write it
     * @param configuration how the run was configured, as above
     * @param invocation what else the command that started the run was handed
     * @return the report
     */
    public static JsonReport to(Path file, SettingsInEffect configuration,
            Invocation invocation) {
        return new JsonReport(Optional.of(Objects.requireNonNull(file, "file")), Clock.systemUTC(),
                Objects.requireNonNull(configuration, "configuration"),
                Objects.requireNonNull(invocation, "invocation"));
    }

    /**
     * Where a report's file is written before it is given its own name, beside the file it becomes:
     * this report's, and the page {@link HtmlReport} writes.
     *
     * <p>The report is written there in full and then renamed in one step, so that a file with the
     * report's name is always a whole report. A program ended outright while it was writing - a
     * run stopped from outside that ran out of time, say - leaves this one behind instead, and never
     * half a report under the name a reader looks for. Whoever clears a directory for a new run has
     * to clear this too.
     *
     * @param file where the report goes
     * @return where it is written first
     */
    public static Path whileBeingWritten(Path file) {
        return WholeFile.whileBeingWritten(file);
    }

    /** A report that is built but written nowhere, for anyone who only wants to read it back. */
    public static JsonReport inMemory() {
        return inMemory(Clock.systemUTC());
    }

    /** The same, with the clock the report's timestamp comes from, so a test can fix it. */
    public static JsonReport inMemory(Clock clock) {
        return inMemory(clock, SettingsInEffect.of(Settings.defaults()));
    }

    /** The same, configured, for anyone who wants to read back what a configured run would write. */
    public static JsonReport inMemory(Clock clock, SettingsInEffect configuration) {
        return inMemory(clock, configuration, Invocation.none());
    }

    /** The same, with what else the command was handed. */
    public static JsonReport inMemory(Clock clock, SettingsInEffect configuration,
            Invocation invocation) {
        return new JsonReport(Optional.empty(), Objects.requireNonNull(clock, "clock"),
                Objects.requireNonNull(configuration, "configuration"),
                Objects.requireNonNull(invocation, "invocation"));
    }

    /** What was written, once the run has finished. Empty before that. */
    public Optional<JsonValue> document() {
        return Optional.ofNullable(written);
    }

    /**
     * What was written, as the text that went into the file, kept rather than written again for
     * whoever needs the same text. Empty before the run has finished.
     */
    public Optional<String> asText() {
        return Optional.ofNullable(text);
    }

    @Override
    public void on(RunEvent event) {
        phases.on(event);
        timeline.on(event);
        switch (event) {
            case RunEvent.RunStarted started -> {
                api = started.api();
                baseUrl = started.baseUrl();
            }
            case RunEvent.DocumentRead read -> {
                described = read.operations();
                issues = read.issues();
            }
            case RunEvent.OperationSkipped skip -> skipped.add(skip);
            case RunEvent.OperationSetAside aside -> setAside.add(aside.operation());
            case RunEvent.DictionaryRead read -> dictionariesRead.add(read);
            case RunEvent.DictionaryRefused refused -> dictionariesRefused.add(refused);
            case RunEvent.InteractionCompleted completed -> attempted(completed.interaction());
            case RunEvent.FaultFound found -> took(found.finding());
            case RunEvent.ReplyNotJudged notJudged -> notChecked.note(notJudged);
            case RunEvent.RunFinished finished -> {
                // The last attempt is still open when the run ends; nothing else will be said about
                // it, so this is where it is counted.
                closeOffTheAttemptBeingJudged();
                elapsed = finished.elapsed();
                engine = finished.engine();
                cutShort = finished.cutShort();
                unauthorized = finished.unauthorized();
                waited = finished.waited();
                finish();
            }
            case RunEvent.TestCasePlanned ignored -> {
                // What was planned and then not sent leaves no evidence to report on.
            }
            case RunEvent.PhaseStarted ignored -> {
                // Tallied above, and written with everything else when the run ends.
            }
            case RunEvent.PhaseFinished ignored -> {
                // The same.
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
        if (interaction.isAnswered()) {
            answered++;
        }
        closeOffTheAttemptBeingJudged();
        beingJudged = interaction.id();
        somethingWrongWithIt = false;
        sentTo.computeIfAbsent(interaction.testCase().operation(), ignored -> new LinkedHashMap<>())
                .merge(StatusClass.of(interaction), 1, Integer::sum);
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
        kinds.putIfAbsent(finding.category().code(), finding.category());
        faultsByStatus.merge(
                new ByStatus(finding.category().code(), StatusClass.of(finding.interaction())),
                1, Integer::sum);
        if (finding.interactionId().equals(beingJudged)) {
            somethingWrongWithIt = true;
        }
        writtenWhole(finding, tally);
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
        text = JsonText.write(written);
        file.ifPresent(path -> WholeFile.write(path, "the report", out -> out.write(text)));
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
        report.put("run", run());
        // Beside what was tested, before any number: a run stopped from outside reports what it
        // found until then, and every count below is to be read as that.
        report.put("cutShort", JsonValue.of(cutShort));
        report.put("verdict", JsonValue.of(verdict().word()));
        report.put("unauthorized", unauthorizedStop());
        report.put("toldToWait", toldToWait());
        report.put("totals", totals());
        report.put("operations", operations());
        report.put("skippedOperations", skippedOperations());
        report.put("documentIssues", documentIssues());
        report.put("keysNotGiven", keysNotGiven());
        report.put("notChecked", notChecked());
        report.put("dictionaries", dictionaries());
        report.put("limits", limits());
        report.put("settings", settings());
        report.put("engine", engineStatistics());
        report.put("phases", phases());
        report.put("timeline", timeline());
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
        Map<Integer, Integer> byNumber = new LinkedHashMap<>();
        tallies.forEach((kind, tally) -> byNumber.merge(kind.code(), tally.count, Integer::sum));
        return JsonValue.array(byNumber.entrySet().stream()
                .map(entry -> {
                    FaultCategory kind = kinds.get(entry.getKey());
                    Map<String, JsonValue> counted = new LinkedHashMap<>(((JsonValue.JsonObject)
                            categoryOf(kind)).members());
                    // Said here, once for each kind, rather than under every fault written out:
                    // a run can write out a thousand faults of one kind, and the advice is the
                    // same for each.
                    FaultReading.of(kind).advice()
                            .ifPresent(advice -> counted.put("advice", JsonValue.of(advice)));
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
                    namedFault(row, tally.category);
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
     * What the command was handed beyond its settings: which plan and which file of settings, as
     * they were named, and whether the run is being kept. A plan or a file nobody named is written
     * as {@code null}: the run followed the plan RESTest carries, or no file of settings was read.
     */
    private JsonValue run() {
        Map<String, JsonValue> run = new LinkedHashMap<>();
        run.put("plan", orNull(invocation.plan()));
        run.put("settingsFile", orNull(invocation.settingsFile()));
        run.put("stored", JsonValue.of(invocation.stored()));
        return JsonValue.object(run);
    }

    private static JsonValue orNull(Optional<String> text) {
        return text.<JsonValue>map(JsonValue::of).orElse(JsonValue.NULL);
    }

    /**
     * What the run amounts to, by the rule the number the command ends with follows, so that the
     * two never disagree.
     *
     * <p>Counted over the attempts this report heard of, sent and either answered or not. A request
     * still owed an answer when the run gave up waiting is never heard of here, so a run in which
     * every request was still owed reads as having sent nothing; it ends with the same number as one
     * whose requests were all left unanswered.
     */
    private Verdict verdict() {
        return Verdict.of(Verdict.Stop.of(cutShort, unauthorized.isPresent()), attempts, answered,
                faultsOf(Severity.ERROR), faultsOf(Severity.WARNING),
                configuration.settings().checks().failOnWarnings());
    }

    /**
     * Why the run stopped sending early, when the API answered 401 to every request of its opening
     * lap: when it decided to, how many requests that lap sent, and where each key the run held went,
     * written as {@link #keysNotGiven()} writes the keys nobody handed over. Never the key itself,
     * which the report is never handed. Nothing at all when the API did not stop the run.
     *
     * <p>Said as it stood when the run decided: the requests still on their way then are waited for,
     * as at the end of a run's time, and counted with the rest, whatever they bring back.
     */
    private JsonValue unauthorizedStop() {
        return unauthorized.<JsonValue>map(stop -> {
            Map<String, JsonValue> row = new LinkedHashMap<>();
            row.put("at", JsonValue.of(stop.at().toString()));
            row.put("lapRequests", JsonValue.of(stop.lapRequests()));
            row.put("keysHeld", JsonValue.array(invocation.keysHeld().stream()
                    .map(place -> (JsonValue) pair("in", JsonValue.of(place.where().reportWord()),
                            "name", JsonValue.of(place.name())))
                    .toList()));
            return JsonValue.object(row);
        }).orElse(JsonValue.NULL);
    }

    /**
     * What the API asked the run to leave alone, and for how long: how much of the run's idle time
     * that accounted for, how often and how long nothing at all was sent, and each operation that
     * was told to wait, in the order they first were. Nothing at all when the run does not wait when
     * told to; a run that does and never was says so with zeros and no operation.
     */
    private JsonValue toldToWait() {
        return waited.<JsonValue>map(said -> {
            Map<String, JsonValue> block = new LinkedHashMap<>();
            block.put("idle", JsonValue.of(said.idle().toString()));
            block.put("wholeApi", pair("times", JsonValue.of(said.wholeApi().times()),
                    "waited", JsonValue.of(said.wholeApi().waited().toString())));
            block.put("operations", JsonValue.array(said.operations().stream()
                    .map(each -> {
                        Map<String, JsonValue> row = new LinkedHashMap<>();
                        row.put("operation", JsonValue.of(each.operation().value()));
                        row.put("times", JsonValue.of(each.times()));
                        row.put("stated", JsonValue.of(each.stated()));
                        row.put("waited", JsonValue.of(each.waited().toString()));
                        row.put("turnsSkipped", JsonValue.of(each.turnsSkipped()));
                        return (JsonValue) JsonValue.object(row);
                    })
                    .toList()));
            return JsonValue.object(block);
        }).orElse(JsonValue.NULL);
    }

    /**
     * Every operation the document describes, with what happened to it and how its requests ended.
     *
     * <p>The operations that could be read come first, in the order the document gives them; then
     * the ones that could not be read, in the order the run named them, with no method or path,
     * which is what could not be read. An operation a request went to that neither list names - a
     * run whose document was never said to have been read - comes last, so that what is written
     * still adds up to the totals.
     */
    private JsonValue operations() {
        Map<OperationId, String> whyNot = new LinkedHashMap<>();
        skipped.forEach(skip -> whyNot.putIfAbsent(skip.operation(), skip.reason()));
        List<JsonValue> rows = new ArrayList<>();
        Set<OperationId> written = new HashSet<>();
        for (Operation operation : described) {
            rows.add(operation(operation.id(), Optional.of(operation), whyNot));
            written.add(operation.id());
        }
        Stream.concat(whyNot.keySet().stream(), sentTo.keySet().stream())
                .filter(written::add)
                .forEach(id -> rows.add(operation(id, Optional.empty(), whyNot)));
        return JsonValue.array(rows);
    }

    private JsonValue operation(OperationId id, Optional<Operation> read,
            Map<OperationId, String> whyNot) {
        Map<String, JsonValue> row = new LinkedHashMap<>();
        row.put("operation", JsonValue.of(id.value()));
        read.ifPresent(operation -> {
            row.put("method", JsonValue.of(operation.method().name()));
            row.put("path", JsonValue.of(operation.path()));
            operation.summary().ifPresent(summary -> row.put("summary", JsonValue.of(summary)));
        });
        Map<String, Integer> byClass = sentTo.getOrDefault(id, Map.of());
        OperationState state = sentTo.containsKey(id) ? OperationState.TESTED
                : whyNot.containsKey(id) ? OperationState.COULD_NOT_BE_TESTED
                : setAside.contains(id) ? OperationState.LEFT_ALONE_BY_THE_PLAN
                : OperationState.NOT_REACHED;
        row.put("state", JsonValue.of(state.word()));
        if (state == OperationState.COULD_NOT_BE_TESTED) {
            row.put("reason", JsonValue.of(whyNot.get(id)));
        }
        row.put("requests", JsonValue.of(byClass.values().stream().mapToInt(Integer::intValue).sum()));
        row.put("replies", JsonValue.object(Map.of("byClass", byClass(byClass))));
        return JsonValue.object(row);
    }

    /**
     * Every part of the document that could not be used as it is written: where it is, what was
     * wrong, the operation it belongs to when it belongs to one, and what it cost.
     */
    private JsonValue documentIssues() {
        return JsonValue.array(issues.stream()
                .map(issue -> {
                    Map<String, JsonValue> row = new LinkedHashMap<>();
                    row.put("location", JsonValue.of(issue.location()));
                    row.put("message", JsonValue.of(issue.message()));
                    issue.operation().ifPresent(operation ->
                            row.put("operation", JsonValue.of(operation.value())));
                    row.put("effect", JsonValue.of(issue.effect().word()));
                    return (JsonValue) JsonValue.object(row);
                })
                .toList());
    }

    /**
     * Every key the document asks for that nobody handed over, with where it would go and how many
     * of the operations that can be tested ask for it.
     */
    private JsonValue keysNotGiven() {
        return JsonValue.array(invocation.keysNotGiven().stream()
                .map(key -> {
                    Map<String, JsonValue> row = new LinkedHashMap<>();
                    row.put("scheme", JsonValue.of(key.scheme()));
                    row.put("in", JsonValue.of(key.place().where().reportWord()));
                    row.put("name", JsonValue.of(key.place().name()));
                    row.put("operations", JsonValue.of(key.operations()));
                    return (JsonValue) JsonValue.object(row);
                })
                .toList());
    }

    /**
     * Every operation the run said it would not try, each with the reason.
     *
     * <p>All of them, however many there are, where the screen names only the first few. Without
     * this, a run that tested eleven of twenty operations and found nothing wrong reads the same as
     * one that tested all twenty: the totals count what was tried, and only this says what was
     * not. Empty when nothing was said to be skipped - which includes a plan leaving some
     * operations alone on purpose, since that is not the same thing as their being skipped. An
     * operation the description could not be read for at all is listed whatever the plan says,
     * because there was no operation for the plan to judge.
     */
    private JsonValue skippedOperations() {
        return JsonValue.array(skipped.stream()
                .map(skip -> pair("operation", JsonValue.of(skip.operation().value()),
                        "reason", JsonValue.of(skip.reason())))
                .toList());
    }

    /**
     * How many replies each check could not judge, and why, with the operations they came from.
     *
     * <p>Beside the operations that were not tried, because it says the same kind of thing about
     * replies: what a run that found nothing wrong did not look at. One row for each check and
     * reason, most replies first; every operation the replies came from, most first. Empty when
     * every reply was checked in every way it could have been.
     */
    private JsonValue notChecked() {
        return JsonValue.array(notChecked.lines().stream()
                .map(line -> {
                    Map<String, JsonValue> row = new LinkedHashMap<>();
                    named(row, line.check().kind());
                    row.put("what", JsonValue.of(line.check().what()));
                    row.put("reason", JsonValue.of(line.check().why().word()));
                    row.put("why", JsonValue.of(line.check().why().inWords()));
                    row.put("replies", JsonValue.of(line.replies()));
                    row.put("operations", JsonValue.array(line.operations().entrySet().stream()
                            .map(operation -> pair("operation",
                                    JsonValue.of(operation.getKey().value()),
                                    "replies", JsonValue.of(operation.getValue())))
                            .toList()));
                    return (JsonValue) JsonValue.object(row);
                })
                .toList());
    }

    /**
     * The lists of values the run held, with where each came from, and the ones it could not read.
     *
     * <p>A list that cannot be read costs the run its values and does not end it, so a run made
     * without a list somebody meant it to have finishes like any other. Its results alone look
     * exactly like a run that had the list and found it did not help. This is where anything
     * reading the file can tell the two apart: a file it handed over is either named under
     * {@code read} or explained under {@code refused}. Being held is not being drawn on - that is
     * the plan's to decide - but a list that was never read certainly was not.
     */
    private JsonValue dictionaries() {
        return pair("read", JsonValue.array(dictionariesRead.stream()
                        .map(read -> pair("name", JsonValue.of(read.name()),
                                "from", JsonValue.of(read.from())))
                        .toList()),
                "refused", JsonValue.array(dictionariesRefused.stream()
                        .map(refused -> pair("from", JsonValue.of(refused.from()),
                                "reason", JsonValue.of(refused.reason())))
                        .toList()));
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
        Map<String, Integer> everyReply = new LinkedHashMap<>();
        sentTo.values().forEach(operation ->
                operation.forEach((family, count) -> everyReply.merge(family, count, Integer::sum)));
        replies.put("byClass", byClass(everyReply));
        replies.put("commonest", JsonValue.array(repliesByStatus.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(limits.commonestStatusCodesListed())
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
                    namedFault(row, kinds.get(entry.getKey().code()));
                    row.put("statusClass", JsonValue.of(entry.getKey().statusClass()));
                    row.put("count", JsonValue.of(entry.getValue()));
                    return (JsonValue) JsonValue.object(row);
                })
                .toList());
    }

    /** A kind of fault as a row of a count writes it: its number, the catalogue's key, its label. */
    private static void named(Map<String, JsonValue> row, FaultCategory category) {
        row.put("code", JsonValue.of(category.code()));
        row.put("id", JsonValue.of(category.id()));
        row.put("label", JsonValue.of(category.label()));
    }

    /**
     * The same, for a row counting faults: with whether its kind is an error or a warning, and what
     * RESTest calls it, so that a program can pick out the errors without a table of its own.
     */
    private static void namedFault(Map<String, JsonValue> row, FaultCategory category) {
        named(row, category);
        readAs(row, category);
    }

    /** Whether a kind of fault is an error or a warning, and what RESTest calls it. */
    private static void readAs(Map<String, JsonValue> row, FaultCategory category) {
        FaultReading reading = FaultReading.of(category);
        row.put("severity", JsonValue.of(reading.severity().word()));
        row.put("title", JsonValue.of(reading.title()));
    }

    private JsonValue totals() {
        Map<String, JsonValue> totals = new LinkedHashMap<>();
        totals.put("requests", JsonValue.of(attempts));
        totals.put("operations", JsonValue.of(sentTo.size()));
        totals.put("faults", JsonValue.of(faults));
        // The two numbers whose absence made a report of a thousand copies of one fault look like a
        // report of a thousand faults.
        totals.put("faultKinds", JsonValue.of(
                tallies.keySet().stream().map(Kind::code).distinct().count()));
        totals.put("operationsWithFaults", JsonValue.of(
                tallies.keySet().stream().map(Kind::operation).distinct().count()));
        // Errors and warnings apart, since a build fails on the first and, unless told otherwise,
        // not on the second. An operation with both is counted under both.
        totals.put("errors", JsonValue.of(faultsOf(Severity.ERROR)));
        totals.put("warnings", JsonValue.of(faultsOf(Severity.WARNING)));
        totals.put("operationsWithErrors", JsonValue.of(operationsWith(Severity.ERROR)));
        totals.put("operationsWithWarnings", JsonValue.of(operationsWith(Severity.WARNING)));
        totals.put("faultsWrittenInFull", JsonValue.of(writeUps.size()));
        totals.put("faultsCountedOnly", JsonValue.of(faults - writeUps.size()));
        totals.put("elapsed", JsonValue.of(elapsed.toString()));
        return JsonValue.object(totals);
    }

    /**
     * Each stretch of the run that had a purpose of its own, and what it achieved.
     *
     * <p>A run that begins by sending every operation once is spending the first seconds of its
     * budget on that, and this is where a reader finds out what those seconds bought: how long the
     * round took, how many requests it sent, and how many of the API's operations answered it with
     * a success. Empty when the run had no such stretch.
     */
    private JsonValue phases() {
        return JsonValue.array(phases.all().stream()
                .map(phase -> {
                    Map<String, JsonValue> row = new LinkedHashMap<>();
                    row.put("name", JsonValue.of(phase.name()));
                    row.put("startedAt", JsonValue.of(phase.startedAt().toString()));
                    row.put("finishedAt", JsonValue.of(phase.finishedAt().toString()));
                    row.put("elapsed", JsonValue.of(phase.elapsed().toString()));
                    row.put("cutShort", JsonValue.of(phase.cutShort()));
                    row.put("requests", JsonValue.of(phase.requests()));
                    row.put("operations", JsonValue.of(phase.operations()));
                    row.put("operationsAnswering2xx",
                            JsonValue.of(phase.operationsAnsweredWithASuccess()));
                    row.put("replies", JsonValue.object(Map.of("byClass",
                            byClass(phase.repliesByClass()))));
                    return (JsonValue) JsonValue.object(row);
                })
                .toList());
    }

    /**
     * How the run went over the time it was given: when it began, how long it had, and what came
     * back in each equal slice of that time, by family of status code.
     *
     * <p>What a report draws as a timeline, with the stretches above marked on it. The slices add up
     * to {@code replies.byClass}: each reply is counted in the slice its request was sent in. Only
     * the slices that began before the run ended are written, so a run stopped early is not drawn as
     * one that went on sending nothing.
     */
    private JsonValue timeline() {
        Map<String, JsonValue> written = new LinkedHashMap<>();
        written.put("startedAt", JsonValue.of(timeline.startedAt().toString()));
        written.put("budget", JsonValue.of(timeline.budget().toString()));
        written.put("interval", JsonValue.of(timeline.interval().toString()));
        written.put("intervals", JsonValue.array(timeline.intervals().stream()
                .map(slice -> (JsonValue) JsonValue.object(Map.of("byClass", byClass(slice))))
                .toList()));
        return JsonValue.object(written);
    }

    /**
     * Replies counted by family of status code, written in the order of the families - {@code 2xx}
     * before {@code 4xx}, and no reply at all last - wherever the file counts them: for the whole
     * run, for a stretch of it, for a slice of its time. One way of writing them, so that the counts
     * of the parts can be laid beside the count of the whole.
     */
    private static JsonValue byClass(Map<String, Integer> counted) {
        Map<String, JsonValue> byClass = new LinkedHashMap<>();
        new TreeMap<>(counted).forEach((family, count) -> byClass.put(family, JsonValue.of(count)));
        return JsonValue.object(byClass);
    }

    private JsonValue engineStatistics() {
        Map<String, JsonValue> statistics = new LinkedHashMap<>();
        statistics.put("requestsSent", JsonValue.of(engine.requestsSent()));
        statistics.put("wallClock", JsonValue.of(engine.wallClock().toString()));
        statistics.put("idle", JsonValue.of(engine.idle().toString()));
        statistics.put("idleFraction", JsonValue.of(BigDecimal.valueOf(
                Math.round(engine.idleFraction() * 1000) / 1000.0)));
        statistics.put("idleKeepingToTheRate",
                JsonValue.of(engine.idleKeepingToTheRate().toString()));
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
        entry.put("statusClass", JsonValue.of(StatusClass.of(finding.interaction())));
        entry.put("interaction",
                InteractionDocument.of(finding.interaction(), limits.mostBodyBytesKept()));
        return JsonValue.object(entry);
    }

    private static JsonValue categoryOf(FaultCategory category) {
        Map<String, JsonValue> written = new LinkedHashMap<>();
        written.put("code", JsonValue.of(category.code()));
        written.put("id", JsonValue.of(category.id()));
        written.put("descriptiveName", JsonValue.of(category.descriptiveName()));
        written.put("testCaseLabel", JsonValue.of(category.testCaseLabel()));
        written.put("label", JsonValue.of(category.label()));
        readAs(written, category);
        return JsonValue.object(written);
    }

    /** Every operation and kind that went wrong, of the kinds of this severity. */
    private Stream<Map.Entry<Kind, Tally>> talliesOf(Severity severity) {
        return tallies.entrySet().stream()
                .filter(entry -> FaultReading.of(entry.getValue().category).severity() == severity);
    }

    /** How many faults were of kinds of this severity. */
    private long faultsOf(Severity severity) {
        return talliesOf(severity).mapToLong(entry -> entry.getValue().count).sum();
    }

    /** How many operations had at least one fault of a kind of this severity. */
    private long operationsWith(Severity severity) {
        return talliesOf(severity).map(entry -> entry.getKey().operation()).distinct().count();
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

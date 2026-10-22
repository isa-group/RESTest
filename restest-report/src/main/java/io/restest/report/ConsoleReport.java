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
import io.restest.core.execution.Intent;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionOutcome;
import io.restest.core.execution.StatusCodes;
import io.restest.core.execution.TestCase;
import io.restest.core.model.OperationId;
import io.restest.core.oracle.FaultCategory;
import io.restest.core.oracle.FaultReading;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.Severity;
import io.restest.core.settings.CheckSettings;
import io.restest.core.settings.ReportSettings;
import java.io.Flushable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Prints what a run found, in the terminal, as it finds it.
 *
 * <p>Each fault appears the moment it is found, rather than everything appearing at the end, so
 * that a long run against a slow API is something a person can watch rather than wait out. Each one
 * is printed as five things: whether it is an error or a warning and what kind of fault it is, in
 * RESTest's own words with the catalogue's number after them, which operation it happened on, what
 * the API answered, what is wrong in one line, and the {@code curl} command that does it again.
 * {@link FaultReading} says how each kind is read.
 *
 * <p>That last line is the point of the whole report. The first question anybody asks of a testing
 * tool is "is that actually true?", and the answer should cost one paste into a terminal.
 *
 * <p>At the end it prints how much was done, how the API answered across the whole run, what was
 * found - the errors first, then the warnings, each kind with a line on what to look at - and how
 * much of the time was spent waiting for the API rather than working - a run that
 * spends its time idle is a run doing less testing than it looks like it is. The line of status
 * codes is worth a glance even when nothing was found wrong: a run where almost everything came back
 * refused is a run whose requests were the problem, not the API. Last come the operations the run
 * said it would not try - the first few by name, with the reason - because finding nothing wrong
 * says nothing about them, and then any list of values that was handed over and could not be read,
 * because a run made without it is not the run somebody asked for. Between the verdict and those
 * comes how many replies the run could not check, in which way and why, because "no faults found"
 * says nothing about a reply nobody could judge.
 *
 * <p>It writes wherever it is told to write, rather than to the screen directly, so a test can read
 * back exactly what a person would have seen. While a run lasts it is the only thing writing there:
 * what the command running it has to say before the run begins is handed to it, and printed under
 * the line naming the API, because anything else writing to the same place at the same time would
 * land among its lines in whichever order the two happened to arrive.
 */
public final class ConsoleReport implements RunListener {

    /** How many characters the word saying whether a fault is an error or a warning takes. */
    private static final int SEVERITY_COLUMN = 9;

    /** How far the lines under a fault's first line are indented: under its title. */
    private static final String UNDER = " ".repeat(SEVERITY_COLUMN);

    private final Appendable out;

    /** How many faults of each kind, by its number, in the order the kinds were first found. */
    private final Map<Integer, Integer> counts = new LinkedHashMap<>();

    /** The kind each number was first found as, for the words that go with it. */
    private final Map<Integer, FaultCategory> kinds = new LinkedHashMap<>();

    /** The operations that had at least one error, and at least one warning. */
    private final Set<OperationId> operationsWithErrors = new LinkedHashSet<>();
    private final Set<OperationId> operationsWithWarnings = new LinkedHashSet<>();

    /** How every attempt ended, faulty or not, by family of status code. */
    private final Map<String, Integer> repliesByClass = new LinkedHashMap<>();
    private final Set<OperationId> operations = new LinkedHashSet<>();

    /** How many operations made the API fall over, worked out in one place for every report. */
    private final ServerErrors serverErrors = new ServerErrors();

    /** What each stretch of the run achieved, worked out in one place for every report. */
    private final Phases phases = new Phases();

    /** How many replies could not be checked, and why, worked out in one place for every report. */
    private final NotChecked notChecked = new NotChecked();
    private int attempts;
    private int faults;
    private int errors;
    private int warnings;

    /** How many faults were printed in full, and how many of those were warnings. */
    private int printed;
    private int warningsPrinted;

    /** Whether the screen has said it stopped printing faults, and stopped printing warnings. */
    private boolean saidNoMoreFaults;
    private boolean saidNoMoreWarnings;

    /** How many requests were built to push at the API with awkward values throughout. */
    private long awkward;

    /** How many were made by changing one thing in a request the API had accepted. */
    private long changed;

    /**
     * How many steps of series about a thing the run created were sent, by the kind of series, in
     * the order the kinds were first heard of: every creation the API accepted, and every step
     * after one.
     */
    private final Map<String, Long> stepsByKindOfSeries = new LinkedHashMap<>();

    /** How many series began: how many creations meant to begin one the API accepted. */
    private long seriesBegun;

    /** How many creations meant to begin a series the API did not accept, so that none began. */
    private long seriesNotBegun;

    /** How many faults to print in full before the screen stops being the right place for them. */
    private final int faultsShown;

    /** And how many of those may be warnings, so that errors found later still have room. */
    private final int warningsShown;

    /** Whether warnings end the run with 1, as errors do; the summary says which way it is. */
    private final boolean warningsFail;

    /** The operations the run said it would not try, in the order it said so. */
    private final List<RunEvent.OperationSkipped> skipped = new ArrayList<>();

    /** The lists of values that were handed over and could not be read, in the order said. */
    private final List<RunEvent.DictionaryRefused> refusedDictionaries = new ArrayList<>();

    /** How many of those to name before only counting the rest. */
    private final int skippedShown;

    /** How many particulars are printed under one fault before the rest are counted. */
    private final int detailsShown;

    /** What the command running this says before the run begins, printed under the first line. */
    private final String introduction;

    private ConsoleReport(Appendable out, ReportSettings settings, CheckSettings checks,
            String introduction) {
        this.out = Objects.requireNonNull(out, "out");
        this.introduction = Objects.requireNonNull(introduction, "introduction");
        this.faultsShown = settings.faultsShownOnTheConsole();
        this.warningsShown = settings.warningsShownOnTheConsole();
        this.warningsFail = checks.failOnWarnings();
        this.skippedShown = settings.skippedOperationsShownOnTheConsole();
        this.detailsShown = settings.detailsShownPerFault();
    }

    /** A report that writes wherever you tell it to. */
    public static ConsoleReport to(Appendable out) {
        return new ConsoleReport(out, ReportSettings.defaults(), CheckSettings.defaults(), "");
    }

    /**
     * A report that writes wherever you tell it to, and is told how many faults belong on a
     * screen.
     *
     * <p>Part of a run is spent on requests built from values nobody sensible would send, and part
     * on requests the API accepted sent again with one thing broken. Whatever those earn - a
     * refusal, or an acceptance, or the API falling over - lands in the same counts as everything
     * else, and a summary that did not separate them would read as though the API were behaving
     * that way towards ordinary traffic. Each request says which kind it was, and the summary
     * counts them by that.
     *
     * <p>A run that keeps testing for as long as it was given will ask an API the same question
     * thousands of times, and an API that is broken is broken every time. Printing all of them
     * scrolls everything worth reading off the top of the screen, so past the number given here the
     * screen says so once and stops. The count at the end is of all of them, printed or not.
     *
     * @param out where to write
     * @param settings how much of what was found belongs on a screen
     * @return the report
     */
    public static ConsoleReport to(Appendable out, ReportSettings settings) {
        return to(out, settings, "");
    }

    /**
     * The same, handed what the command running it has to say before the run begins.
     *
     * <p>That text - how many operations can be tested, the seed, the budget, what could not be
     * read - is printed straight under the line naming the API and the address, exactly as given.
     * It is handed over rather than printed by the command itself because this report writes that
     * line when it is told the run has begun, which happens alongside the command rather than
     * before it: two writers on one screen put their lines there in either order, and sometimes
     * one inside the other.
     *
     * @param out where to write
     * @param settings how much of what was found belongs on a screen
     * @param introduction the lines to print under the first one, each ending in a line break;
     *     empty for none
     * @return the report
     */
    public static ConsoleReport to(Appendable out, ReportSettings settings, String introduction) {
        return to(out, settings, CheckSettings.defaults(), introduction);
    }

    /**
     * The same, told whether warnings end the run with 1 as errors do, so that the summary can say
     * which way it is: a run that found only warnings and ends as if it found nothing wrong should
     * say why, and how to make it end otherwise.
     *
     * @param out where to write
     * @param settings how much of what was found belongs on a screen
     * @param checks the checks the run makes, and whether warnings fail it
     * @param introduction the lines to print under the first one, each ending in a line break;
     *     empty for none
     * @return the report
     */
    public static ConsoleReport to(Appendable out, ReportSettings settings, CheckSettings checks,
            String introduction) {
        return new ConsoleReport(out, Objects.requireNonNull(settings, "settings"),
                Objects.requireNonNull(checks, "checks"), introduction);
    }

    /**
     * How many faults this run found - all of them, errors and warnings, including the ones past the
     * limit that were counted rather than printed. A run that found two thousand faults and showed
     * fifty still found two thousand.
     */
    public int faults() {
        return faults;
    }

    /**
     * How many of those were errors: the API failing at its job. The command's answer to whoever ran
     * it is decided from this, and from the next.
     */
    public int errors() {
        return errors;
    }

    /** How many were warnings: the API answering something its document does not describe. */
    public int warnings() {
        return warnings;
    }

    @Override
    public void on(RunEvent event) {
        phases.on(event);
        switch (event) {
            case RunEvent.RunStarted started -> {
                began(started);
                sendOnItsWay();
            }
            case RunEvent.OperationSkipped skip -> {
                // Said with the summary rather than as heard: the list qualifies the verdict, so it
                // is printed beside it.
                skipped.add(skip);
            }
            case RunEvent.DictionaryRead ignored -> {
                // Nothing to say about a list that was read: it is what was asked for.
            }
            case RunEvent.DictionaryRefused refused -> {
                // Said as the run starts by whoever read the file, and said again here with the
                // summary, because the first time is one line among many and easy to miss.
                refusedDictionaries.add(refused);
            }
            case RunEvent.InteractionCompleted completed -> {
                attempts++;
                TestCase sent = completed.interaction().testCase();
                if (sent.intent() == Intent.PUSHING) {
                    awkward++;
                }
                if (sent.mutation().isPresent()) {
                    changed++;
                }
                sent.sequence().ifPresent(step -> {
                    // A creation refused made nothing for a series to be about, so it is no step
                    // of one: it is an ordinary request that did not work.
                    if (step.step() == 1 && !theApiAccepted(completed.interaction())) {
                        seriesNotBegun++;
                        return;
                    }
                    stepsByKindOfSeries.merge(step.shape(), 1L, Long::sum);
                    if (step.step() == 1) {
                        seriesBegun++;
                    }
                });
                operations.add(completed.interaction().testCase().operation());
                repliesByClass.merge(classOf(completed.interaction()), 1, Integer::sum);
                serverErrors.note(completed.interaction());
            }
            case RunEvent.FaultFound found -> {
                print(found.finding());
                sendOnItsWay();
            }
            case RunEvent.ReplyNotJudged notJudged -> {
                // Counted as heard and said once, with the summary: it qualifies the verdict, so it
                // is printed beside it, and one line per reply would bury the faults.
                notChecked.note(notJudged);
            }
            case RunEvent.RunFinished finished -> {
                summarise(finished);
                sendOnItsWay();
            }
            case RunEvent.TestCasePlanned ignored -> {
                // A planned test is not news until it has been sent; printing one line per plan
                // would bury the faults, which are what this report exists to show.
            }
            case RunEvent.PhaseStarted ignored -> {
                // Tallied above, and said once in the summary rather than as it happens.
            }
            case RunEvent.PhaseFinished ignored -> {
                // The same.
            }
        }
    }

    private void began(RunEvent.RunStarted started) {
        write("RESTest testing " + started.api() + " at " + started.baseUrl());
        write("");
        try {
            out.append(introduction);
            // A last line left open would have the next thing printed carry straight on from it.
            if (!introduction.isEmpty() && !introduction.endsWith("\n")) {
                out.append(System.lineSeparator());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("the report could not be written", e);
        }
    }

    private void print(Finding finding) {
        FaultCategory kind = finding.category();
        FaultReading reading = FaultReading.of(kind);
        boolean warning = reading.severity() == Severity.WARNING;
        faults++;
        counts.merge(kind.code(), 1, Integer::sum);
        kinds.putIfAbsent(kind.code(), kind);
        if (warning) {
            warnings++;
            operationsWithWarnings.add(finding.operation());
        } else {
            errors++;
            operationsWithErrors.add(finding.operation());
        }
        // Two limits, each said once, the first time it stops a fault: how many faults the screen
        // has room for, and how many of those may be warnings. Without the second, a document that
        // leaves out the ways its API refuses a request fills the screen with warnings from the first
        // requests on, and an API falling over later never reaches it.
        if (printed >= faultsShown) {
            if (!saidNoMoreFaults) {
                saidNoMoreFaults = true;
                write("... more faults are being found; every one of them is counted in the run's "
                        + "report and in the total below");
                write("");
            }
            return;
        }
        if (warning && warningsPrinted >= warningsShown) {
            if (!saidNoMoreWarnings) {
                saidNoMoreWarnings = true;
                write("... more warnings are being found; every one of them is counted in the "
                        + "run's report and in the total below");
                write("");
            }
            return;
        }
        printed++;
        if (warning) {
            warningsPrinted++;
        }
        write(severityColumn(reading.severity()) + reading.title() + "  (" + label(kind) + ")");
        write(UNDER + finding.operation() + " - " + finding.interaction().request().method()
                + " " + finding.interaction().request().url()
                + "  ->  " + answerTo(finding.interaction()));
        write(UNDER + finding.summary());
        List<String> details = finding.details();
        for (String detail : details.subList(0, Math.min(details.size(), detailsShown))) {
            write(UNDER + "  " + detail);
        }
        if (details.size() > detailsShown) {
            write(UNDER + "  ... and " + (details.size() - detailsShown) + " more");
        }
        // A step of a series may only fail because of the steps before it - a thing read after it
        // was deleted - and the command below sends this one alone, so say so rather than hand
        // over a command that answers differently. The creation a series begins with has nothing
        // before it, and its command repeats it as it was.
        finding.interaction().testCase().sequence().filter(step -> step.step() > 1)
                .ifPresent(step -> write(UNDER + "step " + step.step() + " of a " + step.shape()
                        + " series: " + step.description() + ". The command below sends this "
                        + "step alone, without the ones before it"));
        write(UNDER + CurlCommand.of(finding.interaction().request()));
        write("");
    }

    /** The word saying how serious a fault is, padded so that every title starts in one column. */
    private static String severityColumn(Severity severity) {
        String word = severity.word();
        return word + " ".repeat(Math.max(2, SEVERITY_COLUMN - word.length()));
    }

    /** Whether the API answered with a success. */
    private static boolean theApiAccepted(Interaction interaction) {
        return interaction.statusCode().filter(StatusCodes::isSuccess).isPresent();
    }

    /**
     * What the API answered, in the words a developer already works in.
     *
     * <p>The first thing anybody asks of a reported fault is "what did it return", and until now
     * this line did not say. An attempt that got nothing back says so rather than showing a code it
     * never received.
     */
    private static String answerTo(Interaction interaction) {
        return switch (interaction.outcome()) {
            case InteractionOutcome.Answered answered ->
                    String.valueOf(answered.response().statusCode());
            case InteractionOutcome.MalformedResponse malformed -> malformed.statusLine()
                    .map(line -> line.statusCode() + ", and the reply could not be read")
                    .orElse("a reply that could not be read");
            case InteractionOutcome.TransportFailure ignored -> "no reply";
        };
    }

    /** Which family that answer belongs to, or that there was none. */
    private static String classOf(Interaction interaction) {
        return interaction.statusCode().map(code -> (code / 100) + "xx").orElse("no reply");
    }


    private void summarise(RunEvent.RunFinished finished) {
        summariseWhatWasSent(finished);
        // After the verdict, because they qualify it: "no faults found" says nothing about a reply
        // that could not be checked, nor about an operation that was never tried. Said whether or
        // not anything was sent at all.
        nameWhatWasNotChecked();
        nameWhatWasSkipped();
        nameTheListsThatCouldNotBeRead();
    }

    /**
     * How many replies could not be checked, one line for each way they could not be and each
     * reason, most first.
     *
     * <p>Counts rather than names: a reply the document gives no shape for is usually one of
     * thousands like it, and the run's report names every operation they came from. Nothing is said
     * when every reply was checked in every way it could have been.
     */
    private void nameWhatWasNotChecked() {
        if (notChecked.none()) {
            return;
        }
        write("some replies could not be checked; report.json names their operations:");
        notChecked.lines().forEach(line -> write("  " + line.check().what() + " of "
                + counted(line.replies(), "reply", "replies") + ", on "
                + counted(line.operations().size(), "operation", "operations") + ": "
                + line.check().why().inWords()));
    }

    /** A count with the word for what it counts, in the singular when there is one. */
    private static String counted(long count, String one, String many) {
        return count + " " + (count == 1 ? one : many);
    }

    /**
     * The lists of values that were handed over and never used, because they could not be read.
     *
     * <p>Every one of them, since there are only ever as many as somebody named. A run that lost one
     * is a run made without the values somebody meant it to have - which looks, from its results
     * alone, exactly like a run made with them that found they did not help.
     */
    private void nameTheListsThatCouldNotBeRead() {
        if (refusedDictionaries.isEmpty()) {
            return;
        }
        write(refusedDictionaries.size() + (refusedDictionaries.size() == 1
                ? " list of values could not be read, so none of its values were sent:"
                : " lists of values could not be read, so none of their values were sent:"));
        // Indented line by line: a reason quoting the YAML reader runs to several lines, with a
        // marker under the column it stopped at, and only the first would otherwise be indented.
        refusedDictionaries.forEach(refused -> refused.reason().lines()
                .forEach(line -> write("  " + line)));
    }

    /**
     * The operations this run never tried, each with the reason.
     *
     * <p>The first few by name and the rest counted. A description the tool can hardly test makes
     * this list as long as the description, and the screen is not the place for all of it; the
     * run's report names every one.
     */
    private void nameWhatWasSkipped() {
        if (skipped.isEmpty()) {
            return;
        }
        write(skipped.size() + (skipped.size() == 1 ? " operation" : " operations")
                + " could not be tested:");
        int named = Math.min(skipped.size(), skippedShown);
        skipped.subList(0, named)
                .forEach(skip -> write("  " + skip.operation() + ": " + skip.reason()));
        if (named < skipped.size()) {
            write("  " + (named == 0 ? "" : "... and " + (skipped.size() - named) + " more; ")
                    + "the run's report names every one");
        }
    }

    private void summariseWhatWasSent(RunEvent.RunFinished finished) {
        // Said on the first line, the way a first round cut short is said on its own: everything
        // under it is then what the run found until it was stopped, not what its budget would have.
        write(attempts + " requests to " + operations.size() + " operations in "
                + readable(finished.elapsed()) + ", "
                + Math.round(finished.engine().idleFraction() * 100) + "% of it idle"
                + (finished.cutShort() ? ", cut short" : ""));
        if (attempts == 0) {
            // Not the same sentence as "no faults found", and the difference matters: a run that
            // asked the API nothing has no evidence that anything is right.
            write("nothing was tested");
            return;
        }
        // How the run began, straight after how long it lasted: a reader who wants to know what
        // the first round bought is looking at the time, and this is the line that answers it.
        phases.all().forEach(phase -> write("  " + phase.name()
                + (phase.cutShort() ? ", cut short" : "") + ": "
                + phase.requests() + (phase.requests() == 1 ? " request" : " requests") + " in "
                + readable(phase.elapsed()) + ", " + phase.operationsAnsweredWithASuccess() + " of "
                + phase.operations() + " operations answered 2xx"));
        write("  " + repliesByClass.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue() + " " + entry.getKey())
                .collect(java.util.stream.Collectors.joining(", ")));
        if (awkward > 0) {
            // What the line says is what is known: how many requests were pushing rather than
            // trying to work. It does not say those requests should have been refused, because
            // nobody knows that - an empty word or a zero is a perfectly good value in a great many
            // APIs. The refusals are mentioned only where there are refusals on the screen to
            // account for.
            long refusals = repliesByClass.getOrDefault("4xx", 0);
            write("  " + awkward + " of them were pushing at the API with values nobody sensible "
                    + "would send" + (refusals > 0
                            ? ", which accounts for some of the " + refusals + " refusals above"
                            : ""));
        }
        if (changed > 0) {
            write("  " + changed + " of them changed one thing in a request the API had accepted");
        }
        if (seriesBegun > 0) {
            // By the kind of series rather than by what each step expected, which is the report's
            // to count and not to interpret: what each step of each kind is for is the tool's
            // business, and the stored run says it step by step.
            long steps = stepsByKindOfSeries.values().stream().mapToLong(Long::longValue).sum();
            write("  " + steps + " of them were steps of " + seriesBegun + " series about a thing "
                    + "the run created: " + stepsByKindOfSeries.entrySet().stream()
                            .map(kind -> kind.getKey() + " " + kind.getValue())
                            .collect(java.util.stream.Collectors.joining(", "))
                    + (seriesNotBegun > 0 ? "; " + seriesNotBegun + " creation(s) meant to begin "
                            + "one were not accepted" : ""));
        } else if (seriesNotBegun > 0) {
            write("  " + seriesNotBegun + " creation(s) meant to begin a series about a thing the "
                    + "run created were not accepted, so no series began");
        }
        if (!serverErrors.none()) {
            write("  " + serverErrors.operationsAnswering500() + " operation(s) answered 500, "
                    + serverErrors.operationsAnsweringAny5xx() + " answered some 5xx");
        }
        if (counts.isEmpty()) {
            write("no faults found");
            return;
        }
        // Errors first, because they are what ended the run with 1, and then warnings, saying
        // whether they did too: a run that found only warnings and ended as if it found nothing
        // wrong says why, and names the setting that would make it end otherwise.
        if (errors > 0) {
            write(counted(errors, "error", "errors") + ", on "
                    + counted(operationsWithErrors.size(), "operation", "operations") + ":");
            listKinds(Severity.ERROR);
        }
        if (warnings > 0) {
            write(counted(warnings, "warning", "warnings") + ", on "
                    + counted(operationsWithWarnings.size(), "operation", "operations")
                    + (warningsFail
                            ? ", which fail the run, as checks.failOnWarnings asks:"
                            : ", which do not fail the run (checks.failOnWarnings):"));
            listKinds(Severity.WARNING);
        }
    }

    /**
     * Each kind of the given severity, in the order it was first found: how many, RESTest's title
     * with the catalogue's number after it, and, under it, what to look at.
     */
    private void listKinds(Severity severity) {
        counts.forEach((code, count) -> {
            FaultCategory kind = kinds.get(code);
            FaultReading reading = FaultReading.of(kind);
            if (reading.severity() != severity) {
                return;
            }
            write("  " + count + " x " + reading.title() + "  (" + label(kind) + ")");
            reading.advice().ifPresent(advice -> write("        " + advice));
        });
    }

    private static String label(FaultCategory category) {
        return "F" + category.code();
    }

    /**
     * A length of time the way a person says it: {@code 412ms}, {@code 6.4s}, {@code 2m 4s}.
     *
     * <p>Written the same way on every machine, rather than the way that machine happens to be set
     * up to write numbers. A report saying {@code 6,4s} in one country and {@code 6.4s} in another
     * cannot be compared across the two, and comparing runs is much of what a report is for.
     */
    static String readable(Duration elapsed) {
        long millis = elapsed.toMillis();
        if (millis < 1000) {
            return millis + "ms";
        }
        if (millis < 60_000) {
            return String.format(Locale.ROOT, "%.1fs", millis / 1000.0);
        }
        return elapsed.toMinutes() + "m " + (elapsed.toSecondsPart()) + "s";
    }

    /**
     * Pushes what has been written to wherever it is really going, when that can be asked.
     *
     * <p>Without this the report is not the live one the top of this class promises. The terminal is
     * written to through a buffer that empties itself only when a whole line arrives by one
     * particular route, and this class does not write lines that way - so a fault written here sat
     * in the buffer, unseen, until several thousand characters of later faults pushed it out, or
     * until the run ended and the summary emptied everything at once. Watching a run showed nothing
     * happening for a minute and then all of it at the end, which is exactly what printing faults as
     * they are found was supposed to avoid.
     *
     * <p>Done once per thing worth reading rather than once per line: a fault is six lines and they
     * belong on the screen together.
     */
    private void sendOnItsWay() {
        if (out instanceof Flushable flushable) {
            try {
                flushable.flush();
            } catch (IOException e) {
                throw new UncheckedIOException("the report could not be written", e);
            }
        }
    }

    /**
     * One line out. Where a report is being written to can fail - a full disk, a closed pipe - and
     * that is worth knowing about rather than swallowing, so it is passed on. Whoever is announcing
     * events counts a listener that fails and carries on with the rest of the run.
     */
    private void write(String line) {
        try {
            out.append(line).append(System.lineSeparator());
        } catch (IOException e) {
            throw new UncheckedIOException("the report could not be written", e);
        }
    }
}

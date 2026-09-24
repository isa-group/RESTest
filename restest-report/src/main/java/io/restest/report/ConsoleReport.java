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
import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionOutcome;
import io.restest.core.model.OperationId;
import io.restest.core.oracle.FaultCategory;
import io.restest.core.oracle.Finding;
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
 * is printed as five things: which kind of fault it is, which operation it happened on, what the API
 * answered, what is wrong in one line, and the {@code curl} command that does it again.
 *
 * <p>That last line is the point of the whole report. The first question anybody asks of a testing
 * tool is "is that actually true?", and the answer should cost one paste into a terminal.
 *
 * <p>At the end it prints how much was done, how the API answered across the whole run, what was
 * found, and how much of the time was spent waiting for the API rather than working - a run that
 * spends its time idle is a run doing less testing than it looks like it is. The line of status
 * codes is worth a glance even when nothing was found wrong: a run where almost everything came back
 * refused is a run whose requests were the problem, not the API. Last come the operations the run
 * said it would not try - the first few by name, with the reason - because finding nothing wrong
 * says nothing about them.
 *
 * <p>It writes wherever it is told to write, rather than to the screen directly, so a test can read
 * back exactly what a person would have seen. While a run lasts it is the only thing writing there:
 * what the command running it has to say before the run begins is handed to it, and printed under
 * the line naming the API, because anything else writing to the same place at the same time would
 * land among its lines in whichever order the two happened to arrive.
 */
public final class ConsoleReport implements RunListener {

    /** How many particular disagreements to print under one fault before saying how many remain. */
    private static final int DETAILS_SHOWN = 10;

    private final Appendable out;
    private final Map<FaultCategory, Integer> counts = new LinkedHashMap<>();

    /** How every attempt ended, faulty or not, by family of status code. */
    private final Map<String, Integer> repliesByClass = new LinkedHashMap<>();
    private final Set<OperationId> operations = new LinkedHashSet<>();

    /** How many operations made the API fall over, worked out in one place for every report. */
    private final ServerErrors serverErrors = new ServerErrors();

    /** What each stretch of the run achieved, worked out in one place for every report. */
    private final Phases phases = new Phases();
    private int attempts;
    private int faults;

    /** The names of the lists a run pushes at the API with, rather than tries to work with. */
    private final Set<String> awkwardSources;

    /** How many requests carried at least one value from one of them. */
    private long awkward;

    /** How many faults to print in full before the screen stops being the right place for them. */
    private final int faultsShown;

    /** The operations the run said it would not try, in the order it said so. */
    private final List<RunEvent.OperationSkipped> skipped = new ArrayList<>();

    /** How many of those to name before only counting the rest. */
    private final int skippedShown;

    /** What the command running this says before the run begins, printed under the first line. */
    private final String introduction;

    private ConsoleReport(Appendable out, Set<String> awkwardSources, ReportSettings settings,
            String introduction) {
        this.out = Objects.requireNonNull(out, "out");
        this.introduction = Objects.requireNonNull(introduction, "introduction");
        this.awkwardSources = Set.copyOf(Objects.requireNonNull(awkwardSources, "awkwardSources"));
        this.faultsShown = settings.faultsShownOnTheConsole();
        this.skippedShown = settings.skippedOperationsShownOnTheConsole();
    }

    /** A report that writes wherever you tell it to. */
    public static ConsoleReport to(Appendable out) {
        return new ConsoleReport(out, Set.of(), ReportSettings.defaults(), "");
    }

    /**
     * A report that writes wherever you tell it to, knows which lists a run pushes with, and is
     * told how many faults belong on a screen.
     *
     * <p>Part of a run is spent on requests built from values nobody sensible would send. Whatever
     * those earn - a refusal, or an acceptance, or the API falling over - lands in the same counts
     * as everything else, and a summary that did not separate them would read as though the API
     * were behaving that way towards ordinary traffic.
     *
     * <p>A run that keeps testing for as long as it was given will ask an API the same question
     * thousands of times, and an API that is broken is broken every time. Printing all of them
     * scrolls everything worth reading off the top of the screen, so past the number given here the
     * screen says so once and stops. The count at the end is of all of them, printed or not.
     *
     * @param out where to write
     * @param awkwardSources the names of the lists a run pushes at the API with
     * @param settings how much of what was found belongs on a screen
     * @return the report
     */
    public static ConsoleReport to(Appendable out, Set<String> awkwardSources,
            ReportSettings settings) {
        return to(out, awkwardSources, settings, "");
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
     * @param awkwardSources the names of the lists a run pushes at the API with
     * @param settings how much of what was found belongs on a screen
     * @param introduction the lines to print under the first one, each ending in a line break;
     *     empty for none
     * @return the report
     */
    public static ConsoleReport to(Appendable out, Set<String> awkwardSources,
            ReportSettings settings, String introduction) {
        return new ConsoleReport(out, awkwardSources,
                Objects.requireNonNull(settings, "settings"), introduction);
    }

    /**
     * How many faults this run found - all of them, including the ones past the limit that were
     * counted rather than printed. The command's answer to whoever ran it is decided from this, and
     * a run that found two thousand faults and showed fifty still found two thousand.
     */
    public int faults() {
        return faults;
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
            case RunEvent.InteractionCompleted completed -> {
                attempts++;
                if (carriedSomethingAwkward(completed.interaction())) {
                    awkward++;
                }
                operations.add(completed.interaction().testCase().operation());
                repliesByClass.merge(classOf(completed.interaction()), 1, Integer::sum);
                serverErrors.note(completed.interaction());
            }
            case RunEvent.FaultFound found -> {
                print(found.finding());
                sendOnItsWay();
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
        faults++;
        counts.merge(finding.category(), 1, Integer::sum);
        if (faults > faultsShown) {
            if (faults == faultsShown + 1) {
                write("... more faults are being found; every one of them is counted in the run's "
                        + "report and in the total below");
                write("");
            }
            return;
        }
        write(label(finding.category()) + "  " + finding.category().descriptiveName());
        write("      " + finding.operation() + " - " + finding.interaction().request().method()
                + " " + finding.interaction().request().url()
                + "  ->  " + answerTo(finding.interaction()));
        write("      " + finding.summary());
        List<String> details = finding.details();
        for (String detail : details.subList(0, Math.min(details.size(), DETAILS_SHOWN))) {
            write("        " + detail);
        }
        if (details.size() > DETAILS_SHOWN) {
            write("        ... and " + (details.size() - DETAILS_SHOWN) + " more");
        }
        write("      " + CurlCommand.of(finding.interaction().request()));
        write("");
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


    /** Whether any value in this attempt came from a list the run pushes at the API with. */
    private boolean carriedSomethingAwkward(io.restest.core.execution.Interaction interaction) {
        if (awkwardSources.isEmpty()) {
            return false;
        }
        return interaction.testCase().parameterValues().stream()
                .map(io.restest.core.execution.ParameterValue::origin)
                .anyMatch(origin -> origin instanceof io.restest.core.execution.ValueOrigin.Generated
                        made && awkwardSources.contains(made.source()));
    }

    private void summarise(RunEvent.RunFinished finished) {
        summariseWhatWasSent(finished);
        // After the verdict, because it qualifies it: "no faults found" says nothing about an
        // operation that was never tried. Said whether or not anything was sent at all.
        nameWhatWasSkipped();
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
        write(attempts + " requests to " + operations.size() + " operations in "
                + readable(finished.elapsed()) + ", "
                + Math.round(finished.engine().idleFraction() * 100) + "% of it idle");
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
        if (!serverErrors.none()) {
            write("  " + serverErrors.operationsAnswering500() + " operation(s) answered 500, "
                    + serverErrors.operationsAnsweringAny5xx() + " answered some 5xx");
        }
        if (counts.isEmpty()) {
            write("no faults found");
            return;
        }
        write(faults + (faults == 1 ? " fault:" : " faults:"));
        counts.forEach((category, count) -> write("  " + count + " x " + label(category) + "  "
                + category.descriptiveName()));
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

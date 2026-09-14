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
import io.restest.core.model.OperationId;
import io.restest.core.oracle.FaultCategory;
import io.restest.core.oracle.Finding;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
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
 * is printed as four things: which kind of fault it is, which operation it happened on, what is
 * wrong in one line, and the {@code curl} command that does it again.
 *
 * <p>That last line is the point of the whole report. The first question anybody asks of a testing
 * tool is "is that actually true?", and the answer should cost one paste into a terminal.
 *
 * <p>At the end it prints how much was done, what was found, and how much of the time was spent
 * waiting for the API rather than working - a run that spends its time idle is a run doing less
 * testing than it looks like it is.
 *
 * <p>It writes wherever it is told to write, rather than to the screen directly, so a test can read
 * back exactly what a person would have seen.
 */
public final class ConsoleReport implements RunListener {

    /** How many particular disagreements to print under one fault before saying how many remain. */
    private static final int DETAILS_SHOWN = 10;

    /**
     * How many faults to print in full before the screen stops being the right place for them.
     *
     * <p>A run that keeps testing for as long as it was given will ask an API the same question
     * thousands of times, and an API that is broken is broken every time. Printing all of them
     * scrolls everything worth reading off the top of the screen, so past this many the screen says
     * so once and the rest are left to the run's own file, which has every one of them. The count
     * at the end is of all of them, printed or not.
     */
    private static final int FAULTS_SHOWN = 50;

    private final Appendable out;
    private final Map<FaultCategory, Integer> counts = new LinkedHashMap<>();
    private final Set<OperationId> operations = new LinkedHashSet<>();
    private int attempts;
    private int faults;

    private ConsoleReport(Appendable out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    /** A report that writes wherever you tell it to. */
    public static ConsoleReport to(Appendable out) {
        return new ConsoleReport(out);
    }

    /** How many faults have been printed so far. */
    public int faults() {
        return faults;
    }

    @Override
    public void on(RunEvent event) {
        switch (event) {
            case RunEvent.RunStarted started -> began(started);
            case RunEvent.InteractionCompleted completed -> {
                attempts++;
                operations.add(completed.interaction().testCase().operation());
            }
            case RunEvent.FaultFound found -> print(found.finding());
            case RunEvent.RunFinished finished -> summarise(finished);
            case RunEvent.TestCasePlanned ignored -> {
                // A planned test is not news until it has been sent; printing one line per plan
                // would bury the faults, which are what this report exists to show.
            }
        }
    }

    private void began(RunEvent.RunStarted started) {
        write("RESTest testing " + started.api() + " at " + started.baseUrl());
        write("");
    }

    private void print(Finding finding) {
        faults++;
        counts.merge(finding.category(), 1, Integer::sum);
        if (faults > FAULTS_SHOWN) {
            if (faults == FAULTS_SHOWN + 1) {
                write("... more faults are being found; they are all in the run's report, and "
                        + "counted below");
                write("");
            }
            return;
        }
        write(label(finding.category()) + "  " + finding.category().descriptiveName());
        write("      " + finding.operation() + " - " + finding.interaction().request().method()
                + " " + finding.interaction().request().url());
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

    private void summarise(RunEvent.RunFinished finished) {
        write(attempts + " requests to " + operations.size() + " operations in "
                + readable(finished.elapsed()) + ", "
                + Math.round(finished.engine().idleFraction() * 100) + "% of it idle");
        if (attempts == 0) {
            // Not the same sentence as "no faults found", and the difference matters: a run that
            // asked the API nothing has no evidence that anything is right.
            write("nothing was tested");
            return;
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

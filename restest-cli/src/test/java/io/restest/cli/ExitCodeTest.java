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
package io.restest.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.event.RunEvent;
import io.restest.core.event.Unauthorized;
import io.restest.core.exec.EngineStatistics;
import io.restest.report.HtmlReport;
import io.restest.report.JsonReport;
import io.restest.report.Verdict;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What number a finished run leaves behind, for each way a run can finish.
 *
 * <p>This is the part of RESTest other people's scripts depend on, so every one of the answers is
 * pinned here rather than being inferred from a run that happened to go a particular way. The order
 * the questions are asked in is the whole of the meaning, and the cases where two things went wrong
 * at once are what prove that order is real.
 */
class ExitCodeTest {

    private static final RunLoop.Outcome A_REAL_RUN = outcome(120, 120);

    @Test
    @DisplayName("a run that tested the API and found nothing wrong answers 0")
    void a_clean_run() {
        assertThat(ExitCode.of(A_REAL_RUN, 0, 0, 0, 0, 0, false)).isEqualTo(ExitCode.NO_ERRORS);
    }

    @Test
    @DisplayName("a run that found an error answers 1: the API is what is wrong, not the tool")
    void errors_found() {
        assertThat(ExitCode.of(A_REAL_RUN, 0, 0, 0, 1, 0, false)).isEqualTo(ExitCode.ERRORS_FOUND);
        assertThat(ExitCode.of(A_REAL_RUN, 0, 0, 0, 4_312, 0, false))
                .isEqualTo(ExitCode.ERRORS_FOUND);
        assertThat(ExitCode.of(A_REAL_RUN, 0, 0, 0, 1, 900, false))
                .describedAs("warnings beside it change nothing")
                .isEqualTo(ExitCode.ERRORS_FOUND);
    }

    @Test
    @DisplayName("a run that found only warnings answers 0: the API and its document disagree, and "
            + "the document may be the one out of date")
    void only_warnings_found() {
        assertThat(ExitCode.of(A_REAL_RUN, 0, 0, 0, 0, 31_255, false))
                .isEqualTo(ExitCode.NO_ERRORS);
    }

    @Test
    @DisplayName("told that warnings fail it, a run that found only warnings answers 1")
    void warnings_found_when_they_fail_the_run() {
        assertThat(ExitCode.of(A_REAL_RUN, 0, 0, 0, 0, 1, true)).isEqualTo(ExitCode.ERRORS_FOUND);
        assertThat(ExitCode.of(A_REAL_RUN, 0, 0, 0, 0, 0, true))
                .describedAs("and with no warning at all, still 0")
                .isEqualTo(ExitCode.NO_ERRORS);
        assertThat(ExitCode.of(A_REAL_RUN, 0, 0, 0, 3, 0, true)).isEqualTo(ExitCode.ERRORS_FOUND);
    }

    @Test
    @DisplayName("warnings that fail a run come after every question asked before them")
    void warnings_never_outrank_what_comes_first() {
        assertThat(ExitCode.of(A_REAL_RUN, 1, 0, 0, 0, 5, true)).isEqualTo(ExitCode.TOOL_FAILED);
        assertThat(ExitCode.of(outcome(500, 0), 0, 0, 0, 0, 5, true))
                .isEqualTo(ExitCode.NOTHING_TO_TEST);
        assertThat(ExitCode.of(cutShort(120, 120, 0), 0, 0, 0, 0, 5, true))
                .isEqualTo(ExitCode.INTERRUPTED);
    }

    @Test
    @DisplayName("a run that sent nothing answers 3, however clean it looks")
    void nothing_was_sent() {
        assertThat(ExitCode.of(outcome(0, 0), 0, 0, 0, 0, 0, false))
                .isEqualTo(ExitCode.NOTHING_TO_TEST);
    }

    @Test
    @DisplayName("a run nothing answered answers 3, because it learnt nothing about the API")
    void nothing_answered() {
        assertThat(ExitCode.of(outcome(500, 0), 0, 0, 0, 0, 0, false))
                .isEqualTo(ExitCode.NOTHING_TO_TEST);
    }

    @Test
    @DisplayName("a run that never got as far as a loop answers 3 rather than a clean bill of health")
    void the_run_never_started() {
        assertThat(ExitCode.of(null, 0, 0, 0, 0, 0, false)).isEqualTo(ExitCode.NOTHING_TO_TEST);
    }

    @Test
    @DisplayName("a report that threw answers 4, because what was printed may be wrong")
    void a_report_broke() {
        assertThat(ExitCode.of(A_REAL_RUN, 1, 0, 0, 0, 0, false)).isEqualTo(ExitCode.TOOL_FAILED);
    }

    @Test
    @DisplayName("an announcement that never arrived answers 4, for the same reason")
    void something_was_never_heard() {
        assertThat(ExitCode.of(A_REAL_RUN, 0, 7, 0, 0, 0, false)).isEqualTo(ExitCode.TOOL_FAILED);
    }

    @Test
    @DisplayName("our own failure is reported before the API's, whichever else is true")
    void the_tool_breaking_outranks_everything() {
        assertThat(ExitCode.of(A_REAL_RUN, 1, 0, 0, 900, 0, false))
                .describedAs("faults found by a run whose reporting broke cannot be trusted to be "
                        + "all of them")
                .isEqualTo(ExitCode.TOOL_FAILED);
        assertThat(ExitCode.of(outcome(0, 0), 1, 0, 0, 0, 0, false))
                .describedAs("and a broken report is more specific than 'nothing was tested'")
                .isEqualTo(ExitCode.TOOL_FAILED);
    }

    @Test
    @DisplayName("having tested nothing is reported before anything found while testing nothing")
    void testing_nothing_outranks_finding_nothing() {
        assertThat(ExitCode.of(outcome(0, 0), 0, 0, 0, 0, 0, false))
                .describedAs("0 would read as 'this API is fine', which such a run cannot know")
                .isNotEqualTo(ExitCode.NO_ERRORS);
    }

    @Test
    @DisplayName("a run in which RESTest lost a request answers 4, whatever it found besides")
    void a_lost_request_answers_four() {
        assertThat(ExitCode.of(lost(120, 119, 1), 0, 0, 0, 0, 0, false))
                .isEqualTo(ExitCode.TOOL_FAILED);
        assertThat(ExitCode.of(lost(120, 110, 10), 0, 0, 0, 37, 0, false))
                .describedAs("faults found by a run that lost part of its own work cannot be "
                        + "trusted to be all of them")
                .isEqualTo(ExitCode.TOOL_FAILED);
    }

    @Test
    @DisplayName("a run in which RESTest lost every request answers 4, not the 3 that blames the address")
    void losing_everything_is_our_fault_not_the_addresses() {
        assertThat(ExitCode.of(lost(32, 0, 32), 0, 0, 0, 0, 0, false))
                .describedAs("nothing was answered, and the reason is RESTest")
                .isEqualTo(ExitCode.TOOL_FAILED);
    }

    @Test
    @DisplayName("an exchange kept without its details answers 4, because every report is missing it")
    void an_exchange_kept_without_its_details_answers_four() {
        assertThat(ExitCode.of(A_REAL_RUN, 0, 0, 1, 0, 0, false)).isEqualTo(ExitCode.TOOL_FAILED);
        assertThat(ExitCode.of(A_REAL_RUN, 0, 0, 3, 12, 0, false)).isEqualTo(ExitCode.TOOL_FAILED);
    }

    @Test
    @DisplayName("a run stopped from outside answers 130, whatever it found and whatever else went wrong")
    void a_run_stopped_from_outside_answers_what_java_would() {
        assertThat(ExitCode.of(cutShort(120, 120, 0), 0, 0, 0, 0, 0, false))
                .describedAs("a run stopped half-way that found nothing has not passed")
                .isEqualTo(ExitCode.INTERRUPTED).isEqualTo(130);
        assertThat(ExitCode.of(cutShort(120, 120, 0), 0, 0, 0, 7, 0, false))
                .isEqualTo(ExitCode.INTERRUPTED);
        assertThat(ExitCode.of(cutShort(0, 0, 0), 0, 0, 0, 0, 0, false))
                .describedAs("stopped before it sent anything: not 3, which says look at the address")
                .isEqualTo(ExitCode.INTERRUPTED);
        assertThat(ExitCode.of(cutShort(32, 0, 32), 1, 2, 3, 0, 0, false))
                .describedAs("a program stopped from outside ends with Java's number whatever this "
                        + "says, so a test must read the same")
                .isEqualTo(ExitCode.INTERRUPTED);
        assertThat(ExitCode.TERMINATED).isEqualTo(143);
    }

    /**
     * One way a run can go well enough for RESTest itself, the verdict worked out from its counts
     * and the number it ends with. The verdict is what a report writes; the runs that read it back
     * from a report a real run wrote are in {@link RestestTest} and {@link CheckSwitchesCommandTest}.
     */
    record Case(String run, RunLoop.Outcome outcome, long errors, long warnings,
            boolean warningsFail, Verdict says, int endsWith) {

        @Override
        public String toString() {
            return run;
        }
    }

    static List<Case> everyVerdict() {
        return List.of(
                new Case("a clean run", A_REAL_RUN, 0, 0, false, Verdict.NO_ERRORS_FOUND,
                        ExitCode.NO_ERRORS),
                new Case("errors found", A_REAL_RUN, 3, 0, false, Verdict.ERRORS_FOUND,
                        ExitCode.ERRORS_FOUND),
                new Case("only warnings", A_REAL_RUN, 0, 7, false, Verdict.NO_ERRORS_FOUND,
                        ExitCode.NO_ERRORS),
                new Case("only warnings, told they fail it", A_REAL_RUN, 0, 7, true,
                        Verdict.FAILED_ON_WARNINGS, ExitCode.ERRORS_FOUND),
                new Case("nothing sent", outcome(0, 0), 0, 0, false, Verdict.NOTHING_TESTED,
                        ExitCode.NOTHING_TO_TEST),
                new Case("nothing answered, with a fault for every request", outcome(64, 0), 64,
                        0, false, Verdict.NOTHING_ANSWERED, ExitCode.NOTHING_TO_TEST),
                new Case("stopped from outside", cutShort(32, 30, 0), 5, 0, false,
                        Verdict.STOPPED, ExitCode.INTERRUPTED),
                new Case("the whole opening lap refused, warnings told they fail it", refused(38,
                        false), 0, 38, true, Verdict.UNAUTHORIZED, ExitCode.STOPPED_BY_THE_API),
                new Case("the whole opening lap refused, with errors found on the way", refused(38,
                        false), 2, 0, false, Verdict.UNAUTHORIZED, ExitCode.STOPPED_BY_THE_API),
                new Case("the whole opening lap refused, then stopped from outside", refused(38,
                        true), 0, 0, false, Verdict.STOPPED, ExitCode.INTERRUPTED));
    }

    @Test
    @DisplayName("a run the API stopped by refusing its whole opening lap ends with 5, which is "
            + "nothing else's number")
    void a_run_the_api_stopped_ends_with_five() {
        assertThat(ExitCode.of(refused(38, false), 0, 0, 0, 0, 38, false))
                .isEqualTo(ExitCode.STOPPED_BY_THE_API).isEqualTo(5);
        assertThat(ExitCode.of(refused(38, false), 1, 0, 0, 0, 0, false))
                .describedAs("RESTest's own failure is still said first")
                .isEqualTo(ExitCode.TOOL_FAILED);
        assertThat(List.of(ExitCode.NO_ERRORS, ExitCode.ERRORS_FOUND, ExitCode.BAD_COMMAND_LINE,
                ExitCode.NOTHING_TO_TEST, ExitCode.TOOL_FAILED, ExitCode.INTERRUPTED,
                ExitCode.TERMINATED)).doesNotContain(ExitCode.STOPPED_BY_THE_API);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyVerdict")
    @DisplayName("the number agrees with the verdict worked out from the same counts, whenever "
            + "RESTest itself went right")
    void the_number_agrees_with_the_verdict(Case run) {
        assertThat(Verdict.of(Verdict.Stop.of(run.outcome().cutShort(),
                run.outcome().refusedLap().isPresent()), run.outcome().sent(),
                run.outcome().answered(), run.errors(), run.warnings(), run.warningsFail()))
                .describedAs("the verdict a report writes")
                .isEqualTo(run.says());
        assertThat(ExitCode.of(run.outcome(), 0, 0, 0, run.errors(), run.warnings(),
                run.warningsFail()))
                .describedAs("ends as its report says it went: %s", run.says().word())
                .isEqualTo(run.endsWith());
    }

    @Test
    @DisplayName("the page a run writes gives, for every verdict, the number the run ends with")
    void the_page_gives_the_number_for_every_verdict(@TempDir Path directory) throws IOException {
        Path page = directory.resolve("report.html");
        HtmlReport report = HtmlReport.around(JsonReport.inMemory(), page);
        Instant now = Instant.now();
        report.on(new RunEvent.RunStarted(now, "Pets", "http://localhost", Duration.ofSeconds(1)));
        report.on(new RunEvent.RunFinished(now, Duration.ofSeconds(1), EngineStatistics.none()));

        Map<String, String> onThePage = new TreeMap<>();
        Matcher row = Pattern.compile("^\\s+(\\w+): \\['([^']+)', ", Pattern.MULTILINE)
                .matcher(theExitCodesIn(Files.readString(page)));
        while (row.find()) {
            onThePage.put(row.group(1), row.group(2));
        }

        assertThat(onThePage.keySet())
                .describedAs("one number for each word report.json can write, and no other")
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(Verdict.values())
                        .map(Verdict::word).toList());
        for (Verdict verdict : Verdict.values()) {
            String said = onThePage.get(verdict.word());
            if (verdict == Verdict.STOPPED) {
                assertThat(said).describedAs("a stopped run ends with Java's number for how")
                        .contains(String.valueOf(ExitCode.of(verdict)))
                        .contains(String.valueOf(ExitCode.TERMINATED));
            } else {
                assertThat(said).describedAs("what the page says for %s", verdict.word())
                        .isEqualTo(String.valueOf(ExitCode.of(verdict)));
            }
        }
    }

    /** The table of exit codes in a page a run wrote. */
    private static String theExitCodesIn(String page) {
        int start = page.indexOf("var EXIT_CODES = {");
        assertThat(start).describedAs("the page carries its table of exit codes").isNotNegative();
        return page.substring(start, page.indexOf("};", start));
    }

    private static RunLoop.Outcome outcome(long sent, long answered) {
        return new RunLoop.Outcome(sent, answered, 0, 0, 0, 1, 0, 0, Optional.empty(), false,
                Optional.empty(), Optional.empty());
    }

    private static RunLoop.Outcome lost(long sent, long answered, long lost) {
        return new RunLoop.Outcome(sent, answered, 0, 0, 0, 1, 0, lost,
                Optional.of(new StackOverflowError()), false, Optional.empty(), Optional.empty());
    }

    private static RunLoop.Outcome refused(long lap, boolean thenCutShort) {
        return new RunLoop.Outcome(lap, lap, 0, 0, 0, 0, 0, 0, Optional.empty(), thenCutShort,
                Optional.of(new Unauthorized(Instant.now(), lap)), Optional.empty());
    }

    private static RunLoop.Outcome cutShort(long sent, long answered, long lost) {
        return new RunLoop.Outcome(sent, answered, 0, 0, 0, 1, 0, lost,
                lost > 0 ? Optional.of(new StackOverflowError()) : Optional.empty(), true,
                Optional.empty(), Optional.empty());
    }
}

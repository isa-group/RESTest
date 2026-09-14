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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        assertThat(ExitCode.of(A_REAL_RUN, 0, 0, 0)).isEqualTo(ExitCode.NO_FAULTS);
    }

    @Test
    @DisplayName("a run that found a fault answers 1: the API is what is wrong, not the tool")
    void faults_found() {
        assertThat(ExitCode.of(A_REAL_RUN, 0, 0, 1)).isEqualTo(ExitCode.FAULTS_FOUND);
        assertThat(ExitCode.of(A_REAL_RUN, 0, 0, 4_312)).isEqualTo(ExitCode.FAULTS_FOUND);
    }

    @Test
    @DisplayName("a run that sent nothing answers 3, however clean it looks")
    void nothing_was_sent() {
        assertThat(ExitCode.of(outcome(0, 0), 0, 0, 0)).isEqualTo(ExitCode.NOTHING_TO_TEST);
    }

    @Test
    @DisplayName("a run nothing answered answers 3, because it learnt nothing about the API")
    void nothing_answered() {
        assertThat(ExitCode.of(outcome(500, 0), 0, 0, 0)).isEqualTo(ExitCode.NOTHING_TO_TEST);
    }

    @Test
    @DisplayName("a run that never got as far as a loop answers 3 rather than a clean bill of health")
    void the_run_never_started() {
        assertThat(ExitCode.of(null, 0, 0, 0)).isEqualTo(ExitCode.NOTHING_TO_TEST);
    }

    @Test
    @DisplayName("a report that threw answers 4, because what was printed may be wrong")
    void a_report_broke() {
        assertThat(ExitCode.of(A_REAL_RUN, 1, 0, 0)).isEqualTo(ExitCode.TOOL_FAILED);
    }

    @Test
    @DisplayName("an announcement that never arrived answers 4, for the same reason")
    void something_was_never_heard() {
        assertThat(ExitCode.of(A_REAL_RUN, 0, 7, 0)).isEqualTo(ExitCode.TOOL_FAILED);
    }

    @Test
    @DisplayName("our own failure is reported before the API's, whichever else is true")
    void the_tool_breaking_outranks_everything() {
        assertThat(ExitCode.of(A_REAL_RUN, 1, 0, 900))
                .describedAs("faults found by a run whose reporting broke cannot be trusted to be "
                        + "all of them")
                .isEqualTo(ExitCode.TOOL_FAILED);
        assertThat(ExitCode.of(outcome(0, 0), 1, 0, 0))
                .describedAs("and a broken report is more specific than 'nothing was tested'")
                .isEqualTo(ExitCode.TOOL_FAILED);
    }

    @Test
    @DisplayName("having tested nothing is reported before anything found while testing nothing")
    void testing_nothing_outranks_finding_nothing() {
        assertThat(ExitCode.of(outcome(0, 0), 0, 0, 0))
                .describedAs("0 would read as 'this API is fine', which such a run cannot know")
                .isNotEqualTo(ExitCode.NO_FAULTS);
    }

    private static RunLoop.Outcome outcome(long sent, long answered) {
        return new RunLoop.Outcome(sent, answered, 0, 0, 1, 0);
    }
}

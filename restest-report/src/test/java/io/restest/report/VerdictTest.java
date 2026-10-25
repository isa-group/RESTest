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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VerdictTest {

    @Test
    @DisplayName("the questions are asked in order: stopped, then nothing sent, then nothing "
            + "answered, and only then errors and warnings")
    void the_questions_are_asked_in_order() {
        assertThat(Verdict.of(true, 0, 0, 9, 9, true)).isEqualTo(Verdict.STOPPED);
        assertThat(Verdict.of(false, 0, 0, 9, 9, true)).isEqualTo(Verdict.NOTHING_TESTED);
        assertThat(Verdict.of(false, 70_548, 0, 70_548, 0, false))
                .describedAs("an API that answered nothing found no errors in itself, however many "
                        + "unanswered requests were counted against it")
                .isEqualTo(Verdict.NOTHING_ANSWERED);
        assertThat(Verdict.of(false, 10, 10, 1, 9, false)).isEqualTo(Verdict.ERRORS_FOUND);
        assertThat(Verdict.of(false, 10, 10, 0, 9, true)).isEqualTo(Verdict.FAILED_ON_WARNINGS);
        assertThat(Verdict.of(false, 10, 10, 0, 9, false)).isEqualTo(Verdict.NO_ERRORS_FOUND);
        assertThat(Verdict.of(false, 10, 1, 0, 0, false)).isEqualTo(Verdict.NO_ERRORS_FOUND);
    }

    @Test
    @DisplayName("each verdict has a word of its own")
    void each_verdict_has_a_word_of_its_own() {
        assertThat(Arrays.stream(Verdict.values()).map(Verdict::word))
                .containsExactly("stopped", "nothingTested", "nothingAnswered", "errorsFound",
                        "failedOnWarnings", "noErrorsFound");
    }
}

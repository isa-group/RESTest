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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BudgetDurationTest {

    @ParameterizedTest(name = "{0} is {1} seconds")
    @CsvSource({
        "30s, 30",
        "5m, 300",
        "2h, 7200",
        "90, 90",
        "PT1M30S, 90",
        "pt1m30s, 90",
        "1H, 3600",
    })
    @DisplayName("a length of time can be written the way people write it, or formally")
    void lengths_of_time_are_read_as_written(String typed, long expectedSeconds) {
        assertThat(BudgetDuration.parse(typed)).isEqualTo(Duration.ofSeconds(expectedSeconds));
    }

    @Test
    @DisplayName("a length shorter than a second keeps its milliseconds")
    void milliseconds_survive() {
        assertThat(BudgetDuration.parse("500ms")).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    @DisplayName("surrounding spaces are not a mistake worth complaining about")
    void spaces_are_forgiven() {
        assertThat(BudgetDuration.parse("  45s ")).isEqualTo(Duration.ofSeconds(45));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "soon", "30x", "-5s", "3.5s", "PT", "PTX", "s30"})
    @DisplayName("anything that is not a length of time is refused, saying what would have worked")
    void nonsense_is_refused_with_an_example(String typed) {
        assertThatThrownBy(() -> BudgetDuration.parse(typed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30s");
    }

    @Test
    @DisplayName("a budget of no time at all is refused rather than producing an empty run")
    void nothing_is_not_a_budget() {
        assertThatThrownBy(() -> BudgetDuration.parse("0s"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before it sent anything");
        assertThatThrownBy(() -> BudgetDuration.parse("PT0S"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before it sent anything");
        assertThatThrownBy(() -> BudgetDuration.parse("PT-5S"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before it sent anything");
    }

    @Test
    @DisplayName("the command line hands its own text straight to the same reading")
    void the_converter_reads_what_the_command_line_gives_it() throws Exception {
        assertThat(new BudgetDuration().convert("2m")).isEqualTo(Duration.ofMinutes(2));
    }
}

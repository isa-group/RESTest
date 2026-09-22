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
package io.restest.core.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Writing a length of time the way people write one.
 *
 * <p>One spelling for every length of time RESTest accepts, whether it was typed after
 * {@code --budget}, written in a file of settings or handed over in an environment variable.
 */
class LengthOfTimeTest {

    @ParameterizedTest
    @CsvSource({
            "500ms, PT0.5S",
            "30s, PT30S",
            "5m, PT5M",
            "2h, PT2H",
            "90, PT1M30S",
            "0s, PT0S",
            "PT1M30S, PT1M30S",
    })
    @DisplayName("every spelling a person might use means the same length of time")
    void spellings(String typed, String expected) {
        assertThat(LengthOfTime.parse(typed)).isEqualTo(Duration.parse(expected));
    }

    @Test
    @DisplayName("a unit may be written in capitals, and with a space before it")
    void capitals_and_spaces() {
        assertThat(LengthOfTime.parse("30S")).isEqualTo(Duration.ofSeconds(30));
        assertThat(LengthOfTime.parse(" 5 M ")).isEqualTo(Duration.ofMinutes(5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "soon", "30 seconds", "-5s", "1.5s", "PTX", "s"})
    @DisplayName("anything that is not a length of time is refused, with the spellings that work")
    void refusals(String typed) {
        assertThatThrownBy(() -> LengthOfTime.parse(typed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not a length of time")
                .describedAs("somebody who mistyped a length wants the spelling, not the grammar")
                .hasMessageContaining("30s");
    }

    @Test
    @DisplayName("nothing at all is refused the same way, rather than becoming zero")
    void nothing_is_refused() {
        assertThatThrownBy(() -> LengthOfTime.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not a length of time");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "9223372036854775807h",
            "9223372036854775807m",
            "9223372036854775807",
            "99999999999999999999",
    })
    @DisplayName("a length of time too long to write down is refused, rather than wrapping round "
            + "to a small one or dying where it is printed")
    void lengths_too_long_to_write_down(String typed) {
        assertThatThrownBy(() -> LengthOfTime.parse(typed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longer than any length of time RESTest can write down");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT2562047788015216H", "PT9223372036854775807S"})
    @DisplayName("and so is one written in the formal spelling, which refuses it as text that is "
            + "not a length of time at all")
    void formal_lengths_too_long(String typed) {
        assertThatThrownBy(() -> LengthOfTime.parse(typed))
                .describedAs("what matters is that it is turned down rather than overflowing; "
                        + "the formal spelling is refused by the platform before this sees it")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0.0005S", "PT0.0000001S", "PT1.5001S"})
    @DisplayName("a length of time finer than a millisecond is refused, because writing it out "
            + "would hand back a different length")
    void lengths_finer_than_this_can_write(String typed) {
        assertThatThrownBy(() -> LengthOfTime.parse(typed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finer than a millisecond");
    }

    @Test
    @DisplayName("the longest length of time there is can still be written out and read back")
    void the_longest_one_still_works() {
        Duration longest = Duration.ofMillis(Long.MAX_VALUE);

        assertThat(LengthOfTime.parse(LengthOfTime.written(longest))).isEqualTo(longest);
    }

    @ParameterizedTest
    @CsvSource({
            "PT0S, 0s",
            "PT0.5S, 500ms",
            "PT30S, 30s",
            "PT5M, 5m",
            "PT2H, 2h",
            "PT1M30S, 90s",
            "PT0.001S, 1ms",
    })
    @DisplayName("what is written out is the largest whole unit that divides it exactly")
    void written_in_the_largest_unit(String length, String expected) {
        assertThat(LengthOfTime.written(Duration.parse(length))).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT0.5S", "PT30S", "PT5M", "PT2H", "PT1M30S", "PT0.001S"})
    @DisplayName("what is written out reads back as the same length, which is what makes "
            + "--print-settings a file the tool accepts")
    void writing_and_reading_agree(String length) {
        Duration original = Duration.parse(length);
        assertThat(LengthOfTime.parse(LengthOfTime.written(original))).isEqualTo(original);
    }
}

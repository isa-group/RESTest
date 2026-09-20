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
package io.restest.gen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The count that decides whether a spelling rule is safe to build from at all.
 *
 * <p>Two properties matter and they are tested separately. It must never answer <em>lower</em> than
 * the longest string the rule really allows, because a low answer lets through the rule that
 * exhausts the program's memory. And it should not answer absurdly high for the rules real
 * documents contain, because a high answer costs a spelling rule that would have worked.
 */
class LongestMatchTest {

    /** How far a repetition with no end of its own is taken to run, in these tests. */
    private static final int EIGHT = 8;

    @Nested
    @DisplayName("the count it arrives at")
    class TheCount {

        @ParameterizedTest(name = "{0} could be {1} characters")
        @CsvSource({
                "'^[A-Z]{3}$',                     3",
                "'^[A-Z]{2,5}$',                   5",
                "'^[a-z]+$',                       8",
                "'^[a-z]*$',                       8",
                "'^[a-z]?$',                       1",
                "'^abc$',                          3",
                "'^a|bb|ccc$',                     3",
                "'^\\d{4}-\\d{2}-\\d{2}$',         10",
                "'^\\p{L}+$',                      8",
                "'^(ab){3}$',                      6",
                "'^(ab{2}){3}$',                   9",
                "'^[0-9a-fA-F]{128}$',             128",
                "'^[a-z]{2,}$',                    10",
        })
        @DisplayName("a rule is worth what its pieces come to")
        void the_pieces_add_up(String rule, long longest) {
            assertThat(LongestMatch.of(rule, EIGHT)).hasValue(longest);
        }

        @Test
        @DisplayName("a question about what comes next costs no characters")
        void a_lookahead_costs_nothing() {
            assertThat(LongestMatch.of("^(?=.*[A-Z])[A-Za-z0-9]{8}$", EIGHT)).hasValue(8);
        }

        @Test
        @DisplayName("repetitions inside repetitions multiply")
        void nesting_multiplies() {
            assertThat(LongestMatch.of("^(((a+)+)+)+$", EIGHT)).hasValue(8L * 8 * 8 * 8);
        }

        @Test
        @DisplayName("a count too large to hold in a number is answered, not wrapped round")
        void an_enormous_count_does_not_wrap() {
            OptionalLong enormous = LongestMatch.of("^(a{1000000000}){1000000000}$", EIGHT);

            assertThat(enormous).isPresent();
            assertThat(enormous.orElseThrow())
                    .describedAs("wrapping round would make this look small and safe")
                    .isGreaterThan(1_000_000_000L);
        }
    }

    @Nested
    @DisplayName("the rules real documents state")
    class RealRules {

        @Test
        @DisplayName("every spelling rule in the corpus comes to a length worth building")
        void the_corpus_is_all_short() {
            for (String rule : MatchingStringsTest.THE_CORPUS_STATES) {
                assertThat(LongestMatch.of(rule, EIGHT))
                        .describedAs("no answer for %s, which costs the rule", rule)
                        .isPresent();
                assertThat(LongestMatch.of(rule, EIGHT).orElseThrow())
                        .describedAs("%s is counted as far longer than it is", rule)
                        .isLessThanOrEqualTo(64);
            }
        }
    }

    @Nested
    @DisplayName("what it will not answer for")
    class Declines {

        @ParameterizedTest
        @ValueSource(strings = {"^[A-Z", "(unclosed", "a)b", "[a-z", "\\"})
        @DisplayName("a rule it cannot read through says nothing rather than guessing low")
        void an_unreadable_rule_says_nothing(String notARule) {
            assertThat(LongestMatch.of(notARule, EIGHT)).isEmpty();
        }

        @Test
        @DisplayName("a rule nested deeper than anybody writes says nothing")
        void a_deeply_nested_rule_says_nothing() {
            String nested = "(".repeat(20_000) + "a" + ")".repeat(20_000);

            assertThat(LongestMatch.of(nested, EIGHT))
                    .describedAs("reading it to the bottom would run out of room to read in")
                    .isEmpty();
        }
    }
}

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

import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Strings built from the spelling rule a specification states, checked against that same rule read
 * again by the platform rather than by the library that built them.
 */
class MatchingStringsTest {

    /** No length stated, which is what most shapes with a rule say. */
    private static final long ANY_LENGTH = Long.MAX_VALUE;

    /**
     * One source of randomness for the whole class, so a test repeated twenty times sees twenty
     * different draws. A generator made inside the test body would make the same draw every
     * repetition, and a test that runs the same computation twenty times is one test.
     */
    private static final SplittableRandom RANDOM = new SplittableRandom(20260920L);

    /**
     * Every distinct spelling rule the fifty documents of the corpus state, written out.
     *
     * <p>Written out rather than read from the documents on purpose. This is the list that decides
     * whether the feature is worth anything at all, so it belongs where somebody reviewing the
     * change can see it; the corpus test is what notices if the documents grow a rule this list
     * does not have.
     */
    static final List<String> THE_CORPUS_STATES = List.of(
            "^\\\\d+(\\.\\\\d+)?$",
            "^[A-Z]{2}$",
            "^[A-Z0-9]{8}$",
            "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{4})$",
            "^[A-Z]{3}$",
            "^[A-Z0-9]*$",
            "[0-9]{2}",
            "^[A-Z0-9*]{3}$",
            "^[\\p{L}]+([ '-][\\p{L}]+){0,2}$",
            "^[\\p{L}]+([ '-][\\p{L}]+){0,2}\\.?$",
            "^[A-Za-z]{3}$",
            "^[A-Z0-9a-z]{2}$",
            "^[a-zA-Z0-9-]{2,5}$",
            "^[A-Z0-9]{3}$",
            "^[1-5]$",
            "@",
            "[A-Z0-9]{3}",
            "[PNC]",
            "[a-zA-Z0-9-]{2,5}",
            "[A-Z]{3}",
            "^(?:top|bottom|after:\\d+)$",
            "^(?:first|last|after:\\d+)$",
            "^ssh-(rsa|dss|ed25519) |^ecdsa-sha2-nistp(256|384|521) ",
            "^[0-9a-fA-F]+$",
            "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} GMT",
            "^[0-9]*$");

    @Nested
    @DisplayName("what it can read")
    class WhatItCanRead {

        @Test
        @DisplayName("every spelling rule the corpus states is read and satisfied")
        void every_rule_in_the_corpus_is_honoured() {
            for (String rule : THE_CORPUS_STATES) {
                Optional<MatchingStrings> spellings = reading(rule, 1, ANY_LENGTH);

                assertThat(spellings)
                        .describedAs("a rule the corpus states and this cannot read: %s", rule)
                        .isPresent();
                Optional<String> built = spellings.orElseThrow().next(RANDOM);
                assertThat(built)
                        .describedAs("nothing could be built for %s", rule)
                        .isPresent();
                assertThat(Pattern.compile(rule).matcher(built.orElseThrow()).find())
                        .describedAs("%s produced '%s', which it does not accept", rule,
                                built.orElseThrow())
                        .isTrue();
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {"^[A-Z]{3}$", "[0-9]{2}", "^(?:top|bottom)$", "^\\p{L}+$",
                "^[a-z]{2,5}$", "^ab?c*d+$", "^(one|two|three)$"})
        @DisplayName("the notations a specification actually uses are all read")
        void the_usual_notations_are_read(String rule) {
            assertThat(reading(rule, 1, ANY_LENGTH)).isPresent();
        }

        @Test
        @DisplayName("a value is drawn afresh rather than the same one every time")
        void the_values_vary() {
            MatchingStrings spellings = reading("^[A-Z]{8}$", 1, ANY_LENGTH).orElseThrow();

            List<String> drawn = IntStream.range(0, 20)
                    .mapToObj(draw -> spellings.next(RANDOM).orElseThrow())
                    .toList();

            assertThat(drawn).describedAs("every draw the same means a run tests one value")
                    .doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("the same starting number produces the same string")
        void the_same_seed_produces_the_same_string() {
            MatchingStrings spellings = reading("^[A-Za-z0-9]{12}$", 1, ANY_LENGTH).orElseThrow();

            assertThat(spellings.next(new SplittableRandom(99L)))
                    .isEqualTo(spellings.next(new SplittableRandom(99L)));
        }
    }

    @Nested
    @DisplayName("the lengths a shape demands alongside the spelling")
    class Lengths {

        @RepeatedTest(10)
        @DisplayName("a rule of one or more characters is stretched to the exact length demanded")
        void an_exact_length_is_reached() {
            // A commit identifier, as one document in the corpus writes it: forty hexadecimal
            // digits, where the rule alone would be happy with one. The two statements have to
            // hold at once, and only the length says forty.
            MatchingStrings spellings = reading("^[0-9a-fA-F]+$", 40, 40).orElseThrow();

            assertThat(spellings.next(RANDOM))
                    .hasValueSatisfying(built -> assertThat(built).hasSize(40));
        }

        @RepeatedTest(20)
        @DisplayName("a rule with no end of its own is kept inside the length allowed")
        void an_unbounded_rule_stays_short() {
            MatchingStrings spellings = reading("^[0-9]*$", 1, 20).orElseThrow();

            assertThat(spellings.next(RANDOM))
                    .hasValueSatisfying(built -> assertThat(built.length()).isBetween(1, 20));
        }

        @Test
        @DisplayName("repetitions inside repetitions are refused before they multiply")
        void nested_repetitions_are_refused() {
            // Four "one or more" groups inside each other. Left to run as far as the builder's own
            // default would allow, this produces strings of hundreds of thousands of characters;
            // even at eight apiece it is four thousand, which is more than any value may be when
            // the shape says it may be sixty-four.
            assertThat(reading("^(((a+)+)+)+$", 1, 64)).isEmpty();
        }

        @Test
        @DisplayName("a repetition inside a repetition is read when what it could build still fits")
        void nested_repetitions_that_fit_are_read() {
            assertThat(reading("^(ab{2}){3}$", 1, 64))
                    .describedAs("six characters, which is not a reason to refuse anything")
                    .isPresent();
        }

        @Test
        @DisplayName("a rule demanding more characters than are ever sent is refused unread")
        void a_rule_demanding_an_enormous_value_is_refused() {
            // Refused by reading the rule rather than by building from it: one attempt at this
            // would allocate a megabyte, and the loop would have made two hundred of them.
            assertThat(reading("^[a-z]{1000000}$", 1, 64)).isEmpty();
            assertThat(reading("^[a-z]{100000000}$", 1, 64)).isEmpty();
            // Both ends of a range, because a rule that states its own upper end is not subject to
            // the limit on how far a repetition runs. Drawing one of these produced nine hundred
            // thousand characters.
            assertThat(reading("^[a-z]{0,1000000}$", 1, 64)).isEmpty();
        }

        @Test
        @DisplayName("a repetition counted in the ordinary way is still read")
        void an_ordinary_repetition_count_is_read() {
            assertThat(reading("^[a-z]{3}$", 1, 64)).isPresent();
            assertThat(reading("^[a-z]{2,5}$", 1, 64)).isPresent();
            assertThat(reading("^[a-z]{2,}$", 1, 64)).isPresent();
        }

        @Test
        @DisplayName("nothing is offered when the spelling and the length cannot both hold")
        void nothing_is_offered_when_the_two_cannot_both_hold() {
            // Three capital letters, in a shape that also insists on ten characters.
            MatchingStrings spellings = reading("^[A-Z]{3}$", 10, 10).orElseThrow();

            assertThat(spellings.next(RANDOM))
                    .describedAs("a value that breaks the shape is worse than no value")
                    .isEmpty();
        }

        @Test
        @DisplayName("a shape demanding more characters than anything is built with is declined")
        void an_absurd_length_is_declined() {
            assertThat(reading("^[A-Z]+$", 20_000, Long.MAX_VALUE)).isEmpty();
        }

        @Test
        @DisplayName("a shape whose lengths contradict each other is declined")
        void contradictory_lengths_are_declined() {
            assertThat(reading("^[A-Z]+$", 10, 3)).isEmpty();
        }
    }

    @Nested
    @DisplayName("what it declines")
    class WhatItDeclines {

        @ParameterizedTest
        @ValueSource(strings = {"^[A-Z", "(unclosed", "\\p{Nonsense}", "(?<1bad>x)"})
        @DisplayName("a rule that is not a rule at all is declined rather than thrown over")
        void a_malformed_rule_is_declined(String notARule) {
            assertThat(reading(notARule, 1, ANY_LENGTH)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"\\k<nope>", "a{2,1}", "[z-a]"})
        @DisplayName("a rule the builder reads but this platform will not is declined too")
        void a_rule_only_one_of_the_two_readers_accepts_is_declined(String oneSided) {
            // Nothing here is offered without being held against the rule again, by this platform's
            // own machinery. A rule only the builder can read is one no value could be checked
            // against, so no value is built from it.
            assertThat(reading(oneSided, 1, ANY_LENGTH)).isEmpty();
        }

        @Test
        @DisplayName("a rule the two readers understand differently is declined, not half-honoured")
        void a_rule_the_two_readers_disagree_about_is_declined() {
            // "Eight characters, at least one of them a capital", which is how a password rule is
            // written. The builder does not understand the first half and builds ten characters;
            // this platform holds it to eight. Left accepted, every candidate would be refused one
            // by one and the parameter would be reported as one no value could be found for - when
            // an ordinary word does perfectly well.
            assertThat(reading("^(?=.*[A-Z])[A-Za-z0-9]{8}$", 1, ANY_LENGTH))
                    .describedAs("a rule whose two readings disagree is one to carry on without")
                    .isEmpty();
        }

        @Test
        @DisplayName("a rule nested thousands deep does not take the run down with it")
        void a_deeply_nested_rule_is_declined() {
            String nested = "(".repeat(20_000) + "a" + ")".repeat(20_000);

            assertThat(reading(nested, 1, ANY_LENGTH)).isEmpty();
        }
    }

    @Nested
    @DisplayName("holding a value from elsewhere against a stated rule")
    class Allows {

        @Test
        @DisplayName("a shape that states no rule holds nothing against anything")
        void no_rule_allows_everything() {
            assertThat(MatchingStrings.allows(Optional.empty(), "anything at all")).isTrue();
        }

        @Test
        @DisplayName("a rule is met somewhere in the value, not by the whole of it")
        void a_rule_is_met_anywhere_in_the_value() {
            assertThat(MatchingStrings.allows(Optional.of("@"), "someone@example.com")).isTrue();
        }

        @Test
        @DisplayName("a value the rule refuses is refused")
        void a_value_the_rule_refuses_is_refused() {
            assertThat(MatchingStrings.allows(Optional.of("^[A-Z]{3}$"), "abc")).isFalse();
        }

        @Test
        @DisplayName("a rule nothing can be checked against holds nothing against the value")
        void an_uncheckable_rule_allows_the_value() {
            assertThat(MatchingStrings.allows(Optional.of("^[A-Z"), "anything")).isTrue();
        }
    }

    /** Asked with no preference for a shorter value, so what comes back is what the rule allows. */
    private static Optional<MatchingStrings> reading(String rule, long shortest, long longest) {
        return MatchingStrings.reading(rule, shortest, longest, longest, RANDOM);
    }
}

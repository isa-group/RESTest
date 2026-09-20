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

import com.github.curiousoddman.rgxgen.RgxGen;
import com.github.curiousoddman.rgxgen.config.RgxGenOption;
import com.github.curiousoddman.rgxgen.config.RgxGenProperties;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The two lengths that decide whether a spelling rule is safe to build from, and how hard to look
 * for a short value once it is.
 *
 * <p>One property matters more than all the others: the longer of the two must never be
 * <em>smaller</em> than what the builder actually builds, because a low answer is what lets through
 * the rule that exhausts the program's memory. That property is tested against the builder itself
 * rather than against anybody's expectations, which is the only way to test it that means anything -
 * the first version of this file asserted by hand that a question about what comes next costs no
 * characters, which is true of the notation, false of the builder, and left three ways to end a run.
 */
class MatchLengthTest {

    /** How far a repetition with no end of its own is taken to run, in these tests. */
    private static final int EIGHT = 8;

    /** Enough draws to catch a builder that only occasionally exceeds what was counted. */
    private static final int DRAWS = 200;

    @Nested
    @DisplayName("never less than what the builder builds")
    class NeverLessThanTheBuilderBuilds {

        /**
         * Rules chosen for the constructs whose length is easy to get wrong, alongside the ones the
         * corpus actually states. Every one of the first six was an undercount at some point in this
         * increment's life, and three of them ended the run.
         */
        private static final List<String> AWKWARD = List.of(
                "(?=[a-z]{40})x",
                "(?![a-z]{40})x",
                "(?<=[a-z]{40})x",
                "(?=[a-z]{9}){3}",
                "a{2}{3}",
                "[a-z]{1,3}{4}",
                "^(((a+)+)+)+$",
                "^(ab{2}){3}$",
                "(a|bb|ccc){4}",
                "^\\p{L}+(-\\p{L}+)*$",
                "x?y*z+",
                "[^a-z]{2,4}");

        @Test
        @DisplayName("nothing the builder makes is longer than the count says it could be")
        void the_builder_never_exceeds_the_count() {
            SplittableRandom random = new SplittableRandom(20260920L);
            List<String> rules = new java.util.ArrayList<>(AWKWARD);
            rules.addAll(MatchingStringsTest.THE_CORPUS_STATES);

            for (String rule : rules) {
                Optional<MatchLength> counted = MatchLength.of(rule, EIGHT);
                if (counted.isEmpty()) {
                    continue;
                }
                RgxGenProperties options = new RgxGenProperties();
                RgxGenOption.INFINITE_PATTERN_REPETITION.setInProperties(options, EIGHT);
                RgxGen builder = RgxGen.parse(options, rule);
                for (int draw = 0; draw < DRAWS; draw++) {
                    String built = builder.generate(random);
                    assertThat((long) built.length())
                            .describedAs("%s was counted at no more than %d and built %d", rule,
                                    counted.orElseThrow().longest(), built.length())
                            .isLessThanOrEqualTo(counted.orElseThrow().longest());
                }
            }
        }

        @Test
        @DisplayName("nor is anything the builder makes from five thousand rules nobody wrote")
        void the_builder_never_exceeds_the_count_on_rules_nobody_wrote() {
            // Hand-written cases only cover what their author thought of, and what their author did
            // not think of is exactly how this count came to be wrong three times. So the rules are
            // made up: every construct whose length is awkward, put together at random, deep enough
            // to nest. Each one is asked, and then the builder is held to the answer.
            SplittableRandom random = new SplittableRandom(20260920L);
            for (int attempt = 0; attempt < 5_000; attempt++) {
                String rule = aRuleNobodyWrote(random, 0);
                Optional<MatchLength> counted = MatchLength.of(rule, EIGHT);
                // Skipped the way the tool skips them: a rule that could build more than any value
                // is allowed never reaches the builder in a run either. Kept well under that here,
                // because what is being checked is the arithmetic and not the patience of whoever
                // is waiting for the build.
                if (counted.isEmpty() || counted.orElseThrow().longest() > 500) {
                    continue;
                }
                RgxGenProperties options = new RgxGenProperties();
                RgxGenOption.INFINITE_PATTERN_REPETITION.setInProperties(options, EIGHT);
                RgxGen builder;
                try {
                    java.util.regex.Pattern.compile(rule);
                    builder = RgxGen.parse(options, rule);
                } catch (RuntimeException neitherReaderLikesIt) {
                    continue;
                }
                for (int draw = 0; draw < 5; draw++) {
                    String built = builder.generate(random);
                    assertThat((long) built.length())
                            .describedAs("%s was counted at %d..%d and built %d characters", rule,
                                    counted.orElseThrow().shortest(),
                                    counted.orElseThrow().longest(), built.length())
                            .isBetween(counted.orElseThrow().shortest(),
                                    counted.orElseThrow().longest());
                }
            }
        }

        /**
         * One made-up rule, from as wide an alphabet as can be written.
         *
         * <p>Wide on purpose, and wider than the constructs the count knows how to read. A fuzzer
         * drawing only from what its author has already thought about re-confirms the bugs already
         * fixed and finds nothing; the ones that ended a run in this increment were all in
         * constructs missing from an earlier version of this list. So everything goes in - the marks
         * for where a value begins and ends, quotations, characters written as numbers, the
         * reluctant and possessive quantifiers, sets holding sets - and a rule the count declines is
         * a pass, because declining is safe. What must never happen is an answer that is too small.
         */
        private static String aRuleNobodyWrote(SplittableRandom random, int depth) {
            StringBuilder rule = new StringBuilder();
            int pieces = 1 + random.nextInt(3);
            for (int piece = 0; piece < pieces; piece++) {
                rule.append(onePiece(random, depth)).append(aQuantifier(random));
            }
            return rule.toString();
        }

        private static String onePiece(SplittableRandom random, int depth) {
            if (depth < 3 && random.nextInt(3) == 0) {
                String inside = aRuleNobodyWrote(random, depth + 1);
                if (random.nextInt(4) == 0) {
                    inside = inside + "|" + aRuleNobodyWrote(random, depth + 1);
                }
                return switch (random.nextInt(9)) {
                    case 0 -> "(?:" + inside + ")";
                    case 1 -> "(?=" + inside + ")";
                    case 2 -> "(?!" + inside + ")";
                    case 3 -> "(?<=" + inside + ")";
                    case 4 -> "(?<!" + inside + ")";
                    case 5 -> "(?<name" + depth + ">" + inside + ")";
                    case 6 -> "(?>" + inside + ")";
                    case 7 -> "(?i:" + inside + ")";
                    default -> "(" + inside + ")";
                };
            }
            return switch (random.nextInt(22)) {
                case 0 -> "[a-z]";
                case 1 -> "[^0-9]";
                case 2 -> "[^]]";
                case 3 -> "[a-z&&[^aeiou]]";
                case 4 -> "\\d";
                case 5 -> "\\w";
                case 6 -> "\\s";
                case 7 -> "\\p{L}";
                case 8 -> "\\A";
                case 9 -> "\\z";
                case 10 -> "\\Z";
                case 11 -> "\\b";
                case 12 -> "\\Q{9}\\E";
                case 13 -> "\\x41";
                case 14 -> "\\u0041";
                case 15 -> "\\052";
                case 16 -> "\\cA";
                case 17 -> "\\n";
                case 18 -> ".";
                case 19 -> "ab";
                case 20 -> "-";
                default -> "x";
            };
        }

        private static String aQuantifier(SplittableRandom random) {
            String count = switch (random.nextInt(8)) {
                case 0 -> "*";
                case 1 -> "+";
                case 2 -> "?";
                case 3 -> "{" + (1 + random.nextInt(4)) + "}";
                case 4 -> "{" + random.nextInt(3) + "," + (3 + random.nextInt(4)) + "}";
                case 5 -> "{" + (1 + random.nextInt(3)) + ",}";
                default -> "";
            };
            if (count.isEmpty() || random.nextInt(3) != 0) {
                return count;
            }
            return count + (random.nextBoolean() ? "?" : "+");
        }
    }

    @Nested
    @DisplayName("the two lengths it arrives at")
    class TheLengths {

        @ParameterizedTest(name = "{0} is between {1} and {2} characters")
        @CsvSource({
                "'^[A-Z]{3}$',                     3,   3",
                "'^[A-Z]{2,5}$',                   2,   5",
                "'^[a-z]+$',                       1,   8",
                "'^[a-z]*$',                       0,   8",
                "'^[a-z]?$',                       0,   1",
                "'^abc$',                          3,   3",
                "'^a|bb|ccc$',                     1,   3",
                "'^\\d{4}-\\d{2}-\\d{2}$',         10,  10",
                "'^\\p{L}+$',                      1,   8",
                "'^(ab){3}$',                      6,   6",
                "'^(ab{2}){3}$',                   9,   9",
                "'^[0-9a-fA-F]{128}$',             128, 128",
                "'^[a-z]{2,}$',                    2,   10",
                "'^$',                             0,   0",
                "'a{2}{3}',                        6,   6",
        })
        @DisplayName("a rule comes to what its pieces come to")
        void the_pieces_add_up(String rule, long shortest, long longest) {
            assertThat(MatchLength.of(rule, EIGHT))
                    .contains(new MatchLength(shortest, longest));
        }

        @Test
        @DisplayName("a question about what comes next is counted, because the builder builds it")
        void a_lookahead_is_counted() {
            // The notation says this matches no characters. The builder does not read it that way,
            // and what is being counted here is what the builder will make.
            assertThat(MatchLength.of("^(?=.*[A-Z])[A-Za-z0-9]{8}$", EIGHT))
                    .hasValueSatisfying(both -> assertThat(both.longest()).isGreaterThan(8));
        }

        @Test
        @DisplayName("repetitions inside repetitions multiply")
        void nesting_multiplies() {
            assertThat(MatchLength.of("^(((a+)+)+)+$", EIGHT))
                    .contains(new MatchLength(1, 8L * 8 * 8 * 8));
        }

        @Test
        @DisplayName("a count too large to hold in a number is answered, not wrapped round")
        void an_enormous_count_does_not_wrap() {
            Optional<MatchLength> enormous =
                    MatchLength.of("^(a{1000000000}){1000000000}$", EIGHT);

            assertThat(enormous).isPresent();
            assertThat(enormous.orElseThrow().longest())
                    .describedAs("wrapping round would make this look small and safe")
                    .isGreaterThan(1_000_000_000L);
        }

        @Test
        @DisplayName("a quotation is not read at all, because the two readers do not agree on one")
        void a_quotation_says_nothing() {
            assertThat(MatchLength.of("\\Q{1000000}\\E", EIGHT))
                    .describedAs("an escape named by a letter is answered for only when it is one "
                            + "of the handful this understands, and a quotation is not among them")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("the rules real documents state")
    class RealRules {

        @Test
        @DisplayName("every spelling rule in the corpus comes to a length worth building")
        void the_corpus_is_all_short() {
            for (String rule : MatchingStringsTest.THE_CORPUS_STATES) {
                assertThat(MatchLength.of(rule, EIGHT))
                        .describedAs("no answer for %s, which costs the rule", rule)
                        .hasValueSatisfying(both -> assertThat(both.longest())
                                .describedAs("%s is counted as far longer than it is", rule)
                                .isLessThanOrEqualTo(64));
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
            assertThat(MatchLength.of(notARule, EIGHT)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"^(\\w{3})-\\1$", "(a)(b)\\2", "(?<part>x)\\k<part>"})
        @DisplayName("a rule repeating what it matched elsewhere says nothing, since it could double")
        void a_back_reference_says_nothing(String repeating) {
            // The builder answers one of these by repeating whatever it built for the group, so a
            // rule can double its own length with every five characters written: twenty-seven of
            // them nested is six hundred million characters in a rule of one hundred and thirty-two.
            assertThat(MatchLength.of(repeating, EIGHT)).isEmpty();
        }

        @Test
        @DisplayName("a rule nested deeper than anybody writes says nothing")
        void a_deeply_nested_rule_says_nothing() {
            String nested = "(".repeat(20_000) + "a" + ")".repeat(20_000);

            assertThat(MatchLength.of(nested, EIGHT))
                    .describedAs("reading it to the bottom would run out of room to read in")
                    .isEmpty();
        }

        @Test
        @DisplayName("groups side by side are not nesting, and are read")
        void groups_side_by_side_are_read() {
            assertThat(MatchLength.of("(a)".repeat(150), EIGHT))
                    .describedAs("a hundred and fifty groups, none inside another")
                    .contains(new MatchLength(150, 150));
        }
    }
}

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

import java.util.Optional;

/**
 * How long a string satisfying a spelling rule can be: at the shortest, and at the very longest.
 *
 * <p>Both ends are asked before anything is built, and each answers a question the caller cannot
 * answer any other way.
 *
 * <p><b>The longest</b> is a matter of safety. A rule is allowed to demand a value far larger than
 * any machine should be asked to make: {@code [a-z]{900000000,}} is nine hundred million characters,
 * nine "repeat this nine times" written inside one another is three hundred and eighty-seven
 * million, and both are short, ordinary-looking rules a specification may legally contain. Building
 * one exhausts the memory of the whole program, which is not something that can be caught and
 * carried on from, so the rule is read instead of tried.
 *
 * <p><b>The shortest</b> is a matter of not wasting the run's time. Values are drawn until one is of
 * an acceptable length, and a rule demanding a hundred and twenty-eight characters will never draw
 * one of sixty-four however long anybody waits. Knowing the shortest it could possibly build is what
 * lets the drawing stop at the first usable value instead of hunting for a shorter one that does not
 * exist.
 *
 * <p>The answers are deliberately generous at the top end and modest at the bottom, so a rule this
 * passes is safe to build from and one it refuses may merely have looked dangerous. Refusing costs a
 * spelling rule, which the caller answers with an ordinary word; being wrong the other way costs the
 * run.
 *
 * <p>What it counts is what the <em>builder</em> will make, not what the notation means, and those
 * are not the same thing. A question about what comes next - "is there a capital letter further
 * along?" - matches no characters at all by the rules of the notation, and the builder cheerfully
 * builds every one of them. Reading that as nothing was a real mistake made here, caught by review,
 * and it is the reason this paragraph exists.
 *
 * <p>It is not a reader of regular expressions in any general sense, and does not try to be. It
 * knows how the pieces of one combine in length - things written one after another add up, a choice
 * between them takes the widest span, and a repetition multiplies - and it treats anything it does
 * not recognise, and anything whose length depends on what was matched elsewhere, as a reason to say
 * nothing at all rather than to guess low.
 *
 * @param shortest the fewest characters a string satisfying the rule could have
 * @param longest the most it could have
 */
record MatchLength(long shortest, long longest) {

    /** Sizes are added and multiplied here without ever wrapping round to a small number again. */
    private static final long BEYOND_COUNTING = Long.MAX_VALUE;

    /**
     * How many groups deep this will go before giving up.
     *
     * <p>Reading a rule means stepping inside each group as it is met, and a rule written with
     * twenty thousand brackets inside one another would step twenty thousand times and exhaust the
     * room the program has for doing so. That is the same kind of failure this record exists to
     * prevent, so it stops well short. No rule anybody writes on purpose is ten groups deep.
     */
    private static final int AS_DEEP_AS_A_RULE_GOES = 100;

    /** What a piece of a rule that could not be read answers with. */
    private static final MatchLength UNREADABLE = null;

    /**
     * How long a string satisfying this rule can be.
     *
     * @param rule the spelling rule, exactly as the specification wrote it
     * @param repetitionsWithNoEnd how many times a repetition that states no end of its own is taken
     *     to run, which is a decision of whoever is about to build from the rule
     * @return the two lengths, or nothing at all when the rule could not be read this way - which
     *     the caller should treat as a rule not to build from
     */
    static Optional<MatchLength> of(String rule, int repetitionsWithNoEnd) {
        Reading reading = new Reading(rule, Math.max(1, repetitionsWithNoEnd));
        MatchLength both = reading.choice();
        // Anything left over means the reading stopped early - an unmatched bracket, a notation it
        // does not know - and a half-read rule is one to say nothing about.
        return both != UNREADABLE && reading.at == rule.length()
                ? Optional.of(both)
                : Optional.empty();
    }

    /** Addition that stops at the largest number there is rather than wrapping round. */
    private MatchLength plus(MatchLength next) {
        return new MatchLength(plus(shortest, next.shortest), plus(longest, next.longest));
    }

    /** The widest span of two alternatives: either could be built, so either length could appear. */
    private MatchLength orElse(MatchLength other) {
        return new MatchLength(Math.min(shortest, other.shortest),
                Math.max(longest, other.longest));
    }

    /** This piece repeated between the two counts of a quantifier. */
    private MatchLength repeated(MatchLength times) {
        return new MatchLength(product(shortest, times.shortest), product(longest, times.longest));
    }

    private static long plus(long first, long second) {
        long total = first + second;
        return total < 0 ? BEYOND_COUNTING : total;
    }

    private static long product(long first, long second) {
        if (first == 0 || second == 0) {
            return 0;
        }
        if (first > BEYOND_COUNTING / second) {
            return BEYOND_COUNTING;
        }
        return first * second;
    }

    /** One walk through one rule, from the first character to the last. */
    private static final class Reading {

        private static final MatchLength NOTHING = new MatchLength(0, 0);
        private static final MatchLength ONE_CHARACTER = new MatchLength(1, 1);
        private static final MatchLength ONCE = new MatchLength(1, 1);

        private final String rule;
        private final int repetitionsWithNoEnd;
        private int at;
        private int depth;

        private Reading(String rule, int repetitionsWithNoEnd) {
            this.rule = rule;
            this.repetitionsWithNoEnd = repetitionsWithNoEnd;
        }

        /** One of several alternatives. */
        private MatchLength choice() {
            MatchLength span = inARow();
            while (span != UNREADABLE && at < rule.length() && rule.charAt(at) == '|') {
                at++;
                MatchLength next = inARow();
                span = next == UNREADABLE ? UNREADABLE : span.orElse(next);
            }
            return span;
        }

        /** Several pieces one after another, which come to all of them together. */
        private MatchLength inARow() {
            MatchLength total = NOTHING;
            while (at < rule.length() && rule.charAt(at) != '|' && rule.charAt(at) != ')') {
                MatchLength piece = repeated();
                if (piece == UNREADABLE) {
                    return UNREADABLE;
                }
                total = total.plus(piece);
            }
            return total;
        }

        /**
         * One piece, as many times over as the rule says it may appear.
         *
         * <p>Counts written one after another - {@code a{2}{3}} - multiply, because that is what the
         * builder does with them. Reading only the first and leaving the second to be mistaken for
         * ordinary characters was an undercount, and an undercount is the one kind of mistake this
         * must not make.
         */
        private MatchLength repeated() {
            MatchLength total = piece();
            if (total == UNREADABLE) {
                return UNREADABLE;
            }
            while (at < rule.length()) {
                int before = at;
                MatchLength times = howManyTimes();
                if (times == UNREADABLE) {
                    return UNREADABLE;
                }
                total = total.repeated(times);
                if (at == before) {
                    break;
                }
            }
            return total;
        }

        /** One piece: a group, a set of characters to choose from, an escape, or a plain character. */
        private MatchLength piece() {
            return switch (rule.charAt(at)) {
                case '(' -> group();
                case '[' -> set();
                case '\\' -> escape();
                // Where a value begins and ends: rules about position, which no character satisfies
                // and none is spent on.
                case '^', '$' -> step(1, NOTHING);
                case '*', '+', '?' -> UNREADABLE;
                default -> step(1, ONE_CHARACTER);
            };
        }

        /**
         * A group, which comes to whatever is inside it.
         *
         * <p>Including a group that asks what comes next without consuming it, which by the rules of
         * the notation is worth nothing at all. The question here is not what the notation means but
         * what the builder will make, and the builder makes those groups the way it makes any other:
         * a rule asking whether nine hundred million letters follow has nine hundred million letters
         * built for it.
         */
        private MatchLength group() {
            if (++depth > AS_DEEP_AS_A_RULE_GOES) {
                return UNREADABLE;
            }
            int opens = at;
            at++;
            if (at < rule.length() && rule.charAt(at) == '?') {
                if (at + 1 >= rule.length()) {
                    return UNREADABLE;
                }
                at = skipGroupIntroduction(at + 1);
                if (at < 0) {
                    at = opens;
                    return UNREADABLE;
                }
            }
            MatchLength inside = choice();
            if (inside == UNREADABLE || at >= rule.length() || rule.charAt(at) != ')') {
                return UNREADABLE;
            }
            at++;
            depth--;
            return inside;
        }

        /**
         * Steps past whatever a group says about itself before its contents begin: that it is not
         * worth remembering, that it has a name, that it looks ahead or behind.
         */
        private int skipGroupIntroduction(int after) {
            char kind = rule.charAt(after);
            if (kind == ':' || kind == '=' || kind == '!' || kind == '>') {
                return after + 1;
            }
            if (kind == '<' && after + 1 < rule.length()
                    && (rule.charAt(after + 1) == '=' || rule.charAt(after + 1) == '!')) {
                return after + 2;
            }
            // A name, or a setting: everything up to the colon or the bracket that ends it.
            int end = after;
            while (end < rule.length() && rule.charAt(end) != ':' && rule.charAt(end) != '>') {
                end++;
            }
            return end < rule.length() ? end + 1 : -1;
        }

        /** A set of characters to choose from, which is one character however many are in it. */
        private MatchLength set() {
            int walk = at + 1;
            if (walk < rule.length() && rule.charAt(walk) == '^') {
                walk++;
            }
            // A closing bracket written first is one of the characters rather than the end of the set.
            if (walk < rule.length() && rule.charAt(walk) == ']') {
                walk++;
            }
            while (walk < rule.length() && rule.charAt(walk) != ']') {
                walk += rule.charAt(walk) == '\\' ? 2 : 1;
            }
            if (walk >= rule.length()) {
                return UNREADABLE;
            }
            at = walk + 1;
            return ONE_CHARACTER;
        }

        /** A character named rather than written: a digit, a letter, a bracket meant literally. */
        private MatchLength escape() {
            if (at + 1 >= rule.length()) {
                return UNREADABLE;
            }
            char named = rule.charAt(at + 1);
            // "The same as the third group again", which the builder answers by repeating whatever
            // it built there - so what this costs is whatever that group cost, and a rule can nest
            // the trick to double its length with every five characters written. Not counted:
            // refused, and the caller sends an ordinary word instead.
            if (named >= '1' && named <= '9' || named == 'k') {
                return UNREADABLE;
            }
            at += 2;
            // A whole family of characters named inside brackets - every letter, every currency
            // sign - or one character written as a number. Either way the braces belong to the
            // escape and are not a repetition count, which is what they would otherwise be read as.
            if ((named == 'p' || named == 'P' || named == 'x' || named == 'u' || named == 'N')
                    && at < rule.length() && rule.charAt(at) == '{') {
                int closes = rule.indexOf('}', at);
                if (closes < 0) {
                    return UNREADABLE;
                }
                at = closes + 1;
                return ONE_CHARACTER;
            }
            // Everything between here and the end of the quotation is itself, braces included.
            if (named == 'Q') {
                int ends = rule.indexOf("\\E", at);
                long literal = (ends < 0 ? rule.length() : ends) - at;
                at = ends < 0 ? rule.length() : ends + 2;
                return new MatchLength(literal, literal);
            }
            // A word boundary spends no characters; everything else stands for exactly one.
            boolean position = named == 'b' || named == 'B' || named == 'A' || named == 'z'
                    || named == 'Z';
            return position ? NOTHING : ONE_CHARACTER;
        }

        /** How many times the piece just read may appear. Once, unless the rule says otherwise. */
        private MatchLength howManyTimes() {
            if (at >= rule.length()) {
                return ONCE;
            }
            MatchLength times = switch (rule.charAt(at)) {
                case '*' -> step(1, new MatchLength(0, repetitionsWithNoEnd));
                case '+' -> step(1, new MatchLength(1, repetitionsWithNoEnd));
                case '?' -> step(1, new MatchLength(0, 1));
                case '{' -> counted();
                default -> ONCE;
            };
            // "As few as possible" and "and do not give any back" change which string is found,
            // never how long one may be.
            if (times != UNREADABLE && at < rule.length()
                    && (rule.charAt(at) == '?' || rule.charAt(at) == '+')) {
                at++;
            }
            return times;
        }

        /** A repetition written out: {@code {3}}, {@code {2,5}}, or {@code {2,}} with no end. */
        private MatchLength counted() {
            int closes = rule.indexOf('}', at);
            if (closes < 0) {
                return ONCE;
            }
            String written = rule.substring(at + 1, closes);
            if (!written.matches("\\d+(,\\d*)?")) {
                // Not a count at all, so the brace was a character somebody wrote and the piece
                // before it stands on its own.
                return ONCE;
            }
            at = closes + 1;
            int comma = written.indexOf(',');
            if (comma < 0) {
                long exactly = number(written);
                return new MatchLength(exactly, exactly);
            }
            long fewest = number(written.substring(0, comma));
            String most = written.substring(comma + 1);
            return new MatchLength(fewest,
                    most.isEmpty() ? plus(fewest, repetitionsWithNoEnd) : number(most));
        }

        /**
         * A count written in the rule, however many digits somebody used.
         *
         * <p>A count too long to hold in a number is not a count to argue with: it is larger than
         * any value could ever be, and saying so is the whole point of asking. Reading it as though
         * the braces were ordinary characters - which is what a stricter reading would do - would
         * answer twenty-five for a rule demanding a thousand million.
         */
        private static long number(String digits) {
            try {
                return Long.parseLong(digits);
            } catch (NumberFormatException longerThanAnyNumber) {
                return BEYOND_COUNTING;
            }
        }

        private MatchLength step(int characters, MatchLength worth) {
            at += characters;
            return worth;
        }
    }
}

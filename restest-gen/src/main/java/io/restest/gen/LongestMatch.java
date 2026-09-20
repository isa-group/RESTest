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

import java.util.OptionalLong;

/**
 * How long a string satisfying a spelling rule could possibly be.
 *
 * <p>Asked before anything is built, and for one reason: a rule is allowed to demand a value far
 * larger than any machine should be asked to make. {@code [a-z]{900000000,}} is nine hundred million
 * characters, and nine "repeat this nine times" written inside one another is three hundred and
 * eighty-seven million - both of them short, ordinary-looking rules that a specification may legally
 * contain. Building one of those exhausts the memory of the whole program, which is not something
 * that can be caught and carried on from. So the question is answered by reading the rule instead.
 *
 * <p>The answer is deliberately generous. It counts what a rule <em>could</em> produce at its very
 * worst, never what it usually will, so a rule it passes is certainly safe and a rule it refuses may
 * merely have looked dangerous. Refusing costs one spelling rule, which the caller answers with an
 * ordinary word; being wrong the other way costs the run.
 *
 * <p>It is not a reader of regular expressions in any general sense, and does not try to be. It
 * knows how the pieces of one combine in length - things written one after another add up, a choice
 * between them takes the longer, and a repetition multiplies - and it treats anything it does not
 * recognise as a reason to say nothing at all rather than to guess low.
 */
final class LongestMatch {

    /** Sizes are added and multiplied here without ever wrapping round to a small number again. */
    private static final long BEYOND_COUNTING = Long.MAX_VALUE;

    /**
     * How many groups deep this will go before giving up.
     *
     * <p>Reading a rule means stepping inside each group as it is met, and a rule written with
     * twenty thousand brackets inside one another would step twenty thousand times and exhaust the
     * room the program has for doing so. That is the same kind of failure this class exists to
     * prevent, so it stops well short. No rule anybody writes on purpose is ten groups deep.
     */
    private static final int AS_DEEP_AS_A_RULE_GOES = 100;

    private final String rule;
    private final int repetitionsWithNoEnd;
    private int at;
    private int depth;

    private LongestMatch(String rule, int repetitionsWithNoEnd) {
        this.rule = rule;
        this.repetitionsWithNoEnd = repetitionsWithNoEnd;
    }

    /**
     * The most characters a string satisfying this rule could have.
     *
     * @param rule the spelling rule, exactly as the specification wrote it
     * @param repetitionsWithNoEnd how many times a repetition that states no end of its own is taken
     *     to run, which is a decision of whoever is about to build from the rule
     * @return the count, or nothing at all when the rule could not be read this way - which the
     *     caller should treat as a rule not to build from
     */
    static OptionalLong of(String rule, int repetitionsWithNoEnd) {
        LongestMatch reading = new LongestMatch(rule, Math.max(1, repetitionsWithNoEnd));
        long longest = reading.choice();
        // Anything left over means the reading stopped early - an unmatched bracket, a notation it
        // does not know - and a half-read rule is one to say nothing about.
        return reading.at == rule.length() && longest >= 0
                ? OptionalLong.of(longest)
                : OptionalLong.empty();
    }

    /** One of several alternatives, which is as long as its longest. */
    private long choice() {
        long longest = inARow();
        while (longest >= 0 && at < rule.length() && rule.charAt(at) == '|') {
            at++;
            longest = Math.max(longest, inARow());
        }
        return longest;
    }

    /** Several pieces one after another, which is as long as all of them together. */
    private long inARow() {
        long total = 0;
        while (at < rule.length() && rule.charAt(at) != '|' && rule.charAt(at) != ')') {
            long piece = repeated();
            if (piece < 0) {
                return -1;
            }
            total = plus(total, piece);
        }
        return total;
    }

    /** One piece, as many times over as the rule says it may appear. */
    private long repeated() {
        long once = piece();
        if (once < 0) {
            return -1;
        }
        long times = howManyTimes();
        return times < 0 ? -1 : product(once, times);
    }

    /** One piece: a group, a set of characters to choose from, an escape, or a plain character. */
    private long piece() {
        char here = rule.charAt(at);
        return switch (here) {
            case '(' -> group();
            case '[' -> set();
            case '\\' -> escape();
            // Where a value begins and ends, and the edges of a word: rules about position, which
            // no character satisfies and none is spent on.
            case '^', '$' -> advanceBy(1, 0);
            case '*', '+', '?' -> -1;
            default -> advanceBy(1, 1);
        };
    }

    /** A group, which is worth whatever is inside it - unless it is one that matches no characters. */
    private long group() {
        if (++depth > AS_DEEP_AS_A_RULE_GOES) {
            return -1;
        }
        int opens = at;
        at++;
        boolean spendsNothing = false;
        if (at < rule.length() && rule.charAt(at) == '?') {
            int after = at + 1;
            if (after >= rule.length()) {
                return -1;
            }
            char kind = rule.charAt(after);
            // A group that asks what comes next, or what came before, without consuming any of it.
            spendsNothing = kind == '=' || kind == '!'
                    || (kind == '<' && after + 1 < rule.length()
                        && (rule.charAt(after + 1) == '=' || rule.charAt(after + 1) == '!'));
            at = skipGroupIntroduction(after);
            if (at < 0) {
                at = opens;
                return -1;
            }
        }
        long inside = choice();
        if (inside < 0 || at >= rule.length() || rule.charAt(at) != ')') {
            return -1;
        }
        at++;
        return spendsNothing ? 0 : inside;
    }

    /**
     * Steps past whatever a group says about itself before its contents begin: that it is not worth
     * remembering, that it has a name, that it looks ahead or behind, or that it turns a setting on.
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
    private long set() {
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
            return -1;
        }
        at = walk + 1;
        return 1;
    }

    /** A character named rather than written: a digit, a letter, a tab, a bracket meant literally. */
    private long escape() {
        if (at + 1 >= rule.length()) {
            return -1;
        }
        char named = rule.charAt(at + 1);
        at += 2;
        // A whole family of characters - every letter, every currency sign - named inside brackets.
        if ((named == 'p' || named == 'P') && at < rule.length() && rule.charAt(at) == '{') {
            int closes = rule.indexOf('}', at);
            if (closes < 0) {
                return -1;
            }
            at = closes + 1;
        }
        // A word boundary spends no characters; everything else stands for exactly one.
        return named == 'b' || named == 'B' || named == 'A' || named == 'z' || named == 'Z' ? 0 : 1;
    }

    /** How many times the piece just read may appear. One, unless the rule says otherwise. */
    private long howManyTimes() {
        if (at >= rule.length()) {
            return 1;
        }
        long times = switch (rule.charAt(at)) {
            case '*' -> advanceBy(1, repetitionsWithNoEnd);
            case '+' -> advanceBy(1, repetitionsWithNoEnd);
            case '?' -> advanceBy(1, 1);
            case '{' -> counted();
            default -> 1;
        };
        // "As few as possible" and "and do not give any back" change which string is found, never
        // how long one may be.
        if (times >= 0 && at < rule.length() && (rule.charAt(at) == '?' || rule.charAt(at) == '+')) {
            at++;
        }
        return times;
    }

    /** A repetition written out: {@code {3}}, {@code {2,5}}, or {@code {2,}} with no end. */
    private long counted() {
        int closes = rule.indexOf('}', at);
        if (closes < 0) {
            return 1;
        }
        String written = rule.substring(at + 1, closes);
        if (!written.matches("\\d+(,\\d*)?")) {
            // Not a count at all, so the brace was a character somebody wrote and the piece before
            // it stands on its own.
            return 1;
        }
        at = closes + 1;
        int comma = written.indexOf(',');
        if (comma < 0) {
            return number(written);
        }
        long fewest = number(written.substring(0, comma));
        String most = written.substring(comma + 1);
        return most.isEmpty() ? plus(fewest, repetitionsWithNoEnd) : number(most);
    }

    /**
     * A count written in the rule, however many digits somebody used.
     *
     * <p>A count too long to hold in a number is not a count to argue with: it is larger than any
     * value could ever be, and saying so is the whole point of asking. Reading it as though the
     * braces were ordinary characters - which is what a stricter reading would do - would answer
     * twenty-five for a rule demanding a thousand million.
     */
    private static long number(String digits) {
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException longerThanAnyNumber) {
            return BEYOND_COUNTING;
        }
    }

    private long advanceBy(int characters, long worth) {
        at += characters;
        return worth;
    }

    /** Addition that stops at the largest number there is rather than wrapping round to a small one. */
    private static long plus(long first, long second) {
        long total = first + second;
        return total < 0 ? BEYOND_COUNTING : total;
    }

    /** Multiplication that does the same. */
    private static long product(long first, long second) {
        if (first == 0 || second == 0) {
            return 0;
        }
        if (first > BEYOND_COUNTING / second) {
            return BEYOND_COUNTING;
        }
        return first * second;
    }
}

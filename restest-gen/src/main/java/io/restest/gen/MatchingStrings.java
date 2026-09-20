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

import com.github.curiousoddman.rgxgen.RgxGen;
import com.github.curiousoddman.rgxgen.config.RgxGenOption;
import com.github.curiousoddman.rgxgen.config.RgxGenProperties;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strings that satisfy the spelling rule a specification states for a value.
 *
 * <p>A specification can say what an acceptable value must <em>look like</em> rather than what it
 * means: a reference number of exactly three capital letters, a name made of letters with at most
 * two spaces in it, a commit identifier of forty hexadecimal digits. The rule is written as a
 * regular expression - a compact notation for "strings of this shape" - and an API that states one
 * usually enforces it, so a made-up word is refused before anything interesting is tested.
 *
 * <p>This works the rule backwards: given "strings of this shape", produce one. That is a small
 * compiler, so it is not written here - a third-party library does the reading and the building, and
 * what this class adds is everything that library cannot know about.
 *
 * <p>Three things, in fact. A shape usually states a length as well as a spelling, and the two have
 * to hold at once: forty hexadecimal digits means forty, not the three the spelling alone would
 * allow. A value has to be short enough to be worth sending, and a rule saying "any number of
 * letters" will happily produce a million of them. And the two readings of a rule - the library's
 * and this platform's - have to agree, because a rule one of them misunderstands would otherwise
 * produce values that satisfy nobody.
 *
 * <p>Two answers other than a string are possible, and they mean different things. <b>Nothing to be
 * read here</b> - the notation is exotic, the rule is malformed, or the two readings disagree about
 * it - means the caller should carry on as though no rule had been stated, because refusing to test
 * a parameter over a rule nobody here understands helps nobody. <b>Read, but nothing fits</b> means
 * the rule and the length the same shape demands cannot both be satisfied by anything this found,
 * and there is genuinely no value to send.
 *
 * <p>Nothing is ever offered without being checked first. Every candidate is held against the rule
 * again, by the plain regular-expression machinery the rest of the tool uses, so a disagreement
 * between the library's reading of a rule and this platform's costs a value rather than producing a
 * wrong one.
 */
final class MatchingStrings {

    /**
     * How far a repetition with no stated end - "one or more letters" - is allowed to run.
     *
     * <p>Small, and much smaller than the longest value the tool will send, because repetitions
     * <em>multiply</em>: a rule of four nested "one or more" groups produces this number raised to
     * the fourth power. Eight of those is a few thousand characters, which is survivable; the
     * library's own default of a hundred is a hundred million, which is not.
     */
    private static final int USUAL_REPETITIONS = 8;

    /** Beyond this, a length the shape demands is not attempted at all. */
    private static final int LONGEST = 10_000;

    /**
     * How many candidates are drawn before giving up on the length the shape demands.
     *
     * <p>Nothing in the library says "of this length", so a shape demanding a string of exactly
     * forty characters - which is how a commit identifier is written down - is met by drawing until
     * one comes out at forty. That happens about once every forty draws, so the allowance grows with
     * the length demanded, and stops growing well before the work does.
     */
    private static final int LEAST_ATTEMPTS = 200;
    private static final int MOST_ATTEMPTS = 2_000;
    private static final int ATTEMPTS_PER_CHARACTER = 5;

    /**
     * How many characters may be built in total while looking for one value.
     *
     * <p>The backstop for a rule whose every candidate is far longer than anything worth sending.
     * One such candidate is survivable and two hundred are not, so the search stops when the work
     * does rather than when the attempts do.
     */
    private static final int CHARACTERS_BUILT = 1_000_000;

    /** How many candidates have to agree with this platform's reading before the rule is used. */
    private static final int PROBES = 3;

    /** Finds a repetition count a rule states outright, as in {@code [a-z]{1000000}}. */
    private static final Pattern STATED_REPETITION = Pattern.compile("\\{\\s*(\\d{1,9})");

    private final String expression;
    private final RgxGen shapes;
    private final Pattern checked;
    private final long shortest;
    private final long longest;
    private final int attempts;

    private MatchingStrings(String expression, RgxGen shapes, Pattern checked, long shortest,
            long longest) {
        this.expression = expression;
        this.shapes = shapes;
        this.checked = checked;
        this.shortest = shortest;
        this.longest = longest;
        this.attempts = (int) Math.min(MOST_ATTEMPTS,
                Math.max(LEAST_ATTEMPTS, ATTEMPTS_PER_CHARACTER * shortest));
    }

    /**
     * The strings one stated rule allows, ready to be drawn from.
     *
     * @param expression the rule, exactly as the specification wrote it
     * @param shortest the fewest characters the value may have
     * @param longest the most it may have - the shape's own limit, or the longest the tool is
     *     willing to send, whichever is smaller
     * @param random where the trial values come from while the rule is being judged
     * @return a way of drawing strings, or nothing at all when there is nothing here to read - in
     *     which case the caller should build a value as though no rule had been stated. Lengths
     *     that contradict each other answer the same way, which is safe because the only caller
     *     works them out from one shape and cannot produce a pair that does
     */
    static Optional<MatchingStrings> reading(String expression, long shortest, long longest,
            RandomGenerator random) {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(random, "random");
        if (shortest > LONGEST || shortest > longest || statedLongerThan(expression, longest)) {
            return Optional.empty();
        }
        try {
            // This platform reads the rule first, and not only because its answer is needed. It is
            // the careful reader of the two: a rule nested thousands deep is refused here in a few
            // microseconds, where the builder would go looking for memory it cannot have and take
            // the run down with it. A rule that gets past this one is a rule worth handing over.
            Pattern checked = Pattern.compile(expression);
            RgxGenProperties howFarRepetitionsRun = new RgxGenProperties();
            RgxGenOption.INFINITE_PATTERN_REPETITION
                    .setInProperties(howFarRepetitionsRun, repetitionLimit(shortest, longest));
            MatchingStrings reading = new MatchingStrings(expression,
                    RgxGen.parse(howFarRepetitionsRun, expression), checked, shortest, longest);
            return reading.bothReadingsAgree(random) ? Optional.of(reading) : Optional.empty();
        } catch (RuntimeException cannotRead) {
            // The library throws its own kind for a rule it cannot read, and this platform throws
            // another for one it will not compile. Neither is a reason to stop the run, and both
            // get the same answer, so one catch covers them.
            return Optional.empty();
        }
    }

    /**
     * Whether the two readings of this rule agree, judged on a few trial values.
     *
     * <p>They can disagree, and silently. The builder does not understand every notation this
     * platform does - a rule saying "eight characters, at least one of them a capital", which is how
     * a password rule is written, is read by the builder as though the first half were not there,
     * and everything it produces is the wrong length. Without this check such a rule would be
     * accepted, every candidate would be rejected one by one, and the parameter would be reported as
     * one no value could be found for - when an ordinary word would have done perfectly well.
     *
     * <p>Length is not judged here, only the spelling. Whether anything of an acceptable length
     * exists is the other question, and the honest answer to that one <em>is</em> that there is no
     * value to send.
     */
    private boolean bothReadingsAgree(RandomGenerator random) {
        for (int probe = 0; probe < PROBES; probe++) {
            if (accepts(shapes.generate(random))) {
                return true;
            }
        }
        return false;
    }

    /**
     * One string that satisfies the rule and the lengths alike.
     *
     * @param random where the choice comes from. Sharing one seeded source across the whole run is
     *     what makes a run repeatable
     * @return the string, or nothing at all when no candidate fitted the length the specification
     *     also demands - which means there is no value to send, not that another source should
     *     invent one
     */
    Optional<String> next(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        long built = 0;
        for (int attempt = 0; attempt < attempts && built < CHARACTERS_BUILT; attempt++) {
            String candidate;
            try {
                candidate = shapes.generate(random);
            } catch (RuntimeException broke) {
                return Optional.empty();
            }
            built += candidate.length();
            if (candidate.length() >= shortest && candidate.length() <= longest
                    && accepts(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a value satisfies this rule.
     *
     * <p>Somewhere in the value rather than all of it, which is what the specification format means
     * by stating one: a rule of {@code @} asks for a value with an at-sign in it, not for a value
     * that is nothing but an at-sign.
     */
    private boolean accepts(String candidate) {
        // Safe to run without a clock watching it, for two reasons together. Runaway backtracking
        // needs a long subject, and nothing reaches here longer than the ten thousand characters
        // that are the most any value is allowed; and every candidate was built from this very rule,
        // so the engine walks a path that exists rather than exhausting the ones that do not.
        return checked.matcher(candidate).find();
    }

    /**
     * Whether a stated rule accepts a value that came from somewhere else.
     *
     * @param expression the rule, or nothing when the specification stated none
     * @param value the value
     * @return whether it may be sent. A rule this platform will not compile is one nothing can be
     *     checked against, so it holds nothing against the value
     */
    static boolean allows(Optional<String> expression, String value) {
        if (expression.isEmpty()) {
            return true;
        }
        try {
            return Pattern.compile(expression.get()).matcher(value).find();
        } catch (RuntimeException cannotRead) {
            return true;
        }
    }

    /**
     * Whether the rule insists outright on more characters than may be sent.
     *
     * <p>Read off the rule rather than discovered by building one, because the building is what
     * there is to avoid: a rule demanding a hundred million characters is a hundred megabytes per
     * attempt, and finding that out the slow way costs the run.
     */
    private static boolean statedLongerThan(String expression, long longest) {
        Matcher counts = STATED_REPETITION.matcher(expression);
        while (counts.find()) {
            if (Long.parseLong(counts.group(1)) > longest) {
                return true;
            }
        }
        return false;
    }

    /**
     * How far a repetition with no stated end is allowed to run.
     *
     * <p>A few by default, because repetitions multiply and a long value is worth nothing a short
     * one is not. The shape's own lower length wins over that, though: a specification insisting on
     * two hundred characters is insisting, and a limit below that would make every candidate too
     * short. Its upper length caps both, so a rule never sets out to build something the shape would
     * refuse.
     */
    private static int repetitionLimit(long shortest, long longest) {
        long wanted = Math.min(Math.max(shortest, USUAL_REPETITIONS), Math.min(longest, LONGEST));
        return (int) Math.max(1, wanted);
    }

    @Override
    public String toString() {
        return "strings matching " + expression;
    }
}

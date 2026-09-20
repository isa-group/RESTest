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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
 * what this class adds is everything that library cannot know about, which is the rest of what the
 * specification said. A shape usually states a length as well as a spelling, and the two have to
 * hold at once: forty hexadecimal digits means forty, not the three the spelling alone would allow.
 *
 * <p>Two answers other than a string are possible, and they mean different things. A rule this
 * cannot read at all - an exotic notation, or one the specification's author mistyped - means the
 * caller should carry on as though none had been stated, because refusing to test a parameter over a
 * rule nobody here understands helps nobody. A rule that <em>was</em> read, but that nothing
 * satisfying it also fits the length the specification demands, means there is genuinely no value to
 * send, and saying so is better than sending one that was never going to be accepted.
 *
 * <p>Nothing is ever offered without being checked first. Every candidate is held against the rule
 * again, by the plain regular-expression machinery the rest of the tool uses, so a disagreement
 * between the library's reading of a rule and this platform's costs a value rather than producing a
 * wrong one.
 */
final class MatchingStrings {

    /**
     * How far a repetition with no stated end - "one or more letters" - is allowed to run when the
     * shape sets no upper length. The same limit the rest of invention keeps to, for the same
     * reason: a value nobody can read teaches nothing a short one does not.
     */
    private static final int USUAL_LONGEST = 64;

    /** Beyond this, a length the shape demands is not attempted at all. */
    private static final int LONGEST = 10_000;

    /**
     * How many candidates are drawn before giving up on the length the shape demands.
     *
     * <p>Generous on purpose, and the number comes from a measurement. Nothing in the library says
     * "of this length", so a shape demanding a string of exactly forty characters - which is how a
     * commit identifier is written down - is met by drawing until one comes out at forty. In the
     * documents measured that happened about once in fifty draws, and a draw costs a few
     * microseconds. Almost every shape is satisfied by the first candidate and never reaches the
     * second.
     */
    private static final int ATTEMPTS = 200;

    private final String expression;
    private final RgxGen shapes;
    private final Pattern checked;
    private final long shortest;
    private final long longest;

    private MatchingStrings(String expression, RgxGen shapes, Pattern checked, long shortest,
            long longest) {
        this.expression = expression;
        this.shapes = shapes;
        this.checked = checked;
        this.shortest = shortest;
        this.longest = longest;
    }

    /**
     * The strings one stated rule allows, ready to be drawn from.
     *
     * @param expression the rule, exactly as the specification wrote it
     * @param shortest the fewest characters the specification accepts
     * @param longest the most it accepts, or a very large number when it says nothing
     * @return a way of drawing strings, or nothing at all when the rule cannot be read - in which
     *     case the caller should build a value as though no rule had been stated
     */
    static Optional<MatchingStrings> reading(String expression, long shortest, long longest) {
        Objects.requireNonNull(expression, "expression");
        if (shortest > LONGEST || shortest > longest) {
            return Optional.empty();
        }
        try {
            RgxGenProperties howFarRepetitionsRun = new RgxGenProperties();
            RgxGenOption.INFINITE_PATTERN_REPETITION
                    .setInProperties(howFarRepetitionsRun, repetitionLimit(shortest, longest));
            RgxGen shapes = RgxGen.parse(howFarRepetitionsRun, expression);
            // Compiled here as well as read there, because a candidate is checked before it is
            // offered and a rule this platform will not compile is one that cannot be checked. Both
            // readings have to succeed for anything to be built from the rule at all.
            Pattern checked = Pattern.compile(expression);
            return Optional.of(new MatchingStrings(expression, shapes, checked, shortest, longest));
        } catch (RuntimeException cannotRead) {
            // The library throws its own kind for a rule it cannot read, and this platform throws
            // another for one it will not compile. Neither is a reason to stop the run, and both
            // get the same answer, so one catch covers them.
            return Optional.empty();
        }
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
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            String candidate;
            try {
                candidate = shapes.generate(random);
            } catch (RuntimeException broke) {
                return Optional.empty();
            }
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
        // Safe to run without a clock watching it. Runaway backtracking is what a regular
        // expression does to input that does not match, and every candidate here was built from
        // the rule it is being held against.
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
        } catch (PatternSyntaxException cannotRead) {
            return true;
        }
    }

    /**
     * How far a repetition with no stated end is allowed to run.
     *
     * <p>The library's own answer is a hundred, which turns "any number of digits" into a
     * hundred-digit number. The shape's own upper length is the better answer wherever it states
     * one, and its lower length wins over both: a specification insisting on two hundred characters
     * is insisting, and a limit below that would make every candidate too short.
     */
    private static int repetitionLimit(long shortest, long longest) {
        long wanted = Math.min(Math.max(shortest, USUAL_LONGEST), Math.min(longest, LONGEST));
        return (int) Math.max(1, wanted);
    }

    @Override
    public String toString() {
        return "strings matching " + expression;
    }
}

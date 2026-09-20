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
 * <p>Four things, in fact. A shape usually states a length as well as a spelling, and the two have
 * to hold at once: forty hexadecimal digits means forty, not the three the spelling alone would
 * allow. A rule may legally ask for more characters than any machine should be asked to make, so
 * how long it could possibly run is worked out from the rule before a single character is built. A
 * value should be short enough to be worth reading, though never at the price of having no value at
 * all. And the two readings of a rule - the library's and this platform's - have to agree, because a
 * rule one of them misunderstands would otherwise produce values that satisfy nobody.
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
     * one comes out at forty. One draw in about forty hits, so twenty allowances per character
     * demanded leaves the odds of missing altogether at about one in a thousand million, which is
     * the difference between a test that passes and a test that passes almost always.
     */
    private static final int LEAST_ATTEMPTS = 200;
    private static final int MOST_ATTEMPTS = 4_000;
    private static final int ATTEMPTS_PER_CHARACTER = 20;

    /**
     * How many characters may be built in total while looking for one value.
     *
     * <p>The backstop for a rule whose every candidate is far longer than anything worth sending.
     * One such candidate is survivable and two hundred are not, so the search stops when the work
     * does rather than when the attempts do.
     */
    private static final int CHARACTERS_BUILT = 1_000_000;

    /**
     * How many candidates have to be tried before the two readings are taken to disagree.
     *
     * <p>One agreement is enough, so this is only reached by a rule the two read differently. A few
     * of them, because a rule they agree about only some of the time would otherwise be thrown away
     * on one unlucky draw, and the verdict is kept for the whole run.
     */
    private static final int PROBES = 8;

    /** Held against a rule that builds nothing, to tell "accepts everything" from "demands empty". */
    private static final String A_PLAIN_WORD = "aA0";

    private final String expression;
    private final RgxGen shapes;
    private final Pattern checked;
    private final MatchLength possible;
    private final MatchLength allowed;
    private final MatchLength preferred;
    private final int attempts;

    private MatchingStrings(String expression, RgxGen shapes, Pattern checked, MatchLength possible,
            MatchLength allowed, MatchLength preferred) {
        this.expression = expression;
        this.shapes = shapes;
        this.checked = checked;
        this.possible = possible;
        this.allowed = allowed;
        this.preferred = preferred;
        this.attempts = (int) Math.min(MOST_ATTEMPTS,
                Math.max(LEAST_ATTEMPTS, ATTEMPTS_PER_CHARACTER * allowed.shortest()));
    }

    /** The preferred lengths, kept inside what the shape actually allows. */
    private static MatchLength narrowedTo(MatchLength preferred, long shortest, long ceiling) {
        return new MatchLength(Math.min(Math.max(preferred.shortest(), shortest), ceiling),
                Math.max(Math.min(preferred.longest(), ceiling), shortest));
    }

    /**
     * The strings one stated rule allows, ready to be drawn from.
     *
     * @param expression the rule, exactly as the specification wrote it
     * @param allowed the lengths the shape itself permits. A rule demanding a long value is the
     *     document demanding it, exactly as a stated minimum length would be
     * @param preferred the lengths worth having: not too long to read, and not the empty string
     *     where anything else would do. Only a preference - a value outside it is taken when
     *     nothing inside it exists, because no value at all is worse than an awkward one
     * @param random where the trial values come from while the rule is being judged
     * @return a way of drawing strings, or nothing at all when there is nothing here to read - in
     *     which case the caller should build a value as though no rule had been stated. Lengths
     *     that contradict each other answer the same way, which is safe because the only caller
     *     works them out from one shape and cannot produce a pair that does
     */
    static Optional<MatchingStrings> reading(String expression, MatchLength allowed,
            MatchLength preferred, RandomGenerator random) {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(random, "random");
        long shortest = allowed.shortest();
        long ceiling = Math.min(allowed.longest(), LONGEST);
        if (shortest > ceiling) {
            return Optional.empty();
        }
        int repetitions = repetitionLimit(shortest, ceiling);
        // Asked before a single character is built, because the answer decides whether building is
        // safe at all. A rule may legally demand nine hundred million characters, or reach the same
        // number by repeating a repetition, and neither of the two readings below would object: a
        // short rule compiles instantly and parses instantly, and the memory goes when the value is
        // made. That is an error rather than an exception, so no amount of catching helps.
        Optional<MatchLength> couldBe = MatchLength.of(expression, repetitions);
        if (couldBe.isEmpty() || couldBe.orElseThrow().longest() > ceiling) {
            return Optional.empty();
        }
        try {
            Pattern checked = Pattern.compile(expression);
            RgxGenProperties howFarRepetitionsRun = new RgxGenProperties();
            RgxGenOption.INFINITE_PATTERN_REPETITION
                    .setInProperties(howFarRepetitionsRun, repetitions);
            MatchingStrings reading = new MatchingStrings(expression,
                    RgxGen.parse(howFarRepetitionsRun, expression), checked, couldBe.orElseThrow(),
                    new MatchLength(shortest, ceiling), narrowedTo(preferred, shortest, ceiling));
            if (reading.saysNothingAboutTheValue()) {
                return Optional.empty();
            }
            return reading.bothReadingsAgree(random) ? Optional.of(reading) : Optional.empty();
        } catch (RuntimeException cannotRead) {
            // The library throws its own kind for a rule it cannot read, and this platform throws
            // another for one it will not compile. Neither is a reason to stop the run, and both
            // get the same answer, so one catch covers them.
            return Optional.empty();
        }
    }

    /**
     * Whether the rule turns out to refuse nothing, in which case there is nothing to honour.
     *
     * <p>{@code pattern: ""} is legal and is what a document generator writes for a field somebody
     * left blank; {@code ^}, {@code $} and {@code (?:)} say only where a value begins or ends, which
     * every value in existence satisfies. The builder answers all of them with the empty string and
     * nothing else, so honouring such a rule would mean sending an empty value - which cannot even
     * be put in the path of a web address, so the parameter would be lost and the operation with it.
     *
     * <p>A rule that genuinely demands an empty value - {@code ^$} - is a different thing and is
     * kept, which is what asking whether it accepts an ordinary word tells the two apart.
     */
    private boolean saysNothingAboutTheValue() {
        return possible.longest() == 0 && accepts(A_PLAIN_WORD);
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
        // Nothing this rule builds could be of an acceptable length, which is known from the rule
        // rather than discovered by drawing from it a few thousand times.
        if (possible.longest() < allowed.shortest() || possible.shortest() > allowed.longest()) {
            return Optional.empty();
        }
        // Whether a value short enough to be worth reading exists at all. Where one does, it is
        // worth drawing again for; where the rule cannot build one - it demands a hundred and
        // twenty-eight characters, say - the first acceptable value is the answer, and looking for
        // a prettier one would spend half a millisecond of the run's own thread on every value.
        boolean somethingPreferableExists = possible.shortest() <= preferred.longest()
                && possible.longest() >= preferred.shortest();

        String best = null;
        long built = 0;
        for (int attempt = 0; attempt < attempts && built < CHARACTERS_BUILT; attempt++) {
            String candidate;
            try {
                candidate = shapes.generate(random);
            } catch (RuntimeException broke) {
                break;
            }
            built += candidate.length();
            if (candidate.length() < allowed.shortest() || candidate.length() > allowed.longest()
                    || !accepts(candidate)) {
                continue;
            }
            if (candidate.length() >= preferred.shortest()
                    && candidate.length() <= preferred.longest()) {
                return Optional.of(candidate);
            }
            if (best == null || closerToWhatIsWanted(candidate, best)) {
                best = candidate;
            }
            if (!somethingPreferableExists) {
                return Optional.of(best);
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Whether a value that came from somewhere else satisfies this rule.
     *
     * @param value the value
     * @return whether the rule accepts it
     */
    boolean allows(String value) {
        return accepts(value);
    }

    /** Whether the first candidate misses the lengths worth having by less than the second. */
    private boolean closerToWhatIsWanted(String candidate, String best) {
        return howFarOut(candidate.length()) < howFarOut(best.length());
    }

    private long howFarOut(int length) {
        if (length < preferred.shortest()) {
            return preferred.shortest() - length;
        }
        return Math.max(0, length - preferred.longest());
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
        // needs a long subject, and no rule gets this far unless the count above said everything it
        // could build is shorter than the value is allowed to be; and a candidate was built from
        // this very rule, so the engine usually walks a path that exists rather than exhausting the
        // ones that do not.
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

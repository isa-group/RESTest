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

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How a length of time is written down for RESTest, wherever somebody types one.
 *
 * <p>Lengths of time are written the way people write them: {@code 500ms}, {@code 30s}, {@code 5m},
 * {@code 2h}. A bare number means seconds, because that is what somebody typing {@code 90} meant.
 * The formal spelling Java uses, {@code PT1M30S}, is accepted too, so that a script generating one
 * does not have to translate it.
 *
 * <p>One spelling, read in one place, so that how long to keep testing and how long to wait for a
 * reply are written the same way - on the command line, in a file of settings, and in whatever asks
 * next.
 */
public final class LengthOfTime {

    /** A whole number and, optionally, the unit it is counted in. */
    private static final Pattern AMOUNT_AND_UNIT =
            Pattern.compile("(\\d+)\\s*(ms|s|m|h)?", Pattern.CASE_INSENSITIVE);

    private LengthOfTime() {
    }

    /**
     * The length of time this text describes.
     *
     * @param value what was typed, for instance {@code 30s}
     * @return that length of time
     * @throws IllegalArgumentException if the text is not a length of time
     */
    public static Duration parse(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw refuse(value);
        }
        return text.regionMatches(true, 0, "P", 0, 1) ? formal(text, value)
                : amountAndUnit(text, value);
    }

    /**
     * A length of time written the way this reads it back.
     *
     * <p>The largest whole unit that divides it exactly, so ten seconds is written {@code 10s}
     * rather than {@code 10000ms} and two minutes {@code 2m}. What is printed is what a person
     * would have typed, which is the whole point of printing it: the answer is meant to be saved,
     * changed in one line and handed back.
     *
     * @param length the length of time
     * @return it, written down
     */
    public static String written(Duration length) {
        long milliseconds = length.toMillis();
        if (milliseconds == 0) {
            return "0s";
        }
        if (milliseconds % 3_600_000 == 0) {
            return milliseconds / 3_600_000 + "h";
        }
        if (milliseconds % 60_000 == 0) {
            return milliseconds / 60_000 + "m";
        }
        if (milliseconds % 1_000 == 0) {
            return milliseconds / 1_000 + "s";
        }
        return milliseconds + "ms";
    }

    /** What to say about text that is not a length of time, listing the spellings that work. */
    public static IllegalArgumentException refuse(String value) {
        return new IllegalArgumentException("'" + value + "' is not a length of time. Write it as "
                + "500ms, 30s, 5m or 2h, as a plain number of seconds such as 90, or in the formal "
                + "spelling PT1M30S");
    }

    private static Duration formal(String text, String asTyped) {
        try {
            return Duration.parse(text);
        } catch (DateTimeParseException notADuration) {
            throw refuse(asTyped);
        }
    }

    private static Duration amountAndUnit(String text, String asTyped) {
        Matcher matched = AMOUNT_AND_UNIT.matcher(text);
        if (!matched.matches()) {
            throw refuse(asTyped);
        }
        long amount = Long.parseLong(matched.group(1));
        String unit = matched.group(2) == null ? "s" : matched.group(2).toLowerCase(Locale.ROOT);
        return switch (unit) {
            case "ms" -> Duration.ofMillis(amount);
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            default -> throw refuse(asTyped);
        };
    }
}

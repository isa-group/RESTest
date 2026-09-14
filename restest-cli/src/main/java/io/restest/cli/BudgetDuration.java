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
package io.restest.cli;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine.ITypeConverter;

/**
 * Reads the length of time a person typed on the command line.
 *
 * <p>How long to spend testing an API is the one number every run needs, so it is written the way
 * people write lengths of time: {@code 30s}, {@code 5m}, {@code 2h}, {@code 500ms}. A bare number
 * means seconds, because that is what somebody typing {@code --budget 90} meant. The formal spelling
 * Java uses, {@code PT1M30S}, is accepted too, so that a script generating the argument does not
 * have to translate it.
 *
 * <p>Nothing here accepts zero or a negative length: a run that is over before it starts sends
 * nothing, and silently accepting that would produce an empty report rather than an explanation. The
 * refusal says what was given and lists what would have worked, because a person who mistyped a
 * length of time wants to know the spelling, not the grammar.
 */
final class BudgetDuration implements ITypeConverter<Duration> {

    /** A whole number and, optionally, the unit it is counted in. */
    private static final Pattern AMOUNT_AND_UNIT =
            Pattern.compile("(\\d+)\\s*(ms|s|m|h)?", Pattern.CASE_INSENSITIVE);

    @Override
    public Duration convert(String value) {
        return parse(value);
    }

    /**
     * The length of time the text describes.
     *
     * @param value what was typed, for instance {@code 30s}
     * @return that length of time
     * @throws IllegalArgumentException if the text is not a length of time, or is not a positive one
     */
    static Duration parse(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw refuse(value);
        }
        Duration parsed = text.regionMatches(true, 0, "P", 0, 1)
                ? formal(text, value)
                : amountAndUnit(text, value);
        if (parsed.isZero() || parsed.isNegative()) {
            throw new IllegalArgumentException("a budget of '" + value + "' would end the run "
                    + "before it sent anything; give a length of time greater than zero, such as "
                    + "30s");
        }
        return parsed;
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

    private static IllegalArgumentException refuse(String value) {
        return new IllegalArgumentException("'" + value + "' is not a length of time. Write it as "
                + "500ms, 30s, 5m or 2h, as a plain number of seconds such as 90, or in the formal "
                + "spelling PT1M30S");
    }
}

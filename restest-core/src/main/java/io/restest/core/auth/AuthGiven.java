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
package io.restest.core.auth;

import java.util.Objects;

/**
 * One key exactly as the person running the tool handed it over, and where they handed it over:
 * typed after {@code --auth}, or left in the environment variable {@code RESTEST_AUTH}.
 *
 * <p>The text may say which of the document's schemes the key is for, or where it goes, or be the
 * key alone; {@link CredentialPlan} reads it against the document to find out which. Because the
 * text holds the key, printing one of these never shows it: it says where the key came from, which
 * is also how every message about it names it.
 */
public final class AuthGiven {

    /** Where a key was handed over. */
    public enum Source {
        /** Typed after {@code --auth} on the command line. */
        COMMAND_LINE,
        /** Left in the environment variable {@code RESTEST_AUTH}. */
        ENVIRONMENT
    }

    /** The name of the variable a key may be left in, so that it need not be typed. */
    public static final String VARIABLE = "RESTEST_AUTH";

    /** The option a key is typed after. */
    public static final String OPTION = "--auth";

    private final String text;
    private final Source source;
    private final int position;

    private AuthGiven(String text, Source source, int position) {
        this.text = Objects.requireNonNull(text, "text");
        this.source = Objects.requireNonNull(source, "source");
        this.position = position;
    }

    /**
     * A key typed after {@code --auth}.
     *
     * @param text what was typed after the option
     * @param position which {@code --auth} it was, counting from one, so a message can name it
     * @return the key as it was handed over
     */
    public static AuthGiven typed(String text, int position) {
        if (position < 1) {
            throw new IllegalArgumentException("the first --auth is the first, counting from one");
        }
        return new AuthGiven(text, Source.COMMAND_LINE, position);
    }

    /**
     * A key left in {@code RESTEST_AUTH}.
     *
     * @param text what the variable holds
     * @return the key as it was handed over
     */
    public static AuthGiven fromTheEnvironment(String text) {
        return new AuthGiven(text, Source.ENVIRONMENT, 1);
    }

    /** Exactly what was handed over. Only this package reads it. */
    String text() {
        return text;
    }

    /** Where it was handed over. */
    public Source source() {
        return source;
    }

    /**
     * How a message names this key without repeating it: "the key given with --auth", "the second
     * --auth", "the key in RESTEST_AUTH".
     */
    public String named() {
        return switch (source) {
            case ENVIRONMENT -> "the key in " + VARIABLE;
            case COMMAND_LINE -> position == 1 ? "the key given with " + OPTION
                    : "the key given with the " + ordinal(position) + " " + OPTION;
        };
    }

    private static String ordinal(int position) {
        return switch (position) {
            case 2 -> "second";
            case 3 -> "third";
            case 4 -> "fourth";
            case 5 -> "fifth";
            default -> {
                int lastTwo = position % 100;
                int last = position % 10;
                String suffix = lastTwo >= 11 && lastTwo <= 13 ? "th"
                        : last == 1 ? "st" : last == 2 ? "nd" : last == 3 ? "rd" : "th";
                yield position + suffix;
            }
        };
    }

    /** Where the key came from, never the key. */
    @Override
    public String toString() {
        return named();
    }
}

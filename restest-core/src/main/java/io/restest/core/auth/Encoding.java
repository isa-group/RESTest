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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The ways a piece of text is written when it goes inside something else: an address, a web form,
 * a JSON document, a web page.
 *
 * <p>A key put into the query of an address is written with its awkward characters turned into
 * codes, and an API that repeats it back may write it in any of these ways. So the same key is looked
 * for in each of them before anything is written down, and it is put into a request in the first of
 * them - the one the rest of RESTest writes addresses and forms in.
 */
final class Encoding {

    private Encoding() {
    }

    /**
     * The text as an address or a form carries it: letters, digits and {@code - . _ ~} as they are,
     * every other byte as a percent sign and two capital hexadecimal digits.
     */
    static String percent(String text) {
        return percent(text, true);
    }

    /** The same, with the hexadecimal digits written small, as some servers write them back. */
    static String percentInLowerCase(String text) {
        return percent(text, false);
    }

    private static String percent(String text, boolean capitals) {
        StringBuilder written = new StringBuilder(text.length());
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            char character = (char) (b & 0xFF);
            if (isUnreserved(character)) {
                written.append(character);
            } else {
                written.append('%').append(hex(b & 0xFF, capitals));
            }
        }
        return written.toString();
    }

    /**
     * The text the way a web form is written by most servers: a space as {@code +}, letters, digits
     * and {@code . - * _} as they are, and everything else as a percent code.
     */
    static String formStyle(String text) {
        StringBuilder written = new StringBuilder(text.length());
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            char character = (char) (b & 0xFF);
            if (character == ' ') {
                written.append('+');
            } else if (isLetterOrDigit(character) || character == '.' || character == '-'
                    || character == '*' || character == '_') {
                written.append(character);
            } else {
                written.append('%').append(hex(b & 0xFF, true));
            }
        }
        return written.toString();
    }

    /**
     * The text inside a JSON string: quotation marks and backslashes escaped, control characters as
     * codes, and the forward slash escaped too when asked, which some writers do.
     */
    static String json(String text, boolean slashEscaped) {
        StringBuilder written = new StringBuilder(text.length());
        for (char character : text.toCharArray()) {
            switch (character) {
                case '"' -> written.append("\\\"");
                case '\\' -> written.append("\\\\");
                case '/' -> written.append(slashEscaped ? "\\/" : "/");
                default -> {
                    if (character < 0x20) {
                        written.append("\\u").append(fourHex(character, true));
                    } else {
                        written.append(character);
                    }
                }
            }
        }
        return written.toString();
    }

    /**
     * The text inside a JSON string by a writer that escapes everything but letters and digits as a
     * code, which some do for every punctuation mark.
     */
    static String jsonEscapingAllPunctuation(String text, boolean capitals) {
        StringBuilder written = new StringBuilder(text.length());
        for (char character : text.toCharArray()) {
            if (isLetterOrDigit(character)) {
                written.append(character);
            } else {
                written.append("\\u").append(fourHex(character, capitals));
            }
        }
        return written.toString();
    }

    /** The text inside a web page, its five awkward characters written as entities. */
    static String html(String text, boolean numericApostrophe) {
        StringBuilder written = new StringBuilder(text.length());
        for (char character : text.toCharArray()) {
            switch (character) {
                case '&' -> written.append("&amp;");
                case '<' -> written.append("&lt;");
                case '>' -> written.append("&gt;");
                case '"' -> written.append("&quot;");
                case '\'' -> written.append(numericApostrophe ? "&#x27;" : "&#39;");
                default -> written.append(character);
            }
        }
        return written.toString();
    }

    /**
     * A name as written in an address or a form, read back: percent codes decoded, and a {@code +}
     * as a space. Lenient on purpose - it is used to tell whether two names are the same name, and a
     * stray percent sign is then simply itself.
     */
    static String decoded(String written) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(written.length());
        for (int i = 0; i < written.length(); i++) {
            char character = written.charAt(i);
            if (character == '+') {
                bytes.write(' ');
            } else if (character == '%' && i + 2 < written.length()
                    && isHex(written.charAt(i + 1)) && isHex(written.charAt(i + 2))) {
                bytes.write(Integer.parseInt(written.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                byte[] encoded = String.valueOf(character).getBytes(StandardCharsets.UTF_8);
                bytes.write(encoded, 0, encoded.length);
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static boolean isUnreserved(char character) {
        return isLetterOrDigit(character) || character == '-' || character == '.'
                || character == '_' || character == '~';
    }

    private static boolean isLetterOrDigit(char character) {
        return (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9');
    }

    private static boolean isHex(char character) {
        return (character >= '0' && character <= '9') || (character >= 'a' && character <= 'f')
                || (character >= 'A' && character <= 'F');
    }

    private static String hex(int value, boolean capitals) {
        String written = String.format("%02X", value);
        return capitals ? written : written.toLowerCase(Locale.ROOT);
    }

    private static String fourHex(char character, boolean capitals) {
        String written = String.format("%04X", (int) character);
        return capitals ? written : written.toLowerCase(Locale.ROOT);
    }
}

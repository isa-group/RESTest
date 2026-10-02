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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The way a date or a time is written, read from what a description shows, and a value written the
 * same way.
 *
 * <p>Plenty of documents say how a date is written only in words: "in the format YYYY-MM-DD",
 * "yyyy-MM-dd'T'HH:mm:ss with the user's local time", or a sample such as "1996-08-01T00:00:00". An
 * API that reads dates that way refuses one written any other way, so a date in the right form is
 * worth as much here as a date at all. This reads either kind of statement and writes a fresh date
 * in exactly that form: the same parts in the same order, the same separators, seconds only if they
 * were shown, a time zone only if there was one.
 *
 * <p>How a template is read: {@code yyyy} or {@code YYYY} is the year and {@code yy} its last two
 * digits; before any hour, two {@code M}s are the month and two {@code d}s the day; from the hour
 * ({@code HH} or {@code hh}) on, the next pair of letters is the minutes and the one after the
 * seconds, whatever their case, which is how {@code HH:MM:SS} is meant. A dot and {@code S}s are a
 * fraction of a second, {@code Z} is UTC, and {@code XXX} or {@code +hh:mm} an offset from it.
 * Anything else in it - a dash, a slash, a {@code T}, a quoted {@code 'T'} or {@code 'Z'} - is
 * written as it is.
 *
 * <p>{@link ImpliedFormats} finds these statements in a description, and {@link RandomValueProvider}
 * writes the value when it invents one for that place.
 */
final class DateTimeTemplate {

    private enum Part { YEAR, SHORT_YEAR, MONTH, DAY, HOUR, MINUTE, SECOND, FRACTION, UTC, OFFSET,
        OFFSET_WITHOUT_COLON, LITERAL }

    private record Piece(Part part, String text, int digits) {
    }

    /** A sample written the ISO way, which is the only way a sample is recognised. */
    private static final Pattern SAMPLE = Pattern.compile("(\\d{4})-(\\d{2})(?:-(\\d{2}))?"
            + "(?:T(\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.(\\d{1,9}))?)?)?(Z|[+-]\\d{2}:?\\d{2})?");

    private final List<Piece> pieces;
    private final String written;

    private DateTimeTemplate(List<Piece> pieces, String written) {
        this.pieces = List.copyOf(pieces);
        this.written = written;
    }

    /**
     * The form a template written in letters describes, such as {@code YYYY-MM-DDThh:mm:ssZ}.
     *
     * @param template the template, exactly as the description writes it
     * @return the form, or nothing when this is not a template for a date or a time
     */
    static Optional<DateTimeTemplate> fromLetters(String template) {
        Objects.requireNonNull(template, "template");
        List<Piece> pieces = new ArrayList<>();
        boolean sawYear = false;
        boolean sawHour = false;
        boolean sawMinute = false;
        int timeFields = 0;
        int at = 0;
        while (at < template.length()) {
            String rest = template.substring(at);
            char letter = rest.charAt(0);
            if (rest.regionMatches(true, 0, "yyyy", 0, 4)) {
                pieces.add(new Piece(Part.YEAR, "", 0));
                sawYear = true;
                at += 4;
            } else if (rest.regionMatches(true, 0, "yy", 0, 2)) {
                pieces.add(new Piece(Part.SHORT_YEAR, "", 0));
                sawYear = true;
                at += 2;
            } else if (rest.startsWith("'") && rest.indexOf('\'', 1) > 1) {
                // A quoted letter or two, such as 'T' or 'Z', is written as it is.
                int end = rest.indexOf('\'', 1);
                pieces.add(new Piece(Part.LITERAL, rest.substring(1, end), 0));
                at += end + 1;
            } else if (sawHour && rest.startsWith("TZD")) {
                // How the W3C writes "a time zone": Z, or an offset from it.
                pieces.add(new Piece(Part.UTC, "", 0));
                at += 3;
            } else if (twice(rest, 'h')) {
                pieces.add(new Piece(Part.HOUR, "", 0));
                sawHour = true;
                timeFields = 1;
                at += 2;
            } else if (sawHour && (twice(rest, 'm') || twice(rest, 's'))) {
                boolean minutes = timeFields == 1 && twice(rest, 'm');
                pieces.add(new Piece(minutes ? Part.MINUTE : Part.SECOND, "", 0));
                sawMinute |= minutes;
                timeFields++;
                at += 2;
            } else if (twice(rest, 'm')) {
                pieces.add(new Piece(Part.MONTH, "", 0));
                at += 2;
            } else if (twice(rest, 'd')) {
                pieces.add(new Piece(Part.DAY, "", 0));
                at += 2;
            } else if (letter == '.' && rest.length() > 1
                    && Character.toLowerCase(rest.charAt(1)) == 's') {
                int digits = 0;
                while (digits + 1 < rest.length()
                        && Character.toLowerCase(rest.charAt(digits + 1)) == 's') {
                    digits++;
                }
                pieces.add(new Piece(Part.FRACTION, "", Math.min(digits, 9)));
                at += 1 + digits;
            } else if (letter == 'Z' && sawHour) {
                pieces.add(new Piece(Part.UTC, "", 0));
                at += 1;
            } else if (sawHour && rest.regionMatches(true, 0, "xxx", 0, 3)) {
                pieces.add(new Piece(Part.OFFSET, "", 0));
                at += 3;
            } else if (sawHour && (letter == 'X' || letter == 'x')) {
                // One X is UTC written as Z, the way Java's own patterns write it.
                pieces.add(new Piece(Part.UTC, "", 0));
                at += 1;
            } else if (sawHour && letter == 'z') {
                // A time zone's name, however many letters ask for it.
                pieces.add(new Piece(Part.LITERAL, "UTC", 0));
                while (at < template.length() && template.charAt(at) == 'z') {
                    at++;
                }
            } else if (sawHour && (letter == '+' || letter == '-' || letter == '±')
                    && rest.length() >= 5 && twice(rest.substring(1), 'h')) {
                boolean colon = rest.length() >= 6 && rest.charAt(3) == ':';
                pieces.add(new Piece(colon ? Part.OFFSET : Part.OFFSET_WITHOUT_COLON, "", 0));
                at += colon ? 6 : 5;
            } else if ("-/.: T".indexOf(letter) >= 0) {
                pieces.add(new Piece(Part.LITERAL, String.valueOf(letter), 0));
                at += 1;
            } else {
                return Optional.empty();
            }
        }
        // A year, or a time down to its minutes: an hour alone is not a form anybody writes.
        return sawYear || (sawHour && sawMinute)
                ? Optional.of(new DateTimeTemplate(pieces, template)) : Optional.empty();
    }

    /**
     * The form a sample date shows, such as {@code 1996-08-01T00:00:00} or {@code 2011-01}.
     *
     * @param sample the sample, exactly as the description writes it
     * @return the form, or nothing when this is not a date that could exist, written the ISO way
     */
    static Optional<DateTimeTemplate> fromSample(String sample) {
        Matcher written = SAMPLE.matcher(Objects.requireNonNull(sample, "sample"));
        if (!written.matches() || !between(written.group(2), 1, 12)
                || (written.group(3) != null && !between(written.group(3), 1, 31))
                || (written.group(4) != null && (!between(written.group(4), 0, 23)
                        || !between(written.group(5), 0, 59)))
                || (written.group(8) != null && written.group(4) == null)) {
            return Optional.empty();
        }
        StringBuilder template = new StringBuilder("yyyy-MM");
        if (written.group(3) != null) {
            template.append("-dd");
        }
        if (written.group(4) != null) {
            template.append("'T'HH:mm");
            if (written.group(6) != null) {
                template.append(":ss");
            }
            if (written.group(7) != null) {
                template.append('.').append("S".repeat(written.group(7).length()));
            }
        }
        if (written.group(8) != null) {
            template.append(written.group(8).equals("Z") ? "Z"
                    : written.group(8).contains(":") ? "+hh:mm" : "+hhmm");
        }
        return fromLetters(template.toString());
    }

    /**
     * A fresh date or time written in this form, drawn from the same years every date RESTest
     * invents is drawn from, at UTC wherever the form shows a time zone.
     *
     * @param random where it comes from
     * @return the value
     */
    String valueFor(RandomGenerator random) {
        LocalDateTime moment = FormattedStrings.localMoment(random);
        StringBuilder value = new StringBuilder();
        for (Piece piece : pieces) {
            switch (piece.part()) {
                case YEAR -> value.append(String.format(Locale.ROOT, "%04d", moment.getYear()));
                case SHORT_YEAR -> value.append(
                        String.format(Locale.ROOT, "%02d", moment.getYear() % 100));
                case MONTH -> value.append(two(moment.getMonthValue()));
                case DAY -> value.append(two(moment.getDayOfMonth()));
                case HOUR -> value.append(two(moment.getHour()));
                case MINUTE -> value.append(two(moment.getMinute()));
                case SECOND -> value.append(two(moment.getSecond()));
                case FRACTION -> {
                    value.append('.');
                    for (int digit = 0; digit < piece.digits(); digit++) {
                        value.append(random.nextInt(10));
                    }
                }
                case UTC -> value.append('Z');
                case OFFSET -> value.append("+00:00");
                case OFFSET_WITHOUT_COLON -> value.append("+0000");
                case LITERAL -> value.append(piece.text());
            }
        }
        return value.toString();
    }

    /** The template as it was read, or as a sample was turned into one. */
    String written() {
        return written;
    }

    @Override
    public String toString() {
        return written;
    }

    private static boolean twice(String text, char letter) {
        return text.length() >= 2 && Character.toLowerCase(text.charAt(0)) == letter
                && Character.toLowerCase(text.charAt(1)) == letter;
    }

    private static boolean between(String number, int lowest, int highest) {
        int value = Integer.parseInt(number);
        return value >= lowest && value <= highest;
    }

    private static String two(int number) {
        return String.format(Locale.ROOT, "%02d", number);
    }
}

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

import io.restest.core.execution.Interaction;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads how long an API asks to be left alone, from the {@code Retry-After} header of its reply.
 *
 * <p>An API that is being asked too much can answer 429, "too many requests", or 503, "not now",
 * and say in that header when to ask again: either as a number of seconds, {@code Retry-After: 30},
 * or as a moment, {@code Retry-After: Thu, 29 Oct 2026 10:00:00 GMT}. {@link ToldToWait} uses what
 * this reads to decide how long an operation, or the whole API, is left alone.
 *
 * <p>A moment is written in one of the three ways the rules of HTTP say a reader must understand,
 * one current and two old. It is measured from the moment the reply itself says it was sent, in its
 * {@code Date} header, when it says so, so that a clock running fast or slow on either side does not
 * lengthen or shorten the wait; otherwise from the moment it is read. A moment already past, or
 * {@code 0}, is a wait of no time at all.
 *
 * <p>What the API says is not trusted to be sensible. A wait longer than the run has left is the
 * whole of what it has left, and anything that cannot be read - a word, a negative number, a moment
 * that does not exist - is as if the header were not there, so that the caller guesses instead.
 * Nothing an API writes in the header can make this fail.
 */
final class RetryAfter {

    /** The current way of writing a moment: {@code Sun, 06 Nov 1994 08:49:37 GMT}. */
    private static final DateTimeFormatter IMF_FIXDATE = DateTimeFormatter.RFC_1123_DATE_TIME;

    /**
     * The name of the day a moment begins with in the current way of writing it, which is left out
     * before it is read: it says nothing the date does not, and the two old ways are read without
     * it too, so a wrong one changes nothing in any of the three.
     */
    private static final Pattern DAY_NAME = Pattern.compile("^[A-Za-z]{3}, ");

    /** The first of the old ways: {@code Sunday, 06-Nov-94 08:49:37 GMT}, with a two-digit year. */
    private static final Pattern RFC_850 = Pattern.compile(
            "[A-Za-z]+, (\\d{2})-([A-Za-z]{3})-(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2}) GMT");

    /**
     * The second of the old ways, that of C's {@code asctime}: {@code Sun Nov  6 08:49:37 1994}, a
     * day of one digit padded with a space.
     */
    private static final Pattern ASCTIME = Pattern.compile(
            "[A-Za-z]{3} ([A-Za-z]{3}) ([ \\d]\\d) (\\d{2}):(\\d{2}):(\\d{2}) (\\d{4})");

    private static final List<String> MONTHS = List.of("jan", "feb", "mar", "apr", "may", "jun",
            "jul", "aug", "sep", "oct", "nov", "dec");

    private RetryAfter() {
    }

    /**
     * Until when the reply asks to be left alone, if its {@code Retry-After} can be read. Of several
     * such headers, the first that can be read counts.
     *
     * @param reply what came back
     * @param now the moment it is read
     * @param latest the latest moment that counts; a wait that would end after it ends there
     * @return the moment the wait ends, between {@code now} and {@code latest} unless {@code latest}
     *     is already past, or nothing when the reply says nothing that can be read
     */
    static Optional<Instant> until(Interaction reply, Instant now, Instant latest) {
        for (String header : reply.responseHeaderValues("Retry-After")) {
            String value = header.strip();
            Optional<Instant> until = readSeconds(value, now, latest);
            if (until.isEmpty()) {
                Optional<Instant> moment = moment(value, now);
                if (moment.isPresent()) {
                    Instant sent = reply.responseHeaderValues("Date").stream()
                            .map(date -> moment(date.strip(), now))
                            .flatMap(Optional::stream)
                            .findFirst()
                            .orElse(now);
                    until = Optional.of(after(now, Duration.between(sent, moment.get()), latest));
                }
            }
            if (until.isPresent()) {
                return until;
            }
        }
        return Optional.empty();
    }

    /** A number of seconds, digits only, however many of them; nothing for anything else. */
    private static Optional<Instant> readSeconds(String value, Instant now, Instant latest) {
        if (value.isEmpty() || !value.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return Optional.empty();
        }
        long seconds;
        try {
            seconds = Long.parseLong(value);
        } catch (NumberFormatException tooLong) {
            // More digits than a long holds is still a number of seconds, and longer than any run.
            seconds = Long.MAX_VALUE;
        }
        return Optional.of(after(now, Duration.ofSeconds(seconds), latest));
    }

    /**
     * {@code now} plus {@code wait}, never before {@code now} and never after {@code latest}, worked
     * out without ever going beyond what the clock can count.
     */
    static Instant after(Instant now, Duration wait, Instant latest) {
        if (wait.isNegative() || !latest.isAfter(now)) {
            return now;
        }
        return wait.compareTo(Duration.between(now, latest)) >= 0 ? latest : now.plus(wait);
    }

    /** A moment written in any of the three ways, or nothing. */
    private static Optional<Instant> moment(String value, Instant now) {
        try {
            return Optional.of(ZonedDateTime.parse(DAY_NAME.matcher(value).replaceFirst(""),
                    IMF_FIXDATE).toInstant());
        } catch (DateTimeParseException notThisWay) {
            // One of the old ways, then.
        }
        Matcher rfc850 = RFC_850.matcher(value);
        if (rfc850.matches()) {
            return at(twoDigitYear(Integer.parseInt(rfc850.group(3)), now), rfc850.group(2),
                    rfc850.group(1), rfc850.group(4), rfc850.group(5), rfc850.group(6));
        }
        Matcher asctime = ASCTIME.matcher(value);
        if (asctime.matches()) {
            return at(Integer.parseInt(asctime.group(6)), asctime.group(1),
                    asctime.group(2).strip(), asctime.group(3), asctime.group(4),
                    asctime.group(5));
        }
        return Optional.empty();
    }

    /**
     * The year a two-digit year stands for, as the rules of HTTP say: the one in this century,
     * unless that is more than fifty years ahead, when it is the one a century before.
     */
    private static int twoDigitYear(int digits, Instant now) {
        int thisYear = now.atZone(ZoneOffset.UTC).getYear();
        int year = thisYear - Math.floorMod(thisYear, 100) + digits;
        return year > thisYear + 50 ? year - 100 : year;
    }

    private static Optional<Instant> at(int year, String month, String day, String hour,
            String minute, String second) {
        int monthNumber = MONTHS.indexOf(month.toLowerCase(Locale.ROOT)) + 1;
        if (monthNumber == 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.of(year, monthNumber, Integer.parseInt(day),
                    Integer.parseInt(hour), Integer.parseInt(minute), Integer.parseInt(second))
                    .toInstant(ZoneOffset.UTC));
        } catch (DateTimeException noSuchMoment) {
            return Optional.empty();
        }
    }
}

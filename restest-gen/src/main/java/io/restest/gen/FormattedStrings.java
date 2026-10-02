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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Values for the kinds of text a specification can name outright.
 *
 * <p>Alongside saying that a value is a piece of text, a specification may say <em>which kind</em>:
 * a date, a moment in time, an e-mail address, a web address, an identifier. That one word is the
 * cheapest information in the whole document and the most valuable, because the alternative - a word
 * of random letters where a date was wanted - is refused by every API that looks at what it is
 * given, and refused before anything worth testing has happened.
 *
 * <p>So the kinds are built here rather than looked up in a list. Building them means every value is
 * different and every value is correct: a date really is a date rather than four digits that happen
 * to look like a year, and an identifier that has to be unique is unique. A list of a dozen
 * prepared values could promise neither.
 *
 * <p>Every address, host name and e-mail address it produces is one the internet's own standards set
 * aside for use in documentation and examples, so nothing here can reach a real machine or a real
 * person by accident, and nothing in it belongs to anybody.
 *
 * <p>A kind it has never heard of is not a problem and not an error: it says nothing, and whoever
 * asked invents an ordinary word instead. That covers the names a document invents for itself, and
 * the several kinds - a password, a regular expression, a template - where an ordinary word is
 * already a perfectly good answer.
 */
final class FormattedStrings {

    /** Reserved for documentation by RFC 2606, so no real site or mailbox can be reached. */
    private static final String EXAMPLE_DOMAIN = "example.com";

    /** Reserved for documentation by RFC 5737 and RFC 3849. */
    private static final String DOCUMENTATION_IPV4 = "192.0.2.";
    private static final String DOCUMENTATION_IPV6 = "2001:db8::";

    /** 1990-01-01 and 2040-01-01 as days since 1970, which is where dates and times are drawn from. */
    private static final long EARLIEST_DAY = 7305L;
    private static final long LATEST_DAY = 25567L;

    private static final long SECONDS_IN_A_DAY = 86_400L;

    /** How many bytes are encoded for a value a document describes as encoded bytes. */
    private static final int ENCODED_BYTES = 12;

    private static final int SHORTEST_WORD = 5;
    private static final int LONGEST_WORD = 10;

    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";

    /**
     * Telephone numbers set aside for fiction: the United Kingdom keeps 07700 900000 to 900999
     * for drama, and North America keeps 555-0100 to 555-0199 in every area. Written the
     * international way, a plus sign and the country's code first, which is the form most APIs
     * check for. Each range is a prefix and the hundred or thousand numbers after it.
     */
    private static final String UK_FICTION = "+447700900";
    private static final String NORTH_AMERICAN_FICTION = "+120255501";

    /**
     * Card numbers the card companies publish for testing: each passes the check digit every card
     * number carries, and none can be charged.
     */
    private static final List<String> TEST_CARD_NUMBERS = List.of("4111111111111111",
            "4242424242424242", "5555555555554444", "378282246310005", "6011111111111117");

    /** Currency codes, as ISO 4217 writes them. */
    private static final List<String> CURRENCIES = List.of("USD", "EUR", "GBP", "JPY", "CHF",
            "CAD", "AUD", "CNY", "SEK", "NOK", "MXN", "BRL", "INR");

    /** Country codes, as ISO 3166-1 writes them in two letters. */
    private static final List<String> COUNTRIES = List.of("US", "GB", "DE", "FR", "ES", "IT", "NL",
            "PT", "SE", "JP", "CN", "BR", "IN", "CA", "AU", "MX");

    /** Language tags, as BCP 47 writes them: a language, or a language and where it is spoken. */
    private static final List<String> LANGUAGES = List.of("en", "es", "fr", "de", "it", "pt",
            "en-US", "en-GB", "es-ES", "pt-BR", "de-DE", "fr-FR");

    private FormattedStrings() {
    }

    /** A telephone number nobody answers, written the international way. */
    static String phone(RandomGenerator random) {
        return random.nextBoolean()
                ? UK_FICTION + String.format(Locale.ROOT, "%03d", random.nextInt(1000))
                : NORTH_AMERICAN_FICTION + String.format(Locale.ROOT, "%02d", random.nextInt(100));
    }

    /** A card number published for testing, which no payment can be taken from. */
    static String testCardNumber(RandomGenerator random) {
        return oneOf(TEST_CARD_NUMBERS, random);
    }

    /** A currency's three-letter code. */
    static String currency(RandomGenerator random) {
        return oneOf(CURRENCIES, random);
    }

    /** A country's two-letter code. */
    static String country(RandomGenerator random) {
        return oneOf(COUNTRIES, random);
    }

    /** A language tag. */
    static String language(RandomGenerator random) {
        return oneOf(LANGUAGES, random);
    }

    /** A moment written as the web's own headers write one: {@code Sun, 06 Nov 1994 08:49:37 GMT}. */
    static String httpDate(RandomGenerator random) {
        return moment(random).format(DateTimeFormatter.RFC_1123_DATE_TIME);
    }

    /** A moment written as the seconds since the start of 1970. */
    static String epochSeconds(RandomGenerator random) {
        return Long.toString(moment(random).toEpochSecond());
    }

    /** A moment written as the milliseconds since the start of 1970. */
    static String epochMillis(RandomGenerator random) {
        return Long.toString(moment(random).toInstant().toEpochMilli() + random.nextInt(1000));
    }

    /** A moment drawn from the same years every other date here is drawn from. */
    static java.time.LocalDateTime localMoment(RandomGenerator random) {
        return moment(random).toLocalDateTime();
    }

    private static String oneOf(List<String> values, RandomGenerator random) {
        return values.get(random.nextInt(values.size()));
    }

    /**
     * A value of the kind this name stands for.
     *
     * @param format the kind, exactly as the specification named it
     * @param random where the value comes from. Sharing one seeded source across the whole run is
     *     what makes a run repeatable
     * @return the value, or nothing at all when this kind is not one we know how to build - in which
     *     case an ordinary word is what the caller should fall back to
     */
    static Optional<String> of(String format, RandomGenerator random) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(random, "random");
        return Optional.ofNullable(switch (format) {
            case "date-time" -> moment(random).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            case "date" -> LocalDate.ofEpochDay(day(random)).toString();
            case "time" -> OffsetTime.of(random.nextInt(24), random.nextInt(60), random.nextInt(60),
                    0, ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_TIME);
            case "duration" -> Duration.ofSeconds(1 + random.nextInt((int) SECONDS_IN_A_DAY))
                    .toString();
            // A plain address is also a valid internationalised one, so both names get the same
            // answer rather than one of them getting nothing.
            case "email", "idn-email" -> word(random) + "@" + EXAMPLE_DOMAIN;
            case "hostname", "idn-hostname" -> word(random) + "." + EXAMPLE_DOMAIN;
            case "ipv4" -> DOCUMENTATION_IPV4 + (1 + random.nextInt(254));
            case "ipv6" -> DOCUMENTATION_IPV6 + Integer.toHexString(1 + random.nextInt(0xFFFF));
            // 'url' is not one of the names the specification format defines, and documents use it
            // anyway - forty times in the documents measured, against five hundred for 'uri'.
            case "uri", "url", "iri" -> "https://" + EXAMPLE_DOMAIN + "/" + word(random);
            case "uri-reference", "iri-reference" -> "/" + word(random);
            case "uuid" -> identifier(random).toString();
            case "byte" -> Base64.getEncoder().encodeToString(bytes(random));
            case "json-pointer" -> "/" + word(random);
            case "relative-json-pointer" -> random.nextInt(4) + "/" + word(random);
            default -> null;
        });
    }

    private static java.time.OffsetDateTime moment(RandomGenerator random) {
        return Instant.ofEpochSecond(day(random) * SECONDS_IN_A_DAY
                + random.nextInt((int) SECONDS_IN_A_DAY)).atOffset(ZoneOffset.UTC);
    }

    private static long day(RandomGenerator random) {
        return EARLIEST_DAY + random.nextLong(LATEST_DAY - EARLIEST_DAY);
    }

    /**
     * An identifier of the kind nearly every API means by one: random, and marked as such.
     *
     * <p>The two marker groups are what the standard calls the version and the variant, and setting
     * them is what separates a proper identifier from thirty-two hexadecimal digits. Some APIs check
     * them. Drawn from the run's own numbers rather than from the system's, because an identifier
     * nobody can reproduce would make a run impossible to repeat.
     */
    private static UUID identifier(RandomGenerator random) {
        long high = (random.nextLong() & ~0xF000L) | 0x4000L;
        long low = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) | Long.MIN_VALUE;
        return new UUID(high, low);
    }

    private static byte[] bytes(RandomGenerator random) {
        byte[] some = new byte[ENCODED_BYTES];
        random.nextBytes(some);
        return some;
    }

    /** Lower case throughout, because a host name and the part of an address before the at-sign are. */
    private static String word(RandomGenerator random) {
        int length = SHORTEST_WORD + random.nextInt(LONGEST_WORD - SHORTEST_WORD + 1);
        StringBuilder word = new StringBuilder(length);
        for (int letter = 0; letter < length; letter++) {
            word.append(LOWERCASE.charAt(random.nextInt(LOWERCASE.length())));
        }
        return word.toString();
    }
}

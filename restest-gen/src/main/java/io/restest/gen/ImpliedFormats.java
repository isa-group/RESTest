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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The kind of value a piece of text is meant to be, when its name or its description says so and
 * the document does not.
 *
 * <p>A document that writes {@code format: email} gets an e-mail address from RESTest. Many write
 * nothing of the kind and still mean one: a property called {@code billing_email}, a parameter
 * described as "A two-letter ISO 3166-1 alpha-2 code", a card number called {@code ccNumber}. An
 * API checks those values, and an invented word of random letters is refused before anything worth
 * testing has happened. This works out which kind a piece of text is meant to be from those two
 * clues, so that {@link RandomValueProvider}, inventing a value for that place, can sometimes send
 * one; a date or time written out is read by {@link DateTimeTemplate}.
 *
 * <p>The clues are read in a fixed order, the description first and the name after it, and the
 * first rule that recognises one decides. The description goes first because it is the more
 * precise of the two: a date template it writes out, a standard it names. The rules are a table
 * rather than code spread through the tool, checked against fifty real documents by the places in
 * them that do declare what they want, so that changing how a name is recognised is changing one
 * entry here and seeing the effect in that count. Nothing else in RESTest knows how a name or a
 * description is matched.
 *
 * <p>It only says what kind is meant. Whether to send one, and what to do when the document's own
 * spelling rule refuses it, is invention's business.
 */
final class ImpliedFormats {

    /** The kinds a name or a description can imply. */
    enum Kind { EMAIL, URI, UUID, DATE, DATE_TIME, DURATION, PHONE, CARD, CURRENCY, COUNTRY,
        LANGUAGE, HTTP_DATE, EPOCH_SECONDS, EPOCH_MILLIS, TEMPLATE, PASSWORD, USERNAME,
        PERSON_NAME, NAME, GENDER }

    /**
     * What a name or a description implies, and the rule that recognised it.
     *
     * @param kind the kind of value
     * @param rule the rule, by the name the table gives it
     * @param template the form a date or a time is to be written in, for a {@link Kind#TEMPLATE}
     */
    record Implied(Kind kind, String rule, Optional<DateTimeTemplate> template) {

        Implied {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(rule, "rule");
            Objects.requireNonNull(template, "template");
        }

        /**
         * A fresh value of this kind.
         *
         * @param random where it comes from
         * @return the value
         */
        String valueFor(RandomGenerator random) {
            return switch (kind) {
                case EMAIL -> FormattedStrings.of("email", random).orElseThrow();
                case URI -> FormattedStrings.of("uri", random).orElseThrow();
                case UUID -> FormattedStrings.of("uuid", random).orElseThrow();
                case DATE -> FormattedStrings.of("date", random).orElseThrow();
                case DATE_TIME -> FormattedStrings.of("date-time", random).orElseThrow();
                case DURATION -> FormattedStrings.of("duration", random).orElseThrow();
                case PHONE -> FormattedStrings.phone(random);
                case CARD -> FormattedStrings.testCardNumber(random);
                case CURRENCY -> FormattedStrings.currency(random);
                case COUNTRY -> FormattedStrings.country(random);
                case LANGUAGE -> FormattedStrings.language(random);
                case HTTP_DATE -> FormattedStrings.httpDate(random);
                case EPOCH_SECONDS -> FormattedStrings.epochSeconds(random);
                case EPOCH_MILLIS -> FormattedStrings.epochMillis(random);
                case TEMPLATE -> template.orElseThrow().valueFor(random);
                case PASSWORD -> FormattedStrings.password(random);
                case USERNAME -> FormattedStrings.username(random);
                case PERSON_NAME -> FormattedStrings.personName(random);
                case NAME -> FormattedStrings.nameOfLetters(random);
                case GENDER -> FormattedStrings.gender(random);
            };
        }
    }

    /** One place's name, split into words, and its description. */
    private record Place(String name, List<String> words, String description) {
    }

    /** One entry of the table. */
    private interface Rule {
        Optional<Implied> match(Place place);
    }

    /** The articles a description may begin with before saying what a value is. */
    private static final String AT_THE_START =
            "^\\s*(?:(?:a valid|an optional|the|an|a|optional)\\s+)?";

    /**
     * A run of the characters a date or time template is written with, starting where a word
     * starts: {@code YYYY-MM-DD}, {@code yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}, {@code hh:mm}. The whole run
     * has to read as a template, or nothing is taken from it - a template cut short where it stops
     * being readable would be a different form from the one the description shows.
     */
    private static final Pattern A_TEMPLATE = Pattern.compile("(?<![A-Za-z'])[yYmMdDhH]"
            + "(?:[yYmMdDhHsSTZXxz'±+:\\-/.]| (?=[hH]{2}))*+(?![A-Za-z0-9'])");

    /**
     * A date or time written out the ISO way, shown as what the value looks like: "in ISO 8601
     * format (1996-08-01T00:00:00)", "e.g. 1963-11-22T18:30:00Z", "for example 1981-12-15". A
     * date that is only mentioned - a range inside a filter's example - is not a sample.
     */
    private static final Pattern A_SAMPLE = Pattern.compile("(?i:\\bformat\\b|\\be\\.?g\\.|"
            + "\\bexample\\b|\\bfor instance\\b|\\bsuch as\\b|\\blike\\b)[\\s:(\"'`]{0,3}"
            + "(?<![\\d-])(\\d{4}-\\d{2}(?:-\\d{2})?(?:T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d{1,9})?)?"
            + "(?:Z|[+-]\\d{2}:?\\d{2})?)?)(?![\\d-])");

    /** The ways a description says a moment is counted from the start of 1970. */
    private static final String SINCE_1970 = "\\bunix\\b|\\bepoch\\b|\\bsince (?:the start of )?1970\\b";

    /**
     * How each of those kinds is written short in a name. Closed, because a word that merely
     * begins the same way - {@code count} for a country, {@code cursor} for a currency - is a
     * different word.
     */
    private static final Map<String, Set<String>> SHORT_FOR = Map.of(
            "country", Set.of("ctry", "cntry"),
            "currency", Set.of("cur", "curr", "ccy"),
            "language", Set.of("lang", "lng"),
            "locale", Set.of("loc"));

    /** "K code" or "K tag", where K is what such a code is a code for. */
    private static final Pattern A_CODE_FOR = Pattern.compile(
            "\\b(country|currency|language|locale)[ -](codes?|tags?)\\b", Pattern.CASE_INSENSITIVE);

    /** The table, in the order it is read. The first rule that recognises a place decides. */
    private static final List<Rule> RULES = List.of(
            // A form for a date or a time, written out, is the most precise thing a description
            // can say, which is why it comes first.
            template("T1"),
            sample("T2"),
            contains("D1", "\\bISO[ -]?639|\\bBCP[ -]?47\\b|\\bRFC[ -]?5646|\\bIETF language tag",
                    Kind.LANGUAGE),
            // ISO 3166-2 is the codes for provinces and states, not for countries.
            containsUnless("D2", "\\bISO[ -]?3166", "\\b3166-2(?![\\d-])", Kind.COUNTRY),
            contains("D3", "\\bISO[ -]?4217", Kind.CURRENCY),
            contains("T3", "\\bRFC[ -]?3339", Kind.DATE_TIME),
            containsBoth("T4", "\\bISO[ -]?8601", "\\bdurations?\\b", Kind.DURATION),
            // Not RFC 822, 2822 or 7231, which are also the standards for e-mail addresses and for
            // the headers an HTTP request carries.
            contains("T5", "\\bRFC[ -]?1123\\b|\\bHTTP-date\\b|\\bIMF-fixdate\\b", Kind.HTTP_DATE),
            containsBoth("T6m", SINCE_1970, "\\bmilli", Kind.EPOCH_MILLIS),
            contains("T6", SINCE_1970, Kind.EPOCH_SECONDS),
            contains("D6", "\\bE\\.?164\\b", Kind.PHONE),
            codeTiedToTheName("D7"),
            startsWith("D8", "(?:uuid|guid)\\b", Kind.UUID),
            startsWith("D9", "(?:e-?mail(?: address)?|email address)\\b", Kind.EMAIL),
            startsWith("D10", "(?:url|uri|link|web address|web ?site)\\b", Kind.URI),
            startsWith("D11", "(?:(?:tele)?phone|mobile)(?: number)?\\b", Kind.PHONE),
            // Then the name, by the words it ends in.
            lastWordAt("N1", Kind.DATE_TIME),
            endsIn("N2", Kind.EMAIL, "email", "mail"),
            endsIn("N3", Kind.URI, "url", "uri", "website", "homepage"),
            endsIn("N4", Kind.UUID, "uuid", "guid"),
            endsIn("N5", Kind.DATE_TIME, "timestamp"),
            endsIn("N6", Kind.DATE, "birthday", "dob"),
            endsIn("N7", Kind.PHONE, "phone", "mobile", "tel", "telephone"),
            endsIn("N8", Kind.CARD, "card", "creditcard", "cardnumber", "ccnumber"),
            endsIn("N9", Kind.CURRENCY, "currency"),
            endsIn("N10", Kind.LANGUAGE, "language", "lang", "locale", "mothertongue"),
            // What an API checks before it lets anybody in: a password strong enough for the
            // usual rule, a user name short and plain enough, a person's name made of letters.
            // An invented word of thirty random letters and digits fails all three.
            endsIn("N11", Kind.PASSWORD, "password", "passwd", "pwd", "passphrase"),
            // "login" alone is a user name; at the end of a longer name - lastLogin - it is when
            // somebody last logged in, which N1 and N5 do not catch and this must not either. Where
            // the description says it may be an e-mail address, it is not a plain user name.
            unless("\\be-?mail", endsIn("N12", Kind.USERNAME, "username", "loginname", "nickname")),
            unless("\\be-?mail", wholeName("N12", Kind.USERNAME, "login")),
            endsIn("N13", Kind.PERSON_NAME, "firstname", "givenname", "forename", "middlename",
                    "lastname", "surname", "familyname", "fullname"),
            endsIn("N14", Kind.GENDER, "gender", "sex"),
            // A bare "name" is a product's, a pet's or a notebook's as often as a person's. What
            // they share is that a name of letters is one, so that is what is sent - a fresh one
            // each time, since many APIs refuse to make two things of the same name. A longer
            // name ending in "name" - companyName, fileName, displayName - is left alone.
            wholeName("N15", Kind.NAME, "name"));

    private ImpliedFormats() {
    }

    /**
     * What a place's name and description imply it is meant to be.
     *
     * @param name the name of the parameter or property, or of the list a piece belongs to
     * @param description what the document says the value is, or nothing
     * @return the kind and the rule that recognised it, or nothing when no rule does
     */
    static Optional<Implied> of(String name, String description) {
        Objects.requireNonNull(name, "name");
        Place place = new Place(name, wordsOf(name), description == null ? "" : description);
        for (Rule rule : RULES) {
            Optional<Implied> implied = rule.match(place);
            if (implied.isPresent()) {
                return implied;
            }
        }
        return Optional.empty();
    }

    /**
     * A name taken apart into the words it is written with, in lower case: at every capital that
     * follows a small letter or a digit, at the end of a run of capitals, and at anything that is
     * not a letter or a digit. {@code billing_email}, {@code ccNumber} and {@code HTTPProxyURL}
     * are {@code billing email}, {@code cc number} and {@code http proxy url}.
     */
    static List<String> wordsOf(String name) {
        String spaced = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
        return Arrays.stream(spaced.split("[^A-Za-z0-9]+"))
                .filter(word -> !word.isEmpty())
                .map(word -> word.toLowerCase(Locale.ROOT))
                .toList();
    }

    private static Rule template(String id) {
        return place -> {
            Matcher found = A_TEMPLATE.matcher(place.description());
            Optional<DateTimeTemplate> longest = Optional.empty();
            while (found.find()) {
                // A sentence's own punctuation after it is not part of it.
                String run = found.group().replaceAll("[.:,/-]+$", "");
                Optional<DateTimeTemplate> read = DateTimeTemplate.fromLetters(run);
                // The fullest form a description writes is the one it means: "YYYY-MM or YYYY"
                // means months, and "±hh:mm after yyyy-MM-ddTHH:mm±hh:mm" means the whole moment.
                if (read.isPresent() && (longest.isEmpty()
                        || read.get().written().length() > longest.get().written().length())) {
                    longest = read;
                }
            }
            return longest.map(template -> new Implied(Kind.TEMPLATE, id, Optional.of(template)));
        };
    }

    private static Rule sample(String id) {
        return place -> {
            Matcher found = A_SAMPLE.matcher(place.description());
            while (found.find()) {
                Optional<DateTimeTemplate> read = DateTimeTemplate.fromSample(found.group(1));
                if (read.isPresent()) {
                    return Optional.of(new Implied(Kind.TEMPLATE, id, read));
                }
            }
            return Optional.empty();
        };
    }

    private static Rule contains(String id, String expression, Kind kind) {
        Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
        return place -> pattern.matcher(place.description()).find()
                ? Optional.of(new Implied(kind, id, Optional.empty())) : Optional.empty();
    }

    private static Rule containsUnless(String id, String expression, String unless, Kind kind) {
        Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
        Pattern not = Pattern.compile(unless, Pattern.CASE_INSENSITIVE);
        return place -> pattern.matcher(place.description()).find()
                && !not.matcher(place.description()).find()
                ? Optional.of(new Implied(kind, id, Optional.empty())) : Optional.empty();
    }

    private static Rule containsBoth(String id, String one, String other, Kind kind) {
        Pattern first = Pattern.compile(one, Pattern.CASE_INSENSITIVE);
        Pattern second = Pattern.compile(other, Pattern.CASE_INSENSITIVE);
        return place -> first.matcher(place.description()).find()
                && second.matcher(place.description()).find()
                ? Optional.of(new Implied(kind, id, Optional.empty())) : Optional.empty();
    }

    private static Rule startsWith(String id, String expression, Kind kind) {
        Pattern pattern = Pattern.compile(AT_THE_START + expression, Pattern.CASE_INSENSITIVE);
        return place -> pattern.matcher(place.description()).find()
                ? Optional.of(new Implied(kind, id, Optional.empty())) : Optional.empty();
    }

    /**
     * "A valid country code", "A language code like en-US": a code for one of the kinds of thing
     * a code is commonly for, tied to the place it describes. One of the name's words has to be
     * that kind or the way it is written short ({@code country_code}, {@code lang}), or the name has
     * to be the initials of the phrase ({@code cc} for a country code) - otherwise the description is
     * only mentioning one, as a description of a view that lists country codes among its fields
     * does.
     */
    private static Rule codeTiedToTheName(String id) {
        return place -> {
            String joined = String.join("", place.words());
            if (joined.isEmpty()) {
                return Optional.empty();
            }
            Matcher found = A_CODE_FOR.matcher(place.description());
            while (found.find()) {
                String kind = found.group(1).toLowerCase(Locale.ROOT);
                String initials = kind.charAt(0) + found.group(2).substring(0, 1)
                        .toLowerCase(Locale.ROOT);
                boolean tied = joined.equals(initials) || place.words().stream().anyMatch(word ->
                        word.equals(kind) || SHORT_FOR.get(kind).contains(word));
                if (tied) {
                    return Optional.of(new Implied(switch (kind) {
                        case "country" -> Kind.COUNTRY;
                        case "currency" -> Kind.CURRENCY;
                        default -> Kind.LANGUAGE;
                    }, id, Optional.empty()));
                }
            }
            return Optional.empty();
        };
    }

    /** A name of two words or more whose last is {@code at}: {@code createdAt}, {@code arrives_at}. */
    private static Rule lastWordAt(String id, Kind kind) {
        return place -> place.words().size() > 1 && place.words().getLast().equals("at")
                ? Optional.of(new Implied(kind, id, Optional.empty())) : Optional.empty();
    }

    /**
     * A rule read only where the description does not say something that overrules the name: a
     * user name the description says may be an e-mail address is not a plain user name.
     */
    private static Rule unless(String expression, Rule rule) {
        Pattern not = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
        return place -> not.matcher(place.description()).find() ? Optional.empty()
                : rule.match(place);
    }

    /** A name that is one of these and nothing more: {@code login}, but not {@code lastLogin}. */
    private static Rule wholeName(String id, Kind kind, String... names) {
        Set<String> wanted = Set.of(names);
        return place -> place.words().size() == 1 && wanted.contains(place.words().getFirst())
                ? Optional.of(new Implied(kind, id, Optional.empty())) : Optional.empty();
    }

    /**
     * A name whose last one, two or three words, joined, are one of these: {@code homePage} ends
     * in {@code homepage}, {@code billing_email} in {@code email}.
     */
    private static Rule endsIn(String id, Kind kind, String... endings) {
        Set<String> wanted = Set.of(endings);
        return place -> {
            List<String> words = place.words();
            for (int last = Math.min(3, words.size()); last >= 1; last--) {
                if (wanted.contains(String.join("", words.subList(words.size() - last,
                        words.size())))) {
                    return Optional.of(new Implied(kind, id, Optional.empty()));
                }
            }
            return Optional.empty();
        };
    }
}

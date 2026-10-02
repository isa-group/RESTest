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

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.gen.ImpliedFormats.Implied;
import io.restest.gen.ImpliedFormats.Kind;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * What a name or a description is taken to imply, rule by rule, with the places of real documents
 * each rule was written for and the ones it must leave alone.
 */
class ImpliedFormatsTest {

    @Nested
    @DisplayName("how a name is taken apart")
    class Words {

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({
            "billing_email, billing email",
            "ccNumber, cc number",
            "Accept-Language, accept language",
            "homePage, home page",
            "VersionGuid, version guid",
            "HTTPProxyURL, http proxy url",
            "created_at, created at",
            "x-request.id, x request id"})
        void a_name_is_its_words(String name, String words) {
            assertThat(ImpliedFormats.wordsOf(name)).containsExactly(words.split(" "));
        }
    }

    @Nested
    @DisplayName("what a description says")
    class Descriptions {

        @ParameterizedTest(name = "{0}: \"{1}\" is {2} by {3}")
        @CsvSource(delimiter = '|', value = {
            "setLang | The language to use for user interface strings. Specify the language using the ISO 639-1 2-letter language code. | LANGUAGE | D1",
            "market | The market (an ISO 3166-1 alpha-2 country code) | COUNTRY | D2",
            "countryCode | A two-letter ISO 3166-1 alpha-2 code specifying the country. | COUNTRY | D2",
            "currency | Search by ISO 4217 currency code | CURRENCY | D3",
            "since | Only show results after this time, an RFC 3339 timestamp | DATE_TIME | T3",
            "duration | The length of the video, an ISO 8601 duration in the format PT#M#S | DURATION | T4",
            "since | The date, as an HTTP-date | HTTP_DATE | T5",
            "from | Unix timestamp of the earliest event | EPOCH_SECONDS | T6",
            "from | Milliseconds since the start of 1970 | EPOCH_MILLIS | T6m",
            "from | Unix timestamp in milliseconds | EPOCH_MILLIS | T6m",
            "countryCode | A country code, two letters | COUNTRY | D7",
            "market | An ISO 3166 2-letter country code | COUNTRY | D2",
            "phone | The phone number, in E.164 form | PHONE | D6",
            "language | A language code like en-US, de-DE, fr, or auto to guess the language | LANGUAGE | D7",
            "cc | A 2-character country code of the country where the results come from. | COUNTRY | D7",
            "lang | A language code | LANGUAGE | D7",
            "id | The UUID of the job to retrieve related jobs for | UUID | D8",
            "value | The email address. | EMAIL | D9",
            "deep_link | Link to the booking page | URI | D10",
            "contact | Phone number of the venue | PHONE | D11"})
        void a_description_implies_a_kind(String name, String description, Kind kind,
                String rule) {
            Optional<Implied> implied = ImpliedFormats.of(name, description);

            assertThat(implied).map(Implied::kind).contains(kind);
            assertThat(implied).map(Implied::rule).contains(rule);
        }

        @ParameterizedTest(name = "{0}: \"{1}\" implies nothing")
        @CsvSource(delimiter = '|', value = {
            "stateCode | ISO 3166-2 defines codes for identifying the principal subdivisions of all countries coded in ISO 3166-1",
            "job_title | Title of the job associated with the UUID",
            "type | The type of email address.",
            "statement_descriptor | An arbitrary string to be displayed on your customer's credit card statement.",
            "view | Level of detail; FULL also includes the country code of each hotel",
            "fromReleaseDate | Returns books released on or after the date, in ISO 8601 format",
            "menaMpaaRating | The rating, 33408548 being the code for none",
            "sender | An RFC 822 email address of the sender",
            "count | How many results to return for each country code",
            "coupon | A coupon valid in any country code listed",
            "filters | Filter e.g. DATE:[2015-01-01 TO 2016-01-01]"})
        void a_mention_is_not_a_statement(String name, String description) {
            assertThat(ImpliedFormats.of(name, description)).isEmpty();
        }
    }

    @Nested
    @DisplayName("a date or a time the description writes out")
    class DatesWrittenOut {

        @ParameterizedTest(name = "\"{0}\" is written {1}")
        @CsvSource(delimiter = '|', value = {
            "Timestamp in the format YYYY-MM-DDThh:mm:ssZ | YYYY-MM-DDThh:mm:ssZ",
            "A timestamp in ISO 8601 format (yyyy-MM-dd'T'HH:mm:ss) with the user's local time | yyyy-MM-dd'T'HH:mm:ss",
            "date in YYYYMMDD format that represents the version | YYYYMMDD",
            "The date period, in ISO 8601 date format YYYY-MM or YYYY | YYYY-MM",
            "This is a timestamp in ISO 8601 format: YYYY-MM-DDTHH:MM:SSZ. | YYYY-MM-DDTHH:MM:SSZ",
            "The duration, expressed in the format hh:mm | hh:mm",
            "The day it starts, as dd/MM/yyyy | dd/MM/yyyy",
            "Created at, as yyyy-MM-dd'T'HH:mm:ss.SSS'Z' | yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "The W3C way, YYYY-MM-DDThh:mm:ssTZD. | YYYY-MM-DDThh:mm:ssTZD",
            "In the form ±hh:mm after yyyy-MM-ddTHH:mm±hh:mm | yyyy-MM-ddTHH:mm±hh:mm"})
        void a_template_is_read(String description, String template) {
            Optional<Implied> implied = ImpliedFormats.of("when", description);

            assertThat(implied).map(Implied::rule).contains("T1");
            assertThat(implied.flatMap(Implied::template)).map(DateTimeTemplate::written)
                    .contains(template);
        }

        @Test
        @DisplayName("a sample written out gives the form of the sample")
        void a_sample_is_read() {
            Optional<Implied> implied = ImpliedFormats.of("fromReleaseDate", "Returns books "
                    + "released on or after the date. Release date is in ISO 8601 format "
                    + "(1996-08-01T00:00:00).");

            assertThat(implied).map(Implied::rule).contains("T2");
            assertThat(implied.flatMap(Implied::template)).map(DateTimeTemplate::written)
                    .contains("yyyy-MM-dd'T'HH:mm:ss");
        }

        @ParameterizedTest(name = "\"{0}\" is read whole or not at all")
        @CsvSource(delimiter = '|', value = {
            "Written YYYY-MM-DDThh:mmQQ",
            "Written yyyy-MM-ddTHH:mm:ssQ",
            "hh alone"})
        void a_template_cut_short_is_no_template(String description) {
            assertThat(ImpliedFormats.of("createdAt", description)).map(Implied::rule)
                    .describedAs("the name's own rule, since no template could be read whole")
                    .contains("N1");
        }

        @Test
        @DisplayName("a template is read before the name, and before anything else the "
                + "description says")
        void the_template_comes_first() {
            assertThat(ImpliedFormats.of("createdAt", "YYYY-MM-DD, the ISO 3166 way"))
                    .map(Implied::rule).contains("T1");
        }
    }

    @Nested
    @DisplayName("what a name says")
    class Names {

        @ParameterizedTest(name = "{0} is {1} by {2}")
        @CsvSource({
            "createdAt, DATE_TIME, N1",
            "arrives_at, DATE_TIME, N1",
            "email, EMAIL, N2",
            "billing_email, EMAIL, N2",
            "imageUrl, URI, N3",
            "homePage, URI, N3",
            "card_uri, URI, N3",
            "parent_uuid, UUID, N4",
            "VersionGuid, UUID, N4",
            "timestamp, DATE_TIME, N5",
            "dob, DATE, N6",
            "display_phone, PHONE, N7",
            "mobile, PHONE, N7",
            "ccNumber, CARD, N8",
            "card_number, CARD, N8",
            "creditCard, CARD, N8",
            "currency, CURRENCY, N9",
            "setLang, LANGUAGE, N10",
            "motherTongue, LANGUAGE, N10",
            "Accept-Language, LANGUAGE, N10"})
        void a_name_implies_a_kind(String name, Kind kind, String rule) {
            Optional<Implied> implied = ImpliedFormats.of(name, "");

            assertThat(implied).map(Implied::kind).contains(kind);
            assertThat(implied).map(Implied::rule).contains(rule);
        }

        @ParameterizedTest(name = "{0} implies nothing")
        @CsvSource({"at", "format", "seat", "emailVerified", "urlString", "cardHolder",
            "phoneNumber", "cc", "country", "date", "startDate", "altLanguages", "name", "id"})
        void a_name_that_is_not_one(String name) {
            assertThat(ImpliedFormats.of(name, "")).isEmpty();
        }
    }

    @Test
    @DisplayName("the description is read before the name")
    void the_description_comes_first() {
        assertThat(ImpliedFormats.of("locale", "The desired language, consisting of an ISO 639 "
                + "language code and an ISO 3166-1 alpha-2 country code"))
                .map(Implied::rule).contains("D1");
        assertThat(ImpliedFormats.of("cc", "A 2-character country code"))
                .map(Implied::kind).contains(Kind.COUNTRY);
    }

    @Test
    @DisplayName("every kind it can imply gives a value")
    void every_kind_gives_a_value() {
        java.util.random.RandomGenerator random = Schemas.fixedRandom();
        for (Kind kind : Kind.values()) {
            Optional<DateTimeTemplate> template = kind == Kind.TEMPLATE
                    ? DateTimeTemplate.fromLetters("yyyy-MM-dd") : Optional.empty();
            assertThat(new Implied(kind, "test", template).valueFor(random))
                    .describedAs(kind.name()).isNotBlank();
        }
        assertThat(List.of(Kind.values())).hasSize(15);
    }
}

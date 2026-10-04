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
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every kind of value the tool claims to know how to build, read back by the platform's own parser
 * for that kind rather than by anything the generator uses.
 *
 * <p>That independence is the point. A test that checked a date with the same code that wrote it
 * would agree with whatever came out.
 */
class FormattedStringsTest {

    @Nested
    @DisplayName("each kind is really of that kind")
    class EachKind {

        @RepeatedTest(20)
        @DisplayName("a moment in time is one the platform can read back")
        void a_date_time_is_a_date_time() {
            assertThatCode(() -> OffsetDateTime.parse(built("date-time")))
                    .doesNotThrowAnyException();
        }

        @RepeatedTest(20)
        @DisplayName("a date is one the platform can read back")
        void a_date_is_a_date() {
            assertThatCode(() -> LocalDate.parse(built("date"))).doesNotThrowAnyException();
        }

        @RepeatedTest(20)
        @DisplayName("a time carries the offset the standard asks for")
        void a_time_is_a_time() {
            assertThatCode(() -> OffsetTime.parse(built("time"))).doesNotThrowAnyException();
        }

        @RepeatedTest(20)
        @DisplayName("a length of time is one the platform can read back")
        void a_duration_is_a_duration() {
            assertThatCode(() -> Duration.parse(built("duration"))).doesNotThrowAnyException();
        }

        @RepeatedTest(20)
        @DisplayName("an identifier is a proper one, version and all")
        void a_uuid_is_a_uuid() {
            UUID identifier = UUID.fromString(built("uuid"));

            assertThat(identifier.version())
                    .describedAs("some APIs check it, and thirty-two digits is not an identifier")
                    .isEqualTo(4);
            assertThat(identifier.variant()).isEqualTo(2);
        }

        @RepeatedTest(20)
        @DisplayName("a web address is absolute and points somewhere")
        void a_uri_is_a_uri() {
            URI address = URI.create(built("uri"));

            assertThat(address.isAbsolute()).isTrue();
            assertThat(address.getHost()).isEqualTo("example.com");
        }

        @RepeatedTest(20)
        @DisplayName("encoded bytes really do decode")
        void a_byte_string_decodes() {
            assertThatCode(() -> Base64.getDecoder().decode(built("byte")))
                    .doesNotThrowAnyException();
        }

        @RepeatedTest(20)
        @DisplayName("an e-mail address has one at-sign and a domain behind it")
        void an_email_is_an_email() {
            assertThat(built("email")).matches("^[a-z]+@example\\.com$");
        }

        @RepeatedTest(20)
        @DisplayName("an internet address is one of the ranges set aside for documentation")
        void an_address_is_a_documentation_address() {
            assertThat(built("ipv4")).matches("^192\\.0\\.2\\.([1-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-4])$");
            assertThat(built("ipv6")).matches("^2001:db8::[0-9a-f]{1,4}$");
        }

        @RepeatedTest(20)
        @DisplayName("a place inside a document starts where one has to start")
        void a_json_pointer_starts_with_a_slash() {
            assertThat(built("json-pointer")).startsWith("/");
            assertThat(built("relative-json-pointer")).matches("^[0-9]+/[a-z]+$");
        }
    }

    @Nested
    @DisplayName("nothing it makes can reach anybody real")
    class NothingReal {

        @Test
        @DisplayName("every host and address is one the standards set aside for documentation")
        void every_host_is_reserved() {
            List<String> reachingOutwards =
                    List.of("email", "idn-email", "hostname", "idn-hostname", "uri", "url", "iri");

            for (String kind : reachingOutwards) {
                for (int draw = 0; draw < 20; draw++) {
                    assertThat(built(kind))
                            .describedAs("%s produced an address that is not reserved", kind)
                            .contains("example.com");
                }
            }
        }
    }

    @Nested
    @DisplayName("what an API checks before it lets anybody in")
    class SigningUp {

        /** The rule for a password found most often in the wild, letter for letter. */
        private static final java.util.regex.Pattern STRONG = java.util.regex.Pattern.compile(
                "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$");

        @Test
        @DisplayName("a password passes the usual rule, every time, and is twelve to sixteen long")
        void a_password_is_strong() {
            java.util.random.RandomGenerator random = Schemas.fixedRandom();
            for (int draw = 0; draw < 2000; draw++) {
                String password = FormattedStrings.password(random);
                assertThat(password).matches(STRONG).hasSizeBetween(12, 16);
            }
        }

        @Test
        @DisplayName("a user name is short, starts with a letter, and is small letters and digits")
        void a_username_is_plain() {
            java.util.random.RandomGenerator random = Schemas.fixedRandom();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (int draw = 0; draw < 2000; draw++) {
                String name = FormattedStrings.username(random);
                assertThat(name).matches("[a-z][a-z0-9]{5,10}");
                seen.add(name);
            }
            assertThat(seen).describedAs("names nobody has taken yet").hasSizeGreaterThan(1990);
        }

        @Test
        @DisplayName("a person's name is letters alone, and a gender one of the usual words")
        void names_and_genders() {
            java.util.random.RandomGenerator random = Schemas.fixedRandom();
            for (int draw = 0; draw < 200; draw++) {
                assertThat(FormattedStrings.personName(random)).matches("[A-Z][a-z]+");
                assertThat(FormattedStrings.gender(random)).isIn("male", "female", "MALE",
                        "FEMALE", "Male", "Female", "M", "F", "other");
            }
        }
    }

    @Nested
    @DisplayName("a kind it has never heard of")
    class Unknown {

        @ParameterizedTest
        @ValueSource(strings = {"swagger", "csv", "ISBN-13", "id1", "int32", "int64", "double",
                "password", "regex", "binary", "", "DATE"})
        @DisplayName("says nothing, so an ordinary word is invented instead")
        void an_unknown_kind_says_nothing(String kind) {
            assertThat(FormattedStrings.of(kind, Schemas.fixedRandom())).isEmpty();
        }
    }

    @Nested
    @DisplayName("a run built on these can still be repeated")
    class Repeatable {

        @Test
        @DisplayName("the same starting number produces the same values")
        void the_same_seed_produces_the_same_values() {
            assertThat(everything(new SplittableRandom(20260920L)))
                    .isEqualTo(everything(new SplittableRandom(20260920L)));
        }

        @RepeatedTest(5)
        @DisplayName("an identifier is different every time, which is what an identifier is for")
        void identifiers_differ() {
            SplittableRandom random = new SplittableRandom(7L);

            assertThat(IntStream.range(0, 50)
                    .mapToObj(draw -> FormattedStrings.of("uuid", random).orElseThrow())
                    .toList())
                    .doesNotHaveDuplicates();
        }

        private List<String> everything(SplittableRandom random) {
            return List.of("date-time", "date", "time", "duration", "email", "hostname", "ipv4",
                            "ipv6", "uri", "uuid", "byte", "json-pointer").stream()
                    .map(kind -> FormattedStrings.of(kind, random).orElseThrow())
                    .toList();
        }
    }

    private static String built(String kind) {
        Optional<String> value = FormattedStrings.of(kind, RANDOM);
        assertThat(value).describedAs("nothing was built for %s", kind).isPresent();
        return value.orElseThrow();
    }

    @Nested
    @DisplayName("the kinds a name or a description implies, and nothing real among them")
    class ImpliedKinds {

        @RepeatedTest(20)
        @DisplayName("a card number passes the check every card number carries, and is one the "
                + "card companies publish for testing")
        void a_card_number_is_a_test_card() {
            String card = FormattedStrings.testCardNumber(RANDOM);

            assertThat(passesLuhn(card)).isTrue();
            assertThat(card).isIn("4111111111111111", "4242424242424242", "5555555555554444",
                    "378282246310005", "6011111111111117");
        }

        @RepeatedTest(20)
        @DisplayName("a telephone number is one set aside for fiction, written the international way")
        void a_phone_is_fictional() {
            assertThat(FormattedStrings.phone(RANDOM))
                    .matches("\\+447700900\\d{3}|\\+120255501\\d{2}");
        }

        @RepeatedTest(20)
        @DisplayName("currency, country and language codes are written as their standards write them")
        void codes_are_written_the_standard_way() {
            assertThat(FormattedStrings.currency(RANDOM)).matches("[A-Z]{3}");
            assertThat(FormattedStrings.country(RANDOM)).matches("[A-Z]{2}");
            assertThat(FormattedStrings.language(RANDOM)).matches("[a-z]{2}(-[A-Z]{2})?");
        }

        @RepeatedTest(20)
        @DisplayName("a moment the web's way, and as seconds or milliseconds since 1970")
        void moments_written_other_ways() {
            assertThat(FormattedStrings.httpDate(RANDOM))
                    .describedAs("the form HTTP prefers, the day always in two digits")
                    .matches("[A-Z][a-z]{2}, \\d{2} [A-Z][a-z]{2} \\d{4} \\d{2}:\\d{2}:\\d{2} GMT");
            assertThat(Long.parseLong(FormattedStrings.epochSeconds(RANDOM)))
                    .isBetween(631_152_000L, 2_208_988_800L);
            assertThat(FormattedStrings.epochMillis(RANDOM)).matches("\\d{12,13}");
        }

        private static boolean passesLuhn(String number) {
            int sum = 0;
            for (int at = 0; at < number.length(); at++) {
                int digit = number.charAt(number.length() - 1 - at) - '0';
                if (at % 2 == 1) {
                    digit *= 2;
                    if (digit > 9) {
                        digit -= 9;
                    }
                }
                sum += digit;
            }
            return sum % 10 == 0;
        }
    }

    /** One source for the whole class, so repeated runs of a test see different draws. */
    private static final SplittableRandom RANDOM = new SplittableRandom(20260920L);
}

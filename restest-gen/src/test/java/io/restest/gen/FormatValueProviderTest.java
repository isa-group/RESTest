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

import io.restest.core.execution.ValueOrigin;
import io.restest.core.gen.GeneratedValue;
import io.restest.core.json.JsonValue;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.StringSchema;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The source of values for a document that says what kind of text it wants. */
class FormatValueProviderTest {

    /** A moment the tests can assert against, rather than whenever they happen to run. */
    private static final Instant A_MOMENT = Instant.parse("2026-09-18T12:34:56Z");

    /**
     * A moment whose seconds are zero, which is the one in sixty that used to be built wrong.
     *
     * <p>The tests here were once pinned to a moment like this one without meaning to be, and every
     * timestamp they looked at was missing its seconds - which the ordinary reader of a date accepts
     * and an API holding out for a proper timestamp does not. Both moments are used deliberately now.
     */
    private static final Instant ON_THE_MINUTE = Instant.parse("2026-09-18T12:00:00Z");

    private static final Clock FIXED = Clock.fixed(A_MOMENT, ZoneOffset.UTC);

    private final FormatValueProvider provider =
            new FormatValueProvider(Schemas.fixedRandom(), FIXED);

    /**
     * What each kind of text has to survive: the reader a real API would use on it, not a pattern
     * written to match whatever we happen to produce.
     */
    private static final Map<String, Consumer<String>> READERS = Map.ofEntries(
            Map.entry("date-time", FormatValueProviderTest::readTimestamp),
            Map.entry("date", FormatValueProviderTest::readDate),
            Map.entry("time", FormatValueProviderTest::readTimeOfDay),
            Map.entry("duration", Duration::parse),
            Map.entry("email", FormatValueProviderTest::readAddress),
            Map.entry("idn-email", FormatValueProviderTest::readAddress),
            Map.entry("hostname", FormatValueProviderTest::readHostname),
            Map.entry("uri", FormatValueProviderTest::readAbsoluteUri),
            Map.entry("url", FormatValueProviderTest::readAbsoluteUri),
            Map.entry("iri", FormatValueProviderTest::readAbsoluteUri),
            Map.entry("uri-reference", URI::create),
            Map.entry("ipv4", FormatValueProviderTest::readIpv4),
            Map.entry("ipv6", FormatValueProviderTest::readIpv6),
            Map.entry("uuid", FormatValueProviderTest::readIdentifier),
            Map.entry("byte", value -> Base64.getDecoder().decode(value)),
            Map.entry("password", FormatValueProviderTest::readPassword),
            Map.entry("int32", Long::parseLong),
            Map.entry("int64", Long::parseLong),
            Map.entry("uint32", Long::parseLong),
            Map.entry("uint64", Long::parseLong));

    static Stream<String> everyKindItKnows() {
        return FormatValueProvider.known().stream().sorted();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyKindItKnows")
    @DisplayName("every kind of text it builds is one a reader of that kind accepts")
    void builds_what_the_document_asked_for(String format) {
        Consumer<String> reader = READERS.get(format);
        assertThat(reader)
                .describedAs("a kind of text was added to the provider without a reader here to "
                        + "check what it produces, so nothing would notice it going wrong")
                .isNotNull();

        // Several draws rather than one: a value that parses by luck once should not pass.
        for (int draw = 0; draw < 50; draw++) {
            String built = text(provider.offer(Schemas.asking(StringSchema.ofFormat(format)))
                    .orElseThrow(() -> new AssertionError("said nothing about " + format)));
            assertThatCode(() -> reader.accept(built))
                    .describedAs("%s produced %s", format, built)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("a kind of text nobody here has heard of is left to whoever answers next")
    void says_nothing_about_a_kind_it_does_not_know() {
        for (String unknown : Set.of("id1", "swagger", "ISBN-13", "raml", "google")) {
            assertThat(provider.offer(Schemas.asking(StringSchema.ofFormat(unknown))))
                    .describedAs("offered a value for %s", unknown)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("a value that is not text at all is left alone, whatever the document calls it")
    void says_nothing_about_anything_but_text() {
        assertThat(provider.offer(Schemas.asking(StringSchema.of()))).isEmpty();
        assertThat(provider.offer(Schemas.asking(numberDescribedAs("int32")))).isEmpty();
    }

    @Test
    @DisplayName("capitals in the document's own spelling make no difference")
    void reads_the_name_whatever_its_capitals() {
        assertThat(provider.offer(Schemas.asking(StringSchema.ofFormat("Date-Time")))).isPresent();
        assertThat(provider.offer(Schemas.asking(StringSchema.ofFormat("UUID")))).isPresent();
    }

    @Test
    @DisplayName("the same starting number and the same day produce the same run twice over")
    void one_seed_and_one_day_mean_one_run() {
        for (String format : FormatValueProvider.known()) {
            String once = text(new FormatValueProvider(Schemas.fixedRandom(), FIXED)
                    .offer(Schemas.asking(StringSchema.ofFormat(format))).orElseThrow());
            // A second clock, later the same day. A run started twice in one day from one number
            // has to send the same values, which reading the clock afresh per value would break.
            Clock laterThatDay = Clock.fixed(A_MOMENT.plus(4, ChronoUnit.HOURS), ZoneOffset.UTC);
            String again = text(new FormatValueProvider(Schemas.fixedRandom(), laterThatDay)
                    .offer(Schemas.asking(StringSchema.ofFormat(format))).orElseThrow());
            assertThat(once).describedAs("%s differed between two runs started the same day from "
                            + "the same number", format)
                    .isEqualTo(again);
        }
    }

    @Test
    @DisplayName("a timestamp keeps its seconds even when the clock's own seconds are zero")
    void a_timestamp_keeps_its_seconds_on_the_minute() {
        FormatValueProvider onTheMinute = new FormatValueProvider(Schemas.fixedRandom(),
                Clock.fixed(ON_THE_MINUTE, ZoneOffset.UTC));

        for (int draw = 0; draw < 100; draw++) {
            for (String format : Set.of("date-time", "time")) {
                String built = text(onTheMinute
                        .offer(Schemas.asking(StringSchema.ofFormat(format))).orElseThrow());
                assertThat(built)
                        .describedAs("%s dropped its seconds, which an API holding out for a "
                                + "proper timestamp refuses", format)
                        .matches(".*\\d{2}:\\d{2}:\\d{2}Z$");
            }
        }
    }

    @Test
    @DisplayName("a time of day varies, rather than every one of them reading the same o'clock")
    void a_time_of_day_is_not_always_the_clock_s_own() {
        Set<String> seen = new HashSet<>();
        for (int draw = 0; draw < 50; draw++) {
            seen.add(text(provider.offer(Schemas.asking(StringSchema.ofFormat("time")))
                    .orElseThrow()));
        }

        assertThat(seen).describedAs("every time of day was the same one, so a run testing a "
                        + "parameter of this kind sends one value and calls it fifty")
                .hasSizeGreaterThan(40);
    }

    @Test
    @DisplayName("an identifier is a fresh one every time, so two things are never created as one")
    void an_identifier_is_never_sent_twice() {
        Set<String> seen = new HashSet<>();
        for (int draw = 0; draw < 200; draw++) {
            seen.add(text(provider.offer(Schemas.asking(StringSchema.ofFormat("uuid")))
                    .orElseThrow()));
        }
        assertThat(seen).hasSize(200);
    }

    @Test
    @DisplayName("a date is worked out from the clock, so it does not go stale as the years pass")
    void a_date_is_worked_out_from_the_clock() {
        Clock yearsLater = Clock.fixed(A_MOMENT.plus(3650, ChronoUnit.DAYS), ZoneOffset.UTC);
        FormatValueProvider later = new FormatValueProvider(Schemas.fixedRandom(), yearsLater);

        for (int draw = 0; draw < 50; draw++) {
            OffsetDateTime built = OffsetDateTime.parse(text(later
                    .offer(Schemas.asking(StringSchema.ofFormat("date-time"))).orElseThrow()));
            assertThat(Duration.between(yearsLater.instant(), built.toInstant()).abs())
                    .describedAs("%s is not near the clock it was built from", built)
                    .isLessThanOrEqualTo(Duration.ofDays(366));
        }
    }

    @Test
    @DisplayName("a document that asks for a kind of text and then forbids every one of them "
            + "is answered with silence rather than with something too long")
    void says_nothing_when_the_document_contradicts_itself() {
        StringSchema noAddressCouldFit = new StringSchema(SchemaMetadata.none(), Optional.empty(),
                Optional.of(3), Optional.empty(), Optional.of("email"));
        assertThat(provider.offer(Schemas.asking(noAddressCouldFit))).isEmpty();

        StringSchema noTimestampCouldFit = new StringSchema(SchemaMetadata.none(),
                Optional.of(200), Optional.empty(), Optional.empty(), Optional.of("date-time"));
        assertThat(provider.offer(Schemas.asking(noTimestampCouldFit))).isEmpty();
    }

    @Test
    @DisplayName("a value says it came from the kind of text the document named")
    void a_value_says_where_it_came_from() {
        GeneratedValue built = provider.offer(Schemas.asking(StringSchema.ofFormat("email")))
                .orElseThrow();
        assertThat(built.origin()).isEqualTo(new ValueOrigin.Generated("format"));
    }

    @Test
    @DisplayName("no value it builds points at a host somebody could actually own")
    void nothing_it_builds_reaches_a_real_host() {
        for (String reachable : Set.of("uri", "url", "iri", "email", "idn-email", "hostname")) {
            for (int draw = 0; draw < 20; draw++) {
                String built = text(provider.offer(Schemas.asking(StringSchema.ofFormat(reachable)))
                        .orElseThrow());
                assertThat(hostOf(built))
                        .describedAs("%s produced %s, which names a host somebody could own",
                                reachable, built)
                        .isEqualTo("example.com");
            }
        }
    }

    /** Whichever part of the value a request would actually connect to, or send mail to. */
    private static String hostOf(String value) {
        if (value.contains("://")) {
            return URI.create(value).getHost();
        }
        if (value.contains("@")) {
            return value.substring(value.indexOf('@') + 1);
        }
        // A bare hostname, whose own first label is the part we made up.
        return value.substring(value.indexOf('.') + 1);
    }

    private static String text(GeneratedValue value) {
        assertThat(value.value()).isInstanceOf(JsonValue.JsonString.class);
        return ((JsonValue.JsonString) value.value()).value();
    }

    private static NumberSchema numberDescribedAs(String format) {
        return new NumberSchema(SchemaMetadata.none(), NumberKind.INTEGER,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(format));
    }

    private static void readAddress(String value) {
        String[] halves = value.split("@");
        if (halves.length != 2 || halves[0].isEmpty()) {
            throw new IllegalArgumentException("not an address: " + value);
        }
        readHostname(halves[1]);
    }

    private static void readHostname(String value) {
        if (!value.matches("[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]"
                + "([a-zA-Z0-9-]*[a-zA-Z0-9])?)+")) {
            throw new IllegalArgumentException("not a hostname: " + value);
        }
    }

    private static void readAbsoluteUri(String value) {
        if (!URI.create(value).isAbsolute()) {
            throw new IllegalArgumentException("not an absolute address: " + value);
        }
    }

    private static void readIdentifier(String value) {
        UUID read = UUID.fromString(value);
        if (read.version() != 4 || read.variant() != 2) {
            throw new IllegalArgumentException("not an identifier of the usual kind: " + value);
        }
    }

    /**
     * Any text at all satisfies what a document means by a password, but the recipe exists to send
     * what an API asking for one usually insists on, so that is what is checked.
     */
    private static void readPassword(String value) {
        if (!value.matches(".*[A-Z].*") || !value.matches(".*[0-9].*")
                || !value.matches(".*[^A-Za-z0-9].*") || value.length() < 8) {
            throw new IllegalArgumentException("not what an API asking for a password wants: "
                    + value);
        }
    }

    /**
     * A timestamp as the rule for one insists on it, seconds included.
     *
     * <p>Not {@link OffsetDateTime#parse}, which accepts {@code 2026-09-18T12:00Z} with no seconds
     * at all. That is the laxer of the two readings and it is not the one an API applies, so using
     * it here would have let exactly the bug this checks for through.
     */
    private static void readTimestamp(String value) {
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")) {
            throw new IllegalArgumentException("not a timestamp with its seconds: " + value);
        }
        OffsetDateTime.parse(value);
    }

    private static void readTimeOfDay(String value) {
        if (!value.matches("\\d{2}:\\d{2}:\\d{2}Z")) {
            throw new IllegalArgumentException("not a time of day with its seconds: " + value);
        }
        OffsetTime.parse(value);
    }

    private static void readDate(String value) {
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("not a date: " + value);
        }
        LocalDate.parse(value);
    }

    private static void readIpv4(String value) {
        if (!value.matches("(\\d{1,3}\\.){3}\\d{1,3}")) {
            throw new IllegalArgumentException("not an internet address: " + value);
        }
    }

    private static void readIpv6(String value) {
        if (!value.matches("[0-9a-f:]+") || !value.contains("::")) {
            throw new IllegalArgumentException("not an internet address: " + value);
        }
    }
}

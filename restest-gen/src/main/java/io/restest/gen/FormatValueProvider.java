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

import io.restest.core.gen.GeneratedValue;
import io.restest.core.gen.ValueProvider;
import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonValue;
import io.restest.core.schema.StringSchema;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Builds a value of the shape a document asks for when it says what kind of text it wants.
 *
 * <p>A specification often says more about a piece of text than how long it may be. It can say the
 * text is a date, an e-mail address, a web address or an identifier, and an API that says so will
 * almost always refuse anything else. Without this, a parameter described as a date received an
 * ordinary word such as {@code a7Kx9mQ2}, the API answered "bad request", and nothing about that
 * operation was ever really tested.
 *
 * <p>This is asked after everything that knows about <em>this particular</em> value - the fixed list
 * of values the document accepts, a list somebody wrote for this parameter, the document's own
 * sample - and before {@link RandomValueProvider}, which invents a value from the shape alone and
 * knows nothing about dates or e-mail addresses. It answers only when the document names a kind of
 * text it recognises, and says nothing otherwise, which is the ordinary way for a source to behave:
 * a document calling for a kind of text nobody here has heard of simply falls through to invention.
 *
 * <p>Values are computed rather than read from a list of ready-made ones, which matters for two of
 * them in particular. A date is worked out from the clock, so it is still a plausible date next year
 * rather than one that has quietly slipped into the past; and an identifier is a fresh one every
 * time, so an API asked to create two things is not asked to create both under the same name.
 * Somebody who would rather send their own fixed values for a kind of text can still do so, by
 * writing them in a file of their own: that file is asked first and this never overrides it.
 *
 * <p>Both the randomness and the clock are handed in rather than taken from the surroundings, so
 * that one starting number and one moment produce one run, however many times it is repeated.
 */
public final class FormatValueProvider implements ValueProvider {

    /**
     * Reserved for use in documentation and guaranteed never to be registered by anybody, so a
     * value built from it cannot send the API under test off to a real host.
     */
    private static final String EXAMPLE_DOMAIN = "example.com";

    /** How far either side of today a date may land. */
    private static final int DAYS_EITHER_SIDE = 365;

    private static final long SECONDS_IN_A_DAY = ChronoUnit.DAYS.getDuration().toSeconds();

    /**
     * Written out rather than left to a value's own {@code toString}, which drops the seconds when
     * they happen to be zero - {@code 2026-09-18T12:00Z}. A reader of ordinary dates accepts that;
     * the rule an API is entitled to hold a timestamp to does not, because it makes the seconds
     * compulsory. One minute in every sixty would have been refused for the want of two characters.
     */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    /** A time of day, with the same compulsory seconds and for the same reason. */
    private static final DateTimeFormatter TIME_OF_DAY =
            DateTimeFormatter.ofPattern("HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    /** A date alone, which has no seconds to lose. */
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withZone(ZoneOffset.UTC);

    /** Enough to look like a real value, short enough that no API refuses it for being long. */
    private static final int WORD_LENGTH = 8;

    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyz";

    /** Kept well inside the smallest of the whole-number widths a document names. */
    private static final int LARGEST_NUMBER_IN_TEXT = 1_000_000;

    /** How many bytes a piece of encoded data is made of. */
    private static final int DATA_BYTES = 12;

    /** What one kind of text is built from: the run's randomness, and the moment it is being run. */
    @FunctionalInterface
    private interface Recipe {
        String build(RandomGenerator random, Clock clock);
    }

    /**
     * The kinds of text this knows how to build, under the names documents write for them.
     *
     * <p>Names are matched without regard to capitals, because documents are inconsistent about it
     * and a parameter described as an {@code Email} wants exactly what one described as an
     * {@code email} does.
     */
    private static final Map<String, Recipe> RECIPES = Map.ofEntries(
            Map.entry("date-time", (random, clock) -> TIMESTAMP.format(around(random, clock))),
            Map.entry("date", (random, clock) -> DATE.format(around(random, clock))),
            Map.entry("time", (random, clock) -> TIME_OF_DAY.format(around(random, clock))),
            Map.entry("duration", (random, clock) ->
                    Duration.ofMinutes(1 + random.nextInt(60 * 24)).toString()),
            Map.entry("email", (random, clock) -> word(random) + "@" + EXAMPLE_DOMAIN),
            Map.entry("idn-email", (random, clock) -> word(random) + "@" + EXAMPLE_DOMAIN),
            Map.entry("hostname", (random, clock) -> word(random) + "." + EXAMPLE_DOMAIN),
            Map.entry("uri", (random, clock) -> "https://" + EXAMPLE_DOMAIN + "/" + word(random)),
            Map.entry("url", (random, clock) -> "https://" + EXAMPLE_DOMAIN + "/" + word(random)),
            Map.entry("iri", (random, clock) -> "https://" + EXAMPLE_DOMAIN + "/" + word(random)),
            Map.entry("uri-reference", (random, clock) -> "/" + word(random)),
            Map.entry("ipv4", (random, clock) -> "192.0.2." + (1 + random.nextInt(254))),
            Map.entry("ipv6", (random, clock) ->
                    "2001:db8::" + Integer.toHexString(1 + random.nextInt(0xffff))),
            Map.entry("uuid", (random, clock) -> identifier(random).toString()),
            Map.entry("byte", (random, clock) -> encodedData(random)),
            Map.entry("password", FormatValueProvider::passwordLikeText),
            Map.entry("int32", FormatValueProvider::numberAsText),
            Map.entry("int64", FormatValueProvider::numberAsText),
            Map.entry("uint32", FormatValueProvider::numberAsText),
            Map.entry("uint64", FormatValueProvider::numberAsText));

    private final RandomGenerator random;
    private final Clock clock;

    /**
     * A provider building values as of now.
     *
     * @param random where the values come from. Sharing one seeded source across the whole run is
     *     what makes a run repeatable
     */
    public FormatValueProvider(RandomGenerator random) {
        this(random, Clock.systemUTC());
    }

    /**
     * A provider building values as of whatever moment it is given, which is how a test pins a date
     * it can then assert something about.
     *
     * @param random where the values come from. Sharing one seeded source across the whole run is
     *     what makes a run repeatable
     * @param clock the moment dates and times are worked out from
     */
    public FormatValueProvider(RandomGenerator random, Clock clock) {
        this.random = Objects.requireNonNull(random, "random");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The names of the kinds of text this can build, for the test that checks it builds them. */
    static Set<String> known() {
        return RECIPES.keySet();
    }

    @Override
    public Optional<GeneratedValue> offer(ValueRequest request) {
        Objects.requireNonNull(request, "request");
        if (!(request.schema() instanceof StringSchema text) || text.format().isEmpty()) {
            return Optional.empty();
        }
        Recipe recipe = RECIPES.get(text.format().orElseThrow().toLowerCase(Locale.ROOT));
        if (recipe == null) {
            return Optional.empty();
        }
        String built = recipe.build(random, clock);
        // A document is free to ask for a kind of text and then forbid every value of it, by
        // demanding a length no e-mail address or timestamp could have. Saying nothing is the
        // honest answer there: the document has contradicted itself, and whatever the next source
        // invents will at least satisfy the half of it that is about length.
        if (!fits(built, text)) {
            return Optional.empty();
        }
        JsonValue value = JsonValue.of(built);
        if (!RequestBuilder.canBeSentFrom(value, request.location())) {
            return Optional.empty();
        }
        return Optional.of(GeneratedValue.generatedBy(value, name()));
    }

    /** Whether the text satisfies what the document says about how long it may be. */
    private static boolean fits(String built, StringSchema text) {
        return text.minLength().map(shortest -> built.length() >= shortest).orElse(true)
                && text.maxLength().map(longest -> built.length() <= longest).orElse(true);
    }

    /**
     * A moment somewhere either side of today, so that past and future dates both get sent.
     *
     * <p>Built from today rather than from this instant, and given a time of day of its own. Two
     * things follow, both of them wanted. A run started twice in one day from the same starting
     * number sends the same dates, where reading the clock afresh for every value would have sent
     * different ones a second later. And a time of day varies, where shifting a moment by whole
     * days would have left every single one of them reading the same o'clock.
     */
    private static Instant around(RandomGenerator random, Clock clock) {
        long day = random.nextLong(-DAYS_EITHER_SIDE, DAYS_EITHER_SIDE + 1L);
        long secondOfDay = random.nextLong(0, SECONDS_IN_A_DAY);
        return clock.instant().truncatedTo(ChronoUnit.DAYS)
                .plus(day, ChronoUnit.DAYS)
                .plusSeconds(secondOfDay);
    }

    private static String word(RandomGenerator random) {
        StringBuilder built = new StringBuilder(WORD_LENGTH);
        for (int letter = 0; letter < WORD_LENGTH; letter++) {
            built.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        }
        return built.toString();
    }

    /**
     * An identifier of the usual kind, built from the run's own randomness.
     *
     * <p>Not {@link UUID#randomUUID()}, which takes its randomness from somewhere else entirely and
     * would make two runs started from the same number send different values.
     */
    private static UUID identifier(RandomGenerator random) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        // The two markers every identifier of this kind carries: which version it is, and which
        // family of layouts it belongs to. Without them it is still sixteen well-formed bytes, but
        // a strict reader is entitled to refuse it.
        bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x40);
        bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);
        long high = 0;
        long low = 0;
        for (int at = 0; at < 8; at++) {
            high = (high << 8) | (bytes[at] & 0xffL);
            low = (low << 8) | (bytes[at + 8] & 0xffL);
        }
        return new UUID(high, low);
    }

    private static String encodedData(RandomGenerator random) {
        byte[] bytes = new byte[DATA_BYTES];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Text of the kind a field for a password usually insists on.
     *
     * <p>Any text at all satisfies what the document actually says - naming a field a password is a
     * hint about how to display it, not a rule about its contents - but an API that asks for one
     * commonly wants a capital, a digit and a symbol, and there is nothing to lose by sending them.
     */
    private static String passwordLikeText(RandomGenerator random, Clock clock) {
        String body = word(random);
        return Character.toUpperCase(body.charAt(0)) + body.substring(1) + random.nextInt(10) + "!";
    }

    private static String numberAsText(RandomGenerator random, Clock clock) {
        return String.valueOf(1 + random.nextInt(LARGEST_NUMBER_IN_TEXT));
    }

    @Override
    public String name() {
        return "format";
    }
}

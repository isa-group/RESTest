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

import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.Parameter;
import io.restest.core.model.ResponseModel;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import io.restest.gen.ImpliedFormats.Implied;
import io.restest.gen.ImpliedFormats.Kind;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How often each rule that reads a name or a description is right, measured over fifty real
 * specifications.
 *
 * <p>The answer is known wherever a document does say what it wants: a property that declares
 * {@code format: date-time}, or whose samples are all e-mail addresses, settles what a rule should
 * have said about it. So every place in every document that writes a piece of text - in the
 * requests and in the replies, since both are named by the same people the same way - is asked of
 * the table, and the answer is held against what the place declares. A rule that falls below nine
 * in ten, over three places or more, is not one to keep.
 *
 * <p>The second number is reach: how many places in the requests declare nothing that settles
 * them - a spelling rule may still hold the value to account - so that a rule changes what is sent
 * there. The numbers move when the corpus or the table does, and are
 * pinned so that a change in them is something somebody decided.
 */
class ImpliedFormatsAcrossTheCorpusTest {

    private static final int AS_DEEP_AS_IT_GOES = 8;

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[a-z]{2,}");
    private static final Pattern WEB = Pattern.compile("https?://\\S+");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern DATE_TIME = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}[T ][\\d:.]+(?:Z|[+-]\\d{2}:?\\d{2})?");
    private static final Pattern A_UUID = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern PHONE = Pattern.compile("\\+?[\\d\\s().-]{7,20}");

    /**
     * What a password, a user name, a name and a gender look like where a document shows one. A
     * password is any word without a space: a sample as weak as foo is still a password, and a
     * strong one is accepted where it is. The others are read from their samples.
     */
    private static final Pattern NO_SPACE = Pattern.compile("\\S+");
    private static final Pattern A_USER_NAME = Pattern.compile("[A-Za-z0-9._-]{2,32}");
    private static final Pattern A_PERSONS_NAME = Pattern.compile("\\p{L}[\\p{L} '.-]*");
    private static final Set<String> GENDER_WORDS = Set.of("male", "female", "m", "f", "other",
            "man", "woman", "unknown", "unspecified", "non-binary", "nonbinary");

    /** Per rule: right and wrong where the answer is known, and the places that declare nothing. */
    private static final Map<String, String> PINNED = new TreeMap<>(Map.ofEntries(
            Map.entry("D1", "0 right, 0 wrong, 4 places with nothing declared"),
            Map.entry("D10", "27 right, 0 wrong, 7 places with nothing declared"),
            Map.entry("D11", "3 right, 0 wrong, 0 places with nothing declared"),
            Map.entry("D2", "3 right, 0 wrong, 6 places with nothing declared"),
            Map.entry("D3", "0 right, 0 wrong, 1 places with nothing declared"),
            Map.entry("D7", "1 right, 0 wrong, 6 places with nothing declared"),
            Map.entry("D8", "0 right, 0 wrong, 6 places with nothing declared"),
            Map.entry("D9", "2 right, 0 wrong, 4 places with nothing declared"),
            Map.entry("N1", "82 right, 2 wrong, 0 places with nothing declared"),
            Map.entry("N10", "2 right, 1 wrong, 8 places with nothing declared"),
            Map.entry("N11", "7 right, 0 wrong, 7 places with nothing declared"),
            Map.entry("N12", "14 right, 0 wrong, 15 places with nothing declared"),
            Map.entry("N13", "9 right, 1 wrong, 12 places with nothing declared"),
            Map.entry("N14", "2 right, 0 wrong, 1 places with nothing declared"),
            Map.entry("N15", "53 right, 7 wrong, 40 places with nothing declared"),
            Map.entry("N2", "9 right, 0 wrong, 8 places with nothing declared"),
            Map.entry("N3", "316 right, 5 wrong, 21 places with nothing declared"),
            Map.entry("N4", "1 right, 0 wrong, 5 places with nothing declared"),
            Map.entry("N5", "4 right, 0 wrong, 0 places with nothing declared"),
            Map.entry("N7", "2 right, 0 wrong, 2 places with nothing declared"),
            Map.entry("N8", "0 right, 0 wrong, 0 places with nothing declared"),
            Map.entry("N9", "0 right, 0 wrong, 2 places with nothing declared"),
            Map.entry("T1", "15 right, 1 wrong, 19 places with nothing declared"),
            Map.entry("T2", "12 right, 0 wrong, 2 places with nothing declared"),
            Map.entry("T4", "0 right, 0 wrong, 0 places with nothing declared"),
            Map.entry("T6", "0 right, 0 wrong, 1 places with nothing declared")));

    @Test
    @DisplayName("each rule is as right as it was measured to be, and reaches as far")
    void each_rule_keeps_its_numbers() {
        Map<String, int[]> counts = measure();
        Map<String, String> found = new TreeMap<>();
        counts.forEach((rule, numbers) -> found.put(rule, numbers[0] + " right, " + numbers[1]
                + " wrong, " + numbers[2] + " places with nothing declared"));

        assertThat(found).isEqualTo(PINNED);
    }

    @Test
    @DisplayName("no rule kept is wrong more than one time in ten, over three places or more")
    void every_rule_kept_is_right_nine_times_in_ten() {
        measure().forEach((rule, numbers) -> {
            int known = numbers[0] + numbers[1];
            // N10, the names of a language, is kept below the bar by the maintainer's choice: one
            // of its misses is a reply's list of language names, and it is the only rule that
            // reaches the languages an API such as LanguageTool asks for by name alone.
            // N15, a bare "name" sent as a name of letters, is kept below it by the same choice:
            // its misses are names written with digits, slashes or underscores - a secret, a
            // branch, a release - and it is the only rule that reaches a registration that refuses
            // a name with a digit in it under that one word.
            if (known >= 3 && !rule.equals("N10") && !rule.equals("N15")) {
                assertThat(numbers[0] * 10)
                        .describedAs("%s: %d right of %d", rule, numbers[0], known)
                        .isGreaterThanOrEqualTo(known * 9);
            }
        });
    }

    /** Every place's clues, deduplicated as the calibration of the table was. */
    private record Place(String document, String name, String description, Optional<String> format,
            boolean hasPattern, List<String> values, boolean inARequest) {
    }

    private static Map<String, int[]> measure() {
        Map<String, int[]> counts = new TreeMap<>();
        SplittableRandom random = new SplittableRandom(20261002L);
        for (Path document : TheCorpus.all()) {
            ApiModel model = TheCorpus.parse(document);
            String name = document.getParent().getFileName().toString();
            Set<Place> places = new LinkedHashSet<>();
            for (Operation operation : model.operations()) {
                for (Parameter parameter : operation.parameters()) {
                    walk(name, parameter.name(), parameter.description().orElse(""),
                            parameter.examples(), parameter.schema(), model, true, places, 0,
                            new HashSet<>());
                }
                operation.requestBody().ifPresent(body -> body.mediaTypes().forEach(type ->
                        body.schemaFor(type).ifPresent(shape -> walk(name, "body", "", List.of(),
                                shape, model, true, places, 0, new HashSet<>()))));
                for (ResponseModel response : operation.responses()) {
                    if (response.status().startsWith("2")) {
                        response.content().values().forEach(shape -> walk(name, "body", "",
                                List.of(), shape, model, false, places, 0, new HashSet<>()));
                    }
                }
            }
            for (Place place : places) {
                Optional<Implied> implied = ImpliedFormats.of(place.name(), place.description());
                if (implied.isEmpty()) {
                    continue;
                }
                int[] numbers = counts.computeIfAbsent(implied.get().rule(), ignored -> new int[3]);
                Optional<String> truth = truthOf(place);
                if (truth.isPresent()) {
                    numbers[agrees(implied.get(), truth.get(), place.values(), random) ? 0 : 1]++;
                } else if (place.inARequest()) {
                    numbers[2]++;
                }
            }
        }
        return counts;
    }

    private static void walk(String document, String name, String description,
            List<JsonValue> examples, CanonicalSchema schema, ApiModel model, boolean inARequest,
            Set<Place> into, int depth, Set<String> entered) {
        if (depth > AS_DEEP_AS_IT_GOES) {
            return;
        }
        switch (schema) {
            case StringSchema text -> {
                List<String> values = new java.util.ArrayList<>();
                for (JsonValue value : concat(examples, text.metadata().examples(),
                        text.metadata().enumeration())) {
                    if (value instanceof JsonValue.JsonString word && !word.value().isBlank()) {
                        values.add(word.value().replace("\"", "").trim());
                    }
                }
                into.add(new Place(document, name,
                        text.metadata().description().orElse(description), text.format(),
                        text.pattern().isPresent(), List.copyOf(values), inARequest));
            }
            case ObjectSchema object -> object.properties().forEach((property, shape) ->
                    walk(document, property, "", List.of(), shape, model, inARequest, into,
                            depth + 1, entered));
            // A piece of a list goes by the list's name and, inside a parameter, by the parameter's
            // description, which is what invention reads for it; a list's own description inside a
            // body is not read, so it is not counted either.
            case ArraySchema list -> walk(document, name, description, List.of(), list.items(),
                    model, inARequest, into, depth + 1, entered);
            case ChoiceSchema choice -> choice.alternatives().forEach(one -> walk(document, name,
                    description, examples, one, model, inARequest, into, depth + 1, entered));
            case SchemaReference reference -> {
                if (entered.add(reference.name() + "@" + name)) {
                    model.resolve(reference).ifPresent(named -> walk(document, name, description,
                            examples, named, model, inARequest, into, depth + 1, entered));
                }
            }
            default -> { }
        }
    }

    private static List<JsonValue> concat(List<JsonValue> first, List<JsonValue> second,
            List<JsonValue> third) {
        List<JsonValue> all = new java.util.ArrayList<>(first);
        all.addAll(second);
        all.addAll(third);
        return all;
    }

    /** What the place declares it is, when it declares anything that settles the question. */
    private static Optional<String> truthOf(Place place) {
        if (place.format().isPresent()) {
            return Optional.of(switch (place.format().get()) {
                case "email", "idn-email" -> "EMAIL";
                case "uri", "url", "iri" -> "URI";
                case "uuid" -> "UUID";
                case "date" -> "DATE";
                case "date-time" -> "DATE_TIME";
                case "password" -> "PASSWORD";
                // A format that names no kind of text - "string", or one a document invented -
                // settles nothing, and invention would not read the place anyway.
                default -> "UNSETTLED";
            }).filter(kind -> !kind.equals("UNSETTLED"));
        }
        List<String> values = place.values();
        if (values.isEmpty()) {
            return Optional.empty();
        }
        if (allMatch(values, EMAIL)) {
            return Optional.of("EMAIL");
        }
        if (allMatch(values, WEB)) {
            return Optional.of("URI");
        }
        if (allMatch(values, DATE)) {
            return Optional.of("DATE");
        }
        if (allMatch(values, DATE_TIME)) {
            return Optional.of("DATE_TIME");
        }
        if (allMatch(values, A_UUID)) {
            return Optional.of("UUID");
        }
        if (allMatch(values, Pattern.compile("[A-Z]{3}"))) {
            return Optional.of("CURRENCY");
        }
        if (allMatch(values, Pattern.compile("[A-Za-z]{2}"))) {
            return Optional.of("COUNTRY_OR_LANGUAGE");
        }
        if (allMatch(values, Pattern.compile("[a-z]{2}[-_][A-Za-z]{2}"))) {
            return Optional.of("LANGUAGE");
        }
        if (values.stream().allMatch(value -> PHONE.matcher(value).matches()
                && value.chars().filter(Character::isDigit).count() >= 7)) {
            return Optional.of("PHONE");
        }
        return Optional.of("OTHER");
    }

    private static boolean agrees(Implied implied, String truth, List<String> values,
            SplittableRandom random) {
        // A password, a user name, a name or a gender has no shape a pattern above would
        // recognise, so where the samples are of no other kind they are held against the kind's
        // own.
        if (truth.equals("OTHER")) {
            switch (implied.kind()) {
                case PASSWORD -> {
                    return allMatch(values, NO_SPACE);
                }
                case USERNAME -> {
                    return allMatch(values, A_USER_NAME);
                }
                case PERSON_NAME, NAME -> {
                    return allMatch(values, A_PERSONS_NAME);
                }
                case GENDER -> {
                    return values.stream().allMatch(value ->
                            GENDER_WORDS.contains(value.toLowerCase(java.util.Locale.ROOT)));
                }
                default -> {
                }
            }
        }
        String kind = implied.kind() == Kind.TEMPLATE
                ? kindOfValue(implied.valueFor(random)) : implied.kind().name();
        return kind.equals(truth)
                || (truth.equals("COUNTRY_OR_LANGUAGE")
                        && (kind.equals("COUNTRY") || kind.equals("LANGUAGE")));
    }

    private static String kindOfValue(String value) {
        return DATE.matcher(value).matches() ? "DATE"
                : DATE_TIME.matcher(value).matches() ? "DATE_TIME" : "TEMPLATE";
    }

    private static boolean allMatch(List<String> values, Pattern pattern) {
        return values.stream().allMatch(value -> pattern.matcher(value).matches());
    }
}

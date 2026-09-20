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

import io.restest.core.gen.GeneratedValue;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.Parameter;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import io.restest.spec.SwaggerSpecificationParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the fifty documents of the corpus say about the <em>characters</em> their values are made of,
 * and whether the values RESTest builds from those statements are ones the same documents accept.
 *
 * <p>Two different things are pinned here, both measurements rather than opinions.
 *
 * <p>The first is how much there is to gain, document by document. A specification can name the kind
 * of a value - a date, a web address, an identifier - or state the spelling it demands as a rule. The
 * counts below say how many places in each document do one or the other, so that a document gaining
 * or losing such a statement is noticed rather than quietly changing what the tool is measured on.
 *
 * <p>The second is the one that could go wrong quietly. A value built from one of those statements
 * still has to satisfy everything else the same shape says - its length above all - and a generator
 * that produced a beautiful date two characters too long for the field it goes in would be no better
 * than the random word it replaced. So every place in every document that says anything about its
 * characters is asked for a value several times over, and every value is held against the whole
 * shape it came from.
 */
class FormatsAndPatternsAcrossTheCorpusTest {

    /**
     * Places whose shape names a kind of value RESTest knows how to build, document by document.
     *
     * <p>Two hundred and twenty-five across the fifty documents, and twenty-five of them in the
     * five APIs the tool is measured on. Far fewer than the thousand-odd times those documents write
     * a {@code format}, because most of those are on values an API <em>returns</em>, and nothing
     * here is sent.
     */
    private static final Map<String, Integer> PLACES_NAMING_A_KIND = new TreeMap<>(Map.ofEntries(
            Map.entry("Amadeus", 1),
            Map.entry("AmadeusHotel", 2),
            Map.entry("BigOven", 110),
            Map.entry("Events", 4),
            Map.entry("GitHub", 10),
            Map.entry("Marvel", 5),
            Map.entry("Petstore", 2),
            Map.entry("Scout", 48),
            Map.entry("Traccar", 18),
            Map.entry("flight-search", 6),
            Map.entry("gestao-hospital", 6),
            Map.entry("kafka-rest-proxy", 4),
            Map.entry("notebook-manager", 2),
            Map.entry("pet-clinic", 7)));

    /**
     * Places whose shape states the spelling it demands, document by document.
     *
     * <p>A small number that includes one of the five APIs the tool is measured on, which is what
     * makes reading these rules worth the work: every one of pet-clinic's ten is on a parameter of
     * an operation the tool tests on every run.
     */
    private static final Map<String, Integer> PLACES_STATING_A_SPELLING = new TreeMap<>(Map.of(
            "AmadeusHotel", 6,
            "GitHub", 7,
            "pet-clinic", 10));

    /**
     * The kinds of value the corpus names in a request that RESTest does not build one of.
     *
     * <p>Four, and every one of them is a kind an ordinary word already answers. Two are somebody
     * writing the <em>type</em> of a value where its kind was asked for; {@code password} is a note
     * to whoever draws the form, not a rule about the characters; and {@code binary} is not text at
     * all but raw bytes, which go in the kind of request body this tool does not send yet.
     *
     * <p>Pinned so that a document arriving with a kind genuinely worth building - a bank account
     * number, a book's identifier - is noticed rather than quietly answered with a word.
     */
    private static final Set<String> KINDS_WE_DO_NOT_BUILD =
            Set.of("Integer", "binary", "password", "string");

    /** How deep into a request body a place is looked for. The same depth a dictionary reaches. */
    private static final int AS_DEEP_AS_IT_GOES = 6;

    /** Draws per place. A value that only breaks its shape on some draws would slip past one. */
    private static final int DRAWS = 5;

    @Test
    @DisplayName("the corpus names the kinds of value it is known to name")
    void the_documents_that_name_a_kind_are_the_ones_measured() {
        Map<String, Integer> found = new TreeMap<>();
        forEachDocument((document, model) -> {
            long here = describedPlaces(model).stream()
                    .filter(place -> place.shape().format()
                            .flatMap(kind -> FormattedStrings.of(kind, Schemas.fixedRandom()))
                            .isPresent())
                    .count();
            if (here > 0) {
                found.put(document, (int) here);
            }
        });

        assertThat(found)
                .describedAs("a document naming more or fewer kinds of value is worth knowing about")
                .containsExactlyInAnyOrderEntriesOf(PLACES_NAMING_A_KIND);
    }

    @Test
    @DisplayName("the kinds of value the corpus names and RESTest does not build are the known ones")
    void the_kinds_we_do_not_build_are_the_ones_measured() {
        Set<String> notBuilt = new java.util.TreeSet<>();
        forEachDocument((document, model) -> describedPlaces(model).stream()
                .map(place -> place.shape().format())
                .flatMap(Optional::stream)
                .filter(kind -> FormattedStrings.of(kind, Schemas.fixedRandom()).isEmpty())
                .forEach(notBuilt::add));

        assertThat(notBuilt)
                .describedAs("a kind arriving here is a kind whose values are ordinary words, so "
                        + "this list is the standing invitation to build one more")
                .containsExactlyInAnyOrderElementsOf(KINDS_WE_DO_NOT_BUILD);
    }

    @Test
    @DisplayName("the corpus states the spelling rules it is known to state")
    void the_documents_that_state_a_spelling_are_the_ones_measured() {
        Map<String, Integer> found = new TreeMap<>();
        forEachDocument((document, model) -> {
            long here = describedPlaces(model).stream()
                    .filter(place -> place.shape().pattern().isPresent())
                    .count();
            if (here > 0) {
                found.put(document, (int) here);
            }
        });

        assertThat(found)
                .describedAs("a document gaining or losing a spelling rule is worth knowing about")
                .containsExactlyInAnyOrderEntriesOf(PLACES_STATING_A_SPELLING);
    }

    @Test
    @DisplayName("every spelling rule the corpus states can be read and built from, every time")
    void every_spelling_rule_in_the_corpus_can_be_read() {
        Set<String> unreadable = new LinkedHashSet<>();
        // Shared across the whole walk, and each rule asked several times, so that a rule which
        // only succeeds on a lucky draw is caught here rather than in somebody's run. Asked at the
        // lengths its own shape states, too: the same rule at (1, 64) and at (40, 40) is two
        // different questions, and only the second is the one the tool will ask.
        RandomGenerator random = Schemas.fixedRandom();
        forEachDocument((document, model) -> {
            for (Described place : describedPlaces(model)) {
                Optional<String> rule = place.shape().pattern();
                if (rule.isEmpty()) {
                    continue;
                }
                long shortest = place.shape().minLength().orElse(1);
                long longest = place.shape().maxLength().map(Integer::longValue)
                        .orElse(Long.MAX_VALUE);
                Optional<MatchingStrings> spellings = MatchingStrings.reading(rule.get(), shortest,
                        longest, Math.max(shortest, Math.min(longest, 64)), random);
                if (spellings.isEmpty()) {
                    unreadable.add(document + " " + place.where() + ": " + rule.get());
                    continue;
                }
                for (int draw = 0; draw < DRAWS; draw++) {
                    if (spellings.orElseThrow().next(random).isEmpty()) {
                        unreadable.add(document + " " + place.where() + " (draw " + draw + "): "
                                + rule.get());
                    }
                }
            }
        });

        assertThat(unreadable)
                .describedAs("a rule nothing can build from is one the tool answers with an "
                        + "ordinary word or with no value at all; worth knowing when it appears")
                .isEmpty();
    }

    @Test
    @DisplayName("a value built from what a document says about its characters satisfies that document")
    void every_described_place_gets_a_value_its_own_shape_accepts() {
        List<String> refused = new ArrayList<>();
        List<String> answeredWithNothing = new ArrayList<>();
        forEachDocument((document, model) -> {
            RandomValueProvider inventing = new RandomValueProvider(model, Schemas.fixedRandom());
            for (Described place : describedPlaces(model)) {
                for (int draw = 0; draw < DRAWS; draw++) {
                    Optional<JsonValue> value =
                            inventing.offer(Schemas.asking(place.shape()))
                                    .map(GeneratedValue::value);
                    if (value.isEmpty()) {
                        answeredWithNothing.add(document + " " + place.where());
                        continue;
                    }
                    SchemaSatisfaction.violations(value.get(), place.shape(), model)
                            .forEach(problem -> refused.add(
                                    document + " " + place.where() + ": " + problem));
                }
            }
        });

        assertThat(refused)
                .describedAs("the whole point is that these values are ones the document accepts")
                .isEmpty();
        // Checked as well as the above, because "no value" satisfies every shape there is: without
        // this, a change that stopped building anything at all for a described place would leave
        // the assertion above perfectly green.
        assertThat(answeredWithNothing)
                .describedAs("a described place that yields nothing is a parameter left out and "
                        + "possibly an operation lost, so it is pinned rather than tolerated")
                .isEmpty();
    }

    @Test
    @DisplayName("a place whose kind of value RESTest knows really does get one of that kind")
    void a_named_kind_reaches_the_request() {
        List<String> notOfItsKind = new ArrayList<>();
        forEachDocument((document, model) -> {
            RandomValueProvider inventing = new RandomValueProvider(model, Schemas.fixedRandom());
            for (Described place : describedPlaces(model)) {
                String kind = place.shape().format().orElse("");
                if (place.shape().pattern().isPresent()
                        || FormattedStrings.of(kind, Schemas.fixedRandom()).isEmpty()) {
                    continue;
                }
                for (int draw = 0; draw < DRAWS; draw++) {
                    Optional<JsonValue> value = inventing.offer(Schemas.asking(place.shape()))
                            .map(GeneratedValue::value);
                    // A shape that says a value may be absent is sometimes sent as nothing at all,
                    // on purpose and whatever kind it names. That is the one answer other than a
                    // value of the named kind which is not a complaint.
                    if (place.shape().metadata().nullable()
                            && value.orElse(JsonValue.NULL) instanceof JsonValue.JsonNull) {
                        continue;
                    }
                    // Anything else that is not text is not "of that kind" either. Read as a silent
                    // pass, this check would survive the whole branch it exists for being deleted.
                    if (!(value.orElse(JsonValue.NULL) instanceof JsonValue.JsonString text)) {
                        notOfItsKind.add(document + " " + place.where() + " (" + kind
                                + "): no text at all, " + value);
                        continue;
                    }
                    if (!readsBackAs(kind, text.value())) {
                        notOfItsKind.add(document + " " + place.where() + " (" + kind + "): "
                                + text.value());
                    }
                }
            }
        });

        assertThat(notOfItsKind)
                .describedAs("without this, deleting the whole branch that reads a declared kind "
                        + "would break nothing: an ordinary word satisfies every length check "
                        + "there is, and a kind is an annotation the shared check cannot hold "
                        + "anybody to")
                .isEmpty();
    }

    /**
     * Whether a value really is of the kind its document named, read back by the platform's own
     * parser for that kind.
     *
     * <p>Written here rather than borrowed from the generator, for the reason every check in these
     * tests is: one that asked the generator whether it had done the right thing would agree with
     * it whatever it did.
     */
    private static boolean readsBackAs(String kind, String value) {
        try {
            switch (kind) {
                case "date-time" -> java.time.OffsetDateTime.parse(value);
                case "date" -> java.time.LocalDate.parse(value);
                case "time" -> java.time.OffsetTime.parse(value);
                case "duration" -> java.time.Duration.parse(value);
                case "uuid" -> java.util.UUID.fromString(value);
                case "byte" -> java.util.Base64.getDecoder().decode(value);
                case "uri", "url", "iri" -> {
                    return java.net.URI.create(value).isAbsolute();
                }
                case "email", "idn-email" -> {
                    return value.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
                }
                case "hostname", "idn-hostname" -> {
                    return value.matches("[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
                }
                case "ipv4" -> {
                    return value.matches("(\\d{1,3}\\.){3}\\d{1,3}");
                }
                case "ipv6" -> {
                    return value.contains(":");
                }
                case "uri-reference", "iri-reference", "json-pointer" -> {
                    return value.startsWith("/");
                }
                case "relative-json-pointer" -> {
                    return value.matches("\\d+/.*");
                }
                // A kind this does not judge is one the generator does not build either, and the
                // caller has already skipped those.
                default -> {
                    return true;
                }
            }
            return true;
        } catch (RuntimeException notOfThatKind) {
            return false;
        }
    }

    /** One place in a request whose shape says something about the characters of its value. */
    private record Described(String where, StringSchema shape) {
    }

    /**
     * Every place in an API whose shape names a kind of value or states a spelling rule.
     *
     * <p>A place is a parameter, or a step inside a request body reached from one. The same walk a
     * list of values takes, so that what is counted here is what somebody writing one would write
     * entries for.
     */
    private static List<Described> describedPlaces(ApiModel model) {
        List<Described> found = new ArrayList<>();
        for (Operation operation : model.operations()) {
            for (Parameter parameter : operation.parameters()) {
                walk(operation.id().value() + " " + parameter.name(), parameter.schema(), model,
                        0, new LinkedHashSet<>(), found);
            }
            operation.requestBody().ifPresent(body -> body.mediaTypes().forEach(mediaType ->
                    body.schemaFor(mediaType).ifPresent(shape ->
                            walk(operation.id().value() + " body(" + mediaType + ")", shape, model,
                                    0, new LinkedHashSet<>(), found))));
        }
        return found;
    }

    private static void walk(String where, CanonicalSchema schema, ApiModel model, int depth,
            Set<String> following, List<Described> found) {
        if (depth > AS_DEEP_AS_IT_GOES) {
            return;
        }
        switch (schema) {
            case StringSchema text -> {
                if (text.format().isPresent() || text.pattern().isPresent()) {
                    found.add(new Described(where, text));
                }
            }
            case ObjectSchema object -> object.properties().forEach((name, property) ->
                    walk(where + "." + name, property, model, depth + 1, following, found));
            case ArraySchema list ->
                    walk(where + "[]", list.items(), model, depth + 1, following, found);
            case ChoiceSchema choice -> {
                int alternative = 0;
                for (CanonicalSchema shape : choice.alternatives()) {
                    walk(where + "/" + alternative++, shape, model, depth + 1, following, found);
                }
            }
            // A shape that refers to itself would otherwise be walked for ever. Each branch of the
            // walk remembers the names it has come through rather than sharing one list, so a shape
            // used twice in different places is still counted twice.
            case SchemaReference reference -> {
                if (following.add(reference.name())) {
                    model.resolve(reference).ifPresent(named ->
                            walk(where, named, model, depth, new LinkedHashSet<>(following), found));
                }
            }
            default -> {
            }
        }
    }

    private static void forEachDocument(java.util.function.BiConsumer<String, ApiModel> check) {
        for (Path document : corpus()) {
            check.accept(document.getParent().getFileName().toString(),
                    new SwaggerSpecificationParser().parse(document.toString()));
        }
    }

    private static List<Path> corpus() {
        try (Stream<Path> tree = Files.walk(specifications())) {
            return tree.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .matches("openapi\\.(yaml|yml|json)"))
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static Path specifications() {
        return repositoryRoot().resolve("restest-spec/src/test/resources/specifications");
    }

    /** The checkout, found by walking up from wherever the tests are being run. */
    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve("restest-spec"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not find the checkout above "
                + Path.of("").toAbsolutePath() + ", and the corpus lives in it");
    }
}

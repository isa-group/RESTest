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
    @DisplayName("every spelling rule the corpus states can be read and built from")
    void every_spelling_rule_in_the_corpus_can_be_read() {
        Set<String> unreadable = new LinkedHashSet<>();
        forEachDocument((document, model) -> describedPlaces(model).stream()
                .map(place -> place.shape().pattern())
                .flatMap(Optional::stream)
                .forEach(rule -> {
                    if (MatchingStrings.reading(rule, 1, Long.MAX_VALUE).isEmpty()) {
                        unreadable.add(document + ": " + rule);
                    }
                }));

        assertThat(unreadable)
                .describedAs("a rule nothing can read is one the tool answers with an ordinary "
                        + "word, which the API will refuse; worth knowing when it appears")
                .isEmpty();
    }

    @Test
    @DisplayName("a value built from what a document says about its characters satisfies that document")
    void every_described_place_gets_a_value_its_own_shape_accepts() {
        List<String> refused = new ArrayList<>();
        forEachDocument((document, model) -> {
            RandomValueProvider inventing =
                    new RandomValueProvider(model, Schemas.fixedRandom());
            for (Described place : describedPlaces(model)) {
                for (int draw = 0; draw < DRAWS; draw++) {
                    // Nothing at all is a fair answer - a shape can demand a spelling and a length
                    // that no string satisfies at once - and saying so is what the tool does with
                    // any impossible shape. What is checked is the values it does offer.
                    inventing.offer(Schemas.asking(place.shape()))
                            .map(GeneratedValue::value)
                            .ifPresent(value -> SchemaSatisfaction
                                    .violations(value, place.shape(), model)
                                    .forEach(problem -> refused.add(
                                            document + " " + place.where() + ": " + problem)));
                }
            }
        });

        assertThat(refused)
                .describedAs("the whole point is that these values are ones the document accepts")
                .isEmpty();
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

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

import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.ResponseModel;
import io.restest.core.schema.AnySchema;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import io.restest.spec.SwaggerSpecificationParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How many gaps in web addresses the kind of thing an address is about can fill, measured over the
 * corpus of real specifications.
 *
 * <p>Before, a gap such as {@code {petTypeId}} was filled from what the API returned only when some
 * reply carried a property with that exact name. Now it can also be filled with the identifier of
 * a thing returned at an address of the same kind - the {@code id} of what {@code GET /pettypes}
 * lists. This counts, from what each document declares its replies look like, how many gaps each
 * rule can reach, and how many only the new one can.
 *
 * <p>It counts what the documents <em>declare</em>, with the kind of value checked - a word for a
 * gap that wants a word, a number for one that wants a number - and without asking a running API.
 * So each number is how many gaps a rule can reach when the API sends back what its document says,
 * which is the most a run can get from it.
 *
 * <p>The numbers move when the corpus does. They are pinned so that a change in them is something
 * somebody decided rather than something that happened.
 */
class IdentifiersByResourceAcrossTheCorpusTest {

    /** How far into a shape the count goes, which is as far as a reply is ever read. */
    private static final int AS_DEEP_AS_IT_GOES = 6;

    /** The five APIs this tool is measured on. */
    private static final List<String> PRIORITY = List.of("flight-search", "gestao-hospital",
            "kafka-rest-proxy", "notebook-manager", "pet-clinic");

    @Test
    @DisplayName("the kind of thing an address is about fills gaps the name never reached")
    void the_kind_reaches_gaps_the_name_never_did() {
        Tally corpus = new Tally();
        Tally priority = new Tally();
        Set<String> newlyReachedInThePriorityFive = new LinkedHashSet<>();
        measure(corpus, priority, newlyReachedInThePriorityFive);

        assertThat(corpus.gaps)
                .describedAs("gaps in the addresses of the whole corpus, counting each gap once "
                        + "for every operation whose address has it")
                .isEqualTo(1_838);
        assertThat(corpus.byName)
                .describedAs("of those, the ones some reply of the same API declares a property "
                        + "for under the gap's own name, of the right kind of value - what the "
                        + "memory could fill before")
                .isEqualTo(812);
        assertThat(corpus.byKind)
                .describedAs("the ones a reply at an address of the same kind declares an "
                        + "identifier of the right kind of value for - what it can fill now")
                .isEqualTo(603);
        assertThat(corpus.onlyByKind)
                .describedAs("and the ones only the kind reaches: 18% of every gap in the corpus")
                .isEqualTo(325);

        assertThat(priority.gaps).isEqualTo(168);
        assertThat(priority.byName).isEqualTo(127);
        assertThat(priority.byKind).isEqualTo(159);
        assertThat(priority.onlyByKind).isEqualTo(35);
        assertThat(newlyReachedInThePriorityFive)
                .describedAs("the gaps in the five that only the kind reaches, by name")
                .containsExactlyInAnyOrder(
                        "gestao-hospital hospital_id", "gestao-hospital produto_id",
                        "gestao-hospital patientId", "notebook-manager notebookId",
                        "pet-clinic petTypeId", "pet-clinic visitId", "pet-clinic specialtyId",
                        "pet-clinic vetId");
    }

    /** What is being counted, on one corpus. */
    private static final class Tally {
        private int gaps;
        private int byName;
        private int byKind;
        private int onlyByKind;
    }

    private static void measure(Tally corpus, Tally priority, Set<String> newlyReached) {
        for (Path document : corpus()) {
            ApiModel model = parse(document);
            String api = document.getParent().getFileName().toString();
            boolean measuredOn = PRIORITY.contains(api);

            Map<String, List<CanonicalSchema>> namesInReplies = new LinkedHashMap<>();
            Map<String, List<Map<String, CanonicalSchema>>> thingsByKind = new LinkedHashMap<>();
            for (Operation operation : model.operations()) {
                Optional<String> kind = operation.method() == HttpMethod.DELETE
                        ? Optional.empty()
                        : ObservedValues.kindOfThingAt(operation.path());
                for (ResponseModel response : operation.responses()) {
                    if (!response.status().startsWith("2")) {
                        continue;
                    }
                    response.schemaFor("application/json").ifPresent(schema -> {
                        names(schema, model, 0, new LinkedHashSet<>(), namesInReplies);
                        kind.ifPresent(named -> things(schema, model, 0, new LinkedHashSet<>(),
                                thingsByKind.computeIfAbsent(named, ignored -> new ArrayList<>())));
                    });
                }
            }

            for (Operation operation : model.operations()) {
                for (Parameter parameter : operation.parameters()) {
                    if (parameter.location() != ParameterLocation.PATH) {
                        continue;
                    }
                    boolean byName = namesInReplies.getOrDefault(parameter.name(), List.of())
                            .stream().anyMatch(declared ->
                                    sameKindOfValue(declared, parameter.schema(), model));
                    boolean byKind = kindsFor(operation.path(), parameter.name()).stream()
                            .anyMatch(kind -> thingsByKind.getOrDefault(kind, List.of()).stream()
                                    .anyMatch(thing -> thing.entrySet().stream().anyMatch(property ->
                                            fillsTheGap(property.getKey(), kind,
                                                    parameter.name())
                                                    && sameKindOfValue(property.getValue(),
                                                            parameter.schema(), model))));
                    count(corpus, byName, byKind);
                    if (measuredOn) {
                        count(priority, byName, byKind);
                        if (byKind && !byName) {
                            newlyReached.add(api + " " + parameter.name());
                        }
                    }
                }
            }
        }
    }

    /** The convincing steps the tool takes, in the words it takes them in. */
    private static boolean fillsTheGap(String property, String kind, String gap) {
        return property.equals(gap) || (ObservedValues.looksLikeAnIdentifier(gap)
                && ObservedValues.isTheIdentifierOf(property, kind));
    }

    /**
     * Whether what a reply declares is the kind of value the gap wants: a word for a word, a
     * number for a number. Only those two are kept of a thing, so a property declared as an
     * object - a repository's {@code owner} for a gap called {@code {owner}} - fills nothing.
     */
    private static boolean sameKindOfValue(CanonicalSchema declared, CanonicalSchema wanted,
            ApiModel model) {
        Set<Class<?>> one = kindsOfValue(declared, model, 0);
        Set<Class<?>> other = kindsOfValue(wanted, model, 0);
        return one.contains(AnySchema.class) && !other.isEmpty()
                || other.contains(AnySchema.class) && !one.isEmpty()
                || one.stream().anyMatch(other::contains);
    }

    /** The kinds of single value a shape can be: a word, a number, or anything. */
    private static Set<Class<?>> kindsOfValue(CanonicalSchema schema, ApiModel model, int depth) {
        CanonicalSchema here = resolved(schema, model, new LinkedHashSet<>());
        Set<Class<?>> kinds = new LinkedHashSet<>();
        if (here == null || depth > AS_DEEP_AS_IT_GOES) {
            return kinds;
        }
        switch (here) {
            case ChoiceSchema choice -> choice.alternatives().forEach(alternative ->
                    kinds.addAll(kindsOfValue(alternative, model, depth + 1)));
            case StringSchema ignored -> kinds.add(StringSchema.class);
            case NumberSchema ignored -> kinds.add(NumberSchema.class);
            case AnySchema ignored -> kinds.add(AnySchema.class);
            default -> { }
        }
        return kinds;
    }

    private static void count(Tally tally, boolean byName, boolean byKind) {
        tally.gaps++;
        tally.byName += byName ? 1 : 0;
        tally.byKind += byKind ? 1 : 0;
        tally.onlyByKind += byKind && !byName ? 1 : 0;
    }

    /** The kinds of thing a gap may be the identifier of, as the tool works them out. */
    private static List<String> kindsFor(String path, String gap) {
        List<String> kinds = new ArrayList<>();
        ObservedValues.kindOfThingBefore(path, gap).ifPresent(kinds::add);
        ObservedValues.kindOfThingInTheName(gap).filter(kind -> !kinds.contains(kind))
                .ifPresent(kinds::add);
        return kinds;
    }

    /** Every property name a shape declares, at any depth, with what it is declared as. */
    private static void names(CanonicalSchema schema, ApiModel model, int depth,
            Set<String> following, Map<String, List<CanonicalSchema>> into) {
        CanonicalSchema here = resolved(schema, model, following);
        if (here == null || depth > AS_DEEP_AS_IT_GOES) {
            return;
        }
        switch (here) {
            case ObjectSchema object -> object.properties().forEach((name, property) -> {
                into.computeIfAbsent(name, ignored -> new ArrayList<>()).add(property);
                names(property, model, depth + 1, new LinkedHashSet<>(following), into);
            });
            case ArraySchema list ->
                    names(list.items(), model, depth + 1, new LinkedHashSet<>(following), into);
            case ChoiceSchema choice -> choice.alternatives().forEach(alternative ->
                    names(alternative, model, depth + 1, new LinkedHashSet<>(following), into));
            default -> { }
        }
    }

    /**
     * The things a reply of this shape is made of, the way the memory takes a reply apart: a
     * list's elements, an object, and what is inside an object with no identifier of its own.
     */
    private static void things(CanonicalSchema schema, ApiModel model, int depth,
            Set<String> following, List<Map<String, CanonicalSchema>> into) {
        CanonicalSchema here = resolved(schema, model, following);
        if (here == null || depth > AS_DEEP_AS_IT_GOES) {
            return;
        }
        switch (here) {
            case ArraySchema list ->
                    things(list.items(), model, depth + 1, new LinkedHashSet<>(following), into);
            case ChoiceSchema choice -> choice.alternatives().forEach(alternative ->
                    things(alternative, model, depth + 1, new LinkedHashSet<>(following), into));
            case ObjectSchema object -> {
                into.add(object.properties());
                if (object.properties().keySet().stream()
                        .noneMatch(ObservedValues::looksLikeAnIdentifier)) {
                    object.properties().values().forEach(inside -> things(inside, model,
                            depth + 1, new LinkedHashSet<>(following), into));
                }
            }
            default -> { }
        }
    }

    private static CanonicalSchema resolved(CanonicalSchema schema, ApiModel model,
            Set<String> following) {
        if (schema instanceof SchemaReference reference) {
            return following.add(reference.name()) ? model.resolve(reference).orElse(null) : null;
        }
        return schema;
    }

    private static ApiModel parse(Path document) {
        return new SwaggerSpecificationParser().parse(document.toString());
    }

    /** The corpus, without the deliberately broken documents kept beside it. */
    private static List<Path> corpus() {
        try (Stream<Path> tree = Files.walk(specifications())) {
            return tree.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .matches("openapi\\.(yaml|yml|json)"))
                    .filter(path -> !path.toString().contains("fixtures"))
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

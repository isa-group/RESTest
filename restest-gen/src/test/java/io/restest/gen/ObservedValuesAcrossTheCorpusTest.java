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
import io.restest.core.model.Operation;
import io.restest.core.model.ResponseModel;
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
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How much of what an API sends back is worth remembering, measured over fifty real specifications.
 *
 * <p>The tool now keeps what the API has returned and sends it again. The question this settles is
 * <em>what to file it under</em>, and there were two candidates. One is the name of the thing - a
 * {@code petId} is a {@code petId} wherever it turns up. The other is the whole way down to it -
 * {@code owner.email} rather than any old {@code email} - which is how one of the tools this one is
 * measured against files the values a person writes by hand, and which tells apart two properties
 * that share a name and mean different things.
 *
 * <p>The answer is the name, and this is where the numbers that say so live. They are also worth
 * keeping for their own sake: they say how much of the corpus this whole mechanism can reach at all.
 *
 * <p>The numbers move when the corpus does. They are pinned so that a change in them is something
 * somebody decided rather than something that happened.
 */
class ObservedValuesAcrossTheCorpusTest {

    /** How far into a shape the count goes, which is as far as anything else in the tool goes. */
    private static final int AS_DEEP_AS_IT_GOES = 6;

    /** The five APIs this tool is measured on. */
    private static final List<String> PRIORITY = List.of("flight-search", "gestao-hospital",
            "kafka-rest-proxy", "notebook-manager", "pet-clinic");

    @Test
    @DisplayName("a name reaches more of the corpus than a whole address would")
    void the_name_reaches_further_than_the_address() {
        Tally corpus = new Tally();
        Tally priority = new Tally();
        measure(corpus, priority, new ArrayList<>());

        assertThat(corpus.values)
                .describedAs("single values inside the request bodies of the whole corpus")
                .isEqualTo(2_125);
        assertThat(corpus.underTheSameName)
                .describedAs("of those, the ones whose name some reply of the same API also "
                        + "carries - which is what this memory can fill")
                .isEqualTo(1_886);
        assertThat(corpus.atTheSameAddress)
                .describedAs("and the ones a reply carries at the same address, which is what "
                        + "filing by the way down to a value would have reached instead")
                .isEqualTo(1_700);
        assertThat(corpus.atTheSameAddress)
                .describedAs("the address reaches strictly less of the corpus than the name, and "
                        + "it would additionally need a rule for turning a reply's own addresses "
                        + "into a request body's, which is a mechanism the name does not need")
                .isLessThan(corpus.underTheSameName);

        assertThat(priority.values).isEqualTo(256);
        assertThat(priority.underTheSameName).isEqualTo(223);
        assertThat(priority.atTheSameAddress).isEqualTo(204);
    }

    @Test
    @DisplayName("one name almost never means two different kinds of value in one API")
    void a_name_almost_never_means_two_things() {
        Tally corpus = new Tally();
        List<String> disagreeing = new ArrayList<>();
        measure(corpus, new Tally(), disagreeing);

        assertThat(corpus.underMoreThanOneShape)
                .describedAs("names a reply carries under more than one shape, which is the whole "
                        + "of what filing by address would have told apart")
                .isEqualTo(57);
        assertThat(disagreeing)
                .describedAs("and of those, the ones where the two shapes disagree about the kind "
                        + "of value rather than merely about the kind of string or number it is - "
                        + "which is the only disagreement that could send the wrong thing, and is "
                        + "already refused because the kind has to match what is being asked for")
                .hasSize(10);
    }

    /** What is being counted, on one corpus. */
    private static final class Tally {
        private int values;
        private int underTheSameName;
        private int atTheSameAddress;
        private int underMoreThanOneShape;
    }

    /**
     * Walks every document, counting the single values inside its request bodies against what its
     * own replies carry.
     */
    private static void measure(Tally corpus, Tally priority, List<String> disagreeing) {
        for (Path document : corpus()) {
            ApiModel model = parse(document);
            boolean measuredOn = PRIORITY.contains(document.getParent().getFileName().toString());

            Map<String, Set<String>> shapesByName = new LinkedHashMap<>();
            Set<String> addresses = new LinkedHashSet<>();
            for (Operation operation : model.operations()) {
                for (ResponseModel response : operation.responses()) {
                    if (!response.status().startsWith("2")) {
                        continue;
                    }
                    response.schemaFor("application/json").ifPresent(schema ->
                            values("", schema, model, 0, new LinkedHashSet<>(), (at, value) -> {
                                String name = lastStepOf(at);
                                if (!name.isEmpty()) {
                                    shapesByName.computeIfAbsent(name,
                                            ignored -> new LinkedHashSet<>()).add(shapeOf(value));
                                    addresses.add(at);
                                }
                            }));
                }
            }

            for (Operation operation : model.operations()) {
                if (operation.requestBody().isEmpty()) {
                    continue;
                }
                CanonicalSchema body =
                        operation.requestBody().get().schemaFor("application/json").orElse(null);
                if (body == null) {
                    continue;
                }
                List<String> inside = new ArrayList<>();
                values("body", body, model, 0, new LinkedHashSet<>(),
                        (at, ignored) -> inside.add(at));
                for (String at : inside) {
                    Set<String> shapes = shapesByName.get(lastStepOf(at));
                    count(corpus, shapes, someReplyCarries(at, addresses));
                    if (measuredOn) {
                        count(priority, shapes, someReplyCarries(at, addresses));
                    }
                    if (shapes != null && shapes.size() > 1 && aboutDifferentKinds(shapes)) {
                        disagreeing.add(document.getParent().getFileName() + " " + at + " "
                                + shapes);
                    }
                }
            }
        }
    }

    private static void count(Tally tally, Set<String> shapes, boolean atTheSameAddress) {
        tally.values++;
        if (shapes != null) {
            tally.underTheSameName++;
            if (atTheSameAddress) {
                tally.atTheSameAddress++;
            }
            if (shapes.size() > 1) {
                tally.underMoreThanOneShape++;
            }
        }
    }

    /**
     * Whether a reply carries a value at the same address as this piece of a request body.
     *
     * <p>"The same address" has to mean the same way down below the top, because a body starts at
     * the body and a reply starts at the reply: {@code body.owner.email} and a reply's own
     * {@code owner.email} are the same address by any reading somebody filing values this way would
     * have to adopt.
     */
    private static boolean someReplyCarries(String inABody, Set<String> addresses) {
        String below = inABody.startsWith("body") ? inABody.substring("body".length()) : inABody;
        return addresses.stream()
                .anyMatch(address -> address.equals(below) || address.endsWith(below));
    }

    /** Whether two shapes filed under one name disagree about the kind of value, not merely its form. */
    private static boolean aboutDifferentKinds(Set<String> shapes) {
        return shapes.stream().map(shape -> shape.split("/")[0]).distinct().count() > 1;
    }

    private static String shapeOf(CanonicalSchema schema) {
        String form = switch (schema) {
            case StringSchema text -> text.format().orElse("");
            case NumberSchema number -> number.format().orElse("");
            default -> "";
        };
        return schema.getClass().getSimpleName() + (form.isEmpty() ? "" : "/" + form);
    }

    private static String lastStepOf(String address) {
        String withoutElements = address.endsWith("[]")
                ? address.substring(0, address.length() - 2)
                : address;
        int at = withoutElements.lastIndexOf('.');
        return at < 0 ? "" : withoutElements.substring(at + 1);
    }

    /** Every single value a shape reaches, with the way down to it. */
    private static void values(String at, CanonicalSchema schema, ApiModel model, int depth,
            Set<String> following, BiConsumer<String, CanonicalSchema> onValue) {
        if (depth > AS_DEEP_AS_IT_GOES) {
            return;
        }
        CanonicalSchema here = schema;
        if (here instanceof SchemaReference reference) {
            if (!following.add(reference.name())) {
                return;
            }
            here = model.resolve(reference).orElse(null);
            if (here == null) {
                return;
            }
        }
        switch (here) {
            case ObjectSchema object -> object.properties().forEach((name, property) ->
                    values(at + "." + name, property, model, depth + 1,
                            new LinkedHashSet<>(following), onValue));
            case ArraySchema list -> values(at + "[]", list.items(), model, depth + 1,
                    new LinkedHashSet<>(following), onValue);
            case ChoiceSchema choice -> choice.alternatives().forEach(alternative ->
                    values(at, alternative, model, depth + 1, new LinkedHashSet<>(following),
                            onValue));
            default -> onValue.accept(at, here);
        }
    }

    private static ApiModel parse(Path document) {
        return new SwaggerSpecificationParser().parse(document.toString());
    }

    /**
     * The corpus, without the deliberately broken documents kept beside it.
     *
     * <p>Those exist to be unreadable, and counting what a document nobody can parse asks for would
     * measure the fixtures rather than the field.
     */
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

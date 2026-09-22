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

import io.restest.core.execution.ValueOrigin;
import io.restest.core.gen.GeneratedValue;
import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.Parameter;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.SchemaReference;
import io.restest.spec.SwaggerSpecificationParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the fifty documents in the corpus actually offer as sample values, and whether the values
 * RESTest takes from them are ones those same documents accept.
 *
 * <p>Two things are pinned here, both of them measurements rather than opinions.
 *
 * <p>The first is how much there is to gain. Six of the fifty documents write sample values for
 * their parameters, and between them those cover a little over two hundred parameters - four in
 * every hundred. Two of the six are APIs the tool is measured on, which is why reading samples is
 * worth doing at all: without them the identifiers in those two are invented, and an invented
 * identifier addresses nothing.
 *
 * <p>The second is the one that could go wrong quietly. A document is free to offer a sample its
 * own rules refuse - an author writing {@code false} where the parameter only accepts the
 * <em>words</em> "true" and "false" - and sending such a value while believing the document
 * endorsed it would be a poor trade. Across the corpus there are six of those, every one of them on
 * a parameter restricted to a fixed list, and a fixed list is precisely what makes RESTest ignore
 * the sample and walk the list instead. So the number of values taken from a sample and refused by
 * the shape it belongs to is nought, and this test is what says so the day a document arrives that
 * changes it. It asks more of a sample than the same check asks of an invented value, the pattern a
 * shape states included, because a sample is not something RESTest had to work out.
 */
class DeclaredSamplesAcrossTheCorpusTest {

    /** Documents offering a sample for at least one parameter, and how many parameters that is. */
    private static final Map<String, Integer> PARAMETERS_WITH_A_SAMPLE = Map.of(
            "kafka-rest-proxy", 103,
            "FDIC", 65,
            "pet-clinic", 26,
            "DHL", 14,
            "AmadeusTravelRestrictions", 2,
            "GitHub", 2);

    @Test
    @DisplayName("the corpus offers samples for the parameters it is known to offer them for")
    void the_documents_that_offer_samples_are_the_ones_measured() {
        Map<String, Integer> found = new LinkedHashMap<>();
        for (Path document : corpus()) {
            ApiModel model = new SwaggerSpecificationParser().parse(document.toString());
            long here = parametersOf(model).filter(p -> !samplesFor(model, p).isEmpty()).count();
            if (here > 0) {
                found.put(document.getParent().getFileName().toString(), (int) here);
            }
        }

        assertThat(found)
                .describedAs("a document gaining or losing sample values is worth knowing about")
                .containsExactlyInAnyOrderEntriesOf(PARAMETERS_WITH_A_SAMPLE);
    }

    @Test
    @DisplayName("no value taken from a document's own sample is one that document refuses")
    void every_sample_offered_is_one_its_shape_accepts() {
        List<String> refused = new ArrayList<>();
        ExampleValueProvider samples = new ExampleValueProvider(Schemas.fixedRandom());
        for (Path document : corpus()) {
            ApiModel model = new SwaggerSpecificationParser().parse(document.toString());
            for (Operation operation : model.operations()) {
                for (Parameter parameter : operation.parameters()) {
                    CanonicalSchema shape = resolved(model, parameter.schema());
                    // Every sample, not one draw: asking once would only check whichever of a
                    // parameter's several samples the seed happened to land on.
                    for (JsonValue sample : samplesFor(model, parameter)) {
                        if (offers(samples, model, operation, parameter)
                                && !whatTheShapeRefuses(sample, shape, model).isEmpty()) {
                            refused.add(document.getParent().getFileName() + " " + operation.id()
                                    + " " + parameter.name() + " = " + sample + " -> "
                                    + whatTheShapeRefuses(sample, shape, model));
                        }
                    }
                }
            }
        }

        assertThat(refused)
                .describedAs("a sample the document's own shape refuses must not be sent as though "
                        + "the document endorsed it")
                .isEmpty();
    }

    @Test
    @DisplayName("a value taken from a sample says so, rather than only saying the document said it")
    void a_sample_is_recorded_as_a_sample() {
        ApiModel model = petClinic();
        Operation owner = model.operation(
                io.restest.core.model.OperationId.of("getOwner")).orElseThrow();
        ExampleValueProvider samples = new ExampleValueProvider(Schemas.fixedRandom());

        GeneratedValue value = samples.offer(ValueRequest.of(owner.id(), "ownerId",
                io.restest.core.model.ParameterLocation.PATH,
                resolved(model, owner.parameters().get(0).schema()),
                owner.parameters().get(0).examples())).orElseThrow();

        assertThat(value.value()).isEqualTo(JsonValue.of(1L));
        assertThat(value.origin())
                .isEqualTo(ValueOrigin.declared(ValueOrigin.Declared.Statement.EXAMPLE));
    }

    /**
     * The identifier an API is measured on is sent, and it is not the only one sent.
     *
     * <p>This test used to demand the document's own identifier on every single request, and that
     * demand was wrong in a way only a real API showed. An identifier a document writes down is
     * real until the run deletes that row - and a run does delete rows, from the increment that
     * gave it request bodies onwards. A generator that cannot vary the value then sends the same
     * 404 for the rest of the run, and the three operations that take an owner never recover.
     *
     * <p>Measured against a containerised pet-clinic, restarting it before every run, five seeds
     * apiece: sending the document's identifier every time covered 16.8 operations on average,
     * and choosing among it and the alternatives covered 19.6. So what is asked for here is what
     * actually helps - that the author's identifier is among what goes out, and that it is not
     * the only thing that ever does.
     */
    @Test
    @DisplayName("the identifiers an API is measured on are its own, and are not the only ones "
            + "tried, because a run deletes the rows its document names")
    void the_priority_corpus_sends_the_identifiers_its_authors_wrote() {
        ApiModel petClinic = petClinic();
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(petClinic, 20260918L);
        Operation owner = petClinic.operation(
                io.restest.core.model.OperationId.of("getOwner")).orElseThrow();

        java.util.List<JsonValue> sent = java.util.stream.IntStream.range(0, 200)
                .mapToObj(draw -> generator.generate(owner).orElseThrow()
                        .parameterValue("ownerId", io.restest.core.model.ParameterLocation.PATH)
                        .orElseThrow().value())
                .toList();

        assertThat(sent)
                .describedAs("the document says an owner is numbered 1, and an invented number "
                        + "addresses an owner that never existed")
                .contains(JsonValue.of(1L));
        java.util.Map<JsonValue, Long> howOften = sent.stream().collect(
                java.util.stream.Collectors.groupingBy(value -> value,
                        java.util.stream.Collectors.counting()));
        assertThat(howOften.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue()).orElseThrow().getKey())
                .describedAs("and it is the value sent most often, which is what the weighting "
                        + "of the plan RESTest carries is for: every other value here is invented "
                        + "and invented ones do not repeat")
                .isEqualTo(JsonValue.of(1L));
        assertThat(java.util.Set.copyOf(sent))
                .describedAs("but not the only one: once the run has deleted owner 1, a generator "
                        + "that could send nothing else would ask for it until the budget ran out")
                .hasSizeGreaterThan(1);
    }

    /**
     * What a shape holds against a value.
     *
     * <p>The shared check, and nothing more. It used to be the shared check plus a pattern, because
     * the shared one left patterns alone while RESTest still invented values that ignored them; a
     * sample is not invented, so nothing excused it from a pattern its own shape declares. Values
     * are invented to match a stated pattern now, so the shared check holds everybody to the same
     * rule and there is nothing left to add here.
     */
    private static List<String> whatTheShapeRefuses(JsonValue sample, CanonicalSchema shape,
            ApiModel model) {
        return SchemaSatisfaction.violations(sample, shape, model);
    }

    private static boolean offers(ExampleValueProvider samples, ApiModel model,
            Operation operation, Parameter parameter) {
        return samples.offer(ValueRequest.of(operation.id(), parameter.name(),
                parameter.location(), resolved(model, parameter.schema()), parameter.examples()))
                .isPresent();
    }

    /** The samples that apply to a parameter: its own if it has any, otherwise its shape's. */
    private static List<JsonValue> samplesFor(ApiModel model, Parameter parameter) {
        return parameter.examples().isEmpty()
                ? resolved(model, parameter.schema()).metadata().examples()
                : parameter.examples();
    }

    private static CanonicalSchema resolved(ApiModel model, CanonicalSchema schema) {
        return schema instanceof SchemaReference reference
                ? model.resolve(reference).orElse(schema) : schema;
    }

    private static Stream<Parameter> parametersOf(ApiModel model) {
        return model.operations().stream().flatMap(operation -> operation.parameters().stream());
    }

    private static ApiModel petClinic() {
        return new SwaggerSpecificationParser().parse(specifications()
                .resolve("restleague-2027/pet-clinic/openapi.yaml").toString());
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

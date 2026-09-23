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
import io.restest.core.schema.StringSchema;
import io.restest.spec.SwaggerSpecificationParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The order the first round of a run goes in: lists, creations, single things, changes, deletions.
 */
class OpeningLapTest {

    private static final Operation LIST_PETS = Operation.of(HttpMethod.GET, "/pets");
    private static final Operation ADD_PET = Operation.of(HttpMethod.POST, "/pets");
    private static final Operation GET_PET = Operation.of(HttpMethod.GET, "/pets/{petId}",
            List.of(inThePath("petId")));
    private static final Operation REPLACE_PET = Operation.of(HttpMethod.PUT, "/pets/{petId}",
            List.of(inThePath("petId")));
    private static final Operation PATCH_PET = Operation.of(HttpMethod.PATCH, "/pets/{petId}",
            List.of(inThePath("petId")));
    private static final Operation DELETE_PET = Operation.of(HttpMethod.DELETE, "/pets/{petId}",
            List.of(inThePath("petId")));
    private static final Operation HEAD_PETS = Operation.of(HttpMethod.HEAD, "/pets");
    private static final Operation OPTIONS_PET = Operation.of(HttpMethod.OPTIONS, "/pets/{petId}",
            List.of(inThePath("petId")));
    private static final Operation ADD_VISIT = Operation.of(HttpMethod.POST,
            "/pets/{petId}/visits", List.of(inThePath("petId")));
    private static final Operation LIST_OWNERS = Operation.of(HttpMethod.GET, "/owners");

    @Test
    @DisplayName("the round goes lists, creations, single things, changes, deletions, and within a "
            + "step keeps the order the document declares")
    void the_steps_come_in_their_order() {
        // Declared in an order that is none of the steps', to show the steps decide it.
        List<Operation> declared = List.of(DELETE_PET, PATCH_PET, GET_PET, ADD_VISIT, LIST_PETS,
                REPLACE_PET, ADD_PET, OPTIONS_PET, HEAD_PETS, LIST_OWNERS);

        assertThat(OpeningLap.steps(declared)).containsExactly(
                List.of(LIST_PETS, HEAD_PETS, LIST_OWNERS),
                List.of(ADD_VISIT, ADD_PET),
                List.of(GET_PET, OPTIONS_PET),
                List.of(PATCH_PET, REPLACE_PET),
                List.of(DELETE_PET));
    }

    @Test
    @DisplayName("a step with nothing in it is left out rather than waited on for nothing")
    void empty_steps_are_left_out() {
        assertThat(OpeningLap.steps(List.of(GET_PET, LIST_PETS)))
                .containsExactly(List.of(LIST_PETS), List.of(GET_PET));
        assertThat(OpeningLap.steps(List.of(DELETE_PET))).containsExactly(List.of(DELETE_PET));
        assertThat(OpeningLap.steps(List.of())).isEmpty();
    }

    @Test
    @DisplayName("every operation is in exactly one step, and every method has a step")
    void every_method_has_a_step() {
        for (HttpMethod method : HttpMethod.values()) {
            Operation operation = Operation.of(method, "/things");
            assertThat(OpeningLap.steps(List.of(operation)))
                    .describedAs("%s", method)
                    .containsExactly(List.of(operation));
        }
        assertThat(OpeningLap.stepOf(Operation.of(HttpMethod.QUERY, "/things")))
                .isEqualTo(OpeningLap.Step.LISTS);
        assertThat(OpeningLap.stepOf(Operation.of(HttpMethod.TRACE, "/things/{id}",
                List.of(inThePath("id"))))).isEqualTo(OpeningLap.Step.SINGLE_THINGS);
    }

    @ParameterizedTest(name = "{0}: steps of {1}")
    @CsvSource({
            "flight-search,    18 8 8 2 4",
            "gestao-hospital,  2 5 8 3 2",
            "kafka-rest-proxy, 1 8 32 4 5",
            "notebook-manager, 1 1 1 1 1",
            "pet-clinic,       7 8 7 7 6",
    })
    @DisplayName("the five APIs the tool is measured on divide into the steps they were counted in")
    void the_priority_corpus_divides_as_counted(String api, String sizes) {
        List<List<Operation>> steps = OpeningLap.steps(parse(api).operations());

        assertThat(steps.stream().map(step -> String.valueOf(step.size())).toList())
                .containsExactly(sizes.trim().split(" "));
    }

    @Test
    @DisplayName("kafka's list of clusters goes alone and first, and everything that needs a "
            + "cluster's identifier waits for it")
    void kafka_asks_for_its_clusters_before_anything_needing_one() {
        List<List<Operation>> steps = OpeningLap.steps(parse("kafka-rest-proxy").operations());

        assertThat(steps.get(0)).extracting(Operation::path).containsExactly("/v3/clusters");
        assertThat(steps.subList(1, steps.size()))
                .allSatisfy(step -> assertThat(step).allSatisfy(operation ->
                        assertThat(operation.path()).contains("{cluster_id}")));
    }

    @Test
    @DisplayName("pet-clinic creates an owner before it asks for one, and deletes last")
    void pet_clinic_creates_before_it_reads_one_thing() {
        List<List<Operation>> steps = OpeningLap.steps(parse("pet-clinic").operations());

        assertThat(stepOf(steps, "addOwner")).isLessThan(stepOf(steps, "getOwner"));
        assertThat(steps.get(steps.size() - 1))
                .allSatisfy(operation -> assertThat(operation.method()).isEqualTo(HttpMethod.DELETE));
    }

    private static int stepOf(List<List<Operation>> steps, String operationId) {
        for (int step = 0; step < steps.size(); step++) {
            if (steps.get(step).stream().anyMatch(operation ->
                    operation.id().value().equals(operationId))) {
                return step;
            }
        }
        throw new AssertionError(operationId + " is in no step");
    }

    private static Parameter inThePath(String name) {
        return Parameter.of(name, ParameterLocation.PATH, true, StringSchema.of());
    }

    private static ApiModel parse(String api) {
        Path document = repositoryRoot()
                .resolve("restest-spec/src/test/resources/specifications/restleague-2027")
                .resolve(api.trim())
                .resolve("openapi.yaml");
        assertThat(document).exists();
        return new SwaggerSpecificationParser().parse(document.toString());
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

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
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.schema.StringSchema;
import io.restest.spec.SwaggerSpecificationParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Which operation creates the thing another operation's address needs, read off the addresses.
 */
class ProducersTest {

    @Nested
    @DisplayName("on the five APIs this tool is measured on")
    class OnThePriorityCorpus {

        @Test
        @DisplayName("pet-clinic: each thing is created at its own collection, a pet under its "
                + "owner, and a pet read on its own is still helped by the creation under an owner")
        void pet_clinic() {
            Map<String, String> pairs = pairsIn("pet-clinic");

            assertThat(pairs)
                    .containsEntry("GET /owners/{ownerId} ownerId", "POST /owners")
                    .containsEntry("DELETE /owners/{ownerId} ownerId", "POST /owners")
                    .containsEntry("POST /owners/{ownerId}/pets ownerId", "POST /owners")
                    .containsEntry("GET /owners/{ownerId}/pets/{petId} ownerId", "POST /owners")
                    .containsEntry("GET /owners/{ownerId}/pets/{petId} petId",
                            "POST /owners/{ownerId}/pets")
                    .containsEntry("GET /pets/{petId} petId", "POST /owners/{ownerId}/pets")
                    .containsEntry("GET /pettypes/{petTypeId} petTypeId", "POST /pettypes")
                    .containsEntry("PUT /visits/{visitId} visitId", "POST /visits")
                    .containsEntry("DELETE /vets/{vetId} vetId", "POST /vets")
                    .containsEntry("GET /specialties/{specialtyId} specialtyId",
                            "POST /specialties");
        }

        @Test
        @DisplayName("kafka-rest-proxy: every operation on one topic is helped by creating a "
                + "topic in the same cluster, and nothing creates a cluster")
        void kafka_rest_proxy() {
            Map<String, String> pairs = pairsIn("kafka-rest-proxy");

            assertThat(pairs)
                    .containsEntry("GET /v3/clusters/{cluster_id}/topics/{topic_name} topic_name",
                            "POST /v3/clusters/{cluster_id}/topics")
                    .containsEntry(
                            "DELETE /v3/clusters/{cluster_id}/topics/{topic_name} topic_name",
                            "POST /v3/clusters/{cluster_id}/topics")
                    .containsEntry("POST /v3/clusters/{cluster_id}/topics/{topic_name}/records "
                            + "topic_name", "POST /v3/clusters/{cluster_id}/topics");
            assertThat(pairs.keySet()).noneMatch(gap -> gap.endsWith(" cluster_id"));
            assertThat(pairs.keySet()).noneMatch(gap -> gap.endsWith(" consumer_group_id"));
        }

        @Test
        @DisplayName("notebook-manager: a notebook read, changed or deleted is created first")
        void notebook_manager() {
            assertThat(pairsIn("notebook-manager")).containsOnly(
                    Map.entry("GET /api/notebooks/{notebookId} notebookId", "POST /api/notebooks"),
                    Map.entry("DELETE /api/notebooks/{notebookId} notebookId",
                            "POST /api/notebooks"),
                    Map.entry("PATCH /api/notebooks/{notebookId} notebookId",
                            "POST /api/notebooks"));
        }

        @Test
        @DisplayName("gestao-hospital: a collection written with a closing slash still creates "
                + "for the address without one, whatever its gap is called")
        void gestao_hospital() {
            Map<String, String> pairs = pairsIn("gestao-hospital");

            assertThat(pairs)
                    .containsEntry("GET /v1/hospitais/{hospital_id} hospital_id",
                            "POST /v1/hospitais/")
                    .containsEntry("GET /v1/hospitais/{id}/leitos id", "POST /v1/hospitais/")
                    .containsEntry("GET /v1/hospitais/{hospital_id}/estoque/{produto_id} "
                            + "produto_id", "POST /v1/hospitais/{hospital_id}/estoque");
            assertThat(pairs.keySet()).noneMatch(gap -> gap.endsWith(" patientId"));
        }
    }

    @Nested
    @DisplayName("the rules")
    class Rules {

        @Test
        @DisplayName("the creation's gaps are paired with the ones in the same places, whatever "
                + "each address calls them")
        void shared_gaps_are_paired_by_place() {
            Operation create = operation(HttpMethod.POST, "/owners/{id}/pets", "id");
            Operation read = operation(HttpMethod.GET, "/owners/{ownerId}/pets/{petId}",
                    "ownerId", "petId");

            List<Producers.Need> needs = Producers.among(List.of(create, read)).of(read);

            assertThat(needs).singleElement().satisfies(need -> {
                assertThat(need.gap()).isEqualTo("petId");
                assertThat(need.producer()).isEqualTo(create);
                assertThat(need.sharedGaps()).containsExactly(Map.entry("ownerId", "id"));
            });
        }

        @Test
        @DisplayName("an operation never creates for itself, and only a POST creates")
        void only_another_post_creates() {
            Operation update = operation(HttpMethod.PUT, "/pets", "petId");
            Operation itself = operation(HttpMethod.POST, "/pets/{petId}", "petId");
            Operation read = operation(HttpMethod.GET, "/pets/{petId}", "petId");

            Producers producers = Producers.among(List.of(update, itself, read));

            assertThat(producers.of(itself)).isEmpty();
            assertThat(producers.of(read)).isEmpty();
        }

        @Test
        @DisplayName("an operation that cannot be attempted is never sent to create")
        void only_what_can_be_attempted() {
            ApiModel model = parse(specification("notebook-manager"));
            Campaign onlyReads = new Campaign(Campaigns.carried().strategies(),
                    WhichOperations.of(Set.of(HttpMethod.GET), List.of()));
            RandomTestCaseGenerator reads = new RandomTestCaseGenerator(model, 1L,
                    Dictionaries.fuzzing().map(List::of).orElse(List.of()), onlyReads);

            Producers producers = Producers.among(reads.testableOperations());

            assertThat(reads.testableOperations()).allSatisfy(operation ->
                    assertThat(producers.of(operation)).isEmpty());
        }

        @Test
        @DisplayName("a gap that is only part of a piece of the address is not one a creation "
                + "fills")
        void a_partial_gap_is_not_filled() {
            Operation create = operation(HttpMethod.POST, "/files");
            Operation read = operation(HttpMethod.GET, "/files/{name}.json", "name");

            assertThat(Producers.among(List.of(create, read)).of(read)).isEmpty();
        }
    }

    @Test
    @DisplayName("across the corpus, how many gaps some operation can create the thing for")
    void across_the_corpus() {
        Tally corpus = new Tally();
        Tally priority = new Tally();
        for (Path document : corpus()) {
            ApiModel model = parse(document);
            boolean measuredOn = PRIORITY.contains(document.getParent().getFileName().toString());
            List<Operation> testable = new RandomTestCaseGenerator(model, 1L)
                    .testableOperations();
            Producers producers = Producers.among(testable);
            for (Operation operation : testable) {
                for (String gap : wholeGapsIn(operation.path())) {
                    corpus.gaps++;
                    if (measuredOn) {
                        priority.gaps++;
                    }
                    producers.of(operation).stream().filter(need -> need.gap().equals(gap))
                            .findFirst().ifPresent(need -> {
                                boolean samePlace = samePlace(operation.path(), need);
                                corpus.count(samePlace);
                                if (measuredOn) {
                                    priority.count(samePlace);
                                }
                            });
                }
            }
        }

        assertThat(List.of(corpus.gaps, corpus.sameAddress, corpus.sameKind))
                .describedAs("gaps in the addresses of the operations the corpus lets a run "
                        + "attempt, each counted once per operation; those created at the same "
                        + "address; those created by kind")
                .containsExactly(1_835, 406, 98);
        assertThat(List.of(priority.gaps, priority.sameAddress, priority.sameKind))
                .describedAs("the same for the five: pet-clinic's /pets/{petId} is the three "
                        + "found by kind")
                .containsExactly(168, 68, 3);
    }

    /** What is being counted, on one corpus. */
    private static final class Tally {
        private int gaps;
        private int sameAddress;
        private int sameKind;

        void count(boolean samePlace) {
            if (samePlace) {
                sameAddress++;
            } else {
                sameKind++;
            }
        }
    }

    private static List<String> wholeGapsIn(String path) {
        return partsOf(path).stream()
                .filter(part -> part.length() > 2 && part.startsWith("{") && part.endsWith("}"))
                .map(part -> part.substring(1, part.length() - 1))
                .toList();
    }

    /** Whether the creation's address is this one cut before the gap, gaps matching gaps. */
    private static boolean samePlace(String path, Producers.Need need) {
        List<String> ours = partsOf(path.substring(0, path.indexOf("{" + need.gap() + "}")));
        List<String> theirs = partsOf(need.producer().path());
        if (ours.size() != theirs.size()) {
            return false;
        }
        for (int at = 0; at < ours.size(); at++) {
            boolean bothGaps = ours.get(at).startsWith("{") && theirs.get(at).startsWith("{");
            if (!bothGaps && !ours.get(at).equals(theirs.get(at))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> partsOf(String path) {
        return Stream.of(path.split("/")).filter(part -> !part.isEmpty()).toList();
    }

    /** The five APIs this tool is measured on. */
    private static final List<String> PRIORITY = List.of("flight-search", "gestao-hospital",
            "kafka-rest-proxy", "notebook-manager", "pet-clinic");

    /** Every gap of one priority API that something creates for, and what creates it. */
    private static Map<String, String> pairsIn(String api) {
        ApiModel model = parse(specification(api));
        List<Operation> testable = new RandomTestCaseGenerator(model, 1L).testableOperations();
        Producers producers = Producers.among(testable);
        Map<String, String> pairs = new TreeMap<>();
        for (Operation operation : testable) {
            for (Producers.Need need : producers.of(operation)) {
                pairs.put(operation.method() + " " + operation.path() + " " + need.gap(),
                        need.producer().method() + " " + need.producer().path());
            }
        }
        return pairs;
    }

    private static Operation operation(HttpMethod method, String path, String... gaps) {
        List<Parameter> parameters = new ArrayList<>();
        for (String gap : gaps) {
            parameters.add(Parameter.of(gap, ParameterLocation.PATH, true, StringSchema.of()));
        }
        return Operation.of(method, path, parameters)
                .withId(OperationId.of(method.name().toLowerCase() + path));
    }

    private static ApiModel parse(Path document) {
        return new SwaggerSpecificationParser().parse(document.toString());
    }

    private static Path specification(String api) {
        return specifications().resolve("restleague-2027").resolve(api).resolve("openapi.yaml");
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

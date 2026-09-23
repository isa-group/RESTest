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

import io.restest.core.event.RunEvent;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.Payload;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.model.ResponseModel;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.StringSchema;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one request an operation is most likely to accept, which is what a run sends each operation
 * first.
 */
class LikeliestRequestTest {

    private static final long SEED = 20260923L;

    /** One parameter the API insists on, four it merely accepts. */
    private static final Operation SEARCH = Operation.of(HttpMethod.GET, "/search", List.of(
            Parameter.of("q", ParameterLocation.QUERY, true, StringSchema.of()),
            Parameter.of("a", ParameterLocation.QUERY, false, StringSchema.of()),
            Parameter.of("b", ParameterLocation.QUERY, false, StringSchema.of()),
            Parameter.of("c", ParameterLocation.QUERY, false, StringSchema.of()),
            Parameter.of("d", ParameterLocation.QUERY, false, StringSchema.of())));

    /** A body the document describes without saying it must be sent, as most documents do. */
    private static final Operation ADD_PET = Operation.of(HttpMethod.POST, "/pets")
            .withRequestBody(RequestBodyModel.json(
                    ObjectSchema.of(Map.of("name", sampled("Leo")), Set.of("name")), false));

    @Test
    @DisplayName("it carries every parameter the API insists on, and nothing it merely accepts")
    void only_what_is_required() {
        RandomTestCaseGenerator generator = generatorFor(SEARCH);

        for (int draw = 0; draw < 200; draw++) {
            assertThat(generator.likeliestRequest(SEARCH).orElseThrow().parameterValues())
                    .extracting(ParameterValue::name)
                    .containsExactly("q");
        }
    }

    @Test
    @DisplayName("it carries a body wherever the document describes one, required or not")
    void a_described_body_is_always_sent() {
        RandomTestCaseGenerator generator = generatorFor(ADD_PET);
        int ordinaryWithoutABody = 0;

        for (int draw = 0; draw < 200; draw++) {
            assertThat(generator.likeliestRequest(ADD_PET).orElseThrow().body()).isPresent();
            if (generator.generate(ADD_PET).orElseThrow().body().isEmpty()) {
                ordinaryWithoutABody++;
            }
        }
        assertThat(ordinaryWithoutABody)
                .describedAs("the ordinary requests beside it still leave the body out sometimes")
                .isPositive();
    }

    @Test
    @DisplayName("but a body a GET merely accepts is left out, and a GET that insists on one is not "
            + "in the round at all")
    void a_get_carries_no_body_and_one_that_insists_is_not_sent() {
        Operation searchWithABody = Operation.of(HttpMethod.GET, "/pets/search")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of()), false));
        Operation searchThatInsists = Operation.of(HttpMethod.GET, "/pets/query")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of()), true));
        RandomTestCaseGenerator generator = generatorFor(searchWithABody, searchThatInsists);

        for (int draw = 0; draw < 20; draw++) {
            assertThat(generator.likeliestRequest(searchWithABody).orElseThrow().body()).isEmpty();
        }
        assertThat(generator.likeliestRequest(searchThatInsists))
                .describedAs("RESTest cannot send a body with a GET, so the operation is named "
                        + "among those that cannot be tested rather than given a request that "
                        + "could not go out")
                .isEmpty();
        assertThat(generator.untestableOperations()).containsKey(searchThatInsists.id());
    }

    @Test
    @DisplayName("a sample the document writes inside a body is what the body carries")
    void a_sample_inside_a_body_is_sent() {
        RandomTestCaseGenerator generator = generatorFor(ADD_PET);

        for (int draw = 0; draw < 50; draw++) {
            assertThat(generator.likeliestRequest(ADD_PET).orElseThrow().body().orElseThrow()
                    .value()).isEqualTo(JsonValue.object(Map.of("name", JsonValue.of("Leo"))));
        }
    }

    @Test
    @DisplayName("the document's own sample comes before a value invented to fit")
    void the_documents_sample_beats_an_invented_value() {
        Operation listOwners = Operation.of(HttpMethod.GET, "/owners", List.of(
                Parameter.of("lastName", ParameterLocation.QUERY, true, StringSchema.of())
                        .withExamples(List.of(JsonValue.of("Davis")))));
        RandomTestCaseGenerator generator = generatorFor(listOwners);
        Set<JsonValue> ordinary = new HashSet<>();

        for (int draw = 0; draw < 100; draw++) {
            assertThat(valueOf(generator.likeliestRequest(listOwners), "lastName"))
                    .isEqualTo(JsonValue.of("Davis"));
            ordinary.add(valueOf(generator.generate(listOwners), "lastName"));
        }
        assertThat(ordinary)
                .describedAs("an ordinary request, choosing among sources, sends other values too")
                .hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("a value the API has handed back comes before the document's own sample")
    void a_value_the_api_returned_beats_the_documents_sample() {
        // The case this ranking is for: a document whose sample identifier was never real, and an
        // API that generated the real one when it started and has just said what it is.
        Operation getCluster = Operation.of(HttpMethod.GET, "/v3/clusters/{cluster_id}",
                        List.of(Parameter.of("cluster_id", ParameterLocation.PATH, true,
                                StringSchema.of()).withExamples(List.of(JsonValue.of("cluster-1")))))
                .withId(OperationId.of("getKafkaCluster"))
                .withResponses(List.of(ResponseModel.json("200",
                        ObjectSchema.of(Map.of("cluster_id", StringSchema.of())))));
        RandomTestCaseGenerator generator = generatorFor(getCluster);
        assertThat(valueOf(generator.likeliestRequest(getCluster), "cluster_id"))
                .describedAs("before the API has said anything, the sample is the best there is")
                .isEqualTo(JsonValue.of("cluster-1"));

        generator.whatListensToTheRun().orElseThrow().on(new RunEvent.InteractionCompleted(
                Instant.EPOCH, answered("getKafkaCluster", "{\"cluster_id\": \"Xk3p9\"}")));

        ParameterValue sent = generator.likeliestRequest(getCluster).orElseThrow()
                .parameterValue("cluster_id", ParameterLocation.PATH).orElseThrow();
        assertThat(sent.value()).isEqualTo(JsonValue.of("Xk3p9"));
        assertThat(sent.origin()).isInstanceOf(ValueOrigin.Derived.class);
    }

    @Test
    @DisplayName("a closed list of accepted values is honoured before anything else")
    void a_closed_list_comes_first() {
        StringSchema threeStatuses = new StringSchema(SchemaMetadata.none()
                .withEnumeration(List.of(JsonValue.of("available"), JsonValue.of("pending"),
                        JsonValue.of("sold"))),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        Operation byStatus = Operation.of(HttpMethod.GET, "/pets/findByStatus", List.of(
                Parameter.of("status", ParameterLocation.QUERY, true, threeStatuses)));
        RandomTestCaseGenerator generator = generatorFor(byStatus);

        for (int draw = 0; draw < 50; draw++) {
            assertThat(valueOf(generator.likeliestRequest(byStatus), "status"))
                    .isIn(JsonValue.of("available"), JsonValue.of("pending"), JsonValue.of("sold"));
        }
    }

    @Test
    @DisplayName("a plan that does not ask for what the API returned never gets it here either")
    void a_plan_without_the_memory_never_uses_it() {
        Operation listOwners = Operation.of(HttpMethod.GET, "/owners", List.of(
                Parameter.of("lastName", ParameterLocation.QUERY, true, StringSchema.of())
                        .withExamples(List.of(JsonValue.of("Davis")))));
        Campaign documentOnly = new Campaign(List.of(new Campaign.PlannedStrategy("nominal", 100,
                List.of(new Campaign.Entry.Group(List.of(
                        new Campaign.Share(new Campaign.Source.Builtin(Campaign.Builtin.RANDOM), 90),
                        new Campaign.Share(new Campaign.Source.Builtin(Campaign.Builtin.EXAMPLE),
                                10)))))),
                WhichOperations.everything());
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model(listOwners), SEED,
                List.of(), documentOnly);

        assertThat(generator.whatListensToTheRun()).isEmpty();
        assertThat(generator.canBuildTheLikeliestRequest()).isTrue();
        for (int draw = 0; draw < 50; draw++) {
            assertThat(valueOf(generator.likeliestRequest(listOwners), "lastName"))
                    .describedAs("the plan weights invention far above the sample, and one request "
                            + "with one chance still carries the sample")
                    .isEqualTo(JsonValue.of("Davis"));
        }
    }

    @Test
    @DisplayName("asking for it changes nothing about the ordinary requests that follow")
    void asking_for_it_leaves_the_ordinary_requests_alone() {
        RandomTestCaseGenerator asked = generatorFor(SEARCH, ADD_PET);
        RandomTestCaseGenerator notAsked = generatorFor(SEARCH, ADD_PET);
        List<String> withIt = new ArrayList<>();
        List<String> without = new ArrayList<>();

        for (int draw = 0; draw < 40; draw++) {
            Operation operation = draw % 2 == 0 ? SEARCH : ADD_PET;
            asked.likeliestRequest(operation);
            withIt.add(RunOrderTest.written(asked.generate(operation)));
            without.add(RunOrderTest.written(notAsked.generate(operation)));
        }

        assertThat(withIt).isEqualTo(without);
    }

    @Test
    @DisplayName("a plan whose every way of building a request pushes at the API has no likeliest "
            + "request to offer")
    void a_plan_that_only_pushes_cannot_build_it() {
        RandomTestCaseGenerator onlyPushes = new RandomTestCaseGenerator(model(SEARCH), SEED,
                Dictionaries.fuzzing().map(List::of).orElse(List.of()), 100);

        assertThat(onlyPushes.canBuildTheLikeliestRequest()).isFalse();
        assertThat(onlyPushes.likeliestRequest(SEARCH)).isEmpty();
    }

    @Test
    @DisplayName("an operation that cannot be attempted has no likeliest request either")
    void an_untestable_operation_has_none() {
        Operation upload = Operation.of(HttpMethod.POST, "/pets/photo")
                .withRequestBody(RequestBodyModel.ofShapes(true,
                        Map.of("multipart/form-data", ObjectSchema.of(Map.of()))));
        RandomTestCaseGenerator generator = generatorFor(upload, SEARCH);

        assertThat(generator.untestableOperations()).containsKey(upload.id());
        assertThat(generator.likeliestRequest(upload)).isEmpty();
    }

    private static JsonValue valueOf(Optional<TestCase> testCase, String name) {
        return testCase.orElseThrow().parameterValues().stream()
                .filter(value -> value.name().equals(name))
                .findFirst()
                .orElseThrow()
                .value();
    }

    private static Interaction answered(String operation, String body) {
        return Interaction.answered(TestCase.of(OperationId.of(operation), List.of()),
                HttpRequestRecord.of(HttpMethod.GET, "https://api.example/v3/clusters/x"),
                new HttpResponseRecord(StatusLine.of(200),
                        List.of(Header.of("Content-Type", "application/json")),
                        Optional.of(Payload.text(body, "application/json"))),
                Instant.EPOCH, Duration.ofMillis(3));
    }

    private static StringSchema sampled(String sample) {
        return new StringSchema(SchemaMetadata.none().withExamples(List.of(JsonValue.of(sample))),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static RandomTestCaseGenerator generatorFor(Operation... operations) {
        return new RandomTestCaseGenerator(model(operations), SEED);
    }

    private static ApiModel model(Operation... operations) {
        return ApiModel.of("Test API", "1.0", List.of(operations));
    }
}

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

import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.TestCase;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.spec.SwaggerSpecificationParser;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Generation, against the five real APIs the tool is measured on.
 *
 * <p>Hand-written examples test what their author thought of. These five documents were written by
 * other people for their own purposes, and they are the ones that turn up the parameter nobody
 * expected: the one with no schema at all, the one whose name collides with a path segment, the one
 * required in the path but described as optional.
 *
 * <p>The counts below are pinned deliberately. 116 of these five APIs' 150 operations can be
 * attempted today; the other 34 all need a request body, which is not invented yet, and every one of
 * them is named with that reason rather than quietly skipped. If a change makes more operations
 * testable, that is good news and the number should be raised on purpose; if it makes fewer, that is
 * a regression this test exists to catch.
 */
class GoldenCorpusGenerationTest {

    @ParameterizedTest(name = "{0}: {1} of {2} operations can be attempted")
    @CsvSource({
            "flight-search,     31, 40",
            "gestao-hospital,   12, 20",
            "kafka-rest-proxy,  50, 50",
            "notebook-manager,   3,  5",
            "pet-clinic,        20, 35",
    })
    @DisplayName("every operation that can be attempted produces a request that could be sent")
    void every_testable_operation_produces_a_sendable_request(String api, int testable, int total) {
        ApiModel model = parse(api);
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model, 20260912L);

        assertThat(model.operations()).hasSize(total);
        assertThat(generator.testableOperations())
                .describedAs("operations that cannot be attempted: %s",
                        generator.untestableOperations())
                .hasSize(testable);

        for (Operation operation : generator.testableOperations()) {
            // Ten draws each: a value that only breaks its schema on some draws - a list that is
            // occasionally too short, a number occasionally off its step - would slip past one.
            for (int draw = 0; draw < 10; draw++) {
                checkOneDraw(model, generator, operation);
            }
        }
    }

    private static void checkOneDraw(ApiModel model, RandomTestCaseGenerator generator,
            Operation operation) {
        TestCase testCase = generator.generate(operation)
                .orElseThrow(() -> new AssertionError("no test case for " + operation.id()));
        assertEveryRequiredParameterIsFilled(operation, testCase);
        assertEveryValueSatisfiesItsSchema(model, operation, testCase);

        HttpRequestRecord request =
                RequestBuilder.build(operation, testCase, "http://localhost:8080");
        URI address = URI.create(request.url());
        assertThat(address.getHost())
                .describedAs("%s produced the address %s", operation.id(), request.url())
                .isEqualTo("localhost");
        assertThat(request.url()).doesNotContain("{").doesNotContain("}");
        assertThat(address.getPath())
                .describedAs("%s produced the address %s, which addresses something else",
                        operation.id(), request.url())
                .doesNotContain("//");
    }

    @ParameterizedTest(name = "{0} is generated the same way twice")
    @CsvSource({"flight-search", "gestao-hospital", "kafka-rest-proxy", "notebook-manager",
            "pet-clinic"})
    @DisplayName("the same starting number produces the same requests against a real API")
    void a_run_against_a_real_api_can_be_repeated_exactly(String api) {
        ApiModel model = parse(api);

        assertThat(urlsFrom(model, 4242L)).isEqualTo(urlsFrom(model, 4242L));
    }

    private static List<String> urlsFrom(ApiModel model, long seed) {
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model, seed);
        return java.util.stream.IntStream.range(0, 25)
                .mapToObj(i -> generator.generate().orElseThrow())
                .map(testCase -> RequestBuilder.build(
                        model.operation(testCase.operation()).orElseThrow(), testCase,
                        "http://localhost:8080").url())
                .toList();
    }

    /**
     * The assertion that matters most: not that a value was produced, but that the value is one the
     * specification actually permits. A generator that sent an empty string for everything would
     * satisfy every other check in this file.
     */
    private static void assertEveryValueSatisfiesItsSchema(ApiModel model, Operation operation,
            TestCase testCase) {
        for (ParameterValue value : testCase.parameterValues()) {
            Parameter parameter = operation.parameter(value.name(), value.location()).orElseThrow();
            assertThat(SchemaSatisfaction.violations(value.value(), parameter.schema(), model))
                    .describedAs("%s sent '%s' as %s", operation.id(), value.value(), value.name())
                    .isEmpty();
        }
    }

    private static void assertEveryRequiredParameterIsFilled(Operation operation,
            TestCase testCase) {
        for (Parameter parameter : operation.parameters()) {
            if (parameter.required()) {
                assertThat(testCase.parameterValue(parameter.name(), parameter.location()))
                        .describedAs("%s requires '%s'", operation.id(), parameter.name())
                        .isPresent()
                        .get()
                        .extracting(ParameterValue::origin).isNotNull();
            }
        }
    }

    @Test
    @DisplayName("an operation whose parameter may be a number or a text is attempted, not skipped")
    void a_parameter_offering_a_choice_of_shapes_no_longer_costs_its_operation() {
        // GitHub's workflow_id is declared `oneOf: [integer, string]` in a path, which is the only
        // place in fifty documents where a choice decides whether a request is sent at all: six
        // operations share that parameter, and a required parameter RESTest cannot read costs the
        // whole operation. Everything else a choice touches in the corpus is a response body.
        Path document = repositoryRoot()
                .resolve("restest-spec/src/test/resources/specifications/community/GitHub")
                .resolve("openapi.yaml");
        assertThat(document).exists();
        ApiModel model = new SwaggerSpecificationParser().parse(document.toString());
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model, 20260917L);

        assertThat(generator.untestableOperations()).isEmpty();
        for (String id : List.of("actions/get-workflow", "actions/disable-workflow",
                "actions/enable-workflow", "actions/create-workflow-dispatch",
                "actions/list-workflow-runs", "actions/get-workflow-usage")) {
            Operation operation = model.operation(OperationId.of(id)).orElseThrow();
            TestCase testCase = generator.generate(operation)
                    .orElseThrow(() -> new AssertionError("no test case for " + id));
            assertThat(testCase.parameterValues())
                    .describedAs("%s should carry a value for workflow_id", id)
                    .anySatisfy(value -> assertThat(value.name()).isEqualTo("workflow_id"));
            assertEveryValueSatisfiesItsSchema(model, operation, testCase);
        }
    }

    private static ApiModel parse(String api) {
        Path document = repositoryRoot()
                .resolve("restest-spec/src/test/resources/specifications/restleague-2027")
                .resolve(api.trim())
                .resolve("openapi.yaml");
        assertThat(document).exists();
        ApiModel model = new SwaggerSpecificationParser().parse(document.toString());
        assertThat(model.operations()).isNotEmpty();
        return model;
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

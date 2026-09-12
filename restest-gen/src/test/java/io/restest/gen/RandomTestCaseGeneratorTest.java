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

import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.ParameterStyle;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.StringSchema;
import io.restest.core.schema.UnsupportedSchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Deciding what to try against an API, without anybody having configured anything. */
class RandomTestCaseGeneratorTest {

    private static final Operation LIST_PETS = Operation.of(HttpMethod.GET, "/pets", List.of(
            Parameter.of("status", ParameterLocation.QUERY, true, StringSchema.of()),
            Parameter.of("limit", ParameterLocation.QUERY, false, StringSchema.of())));

    @Test
    @DisplayName("every parameter the API requires is given a value")
    void required_parameters_are_always_filled_in() {
        RandomTestCaseGenerator generator = generatorFor(LIST_PETS);

        TestCase testCase = generator.generate(LIST_PETS).orElseThrow();

        assertThat(testCase.parameterValue("status", ParameterLocation.QUERY)).isPresent();
        assertThat(testCase.operation()).isEqualTo(LIST_PETS.id());
    }

    @Test
    @DisplayName("a parameter the API does not require is sometimes sent and sometimes not")
    void optional_parameters_vary() {
        RandomTestCaseGenerator generator = generatorFor(LIST_PETS);

        List<Boolean> sent = IntStream.range(0, 40)
                .mapToObj(i -> generator.generate(LIST_PETS).orElseThrow())
                .map(testCase -> testCase.parameterValue("limit", ParameterLocation.QUERY)
                        .isPresent())
                .toList();

        assertThat(sent).describedAs("an API behaves differently depending on which optional "
                        + "parameters arrive, so a run has to try it both ways")
                .contains(true).contains(false);
    }

    @Test
    @DisplayName("every value in a test case says where it came from")
    void every_value_carries_its_origin() {
        Operation withDeclaredValues = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("status", ParameterLocation.QUERY, true, new StringSchema(
                        SchemaMetadata.none().withEnumeration(List.of(JsonValue.of("available"))),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())),
                Parameter.of("name", ParameterLocation.QUERY, true, StringSchema.of())));

        TestCase testCase = generatorFor(withDeclaredValues).generate(withDeclaredValues)
                .orElseThrow();

        assertThat(testCase.parameterValue("status", ParameterLocation.QUERY).orElseThrow()
                .origin()).isEqualTo(ValueOrigin.DECLARED);
        assertThat(testCase.parameterValue("name", ParameterLocation.QUERY).orElseThrow().origin())
                .isEqualTo(new ValueOrigin.Generated("random"));
        assertThat(testCase.parameterValues()).allSatisfy(value ->
                assertThat(value.origin()).isNotNull());
    }

    @Test
    @DisplayName("the same starting number produces the same run, which is what makes a surprise "
            + "worth investigating")
    void the_same_seed_produces_the_same_test_cases() {
        List<String> first = run(1234L);
        List<String> second = run(1234L);
        List<String> other = run(4321L);

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(other);
    }

    @Test
    @DisplayName("an operation that needs a body cannot be attempted yet, and says so by name")
    void an_operation_needing_a_body_is_reported() {
        Operation createPet = Operation.of(HttpMethod.POST, "/pets")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of()), true));
        RandomTestCaseGenerator generator = generatorFor(LIST_PETS, createPet);

        assertThat(generator.testableOperations()).containsExactly(LIST_PETS);
        assertThat(generator.untestableOperations())
                .containsOnlyKeys(createPet.id())
                .extractingByKey(createPet.id(), org.assertj.core.api.InstanceOfAssertFactories
                        .STRING).contains("requires a request body");
    }

    @Test
    @DisplayName("an operation whose parameters cannot be written into a request is reported")
    void an_operation_that_cannot_be_assembled_is_reported() {
        Operation deepObject = Operation.of(HttpMethod.GET, "/search", List.of(
                new Parameter("filter", ParameterLocation.QUERY, false, ObjectSchema.of(Map.of()),
                        ParameterStyle.DEEP_OBJECT, true, Optional.empty(), Optional.empty())));

        assertThat(generatorFor(deepObject).untestableOperations())
                .containsOnlyKeys(deepObject.id());
    }

    @Test
    @DisplayName("an operation requiring a value nobody could invent is reported, not attempted")
    void an_operation_needing_an_unreadable_value_is_reported() {
        Operation unreadable = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("filter", ParameterLocation.QUERY, true,
                        UnsupportedSchema.of("oneOf is not folded in yet"))));

        assertThat(generatorFor(unreadable).untestableOperations())
                .extractingByKey(unreadable.id(), org.assertj.core.api.InstanceOfAssertFactories
                        .STRING).contains("could not be read");
    }

    @Test
    @DisplayName("an optional parameter nobody can invent a value for is simply left out")
    void an_optional_parameter_nobody_can_fill_is_omitted() {
        Operation operation = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("filter", ParameterLocation.QUERY, false,
                        UnsupportedSchema.of("oneOf is not folded in yet")),
                Parameter.of("status", ParameterLocation.QUERY, true, StringSchema.of())));
        RandomTestCaseGenerator generator = generatorFor(operation);

        assertThat(generator.testableOperations()).containsExactly(operation);
        TestCase testCase = generator.generate(operation).orElseThrow();
        assertThat(testCase.parameterValue("filter", ParameterLocation.QUERY)).isEmpty();
        assertThat(testCase.parameterValue("status", ParameterLocation.QUERY)).isPresent();
    }

    @Test
    @DisplayName("an operation is picked at random from the ones that can be attempted")
    void an_operation_is_chosen_among_the_testable_ones() {
        Operation pets = Operation.of(HttpMethod.GET, "/pets");
        Operation stores = Operation.of(HttpMethod.GET, "/stores");
        RandomTestCaseGenerator generator = generatorFor(pets, stores);

        List<OperationId> chosen = IntStream.range(0, 30)
                .mapToObj(i -> generator.generate().orElseThrow().operation())
                .distinct()
                .toList();

        assertThat(chosen).containsExactlyInAnyOrder(pets.id(), stores.id());
    }

    @Test
    @DisplayName("an API with nothing that can be attempted says so instead of failing")
    void an_api_with_nothing_testable_generates_nothing() {
        Operation needsBody = Operation.of(HttpMethod.POST, "/pets")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of()), true));

        assertThat(generatorFor(needsBody).generate()).isEmpty();
    }

    @Test
    @DisplayName("two generators in one program do not disturb each other")
    void two_generators_are_independent() {
        RandomTestCaseGenerator one = new RandomTestCaseGenerator(model(LIST_PETS), 99L);
        RandomTestCaseGenerator two = new RandomTestCaseGenerator(model(LIST_PETS), 99L);

        one.generate(LIST_PETS);
        one.generate(LIST_PETS);
        String fromOne = statusIn(one.generate(LIST_PETS).orElseThrow());

        two.generate(LIST_PETS);
        two.generate(LIST_PETS);
        String fromTwo = statusIn(two.generate(LIST_PETS).orElseThrow());

        assertThat(fromOne).isEqualTo(fromTwo);
        assertThat(one.seed()).isEqualTo(99L);
    }

    @Test
    @DisplayName("an operation already reported as untestable is not quietly attempted anyway")
    void an_untestable_operation_is_not_attempted() {
        Operation createPet = Operation.of(HttpMethod.POST, "/pets")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of()), true));
        RandomTestCaseGenerator generator = generatorFor(createPet);

        assertThat(generator.generate(createPet))
                .describedAs("a body-less POST to an operation that requires one is a request "
                        + "nobody could send, not a test")
                .isEmpty();
    }

    @Test
    @DisplayName("an operation whose required parameter nothing can fill is reported, not attempted "
            + "on every draw and silently failing")
    void an_operation_nobody_can_fill_is_reported() {
        Operation huge = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("token", ParameterLocation.QUERY, true, new io.restest.core.schema
                        .StringSchema(SchemaMetadata.none(), Optional.of(50_000), Optional.empty(),
                        Optional.empty(), Optional.empty()))));
        RandomTestCaseGenerator generator = generatorFor(huge);

        assertThat(generator.testableOperations()).isEmpty();
        assertThat(generator.untestableOperations())
                .extractingByKey(huge.id(), org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("no value could be found");
    }

    private List<String> run(long seed) {
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model(LIST_PETS), seed);
        return IntStream.range(0, 10)
                .mapToObj(i -> generator.generate().orElseThrow())
                .map(RandomTestCaseGeneratorTest::describe)
                .toList();
    }

    private static String describe(TestCase testCase) {
        return testCase.operation() + " " + testCase.parameterValues().stream()
                .map(value -> value.name() + "=" + value.value())
                .toList();
    }

    private static String statusIn(TestCase testCase) {
        return testCase.parameterValue("status", ParameterLocation.QUERY)
                .map(ParameterValue::value).map(Object::toString).orElse("");
    }

    private static RandomTestCaseGenerator generatorFor(Operation... operations) {
        return new RandomTestCaseGenerator(model(operations), 20260912L);
    }

    private static ApiModel model(Operation... operations) {
        return ApiModel.of("Test API", "1.0", List.of(operations));
    }
}

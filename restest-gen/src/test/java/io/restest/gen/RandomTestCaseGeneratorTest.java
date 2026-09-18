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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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

    /**
     * The operations seed 20260912 picks, in order, from three that carry no parameters.
     *
     * <p>Recorded from a run, not derived from anything. It is the seed's meaning written down.
     */
    private static final String PINNED_ORDER =
            "stores stores vets stores stores vets pets vets vets stores "
            + "stores vets stores pets pets stores stores vets stores stores";

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
                .origin()).isEqualTo(
                        ValueOrigin.declared(ValueOrigin.Declared.Statement.ENUMERATION));
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
    @DisplayName("a number is tied to one source of randomness, so changing that source is a "
            + "decision somebody has to take on purpose")
    void the_run_a_seed_produces_is_pinned_to_one_source_of_randomness() {
        Operation pets = Operation.of(HttpMethod.GET, "/pets");
        Operation stores = Operation.of(HttpMethod.GET, "/stores");
        Operation vets = Operation.of(HttpMethod.GET, "/vets");

        String chosen = order(generatorFor(pets, stores, vets));

        // Written out rather than computed, which is the whole point: a computed expectation would
        // agree with whatever the code does. These three operations carry no parameters, so nothing
        // but the choice of operation draws on the run's randomness - which keeps this sentence
        // about the source of randomness alone, and leaves it untouched by every later change to
        // how values are invented.
        assertThat(chosen)
                .describedAs("A number printed by a run means the run it produced, and it means "
                        + "that on somebody else's machine too. Swap the source of randomness, or "
                        + "disturb how the number reaches it, and every seed ever written down - in "
                        + "a bug report, in a paper, in a build log - quietly names a different "
                        + "run. That is a decision to take deliberately and write down, so this "
                        + "test exists to make it impossible to take by accident.")
                .isEqualTo(PINNED_ORDER);
    }

    /** Which operation each attempt picked, as one line. */
    private static String order(RandomTestCaseGenerator generator) {
        return IntStream.range(0, 20)
                .mapToObj(attempt -> generator.generate().orElseThrow().operation().toString())
                .map(operation -> operation.substring(operation.lastIndexOf('/') + 1))
                .collect(java.util.stream.Collectors.joining(" "));
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
    @DisplayName("an operation whose sample identifier is nothing at all still sends requests")
    void a_sample_that_is_nothing_does_not_cost_an_operation_its_whole_budget() {
        Operation getOwner = Operation.of(HttpMethod.GET, "/owners/{ownerId}", List.of(
                Parameter.of("ownerId", ParameterLocation.PATH, true, StringSchema.of())
                        .withExamples(List.of(JsonValue.NULL))));
        RandomTestCaseGenerator generator = generatorFor(getOwner);

        assertThat(generator.untestableOperations()).isEmpty();
        for (int draw = 0; draw < 20; draw++) {
            TestCase testCase = generator.generate(getOwner).orElseThrow();
            // Assembling is what would throw: an empty piece of a path closes the gap instead of
            // filling it, and every request for the operation would be thrown away unattributed.
            assertThat(RequestBuilder.build(getOwner, testCase, "http://localhost:8080").url())
                    .startsWith("http://localhost:8080/owners/")
                    .isNotEqualTo("http://localhost:8080/owners/");
        }
    }

    @Test
    @DisplayName("an operation whose parameters cannot be written into a request is reported")
    void an_operation_that_cannot_be_assembled_is_reported() {
        Operation deepObject = Operation.of(HttpMethod.GET, "/search", List.of(
                new Parameter("filter", ParameterLocation.QUERY, false, ObjectSchema.of(Map.of()),
                        ParameterStyle.DEEP_OBJECT, true, Optional.empty(), Optional.empty(),
                        List.of())));

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

    @Test
    @DisplayName("the operations that cannot be tested are listed in the order the document declares them")
    void untestable_operations_keep_document_order() {
        Operation[] refused = new Operation[6];
        for (int i = 0; i < refused.length; i++) {
            refused[i] = Operation.of(HttpMethod.GET, "/path" + i, List.of(
                    Parameter.of("filter", ParameterLocation.QUERY, true,
                            UnsupportedSchema.of("oneOf is not folded in yet"))));
        }

        // The command line quotes the first of these as its example of what is wrong with the
        // document, so the order is not cosmetic: a map whose order is decided per process would
        // make the same command explain itself differently from one run to the next.
        assertThat(generatorFor(refused).untestableOperations().keySet())
                .containsExactly(refused[0].id(), refused[1].id(), refused[2].id(),
                        refused[3].id(), refused[4].id(), refused[5].id());
    }

    @Test
    @DisplayName("an object with nothing declared in it can never fill a gap in a path")
    void an_empty_object_in_a_path_never_assembles() {
        Operation operation = Operation.of(HttpMethod.GET, "/pets/{petId}", List.of(
                Parameter.of("petId", ParameterLocation.PATH, true, ObjectSchema.of(Map.of()))));
        RandomTestCaseGenerator generator = generatorFor(operation);

        // A value is found, so the operation is offered as testable, and that is the whole of the
        // problem: the value is always the empty object, which writes as nothing, and a gap in a
        // path filled with nothing addresses the collection instead of the item. Every draw, not
        // most of them - an object with no declared properties has nothing to put inside it - so
        // this operation is counted among the ones that can be tested and is then never tested.
        //
        // Pinned rather than fixed. Deciding a value exists and deciding it can be written into a
        // web address are two different questions, and the second one belongs with the work that
        // makes values fit their place. Written down here so that it is a known gap rather than a
        // surprise, and so that closing it has a test waiting.
        assertThat(generator.testableOperations()).containsExactly(operation);
        for (int draw = 0; draw < 20; draw++) {
            TestCase testCase = generator.generate(operation).orElseThrow();
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> RequestBuilder.build(operation, testCase,
                            "https://api.example"))
                    .withMessageContaining("empty");
        }
    }

    private static RandomTestCaseGenerator generatorFor(Operation... operations) {
        return new RandomTestCaseGenerator(model(operations), 20260912L);
    }

    private static ApiModel model(Operation... operations) {
        return ApiModel.of("Test API", "1.0", List.of(operations));
    }
}

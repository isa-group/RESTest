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
package io.restest.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.restest.core.json.JsonValue;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestCaseTest {

    private static final OperationId GET_PET = OperationId.synthesised(HttpMethod.GET,
            "/pets/{petId}");

    @Test
    @DisplayName("a test case can be built for an operation with a value for each parameter")
    void a_test_case_is_built_from_parameter_values() {
        ParameterValue petId = ParameterValue.of("petId", ParameterLocation.PATH,
                JsonValue.of(42L), ValueOrigin.DECLARED);

        TestCase testCase = TestCase.of(GET_PET, List.of(petId));

        assertThat(testCase.operation()).isEqualTo(GET_PET);
        assertThat(testCase.parameterValue("petId", ParameterLocation.PATH)).contains(petId);
        assertThat(testCase.parameterValue("petId", ParameterLocation.QUERY)).isEmpty();
        assertThat(testCase.parameterValue("other", ParameterLocation.PATH)).isEmpty();
        assertThat(testCase.body()).isEmpty();
    }

    @Test
    @DisplayName("a body can be supplied alongside the parameter values")
    void a_test_case_can_carry_a_body() {
        BodyValue body = new BodyValue("application/json", JsonValue.of("payload"),
                new ValueOrigin.Generated("random"));

        TestCase testCase = TestCase.of(GET_PET, List.of(), body);

        assertThat(testCase.body()).contains(body);
    }

    @Test
    @DisplayName("each test case gets its own identifier")
    void identifiers_are_not_shared() {
        TestCase first = TestCase.of(GET_PET, List.of());
        TestCase second = TestCase.of(GET_PET, List.of());

        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    @DisplayName("one parameter given two values in one location is refused")
    void a_parameter_given_two_values_is_refused() {
        List<ParameterValue> twice = List.of(
                ParameterValue.of("limit", ParameterLocation.QUERY, JsonValue.of(1L),
                        ValueOrigin.DECLARED),
                ParameterValue.of("limit", ParameterLocation.QUERY, JsonValue.of(2L),
                        new ValueOrigin.Generated("random")));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> TestCase.of(GET_PET, twice))
                .withMessageContaining("limit")
                .withMessageContaining("QUERY");
    }

    @Test
    @DisplayName("the same name in two locations is two different parameter values, and is allowed")
    void a_name_can_repeat_across_locations() {
        List<ParameterValue> values = List.of(
                ParameterValue.of("id", ParameterLocation.PATH, JsonValue.of(1L),
                        ValueOrigin.DECLARED),
                ParameterValue.of("id", ParameterLocation.QUERY, JsonValue.of(2L),
                        ValueOrigin.DECLARED));

        TestCase testCase = TestCase.of(GET_PET, values);

        assertThat(testCase.parameterValue("id", ParameterLocation.PATH).orElseThrow().value())
                .isEqualTo(JsonValue.of(1L));
        assertThat(testCase.parameterValue("id", ParameterLocation.QUERY).orElseThrow().value())
                .isEqualTo(JsonValue.of(2L));
    }

    @Test
    @DisplayName("a stateful step's value names the interaction it was read from")
    void a_stateful_value_names_its_source_interaction() {
        InteractionId createdPet = InteractionId.generate();
        ParameterValue petId = ParameterValue.of("petId", ParameterLocation.PATH,
                JsonValue.of(7L), new ValueOrigin.Derived(createdPet,
                        "response body field 'id'"));

        TestCase deletePet = TestCase.of(
                OperationId.synthesised(HttpMethod.DELETE, "/pets/{petId}"), List.of(petId));

        ValueOrigin origin = deletePet.parameterValue("petId", ParameterLocation.PATH)
                .orElseThrow().origin();
        assertThat(origin).isInstanceOf(ValueOrigin.Derived.class);
        assertThat(((ValueOrigin.Derived) origin).from()).isEqualTo(createdPet);
    }

    @Test
    @DisplayName("changing the list afterwards does not change the test case")
    void parameter_values_are_copied() {
        List<ParameterValue> values = new ArrayList<>(List.of(
                ParameterValue.of("limit", ParameterLocation.QUERY, JsonValue.of(1L),
                        ValueOrigin.DECLARED)));

        TestCase testCase = TestCase.of(GET_PET, values);
        values.clear();

        assertThat(testCase.parameterValues()).hasSize(1);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> testCase.parameterValues().clear());
    }

    @Test
    @DisplayName("a test case built from nothing expects nothing in particular unless told")
    void a_fresh_test_case_expects_nothing_in_particular() {
        TestCase testCase = TestCase.of(GET_PET, List.of());

        assertThat(testCase.intent()).isEqualTo(Intent.UNKNOWN);
        assertThat(testCase.mutation()).isEmpty();
        assertThat(TestCase.of(GET_PET, List.of(), Optional.empty(), Intent.PUSHING).intent())
                .isEqualTo(Intent.PUSHING);
    }

    @Test
    @DisplayName("a test case made by changing an accepted one says what was changed")
    void a_changed_test_case_names_its_change() {
        Mutation change = new Mutation(InteractionId.generate(), "outsideABound",
                ParameterLocation.PATH, "petId", "sent -1, one below the smallest allowed");

        TestCase changed = TestCase.changed(GET_PET, List.of(ParameterValue.of("petId",
                ParameterLocation.PATH, JsonValue.of(-1L), new ValueOrigin.Generated(
                        "outsideABound"))), Optional.empty(), Intent.REFUSAL_EXPECTED, change);

        assertThat(changed.intent()).isEqualTo(Intent.REFUSAL_EXPECTED);
        assertThat(changed.mutation()).contains(change);
        assertThat(TestCase.changed(GET_PET, List.of(), Optional.empty(), Intent.UNKNOWN, change)
                .intent())
                .describedAs("a change the document does not rule on expects nothing")
                .isEqualTo(Intent.UNKNOWN);
    }

    @Test
    @DisplayName("expecting a refusal without saying what was broken is refused")
    void expecting_a_refusal_needs_a_change() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TestCase.of(GET_PET, List.of(), Optional.empty(),
                        Intent.REFUSAL_EXPECTED))
                .withMessageContaining("says what it broke");
    }

    @Test
    @DisplayName("the first step of a series follows nothing, so it has no reason to expect a "
            + "refusal")
    void a_first_step_cannot_expect_a_refusal() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TestCase.stepOf(GET_PET, List.of(), Optional.empty(),
                        Intent.REFUSAL_EXPECTED, SequenceStep.first("readAfterDelete",
                                "create a pet to read after deleting it")))
                .withMessageContaining("names neither");
    }

    @Test
    @DisplayName("a changed test case cannot claim to be believed in, nor to be awkward throughout")
    void a_changed_test_case_claims_neither_acceptance_nor_pushing() {
        Mutation change = new Mutation(InteractionId.generate(), "dropRequired",
                ParameterLocation.QUERY, "limit", "left out 'limit'");

        for (Intent claimed : List.of(Intent.ACCEPTABLE, Intent.PUSHING)) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> TestCase.changed(GET_PET, List.of(), Optional.empty(),
                            claimed, change))
                    .withMessageContaining(claimed.name());
        }
    }

    @Test
    @DisplayName("a step of a series says where it stands, and may expect a refusal for it")
    void a_step_of_a_series_says_where_it_stands() {
        InteractionId creation = InteractionId.generate();
        InteractionId deletion = InteractionId.generate();
        SequenceStep afterTheDeletion = new SequenceStep("readAfterDelete", 4,
                List.of(creation, deletion), "read the pet the API said it deleted");

        TestCase read = TestCase.stepOf(GET_PET, List.of(ParameterValue.of("petId",
                ParameterLocation.PATH, JsonValue.of(12L), new ValueOrigin.Derived(creation,
                        "the 'id' of what POST /pets created, earlier in this series"))),
                Optional.empty(), Intent.REFUSAL_EXPECTED, afterTheDeletion);

        assertThat(read.sequence()).contains(afterTheDeletion);
        assertThat(read.mutation()).isEmpty();
        assertThat(read.intent())
                .describedAs("the deletion the step follows is the reason a refusal is expected")
                .isEqualTo(Intent.REFUSAL_EXPECTED);
        assertThat(TestCase.of(GET_PET, List.of()).sequence())
                .describedAs("a request built on its own stands alone")
                .isEmpty();
    }

    @Test
    @DisplayName("a step of a series claims neither acceptance nor pushing")
    void a_step_claims_neither_acceptance_nor_pushing() {
        SequenceStep first = SequenceStep.first("createTwice", "create a pet");

        for (Intent claimed : List.of(Intent.ACCEPTABLE, Intent.PUSHING)) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> TestCase.stepOf(GET_PET, List.of(), Optional.empty(),
                            claimed, first))
                    .withMessageContaining(claimed.name());
        }
    }

    @Test
    @DisplayName("a test case is a change to an accepted request or a step of a series, not both")
    void a_change_and_a_step_do_not_mix() {
        Mutation change = new Mutation(InteractionId.generate(), "dropRequired",
                ParameterLocation.QUERY, "limit", "left out 'limit'");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TestCase(TestCaseId.generate(), GET_PET, List.of(),
                        Optional.empty(), Intent.REFUSAL_EXPECTED, Optional.of(change),
                        Optional.of(SequenceStep.first("createTwice", "create a pet"))))
                .withMessageContaining("not both");
    }

    @Test
    @DisplayName("a change names its kind, its place and itself in words")
    void a_change_is_described_completely() {
        InteractionId accepted = InteractionId.generate();

        assertThatIllegalArgumentException().isThrownBy(() -> new Mutation(accepted, " ",
                ParameterLocation.QUERY, "limit", "left out 'limit'"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Mutation(accepted,
                "dropRequired", ParameterLocation.QUERY, "", "left out 'limit'"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Mutation(accepted,
                "dropRequired", ParameterLocation.QUERY, "limit", " "));
    }
}

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
    @DisplayName("a stateful step's value names the earlier test case it depends on")
    void a_stateful_value_names_its_source_test_case() {
        TestCase createPet = TestCase.of(OperationId.of("addPet"), List.of());
        ParameterValue petId = ParameterValue.of("petId", ParameterLocation.PATH,
                JsonValue.of(7L), new ValueOrigin.Derived(createPet.id(),
                        "response body field 'id'"));

        TestCase deletePet = TestCase.of(
                OperationId.synthesised(HttpMethod.DELETE, "/pets/{petId}"), List.of(petId));

        ValueOrigin origin = deletePet.parameterValue("petId", ParameterLocation.PATH)
                .orElseThrow().origin();
        assertThat(origin).isInstanceOf(ValueOrigin.Derived.class);
        assertThat(((ValueOrigin.Derived) origin).from()).isEqualTo(createPet.id());
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
}

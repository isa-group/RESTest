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
package io.restest.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.StringSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OperationTest {

    private static final Operation GET_PET = Operation.of(HttpMethod.GET, "/pets/{petId}", List.of(
                    Parameter.of("petId", ParameterLocation.PATH, true,
                            NumberSchema.of(NumberKind.INTEGER)),
                    Parameter.of("verbose", ParameterLocation.QUERY, false, StringSchema.of()),
                    Parameter.of("X-Trace", ParameterLocation.HEADER, false, StringSchema.of())))
            .withId(OperationId.of("getPetById"));

    @Nested
    @DisplayName("what the document says it answers")
    class Responses {

        // Declared least specific first, on purpose. Sorted the other way round, a naive
        // "first response that covers this code" implementation would pass every test below while
        // being wrong, which is the whole thing these tests exist to catch.
        private final Operation operation = GET_PET.withResponses(List.of(
                ResponseModel.empty("default"),
                ResponseModel.empty("4XX"),
                ResponseModel.empty("404"),
                ResponseModel.json("200", ObjectSchema.of(Map.of("name", StringSchema.of())))));

        @Test
        @DisplayName("an exact status code wins over the range that contains it")
        void the_most_specific_declaration_wins() {
            assertThat(operation.responseFor(404).orElseThrow().status()).isEqualTo("404");
        }

        @Test
        @DisplayName("a range wins over the default")
        void a_range_beats_the_default() {
            assertThat(operation.responseFor(418).orElseThrow().status()).isEqualTo("4XX");
        }

        @Test
        @DisplayName("the default answers for anything else")
        void the_default_is_the_last_resort() {
            assertThat(operation.responseFor(503).orElseThrow().status()).isEqualTo("default");
        }

        @Test
        @DisplayName("an operation declaring nothing for a code says so, rather than guessing")
        void an_undeclared_code_is_visible() {
            Operation onlySuccess = GET_PET.withResponses(List.of(ResponseModel.empty("200")));

            assertThat(onlySuccess.responseFor(500)).isEmpty();
        }

        @Test
        @DisplayName("the shape of a successful body is reachable from the operation")
        void a_body_shape_is_reachable() {
            assertThat(operation.responseFor(200).orElseThrow().schemaFor("application/json"))
                    .isPresent();
        }
    }

    @Nested
    @DisplayName("its inputs")
    class Inputs {

        @Test
        @DisplayName("parameters can be asked for by location")
        void parameters_are_grouped_by_location() {
            assertThat(GET_PET.parameters(ParameterLocation.PATH))
                    .extracting(Parameter::name).containsExactly("petId");
            assertThat(GET_PET.parameters(ParameterLocation.QUERY))
                    .extracting(Parameter::name).containsExactly("verbose");
            assertThat(GET_PET.parameters(ParameterLocation.COOKIE)).isEmpty();
        }

        @Test
        @DisplayName("a parameter is found by name and location, because the two can repeat a name")
        void a_parameter_is_found_by_name_and_location() {
            assertThat(GET_PET.parameter("petId", ParameterLocation.PATH)).isPresent();
            assertThat(GET_PET.parameter("petId", ParameterLocation.QUERY)).isEmpty();
        }

        @Test
        @DisplayName("changing the list afterwards does not change the operation")
        void parameters_are_copied() {
            List<Parameter> declared = new ArrayList<>(List.of(
                    Parameter.of("limit", ParameterLocation.QUERY, false, StringSchema.of())));

            Operation operation = Operation.of(HttpMethod.GET, "/pets").withParameters(declared);
            declared.clear();

            assertThat(operation.parameters()).hasSize(1);
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> operation.parameters().clear());
        }
    }

    @Test
    @DisplayName("an operation declaring its own servers keeps them, and inherits when it does not")
    void an_operation_can_override_the_api_s_servers() {
        Server uploads = Server.at("https://uploads.example.com");

        assertThat(GET_PET.servers()).isEmpty();
        assertThat(GET_PET.withServers(List.of(uploads)).servers()).containsExactly(uploads);
    }

    @Test
    @DisplayName("the path template keeps its braces, so a report can name the operation it came from")
    void the_path_template_survives() {
        assertThat(GET_PET.path()).isEqualTo("/pets/{petId}");
    }

    @Test
    @DisplayName("a template with no parameter to fill it is refused: no request could be assembled")
    void an_unfillable_template_is_refused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Operation.of(HttpMethod.GET, "/pets/{petId}"))
                .withMessageContaining("petId");
    }

    @Test
    @DisplayName("a path parameter naming no template variable is read charitably, not refused")
    void a_superfluous_path_parameter_costs_nothing() {
        Operation operation = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("petId", ParameterLocation.PATH, true, StringSchema.of())));

        assertThat(operation.parameters(ParameterLocation.PATH)).hasSize(1);
    }

    @Test
    @DisplayName("one parameter declared twice in one location is refused, as one operation would be")
    void a_parameter_declared_twice_is_refused() {
        List<Parameter> twice = List.of(
                Parameter.of("limit", ParameterLocation.QUERY, false, StringSchema.of()),
                Parameter.of("limit", ParameterLocation.QUERY, true,
                        NumberSchema.of(NumberKind.INTEGER)));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Operation.of(HttpMethod.GET, "/pets", twice))
                .withMessageContaining("limit")
                .withMessageContaining("QUERY");
    }

    @Test
    @DisplayName("the same name in two locations is two parameters, and is allowed")
    void a_name_can_repeat_across_locations() {
        Operation operation = Operation.of(HttpMethod.GET, "/pets/{id}", List.of(
                Parameter.of("id", ParameterLocation.PATH, true, StringSchema.of()),
                Parameter.of("id", ParameterLocation.QUERY, false, StringSchema.of())));

        assertThat(operation.parameter("id", ParameterLocation.PATH).orElseThrow().required())
                .isTrue();
        assertThat(operation.parameter("id", ParameterLocation.QUERY).orElseThrow().required())
                .isFalse();
    }

    @Test
    @DisplayName("a path that is not a path is refused")
    void a_path_starts_with_a_slash() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Operation.of(HttpMethod.GET, "pets"))
                .withMessageContaining("/");
    }

    @Test
    @DisplayName("an operation with no declared identifier is named after its method and path")
    void an_identifier_is_always_present() {
        Operation deletePet = Operation.of(HttpMethod.DELETE, "/pets/{petId}", List.of(
                Parameter.of("petId", ParameterLocation.PATH, true, StringSchema.of())));

        assertThat(deletePet.id()).isEqualTo(OperationId.of("DELETE /pets/{petId}"));
    }

    @Test
    @DisplayName("adding parameters, a body or responses leaves everything else as it was")
    void the_rest_of_the_operation_survives_each_addition() {
        Operation operation = GET_PET
                .withRequestBody(RequestBodyModel.json(StringSchema.of(), false))
                .withResponses(List.of(ResponseModel.empty("204")));

        assertThat(operation.id()).isEqualTo(OperationId.of("getPetById"));
        assertThat(operation.method()).isEqualTo(HttpMethod.GET);
        assertThat(operation.path()).isEqualTo("/pets/{petId}");
        assertThat(operation.parameters()).hasSize(3);
        assertThat(operation.requestBody()).isPresent();
        assertThat(operation.responses()).hasSize(1);
    }
}

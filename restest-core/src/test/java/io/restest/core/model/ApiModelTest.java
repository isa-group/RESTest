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

import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiModelTest {

    private static final Operation LIST_PETS = Operation.of(HttpMethod.GET, "/pets");
    private static final Operation ADD_PET = Operation.of(HttpMethod.POST, "/pets")
            .withRequestBody(RequestBodyModel.json(StringSchema.of(), true));

    @Test
    @DisplayName("an operation is found by the identifier everything else keys on")
    void an_operation_is_found_by_identifier() {
        ApiModel api = ApiModel.of("Petstore", "1.0.0", List.of(LIST_PETS, ADD_PET));

        assertThat(api.operation(OperationId.of("GET /pets"))).contains(LIST_PETS);
        assertThat(api.operation(OperationId.of("getPetById"))).isEmpty();
    }

    @Test
    @DisplayName("operations can be asked for by method")
    void operations_are_grouped_by_method() {
        ApiModel api = ApiModel.of("Petstore", "1.0.0", List.of(LIST_PETS, ADD_PET));

        assertThat(api.operations(HttpMethod.POST)).containsExactly(ADD_PET);
        assertThat(api.operations(HttpMethod.DELETE)).isEmpty();
    }

    @Test
    @DisplayName("two operations under one identifier are refused: every lookup would be a coin toss")
    void duplicate_identifiers_are_refused() {
        Operation first = Operation.of(HttpMethod.GET, "/pets").withId(OperationId.of("pets"));
        Operation second = Operation.of(HttpMethod.POST, "/pets").withId(OperationId.of("pets"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ApiModel.of("Petstore", "1.0.0", List.of(first, second)))
                .withMessageContaining("pets")
                .withMessageContaining("GET")
                .withMessageContaining("POST");
    }

    @Test
    @DisplayName("what could not be read travels with the model, so a partial run cannot look complete")
    void what_could_not_be_read_is_carried() {
        ApiModel api = new ApiModel("Petstore", "1.0.0", List.of(Server.at("https://api.example")),
                List.of(LIST_PETS), Map.of(),
                List.of(SpecificationIssue.skipped("paths./pets.delete",
                        OperationId.of("DELETE /pets"),
                        "the schema of parameter 'force' does not resolve; operation skipped")));

        assertThat(api.isComplete()).isFalse();
        assertThat(api.issues()).singleElement()
                .hasToString("paths./pets.delete: the schema of parameter 'force' does not "
                        + "resolve; operation skipped");
    }

    @Test
    @DisplayName("what could not be read can be added to a model already built")
    void issues_can_be_added_afterwards() {
        ApiModel complete = ApiModel.of("Petstore", "1.0.0", List.of(LIST_PETS));

        ApiModel partial = complete.withIssues(List.of(
                SpecificationIssue.document("paths./pets.delete", "not readable")));

        assertThat(complete.isComplete()).isTrue();
        assertThat(partial.isComplete()).isFalse();
        assertThat(partial.operations()).isEqualTo(complete.operations());
    }

    @Test
    @DisplayName("a variable whose default is one of the allowed values is accepted")
    void a_restricted_variable_accepts_its_own_default() {
        ServerVariable region = new ServerVariable("eu", List.of("eu", "us"), Optional.of("where"));

        assertThat(region.defaultValue()).isEqualTo("eu");
        assertThat(region.allowed()).containsExactly("eu", "us");
    }

    @Test
    @DisplayName("a resolved URL with a leftover closing brace is still not resolved")
    void a_half_template_is_not_resolved() {
        assertThat(new Server("https://eu.example.com/v2}", Map.of(), Optional.empty())
                .isResolved()).isFalse();
    }

    @Test
    @DisplayName("a document read in full says so")
    void a_complete_document_is_visible() {
        assertThat(ApiModel.of("Petstore", "1.0.0", List.of(LIST_PETS)).isComplete()).isTrue();
    }

    @Test
    @DisplayName("changing the lists afterwards does not change the model")
    void the_model_copies_what_it_is_given() {
        List<Operation> operations = new ArrayList<>(List.of(LIST_PETS));

        ApiModel api = ApiModel.of("Petstore", "1.0.0", operations);
        operations.add(ADD_PET);

        assertThat(api.operations()).containsExactly(LIST_PETS);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> api.operations().add(ADD_PET));
    }

    @Test
    @DisplayName("two APIs coexist without touching each other, which is what two runs in one JVM need")
    void two_models_are_independent() {
        ApiModel first = ApiModel.of("Petstore", "1.0.0", List.of(LIST_PETS));
        ApiModel second = ApiModel.of("Petstore", "2.0.0", List.of(LIST_PETS, ADD_PET));

        assertThat(first.operations()).hasSize(1);
        assertThat(second.operations()).hasSize(2);
        assertThat(first.version()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("a templated server resolves itself from the defaults the document supplied")
    void a_templated_server_resolves_from_the_document() {
        Server templated = new Server("https://{region}.example.com/v2",
                Map.of("region", ServerVariable.of("eu")), Optional.of("production"));

        assertThat(templated.url()).isEqualTo("https://{region}.example.com/v2");
        assertThat(templated.resolvedUrl()).isEqualTo("https://eu.example.com/v2");
        assertThat(templated.isResolved()).isTrue();
        assertThat(templated.description()).contains("production");
    }

    @Test
    @DisplayName("a template the document declares no variable for stays visible as a template")
    void an_undeclared_variable_is_not_hidden() {
        Server templated = Server.at("https://{region}.example.com/v2");

        assertThat(templated.resolvedUrl()).isEqualTo("https://{region}.example.com/v2");
        assertThat(templated.isResolved()).isFalse();
    }

    @Test
    @DisplayName("a default outside the values the document allows is refused")
    void a_contradictory_variable_is_refused() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ServerVariable(
                "mars", List.of("eu", "us"), Optional.empty()));
    }

    @Test
    @DisplayName("an operation is reached at its own servers when it declares any, the API's otherwise")
    void operation_servers_override_the_api_s() {
        Server apiServer = Server.at("https://api.example.com");
        Server uploads = Server.at("https://uploads.example.com");
        Operation upload = Operation.of(HttpMethod.POST, "/uploads").withServers(List.of(uploads));
        ApiModel api = new ApiModel("Petstore", "1.0.0", List.of(apiServer),
                List.of(LIST_PETS, upload), Map.of(), List.of());

        assertThat(api.serversFor(upload)).containsExactly(uploads);
        assertThat(api.serversFor(LIST_PETS)).containsExactly(apiServer);
    }

    @Test
    @DisplayName("a reference resolves against the shapes the document declared by name")
    void a_reference_resolves_against_the_named_schemas() {
        ObjectSchema node = ObjectSchema.of(Map.of("children",
                ArraySchema.of(SchemaReference.to("Node"))));
        ApiModel api = ApiModel.of("Trees", "1.0.0", List.of(LIST_PETS))
                .withSchemas(Map.of("Node", node));

        assertThat(api.schema("Node")).contains(node);
        assertThat(api.resolve(SchemaReference.to("Node"))).contains(node);
        assertThat(api.resolve(SchemaReference.to("Absent"))).isEmpty();
    }

    @Test
    @DisplayName("a server without a URL is refused")
    void a_server_has_a_url() {
        assertThatIllegalArgumentException().isThrownBy(() -> Server.at(" "));
    }

    @Test
    @DisplayName("an issue that does not say where it is or what it is would be unactionable")
    void an_empty_issue_is_refused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SpecificationIssue.document("", "something went wrong"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SpecificationIssue.document("paths./pets", " "));
    }

    @Test
    @DisplayName("an operation skipped and an operation degraded are different findings")
    void an_issue_says_what_it_cost() {
        OperationId deletePet = OperationId.of("deletePet");
        SpecificationIssue skipped = SpecificationIssue.skipped(
                "paths./pets/{id}.delete", deletePet, "the schema does not resolve");
        SpecificationIssue degraded = SpecificationIssue.degraded(
                "paths./pets.get.responses.200", OperationId.of("listPets"),
                "an OpenAPI 3.2 construct we do not read yet; the body shape is unknown");
        SpecificationIssue document = SpecificationIssue.document("info", "no version declared");

        assertThat(skipped.skipsAnOperation()).isTrue();
        assertThat(skipped.operation()).contains(deletePet);
        assertThat(degraded.skipsAnOperation()).isFalse();
        assertThat(degraded.effect()).isEqualTo(SpecificationIssue.Effect.DEGRADED);
        assertThat(document.operation()).isEmpty();
        assertThat(document.skipsAnOperation()).isFalse();
    }

    @Test
    @DisplayName("a default that is itself a template does not depend on declaration order")
    void variables_are_substituted_in_one_pass() {
        Map<String, ServerVariable> variables = new java.util.LinkedHashMap<>();
        variables.put("host", ServerVariable.of("{region}.example.com"));
        variables.put("region", ServerVariable.of("eu"));
        Server server = new Server("https://{host}/v2", variables, Optional.empty());

        assertThat(server.resolvedUrl()).isEqualTo("https://{region}.example.com/v2");
        assertThat(server.isResolved()).isFalse();
    }
}

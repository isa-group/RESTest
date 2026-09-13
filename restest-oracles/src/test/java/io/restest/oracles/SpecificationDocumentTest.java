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
package io.restest.oracles;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.ResponseModel;
import io.restest.core.schema.AnySchema;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SpecificationDocumentTest {

    private final ApiModel pets = Specifications.pets();

    @Test
    @DisplayName("the trail to a shape is written the standard way, with slashes in names escaped")
    void a_plain_trail_is_written() {
        Optional<String> pointer = document().pointerToSchema(
                operation(OperationId.of("GET /pets")), declared("GET /pets", 200),
                "application/json");

        assertThat(pointer)
                .contains("#/paths/~1pets/get/responses/200/content/application~1json/schema");
    }

    @Test
    @DisplayName("the braces of a path's variable part are written the way a web address allows")
    void a_templated_path_is_escaped() {
        Optional<String> pointer = document().pointerToSchema(
                operation(OperationId.of("GET /pets/{petId}")),
                declared("GET /pets/{petId}", 200), "application/json");

        assertThat(pointer.orElseThrow()).contains("~1pets~1%7BpetId%7D");
    }

    @Test
    @DisplayName("a document that spells a status its own way is still found")
    void a_status_spelled_differently_is_found() {
        Optional<String> pointer = document().pointerToSchema(
                operation(OperationId.of("GET /kinds")), declared("GET /kinds", 201),
                "application/json");

        assertThat(pointer.orElseThrow()).contains("/responses/2xx/");
    }

    @Test
    @DisplayName("a document that capitalises a media type its own way is still found")
    void a_media_type_spelled_differently_is_found() {
        Optional<String> pointer = document().pointerToSchema(
                operation(OperationId.of("GET /pets/{petId}")),
                declared("GET /pets/{petId}", 404), "application/json");

        assertThat(pointer.orElseThrow())
                .contains("/responses/default/content/Application~1JSON/schema");
    }

    @Test
    @DisplayName("a reply the document declares but gives no shape for has no trail to follow")
    void a_reply_without_a_shape_has_no_trail() {
        assertThat(document().pointerToSchema(operation(OperationId.of("GET /unsaid")),
                declared("GET /unsaid", 200), "application/json")).isEmpty();
    }

    @Test
    @DisplayName("an operation the document does not have has no trail to follow")
    void an_operation_outside_the_document_has_no_trail() {
        Operation elsewhere = Operation.of(HttpMethod.GET, "/elsewhere")
                .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));

        assertThat(document().pointerToSchema(elsewhere, elsewhere.responses().get(0),
                "application/json")).isEmpty();
    }

    @Test
    @DisplayName("a status the document does not spell at all has no trail to follow")
    void a_status_outside_the_document_has_no_trail() {
        ResponseModel notInTheDocument = ResponseModel.json("500", AnySchema.of());

        assertThat(document().pointerToSchema(operation(OperationId.of("GET /pets")),
                notInTheDocument, "application/json")).isEmpty();
    }

    @Test
    @DisplayName("a reply the document declares with no body at all has no trail to follow")
    void a_reply_with_no_body_declared_has_no_trail() {
        assertThat(document().pointerToSchema(operation(OperationId.of("GET /noreply")),
                declared("GET /noreply", 200), "application/json")).isEmpty();
    }

    @Test
    @DisplayName("a media type the document never declared has no trail to follow")
    void a_media_type_outside_the_document_has_no_trail() {
        assertThat(document().pointerToSchema(operation(OperationId.of("GET /pets")),
                declared("GET /pets", 200), "application/xml")).isEmpty();
    }

    @Test
    @DisplayName("a document writing a charset into the type it declares is matched without it")
    void a_declared_media_type_carrying_a_charset_is_matched() {
        assertThat(document().pointerToSchema(operation(OperationId.of("GET /charsetkey")),
                declared("GET /charsetkey", 200), "application/json").orElseThrow())
                .contains("application~1json%3B%20charset%3Dutf-8");
    }

    @Test
    @DisplayName("a name keeps only what cannot be read back wrongly, and escapes the rest")
    void a_name_is_written_so_that_it_reads_back_as_itself() {
        Optional<String> pointer = document().pointerToSchema(
                operation(OperationId.of("GET /problem")), declared("GET /problem", 200),
                "application/problem+json");

        // The plus sign is escaped rather than kept. It is legal to keep, and whoever reads the
        // trail back would read it as a space, so the shape would be looked for under a name the
        // document never used.
        assertThat(pointer.orElseThrow()).contains("application~1problem%2Bjson");
        assertThat(pointer.orElseThrow()).doesNotContain("+");
    }

    @ParameterizedTest(name = "{0} reads back as {1}")
    @CsvSource({
            "'%7B',          '{'",
            "'%7Bid%7D',     '{id}'",
            "'a%2Bb',        'a+b'",
            "'%20',          ' '",
            "'%e2%9c%93',    '\u2713'",
            "'%E2%9C%93',    '\u2713'",
            "'a+b%20c',      'a+b c'",
            "'%0A',           '\n'",
            "'%09',           '\t'",
    })
    @DisplayName("a name written for a web address is read back as the name it stands for")
    void escapes_are_undone(String written, String meant) {
        assertThat(SpecificationDocument.withoutPercentEscapes(written)).contains(meant);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "%4", "%ZZ", "%4Z", "%Z4", "abc%", "a%2"})
    @DisplayName("a name whose escape is not a whole escape is not guessed at")
    void a_broken_escape_reads_back_as_nothing(String written) {
        assertThat(SpecificationDocument.withoutPercentEscapes(written)).isEmpty();
    }

    @Test
    @DisplayName("a name with nothing to undo says so, rather than answering with itself")
    void a_name_with_no_escapes_has_nothing_to_undo() {
        assertThat(SpecificationDocument.withoutPercentEscapes("application/json")).isEmpty();
        assertThat(SpecificationDocument.withoutPercentEscapes("a+b")).isEmpty();
    }

    @Test
    @DisplayName("the document says which version of OpenAPI it is written in")
    void the_openapi_version_is_read() {
        assertThat(document().openApiVersion()).contains("3.0.3");
        assertThat(SpecificationDocument.of(Specifications.kinds31()).orElseThrow()
                .openApiVersion()).contains("3.1.0");
    }

    @Test
    @DisplayName("an API with no document kept, or one that will not read, has nothing to offer")
    void a_missing_or_broken_document_is_nothing() {
        assertThat(SpecificationDocument.of(Specifications.petsWithoutItsDocument())).isEmpty();

        ApiModel broken = ApiModel.of("Broken", "1.0.0", List.of()).withDocument("{ not json");
        assertThat(SpecificationDocument.of(broken)).isEmpty();

        ApiModel notAnObject = ApiModel.of("Odd", "1.0.0", List.of()).withDocument("[]");
        assertThat(SpecificationDocument.of(notAnObject)).isEmpty();
    }

    @Test
    @DisplayName("the document is handed on exactly as it was kept")
    void the_text_is_handed_on_unchanged() {
        assertThat(document().text()).isEqualTo(pets.document().orElseThrow());
    }

    private SpecificationDocument document() {
        return SpecificationDocument.of(pets).orElseThrow();
    }

    private Operation operation(OperationId id) {
        return pets.operation(id).orElseThrow();
    }

    private ResponseModel declared(String id, int status) {
        return operation(OperationId.of(id)).responseFor(status).orElseThrow();
    }
}

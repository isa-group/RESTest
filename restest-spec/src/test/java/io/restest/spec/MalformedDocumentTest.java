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
package io.restest.spec;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.model.ApiModel;
import io.restest.core.model.SpecificationIssue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Documents with one specific, real-world imperfection each: a name a format requires but the
 * document omits, a reference to a shared definition that is missing or itself unusable, a version
 * string spelled a little differently than the strictest reading of the format allows. Each is
 * written directly to a temporary file and read through {@link SwaggerSpecificationParser} end to
 * end, the same way a real run would - not unit-tested piece by piece - because what matters here is
 * that the whole pipeline degrades exactly one operation, or reports exactly one document-level
 * issue, and never anything more than that.
 */
class MalformedDocumentTest {

    private final SwaggerSpecificationParser parser = new SwaggerSpecificationParser();

    @Test
    @DisplayName("a parameter with no name to send it under degrades its operation, not the document")
    void a_parameter_with_no_name_is_degraded_not_thrown(@TempDir Path dir) throws Exception {
        ApiModel api = parse(dir, """
                openapi: 3.0.0
                info: {title: t, version: '1'}
                paths:
                  /widgets:
                    get:
                      parameters:
                        - in: query
                          schema: {type: string}
                      responses: {'200': {description: ok}}
                """);

        // The backend notices the same gap in its own validation pass and reports it too - both are
        // true at once: its own message, and this class's degraded issue for the operation.
        assertThat(api.operations()).hasSize(1);
        assertThat(api.issues())
                .anySatisfy(issue -> assertThat(issue.effect())
                        .isEqualTo(SpecificationIssue.Effect.DEGRADED));
    }

    @Test
    @DisplayName("a server declaring no URL is dropped, not fatal to reading the rest of the document")
    void a_server_with_no_url_is_dropped(@TempDir Path dir) throws Exception {
        ApiModel withNoUrlAtAll = parse(dir, """
                openapi: 3.0.0
                info: {title: t, version: '1'}
                servers:
                  - description: "no url here"
                paths: {}
                """);
        ApiModel withBlankUrl = parse(dir, """
                openapi: 3.0.0
                info: {title: t, version: '1'}
                servers:
                  - url: ""
                paths: {}
                """);

        assertThat(withNoUrlAtAll.servers()).isEmpty();
        assertThat(withBlankUrl.servers()).isEmpty();
    }

    @Test
    @DisplayName("a location that is not even a well-formed URL is reported, not thrown")
    void a_malformed_url_location_is_reported() {
        ApiModel api = parser.parse("http://exam ple.com/openapi.yaml");

        assertThat(api.operations()).isEmpty();
        assertThat(api.issues()).hasSize(1);
        assertThat(api.issues().get(0).effect()).isEqualTo(SpecificationIssue.Effect.DOCUMENT);
    }

    @Test
    @DisplayName("a request body that is a reference to nothing the document declares is reported")
    void an_unresolved_request_body_reference_is_reported(@TempDir Path dir) throws Exception {
        ApiModel api = parse(dir, """
                openapi: 3.0.0
                info: {title: t, version: '1'}
                paths:
                  /widgets:
                    post:
                      requestBody:
                        $ref: '#/components/requestBodies/Missing'
                      responses: {'200': {description: ok}}
                """);

        assertThat(api.isComplete()).isFalse();
        assertThat(api.operation(io.restest.core.model.OperationId.of("POST /widgets"))
                .orElseThrow().requestBody()).isEmpty();
    }

    @Test
    @DisplayName("a $ref to a composed schema is degraded, not silently reported complete")
    void a_reference_to_a_composed_schema_is_degraded(@TempDir Path dir) throws Exception {
        ApiModel api = parse(dir, """
                openapi: 3.0.0
                info: {title: t, version: '1'}
                paths:
                  /widgets:
                    post:
                      requestBody:
                        content:
                          application/json:
                            schema: {$ref: '#/components/schemas/Pet'}
                      responses: {'200': {description: ok}}
                components:
                  schemas:
                    Pet:
                      allOf:
                        - type: object
                """);

        // The operation that uses the schema is degraded, not skipped, and the named schema itself
        // - not tied to any one operation - is named separately: a future consumer that only walks
        // api.schemas() must be able to see the gap too, not only one reading operation issues.
        assertThat(api.operations()).hasSize(1);
        assertThat(api.issues())
                .anySatisfy(issue -> assertThat(issue.effect())
                        .isEqualTo(SpecificationIssue.Effect.DEGRADED));
        assertThat(api.issues())
                .anySatisfy(issue -> assertThat(issue.location()).contains("Pet"));
    }

    @Test
    @DisplayName("an operation-level parameter overrides a path-level $ref of the same name and location")
    void an_inline_parameter_overrides_a_referenced_path_level_one(@TempDir Path dir) throws Exception {
        ApiModel api = parse(dir, """
                openapi: 3.0.0
                info: {title: t, version: '1'}
                paths:
                  /widgets/{id}:
                    parameters:
                      - $ref: '#/components/parameters/Id'
                    get:
                      parameters:
                        - name: id
                          in: path
                          required: true
                          schema: {type: integer}
                      responses: {'200': {description: ok}}
                components:
                  parameters:
                    Id:
                      name: id
                      in: path
                      required: true
                      schema: {type: string}
                """);

        assertThat(api.operations()).hasSize(1);
        assertThat(api.operations().get(0).parameters()).hasSize(1);
    }

    @Test
    @DisplayName("a plain union of JSON Schema types is degraded, not narrowed to the first type")
    void a_multi_type_union_is_degraded(@TempDir Path dir) throws Exception {
        ApiModel api = parse(dir, """
                openapi: 3.1.0
                info: {title: t, version: '1'}
                paths:
                  /widgets:
                    get:
                      responses:
                        '200':
                          description: ok
                          content:
                            application/json:
                              schema:
                                type: [string, integer]
                """);

        assertThat(api.operations()).hasSize(1);
        assertThat(api.operations().get(0).responses()).hasSize(1);
        assertThat(api.issues()).hasSize(1);
        assertThat(api.issues().get(0).effect()).isEqualTo(SpecificationIssue.Effect.DEGRADED);
    }

    @Test
    @DisplayName("one unusable response key costs only that response, not the whole operation")
    void a_junk_response_key_degrades_only_that_response(@TempDir Path dir) throws Exception {
        ApiModel api = parse(dir, """
                openapi: 3.0.0
                info: {title: t, version: '1'}
                paths:
                  /widgets:
                    get:
                      responses:
                        '200 OK': {description: ok}
                        '200': {description: also ok}
                """);

        assertThat(api.operations()).hasSize(1);
        assertThat(api.operations().get(0).responses()).hasSize(1);
        assertThat(api.issues()).hasSize(1);
        assertThat(api.issues().get(0).effect()).isEqualTo(SpecificationIssue.Effect.DEGRADED);
    }

    @Test
    @DisplayName("a version string with a pre-release tag is accepted, end to end")
    void a_pre_release_version_tag_is_accepted(@TempDir Path dir) throws Exception {
        assertThat(parse(dir, "openapi: 3.0.1-rc1\ninfo: {title: t, version: '1'}\npaths: {}\n")
                .isComplete()).isTrue();
    }

    @Test
    @DisplayName("an unquoted or whitespace-padded version is in scope, but the backend still reports its "
            + "own, stricter check")
    void a_loosely_spelled_version_is_gated_in_but_reported_by_the_backend(@TempDir Path dir)
            throws Exception {
        // YAML reads `3.0` with no quotes as a number, not the string OAS itself requires there, and
        // the backend's own internal version check does not tolerate the surrounding whitespace in
        // `' 3.0.1 '` either. The gate is charitable about both - the version either one declares is
        // unambiguous and in scope - but the backend's own, stricter check still finds the document
        // malformed once it looks more closely, and reports it. Both things are true at once: not
        // rejected on a technicality by this class, and not silently accepted as flawless either.
        ApiModel unquoted = parse(dir, "openapi: 3.0\ninfo: {title: t, version: '1'}\npaths: {}\n");
        ApiModel padded = parse(dir, "openapi: ' 3.0.1 '\ninfo: {title: t, version: '1'}\npaths: {}\n");

        assertThat(unquoted.isComplete()).isFalse();
        assertThat(unquoted.issues().get(0).effect()).isEqualTo(SpecificationIssue.Effect.DOCUMENT);
        assertThat(padded.isComplete()).isFalse();
        assertThat(padded.issues().get(0).effect()).isEqualTo(SpecificationIssue.Effect.DOCUMENT);
    }

    @Test
    @DisplayName("the version gate's own size ceiling is generous, independently of any other limit")
    void the_version_gate_accepts_a_large_document() {
        // SnakeYAML's default loader refuses anything past roughly 3,145,728 characters. The gate
        // raises that ceiling for its own peek; whether the backend it hands off to can also read a
        // document this large is a separate, unrelated limit this test makes no claim about.
        String hugeComment = "# " + "x".repeat(4_000_000) + "\n";
        String document = "openapi: 3.0.0\ninfo:\n  title: t\n  version: '1'\n" + hugeComment;

        assertThat(VersionGate.unsupportedReason(document)).isEmpty();
    }

    @Test
    @DisplayName("a path-level server is used when the operation itself declares none")
    void a_path_level_server_is_used_when_the_operation_declares_none(@TempDir Path dir)
            throws Exception {
        ApiModel api = parse(dir, """
                openapi: 3.0.0
                info: {title: t, version: '1'}
                paths:
                  /widgets:
                    servers:
                      - url: https://path-specific.example.com
                    get:
                      responses: {'200': {description: ok}}
                """);

        assertThat(api.operations().get(0).servers())
                .extracting(io.restest.core.model.Server::url)
                .containsExactly("https://path-specific.example.com");
    }

    @Test
    @DisplayName("a response header that is a reference to nothing the document declares is reported")
    void an_unresolved_header_reference_is_reported(@TempDir Path dir) throws Exception {
        ApiModel api = parse(dir, """
                openapi: 3.0.0
                info: {title: t, version: '1'}
                paths:
                  /widgets:
                    get:
                      responses:
                        '200':
                          description: ok
                          headers:
                            X-Rate-Limit:
                              $ref: '#/components/headers/Missing'
                """);

        assertThat(api.operations()).hasSize(1);
        assertThat(api.operations().get(0).responses().get(0).headers()).isEmpty();
        assertThat(api.issues()).hasSize(1);
        assertThat(api.issues().get(0).effect()).isEqualTo(SpecificationIssue.Effect.DEGRADED);
    }

    @Test
    @DisplayName("a $ref to a $ref is followed to the end of the chain")
    void a_reference_to_a_reference_is_followed(@TempDir Path dir) throws Exception {
        ApiModel api = parse(dir, """
                openapi: 3.0.0
                info: {title: t, version: '1'}
                paths:
                  /widgets/{id}:
                    get:
                      parameters:
                        - $ref: '#/components/parameters/Alias'
                      responses: {'200': {description: ok}}
                components:
                  parameters:
                    Alias:
                      $ref: '#/components/parameters/Id'
                    Id:
                      name: id
                      in: path
                      required: true
                      schema: {type: string}
                """);

        assertThat(api.operations()).hasSize(1);
        assertThat(api.operations().get(0).parameters()).hasSize(1);
        assertThat(api.issues()).isEmpty();
    }

    @Test
    @DisplayName("the backend's own validation messages are reported even when it still returns a document")
    void backend_validation_messages_are_surfaced(@TempDir Path dir) throws Exception {
        ApiModel api = parse(dir, """
                openapi: 3.0.0
                info: not-an-object
                paths: {}
                """);

        assertThat(api.issues()).isNotEmpty();
        assertThat(api.issues()).allSatisfy(issue ->
                assertThat(issue.effect()).isEqualTo(SpecificationIssue.Effect.DOCUMENT));
    }

    @Test
    @DisplayName("one malformed named schema costs only itself, not every operation in the document")
    void a_malformed_named_schema_does_not_lose_the_rest_of_the_document(@TempDir Path dir)
            throws Exception {
        ApiModel api = parse(dir, """
                openapi: 3.0.0
                info: {title: t, version: '1'}
                paths:
                  /widgets:
                    get:
                      responses: {'200': {description: ok}}
                components:
                  schemas:
                    Broken:
                      type: string
                      minLength: 5
                      maxLength: 2
                """);

        assertThat(api.operations()).hasSize(1);
        assertThat(api.schema("Broken")).isPresent();
        assertThat(api.issues()).isNotEmpty();
    }

    @Test
    @DisplayName("one malformed server variable costs only that server, not every operation in the document")
    void a_malformed_server_variable_does_not_lose_the_rest_of_the_document(@TempDir Path dir)
            throws Exception {
        ApiModel api = parse(dir, """
                openapi: 3.0.0
                info: {title: t, version: '1'}
                servers:
                  - url: https://{region}.example.com
                    variables:
                      region:
                        enum: [eu, us]
                        default: ap
                paths:
                  /widgets:
                    get:
                      responses: {'200': {description: ok}}
                """);

        assertThat(api.operations()).hasSize(1);
        assertThat(api.servers()).isEmpty();
    }

    private ApiModel parse(Path dir, String document) throws Exception {
        Path file = Files.createTempFile(dir, "openapi", ".yaml");
        Files.writeString(file, document);
        return parser.parse(file.toString());
    }
}

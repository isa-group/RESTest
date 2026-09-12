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
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.SpecificationIssue;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Drives {@link SwaggerSpecificationParser} through real documents: the priority corpus, a 2.0
 * document converted rather than parsed directly, the 3.1 traps, and the hand-written fixtures for
 * what a real document in this corpus was not found to exercise cleanly on its own.
 */
class SwaggerSpecificationParserTest {

    private final SwaggerSpecificationParser parser = new SwaggerSpecificationParser();

    @Test
    @DisplayName("a plain OAS 3.0.x document from the priority corpus reads without crashing")
    void a_3_0_document_from_the_priority_corpus_reads_without_crashing() {
        ApiModel api = parser.parse("specifications/restleague-2027/pet-clinic/openapi.yaml");

        assertThat(api.operations()).isNotEmpty();
        assertThat(api.title()).isNotBlank();
        // Every operation stays present and testable; the schema composition this increment does not
        // fold (see SchemaConverter) is reported, never skipped - once against the named schema that
        // actually uses it, and again, DEGRADED rather than DOCUMENT, against every operation whose
        // data reaches that schema.
        assertThat(api.issues()).isNotEmpty();
        assertThat(api.issues())
                .noneSatisfy(issue -> assertThat(issue.effect())
                        .isEqualTo(SpecificationIssue.Effect.OPERATION_SKIPPED));
        assertThat(api.issues())
                .anySatisfy(issue -> assertThat(issue.effect())
                        .isEqualTo(SpecificationIssue.Effect.DEGRADED));
    }

    @Test
    @DisplayName("a Swagger 2.0 document converts to the same shape a native 3.x document would have")
    void a_2_0_document_converts_cleanly() {
        ApiModel api = parser.parse("specifications/community/Petstore/openapi.yaml");

        assertThat(api.operations()).isNotEmpty();
        Operation addPet = api.operation(OperationId.of("addPet")).orElseThrow();
        assertThat(addPet.method()).isEqualTo(HttpMethod.POST);
        // OpenAPI 2.0's `in: body` became a RequestBodyModel - no BODY/FORM_DATA parameter location
        // exists in this model at all, so this is only possible if the conversion actually happened.
        assertThat(addPet.requestBody()).isPresent();
        assertThat(addPet.requestBody().orElseThrow().required()).isTrue();
        CanonicalSchema body = addPet.requestBody().orElseThrow().schemaFor("application/json")
                .orElseThrow();
        assertThat(body).isEqualTo(SchemaReference.to("Pet"));
    }

    @Test
    @DisplayName("OAS 3.1's nullable-as-type-array and numeric exclusiveMinimum are both read correctly")
    void oas_3_1_traps_are_read_correctly() {
        ApiModel api = parser.parse("specifications/fixtures/oas31-traps/openapi.yaml");

        assertThat(api.isComplete()).isTrue();
        CanonicalSchema account = api.schema("Account").orElseThrow();
        assertThat(account).isInstanceOf(io.restest.core.schema.ObjectSchema.class);
        io.restest.core.schema.ObjectSchema accountSchema = (io.restest.core.schema.ObjectSchema) account;

        StringSchema displayName = (StringSchema) accountSchema.property("displayName").orElseThrow();
        assertThat(displayName.metadata().nullable()).isTrue();

        NumberSchema balance = (NumberSchema) accountSchema.property("balance").orElseThrow();
        assertThat(balance.exclusiveMinimum()).contains(java.math.BigDecimal.ZERO);
    }

    @Test
    @DisplayName("a dangling $ref degrades gracefully by the model's own design, not special parser handling")
    void a_dangling_reference_resolves_to_empty_without_crashing() {
        ApiModel api = parser.parse("specifications/fixtures/malformed/openapi.yaml");

        Operation attachGadget = api.operation(OperationId.of("attachGadget")).orElseThrow();
        CanonicalSchema requestSchema = attachGadget.requestBody().orElseThrow()
                .schemaFor("application/json").orElseThrow();
        assertThat(requestSchema).isEqualTo(SchemaReference.to("Gadget"));
        assertThat(api.resolve((SchemaReference) requestSchema)).isEmpty();
        // The operation with the dangling reference is still present and testable - not skipped.
        assertThat(api.operation(OperationId.of("listWidgets"))).isPresent();
    }

    @Test
    @DisplayName("an OAS 3.2 document is reported as unsupported, in its own words, rather than parsed")
    void an_unsupported_version_is_reported_not_parsed() {
        ApiModel api = parser.parse("specifications/fixtures/unsupported-version/openapi.yaml");

        assertThat(api.operations()).isEmpty();
        assertThat(api.issues()).hasSize(1);
        SpecificationIssue issue = api.issues().get(0);
        assertThat(issue.effect()).isEqualTo(SpecificationIssue.Effect.DOCUMENT);
        assertThat(issue.message()).contains("3.2");
    }

    @Test
    @DisplayName("a document that is not valid YAML at all is reported, never thrown")
    void an_unparseable_document_is_reported_not_thrown() {
        ApiModel api = parser.parse("specifications/fixtures/unparseable/openapi.yaml");

        assertThat(api.operations()).isEmpty();
        assertThat(api.issues()).hasSize(1);
        assertThat(api.issues().get(0).effect()).isEqualTo(SpecificationIssue.Effect.DOCUMENT);
    }

    @Test
    @DisplayName("a location nothing answers at is reported, never thrown and never silent")
    void a_missing_location_is_reported_not_thrown() {
        ApiModel api = parser.parse("specifications/does/not/exist.yaml");

        assertThat(api.operations()).isEmpty();
        assertThat(api.issues()).hasSize(1);
        assertThat(api.issues().get(0).effect()).isEqualTo(SpecificationIssue.Effect.DOCUMENT);
    }
}

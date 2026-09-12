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
package io.restest.core.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.restest.core.json.JsonValue;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CanonicalSchemaTest {

    private static final List<CanonicalSchema> EVERY_KIND = List.of(
            StringSchema.of(),
            NumberSchema.of(NumberKind.INTEGER),
            BooleanSchema.of(),
            NullSchema.of(),
            AnySchema.of(),
            NothingSchema.of(),
            ArraySchema.of(StringSchema.of()),
            ObjectSchema.of(Map.of("name", StringSchema.of()), Set.of("name")),
            SchemaReference.to("Pet"),
            UnsupportedSchema.of("oneOf is not folded in until M2.1"));

    @Test
    @DisplayName("every kind of schema can be told apart without a default case")
    void the_hierarchy_is_exhaustive() {
        assertThat(EVERY_KIND).map(CanonicalSchemaTest::describe).containsExactly(
                "string", "number", "boolean", "null", "any", "nothing", "array", "object",
                "reference", "unsupported");
    }

    @Test
    @DisplayName("every kind of schema carries the type-independent facts")
    void every_variant_has_metadata() {
        assertThat(EVERY_KIND).allSatisfy(
                schema -> assertThat(schema.metadata()).isEqualTo(SchemaMetadata.none()));
    }

    @Test
    @DisplayName("a schema we could not represent says what defeated us")
    void an_unsupported_schema_carries_its_reason() {
        UnsupportedSchema schema = UnsupportedSchema.of("$ref 'Pet' does not resolve");

        assertThat(schema.reason()).isEqualTo("$ref 'Pet' does not resolve");
    }

    @Test
    @DisplayName("an unsupported schema without a reason is refused: a gap nobody can act on")
    void an_unsupported_schema_needs_a_reason() {
        assertThatIllegalArgumentException().isThrownBy(() -> UnsupportedSchema.of("  "));
    }

    @Test
    @DisplayName("'anything', 'nothing' and 'unreadable' are three different statements")
    void any_nothing_and_unsupported_are_different_statements() {
        assertThat(describe(AnySchema.of())).isEqualTo("any");
        assertThat(describe(NothingSchema.of())).isEqualTo("nothing");
        assertThat(describe(UnsupportedSchema.of("a reason"))).isEqualTo("unsupported");
    }

    @Test
    @DisplayName("a recursive shape is representable, because a reference is a schema")
    void a_shape_can_refer_to_itself() {
        ObjectSchema node = new ObjectSchema(SchemaMetadata.none(),
                Map.of("children", ArraySchema.of(SchemaReference.to("Node"))),
                Set.of(), java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.empty());

        ArraySchema children = (ArraySchema) node.property("children").orElseThrow();
        assertThat(children.items()).isEqualTo(SchemaReference.to("Node"));
    }

    @Test
    @DisplayName("a reference keeps the name, which inlining would have thrown away")
    void a_reference_keeps_the_name() {
        assertThat(SchemaReference.to("Pet").name()).isEqualTo("Pet");
        assertThatIllegalArgumentException().isThrownBy(() -> SchemaReference.to(" "));
    }

    @Test
    @DisplayName("an unreadable construct can sit anywhere a schema can, not only at the top")
    void an_unsupported_schema_nests() {
        ObjectSchema pet = ObjectSchema.of(Map.of("tags",
                ArraySchema.of(UnsupportedSchema.of("oneOf with 3 alternatives, M2.1"))));

        ArraySchema tags = (ArraySchema) pet.property("tags").orElseThrow();
        assertThat(tags.items()).isInstanceOf(UnsupportedSchema.class);
        assertThat(((UnsupportedSchema) tags.items()).reason()).contains("M2.1");
    }

    @Test
    @DisplayName("a nullable string is not the same thing as a null-only schema")
    void nullable_is_not_the_null_type() {
        StringSchema nullableString = new StringSchema(
                SchemaMetadata.none().withNullable(true),
                java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.empty());

        assertThat(nullableString.metadata().nullable()).isTrue();
        assertThat(NullSchema.of().metadata().nullable()).isFalse();
    }

    @Test
    @DisplayName("an enumeration constrains a value whatever its type")
    void an_enumeration_lives_with_the_type_independent_facts() {
        SchemaMetadata enumerated = SchemaMetadata.none()
                .withEnumeration(List.of(JsonValue.of("sold"), JsonValue.of("pending")));

        CanonicalSchema asString = new StringSchema(enumerated, java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        CanonicalSchema asNumber = new NumberSchema(enumerated, NumberKind.INTEGER,
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());

        assertThat(asString.metadata().isEnumerated()).isTrue();
        assertThat(asNumber.metadata().enumeration()).hasSize(2);
    }

    /**
     * Compiling is the assertion: a switch with no default over a sealed interface stops compiling
     * the day a variant is added and not handled. Every generator and oracle gets the same warning.
     */
    private static String describe(CanonicalSchema schema) {
        return switch (schema) {
            case StringSchema ignored -> "string";
            case NumberSchema ignored -> "number";
            case BooleanSchema ignored -> "boolean";
            case NullSchema ignored -> "null";
            case AnySchema ignored -> "any";
            case NothingSchema ignored -> "nothing";
            case ArraySchema ignored -> "array";
            case ObjectSchema ignored -> "object";
            case SchemaReference ignored -> "reference";
            case UnsupportedSchema ignored -> "unsupported";
        };
    }
}

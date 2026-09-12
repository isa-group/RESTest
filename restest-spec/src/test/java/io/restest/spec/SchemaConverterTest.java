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

import io.restest.core.schema.AnySchema;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.BooleanSchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.NothingSchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import io.restest.core.schema.UnsupportedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One case per canonical shape {@link SchemaConverter} must produce, plus the 3.0-to-3.1 differences
 * that fail silently rather than loudly if read the wrong way. Built directly against hand-constructed
 * {@code swagger-parser} schema objects, not documents, so these stay fast and pin exact behaviour
 * independent of any fixture.
 */
class SchemaConverterTest {

    @Test
    @DisplayName("a string schema carries its length, pattern and format constraints")
    void a_string_type_becomes_a_string_schema() {
        Schema<Object> schema = new Schema<>();
        schema.setType("string");
        schema.setMinLength(1);
        schema.setMaxLength(10);
        schema.setPattern("^[a-z]+$");
        schema.setFormat("email");

        CanonicalSchema converted = SchemaConverter.convert(schema);

        assertThat(converted).isInstanceOf(StringSchema.class);
        StringSchema string = (StringSchema) converted;
        assertThat(string.minLength()).contains(1);
        assertThat(string.maxLength()).contains(10);
        assertThat(string.pattern()).contains("^[a-z]+$");
        assertThat(string.format()).contains("email");
    }

    @Test
    @DisplayName("'integer' and 'number' are one record, distinguished by kind")
    void integer_and_number_become_number_schema_with_the_right_kind() {
        Schema<Object> integer = new Schema<>();
        integer.setType("integer");
        Schema<Object> number = new Schema<>();
        number.setType("number");

        assertThat(((NumberSchema) SchemaConverter.convert(integer)).kind()).isEqualTo(NumberKind.INTEGER);
        assertThat(((NumberSchema) SchemaConverter.convert(number)).kind()).isEqualTo(NumberKind.NUMBER);
    }

    @Test
    @DisplayName("a boolean schema has nothing to constrain beyond its type")
    void a_boolean_type_becomes_a_boolean_schema() {
        Schema<Object> schema = new Schema<>();
        schema.setType("boolean");

        assertThat(SchemaConverter.convert(schema)).isInstanceOf(BooleanSchema.class);
    }

    @Test
    @DisplayName("an object schema carries its properties and required names")
    void an_object_type_becomes_an_object_schema() {
        Schema<Object> name = new Schema<>();
        name.setType("string");
        Schema<Object> schema = new Schema<>();
        schema.setType("object");
        schema.setProperties(Map.of("name", name));
        schema.setRequired(List.of("name"));

        CanonicalSchema converted = SchemaConverter.convert(schema);

        assertThat(converted).isInstanceOf(ObjectSchema.class);
        ObjectSchema object = (ObjectSchema) converted;
        assertThat(object.property("name")).contains(StringSchema.of());
        assertThat(object.isRequired("name")).isTrue();
    }

    @Test
    @DisplayName("additionalProperties: false is NothingSchema, not a failure to read the document")
    void additional_properties_false_becomes_nothing_schema() {
        Schema<Object> schema = new Schema<>();
        schema.setType("object");
        schema.setAdditionalProperties(Boolean.FALSE);

        ObjectSchema object = (ObjectSchema) SchemaConverter.convert(schema);

        assertThat(object.isClosed()).isTrue();
        assertThat(object.additionalProperties()).contains(NothingSchema.of());
    }

    @Test
    @DisplayName("an array schema carries the one shape every element has")
    void an_array_type_becomes_an_array_schema() {
        Schema<Object> items = new Schema<>();
        items.setType("string");
        Schema<Object> schema = new Schema<>();
        schema.setType("array");
        schema.setItems(items);
        schema.setMinItems(1);
        schema.setUniqueItems(true);

        CanonicalSchema converted = SchemaConverter.convert(schema);

        assertThat(converted).isInstanceOf(ArraySchema.class);
        ArraySchema array = (ArraySchema) converted;
        assertThat(array.items()).isEqualTo(StringSchema.of());
        assertThat(array.minItems()).contains(1);
        assertThat(array.uniqueItems()).isTrue();
    }

    @Test
    @DisplayName("no declared type and no structural hint is AnySchema: any value will do")
    void no_type_becomes_any_schema() {
        Schema<Object> schema = new Schema<>();

        assertThat(SchemaConverter.convert(schema)).isEqualTo(AnySchema.of());
    }

    @Test
    @DisplayName("a $ref stays a lazy reference, never inlined")
    void a_ref_becomes_a_schema_reference() {
        Schema<Object> schema = new Schema<>();
        schema.set$ref("#/components/schemas/Pet");

        CanonicalSchema converted = SchemaConverter.convert(schema);

        assertThat(converted).isEqualTo(SchemaReference.to("Pet"));
    }

    @Test
    @DisplayName("oneOf/anyOf/allOf composition is not yet folded, deliberately, until increment 2.1")
    void composition_becomes_unsupported() {
        Schema<Object> option = new Schema<>();
        option.setType("string");

        Schema<Object> oneOf = new Schema<>();
        oneOf.setOneOf(List.of(option));
        Schema<Object> anyOf = new Schema<>();
        anyOf.setAnyOf(List.of(option));
        Schema<Object> allOf = new Schema<>();
        allOf.setAllOf(List.of(option));

        assertThat(SchemaConverter.convert(oneOf)).isInstanceOf(UnsupportedSchema.class);
        assertThat(SchemaConverter.convert(anyOf)).isInstanceOf(UnsupportedSchema.class);
        assertThat(SchemaConverter.convert(allOf)).isInstanceOf(UnsupportedSchema.class);
    }

    @Test
    @DisplayName("a tuple-form array, where every element has its own shape, is not representable")
    void a_tuple_form_array_becomes_unsupported() {
        Schema<Object> first = new Schema<>();
        first.setType("string");
        Schema<Object> second = new Schema<>();
        second.setType("integer");
        Schema<Object> schema = new Schema<>();
        schema.setType("array");
        schema.setPrefixItems(List.of(first, second));

        assertThat(SchemaConverter.convert(schema)).isInstanceOf(UnsupportedSchema.class);
    }

    @Test
    @DisplayName("OAS 3.0: nullable is a sibling boolean keyword")
    void nullable_as_a_3_0_boolean_is_read() {
        Schema<Object> schema = new Schema<>();
        schema.setType("string");
        schema.setNullable(true);

        assertThat(SchemaConverter.convert(schema).metadata().nullable()).isTrue();
    }

    @Test
    @DisplayName("OAS 3.1: nullable is a member of a JSON Schema type array, not a sibling keyword")
    void nullable_as_a_3_1_type_array_is_read() {
        Schema<Object> schema = new Schema<>();
        schema.setTypes(Set.of("string", "null"));

        CanonicalSchema converted = SchemaConverter.convert(schema);

        assertThat(converted).isInstanceOf(StringSchema.class);
        assertThat(converted.metadata().nullable()).isTrue();
    }

    @Test
    @DisplayName("OAS 3.0: exclusiveMinimum is a flag next to minimum, not a bound of its own")
    void exclusive_minimum_as_a_3_0_boolean_moves_the_bound() {
        Schema<Object> schema = new Schema<>();
        schema.setType("number");
        schema.setMinimum(BigDecimal.valueOf(5));
        schema.setExclusiveMinimum(true);

        NumberSchema number = (NumberSchema) SchemaConverter.convert(schema);

        assertThat(number.minimum()).isEmpty();
        assertThat(number.exclusiveMinimum()).contains(BigDecimal.valueOf(5));
    }

    @Test
    @DisplayName("OAS 3.1: exclusiveMinimum is itself the numeric bound (JSON Schema 2020-12)")
    void exclusive_minimum_as_a_3_1_number_is_read_directly() {
        Schema<Object> schema = new Schema<>();
        schema.setType("number");
        schema.setExclusiveMinimumValue(BigDecimal.ZERO);

        NumberSchema number = (NumberSchema) SchemaConverter.convert(schema);

        assertThat(number.minimum()).isEmpty();
        assertThat(number.exclusiveMinimum()).contains(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("readOnly and writeOnly become one Access, never both at once")
    void read_only_and_write_only_become_access() {
        Schema<Object> readOnly = new Schema<>();
        readOnly.setType("string");
        readOnly.setReadOnly(true);
        Schema<Object> writeOnly = new Schema<>();
        writeOnly.setType("string");
        writeOnly.setWriteOnly(true);

        assertThat(SchemaConverter.convert(readOnly).metadata().access())
                .isEqualTo(SchemaMetadata.Access.READ_ONLY);
        assertThat(SchemaConverter.convert(writeOnly).metadata().access())
                .isEqualTo(SchemaMetadata.Access.WRITE_ONLY);
    }

    @Test
    @DisplayName("hasUnsupportedConstruct finds an unsupported shape nested inside an object or array")
    void unsupported_constructs_are_found_when_nested() {
        Schema<Object> composed = new Schema<>();
        composed.setOneOf(List.of(new Schema<>()));
        Schema<Object> nestedInObject = new Schema<>();
        nestedInObject.setType("object");
        nestedInObject.setProperties(Map.of("bad", composed));
        Schema<Object> nestedInArray = new Schema<>();
        nestedInArray.setType("array");
        nestedInArray.setItems(composed);
        Schema<Object> clean = new Schema<>();
        clean.setType("string");

        assertThat(SchemaConverter.hasUnsupportedConstruct(SchemaConverter.convert(nestedInObject))).isTrue();
        assertThat(SchemaConverter.hasUnsupportedConstruct(SchemaConverter.convert(nestedInArray))).isTrue();
        assertThat(SchemaConverter.hasUnsupportedConstruct(SchemaConverter.convert(clean))).isFalse();
    }
}

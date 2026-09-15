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

import io.restest.core.json.JsonValue;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

        assertThat(SchemaConverter.hasUnsupportedConstruct(
                SchemaConverter.convert(nestedInObject), Map.of())).isTrue();
        assertThat(SchemaConverter.hasUnsupportedConstruct(
                SchemaConverter.convert(nestedInArray), Map.of())).isTrue();
        assertThat(SchemaConverter.hasUnsupportedConstruct(
                SchemaConverter.convert(clean), Map.of())).isFalse();
    }

    @Test
    @DisplayName("a schema that names no type but describes an object is read as one")
    void properties_without_a_type_become_an_object_schema() {
        Schema<Object> name = new Schema<>();
        name.setType("string");
        Schema<Object> untyped = new Schema<>();
        untyped.setProperties(Map.of("name", name));
        untyped.setRequired(List.of("name"));

        CanonicalSchema converted = SchemaConverter.convert(untyped);

        assertThat(converted).isInstanceOf(ObjectSchema.class);
        ObjectSchema object = (ObjectSchema) converted;
        assertThat(object.properties()).containsOnlyKeys("name");
        assertThat(object.properties().get("name")).isInstanceOf(StringSchema.class);
        assertThat(object.required()).containsExactly("name");
    }

    @Test
    @DisplayName("a date default is the day the document wrote, wherever the machine is")
    void a_date_default_survives_the_machines_time_zone() {
        Schema<Object> schema = new Schema<>();
        schema.setType("string");
        schema.setFormat("date");
        // Built the way the parser builds one: midnight on that day, where this machine is.
        schema.setDefault(Date.from(
                LocalDate.of(2020, 1, 31).atStartOfDay(ZoneId.systemDefault()).toInstant()));

        assertThat(SchemaConverter.convert(schema).metadata().defaultValue())
                .contains(JsonValue.of("2020-01-31"));
    }

    @Test
    @DisplayName("a moment in time keeps its seconds, which the shorthand spelling drops")
    void a_date_time_default_is_written_the_way_the_web_requires() {
        Schema<Object> schema = new Schema<>();
        schema.setType("string");
        schema.setFormat("date-time");
        schema.setDefault(OffsetDateTime.parse("2020-01-31T10:00:00Z"));

        assertThat(SchemaConverter.convert(schema).metadata().defaultValue())
                .contains(JsonValue.of("2020-01-31T10:00:00Z"));
    }

    @Test
    @DisplayName("a base-64 default is read back as its text, not as an address in memory")
    void a_byte_default_is_read_back_as_base_64() {
        Schema<Object> schema = new Schema<>();
        schema.setType("string");
        schema.setFormat("byte");
        schema.setDefault("hi".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(SchemaConverter.convert(schema).metadata().defaultValue())
                .contains(JsonValue.of("aGk="));
    }

    @Test
    @DisplayName("an identifier default keeps the text the document wrote")
    void a_uuid_default_is_read_back_as_its_text() {
        Schema<Object> schema = new Schema<>();
        schema.setType("string");
        schema.setFormat("uuid");
        schema.setDefault(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));

        assertThat(SchemaConverter.convert(schema).metadata().defaultValue())
                .describedAs("the parser turns this one into an object of its own, whose text is "
                        + "not JSON, so leaving it to the last resort would drop a good value")
                .contains(JsonValue.of("550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test
    @DisplayName("a default the parser hands over as a document node is read back as the value itself")
    void a_node_shaped_default_is_read_back_as_a_value() {
        Schema<Object> schema = new Schema<>();
        schema.setType("object");
        schema.setDefault(printsAs("{\"a\":1}"));

        assertThat(SchemaConverter.convert(schema).metadata().defaultValue())
                .get()
                .isInstanceOf(JsonValue.JsonObject.class);
    }

    @Test
    @DisplayName("a default that cannot be written down exactly is left out rather than invented")
    void an_unrepresentable_default_is_left_out() {
        Schema<Object> schema = new Schema<>();
        schema.setType("string");
        schema.setDefault(printsAs("Fri Jan 31 00:00:00 CET 2020"));

        assertThat(SchemaConverter.convert(schema).metadata().defaultValue()).isEmpty();
    }

    @Test
    @DisplayName("an enumeration that cannot be read in full is not read at all")
    void a_partly_unreadable_enumeration_is_unsupported() {
        Schema<Object> schema = new Schema<>();
        schema.setType("string");
        schema.setEnum(List.of("a", printsAs("not json at all"), "b"));

        CanonicalSchema converted = SchemaConverter.convert(schema);

        // Not the same answer as for a default, and the difference is the point. A default that
        // cannot be read is dropped, and the schema then honestly says it has no default. Keeping
        // two of three allowed values would instead state something the document never said: that
        // the API accepts two things. Better to admit the list could not be read.
        assertThat(converted).isInstanceOf(UnsupportedSchema.class);
        assertThat(((UnsupportedSchema) converted).reason()).contains("allowed to take");
    }

    @Test
    @DisplayName("an enumeration every member of which can be read is kept whole")
    void a_readable_enumeration_is_kept() {
        Schema<Object> schema = new Schema<>();
        schema.setType("string");
        schema.setEnum(List.of("a", "b"));

        assertThat(SchemaConverter.convert(schema).metadata().enumeration())
                .containsExactly(JsonValue.of("a"), JsonValue.of("b"));
    }

    @Test
    @DisplayName("a reference to a name nothing declares counts as not understood")
    void a_dangling_reference_is_unsupported() {
        Schema<Object> reference = new Schema<>();
        reference.set$ref("#/components/schemas/Missing");

        CanonicalSchema converted = SchemaConverter.convert(reference);

        assertThat(converted).isInstanceOf(SchemaReference.class);
        assertThat(SchemaConverter.hasUnsupportedConstruct(converted, Map.of())).isTrue();
        assertThat(SchemaConverter.hasUnsupportedConstruct(converted,
                Map.of("Missing", AnySchema.of()))).isFalse();
    }

    /** A value that is nothing but the way it prints, standing in for one of the parser's own nodes. */
    private static Object printsAs(String text) {
        return new Object() {
            @Override
            public String toString() {
                return text;
            }
        };
    }
}

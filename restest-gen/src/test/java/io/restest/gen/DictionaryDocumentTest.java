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
package io.restest.gen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonException;
import io.restest.core.json.JsonValue;
import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.StringSchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reading a list of values somebody wrote down, and finding the ones that apply to an input. */
class DictionaryDocumentTest {

    @Test
    @DisplayName("values are found under the kind of value wanted, and under the bucket for all kinds")
    void keyed_by_type() {
        ValueDictionary dictionary = read("""
                {"version": 1, "name": "d", "keyedBy": "type", "values": {
                   "any": [null],
                   "string": ["a", "b"],
                   "integer": [7]
                }}""");

        assertThat(dictionary.valuesFor(asking("q", StringSchema.of())))
                .containsExactly(JsonValue.of("a"), JsonValue.of("b"), JsonValue.NULL);
        assertThat(dictionary.valuesFor(asking("q", NumberSchema.of(NumberKind.INTEGER))))
                .containsExactly(JsonValue.of(7L), JsonValue.NULL);
        assertThat(dictionary.valuesFor(asking("q", NumberSchema.of(NumberKind.NUMBER))))
                .describedAs("a whole number and a number are different kinds, as JSON has them")
                .containsExactly(JsonValue.NULL);
    }

    @Test
    @DisplayName("a whole object is as ordinary a value as a word, which is what bodies will need")
    void values_may_be_objects_and_lists() {
        ValueDictionary dictionary = read("""
                {"version": 1, "name": "d", "keyedBy": "schema", "values": {
                   "Owner": [{"firstName": "George", "pets": [{"name": "Leo"}]}]
                }}""");

        ValueRequest anOwner = new ValueRequest(OperationId.of("GET /owners"), "owner",
                ParameterLocation.QUERY, ObjectSchema.of(Map.of()), List.of(),
                Optional.of("Owner"));

        assertThat(dictionary.valuesFor(anOwner)).singleElement()
                .isInstanceOf(JsonValue.JsonObject.class);
    }

    @Test
    @DisplayName("values are found under the format the document declares for a value")
    void keyed_by_format() {
        ValueDictionary dictionary = read("""
                {"version": 1, "name": "d", "keyedBy": "format", "values": {
                   "date": ["2026-09-18"]
                }}""");
        CanonicalSchema aDate = new StringSchema(SchemaMetadata.none(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of("date"));

        assertThat(dictionary.valuesFor(asking("when", aDate)))
                .containsExactly(JsonValue.of("2026-09-18"));
        assertThat(dictionary.valuesFor(asking("when", StringSchema.of()))).isEmpty();
    }

    @Test
    @DisplayName("values are found under the parameter's name, wherever it appears")
    void keyed_by_name() {
        ValueDictionary dictionary = read("""
                {"version": 1, "name": "d", "keyedBy": "name", "values": {"petId": [7]}}""");

        assertThat(dictionary.valuesFor(asking("petId", StringSchema.of())))
                .containsExactly(JsonValue.of(7L));
        assertThat(dictionary.valuesFor(asking("ownerId", StringSchema.of()))).isEmpty();
    }

    @Test
    @DisplayName("values are found under one parameter of one operation, named as the report names it")
    void keyed_by_operation_and_parameter() {
        ValueDictionary dictionary = read("""
                {"version": 1, "name": "d", "keyedBy": "operationAndParameter", "values": {
                   "getOwner": {"ownerId": [1, 2]},
                   "GET /pets/{petId}": {"petId": [9]}
                }}""");

        assertThat(values(dictionary, "getOwner", "ownerId"))
                .containsExactly(JsonValue.of(1L), JsonValue.of(2L));
        assertThat(values(dictionary, "getOwner", "somethingElse")).isEmpty();
        assertThat(values(dictionary, "GET /pets/{petId}", "petId"))
                .describedAs("an operation the document gave no identifier is named after its "
                        + "method and path, which is what the tool itself prints")
                .containsExactly(JsonValue.of(9L));
        assertThat(dictionary.keys()).containsExactly("getOwner", "GET /pets/{petId}");
    }

    @Test
    @DisplayName("a dictionary that says nothing about what it expects has not been checked")
    void an_unstated_expectation_is_unknown() {
        assertThat(read("""
                {"version": 1, "name": "d", "keyedBy": "type", "values": {}}""").expects())
                .isEqualTo(Dictionary.Expectation.UNKNOWN);
        assertThat(read("""
                {"version": 1, "name": "d", "keyedBy": "type", "expects": "refusal",
                 "values": {}}""").expects())
                .isEqualTo(Dictionary.Expectation.REFUSAL);
    }

    @Test
    @DisplayName("a file written in a version this build does not know is refused, not half-read")
    void an_unknown_version_is_refused() {
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("""
                        {"version": 99, "name": "d", "keyedBy": "type", "values": {}}"""))
                .withMessageContaining("version 99")
                .withMessageContaining("reads version 1");
    }

    @Test
    @DisplayName("what is wrong with a file is said in words, naming the file")
    void what_is_wrong_is_said_plainly() {
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("this is not JSON at all"))
                .withMessageContaining("a dictionary somebody wrote")
                .withMessageContaining("not JSON");
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("""
                        {"version": 1, "name": "d", "keyedBy": "horoscope", "values": {}}"""))
                .withMessageContaining("horoscope");
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("""
                        {"version": 1, "name": "d", "keyedBy": "type"}"""))
                .withMessageContaining("no 'values'");
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("""
                        {"version": 1, "name": "d", "keyedBy": "type",
                         "values": {"string": "not a list"}}"""))
                .withMessageContaining("other than a list");
    }

    private static List<JsonValue> values(ValueDictionary dictionary, String operation,
            String parameter) {
        return dictionary.valuesFor(ValueRequest.of(OperationId.of(operation), parameter,
                ParameterLocation.QUERY, StringSchema.of()));
    }

    private static ValueRequest asking(String name, CanonicalSchema schema) {
        return ValueRequest.of(OperationId.of("GET /widgets"), name, ParameterLocation.QUERY,
                schema);
    }

    private static ValueDictionary read(String text) {
        return DictionaryDocument.read(text, "a dictionary somebody wrote");
    }
}

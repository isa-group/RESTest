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
import io.restest.core.schema.StringSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reading a list of values somebody wrote down, and finding the ones that apply to an input. */
class DictionaryDocumentTest {

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
        assertThat(dictionary.entriesByOperation().keySet())
                .containsExactly("getOwner", "GET /pets/{petId}");
    }

    @Test
    @DisplayName("a file keyed any other way is refused, and told how to key it")
    void every_other_keying_is_refused() {
        // Written in the shape a file keyed by operation has, so that the keying is the only thing
        // wrong with it and the refusal cannot be for anything else.
        for (String keyedBy : List.of("type", "format", "schema", "name")) {
            assertThatExceptionOfType(JsonException.class)
                    .isThrownBy(() -> read("""
                            version: 1
                            name: d
                            keyedBy: %s
                            values:
                              getOwner:
                                ownerId: [1]
                            """.formatted(keyedBy)))
                    .describedAs(keyedBy)
                    .withMessageContaining("keyed by '" + keyedBy + "'")
                    .withMessageContaining("each list under its operation and the place in its "
                            + "request (keyedBy: operationAndParameter)");
        }
    }

    @Test
    @DisplayName("'any' is an operation's name like any other, so a list under it is refused and "
            + "places under it apply to that operation alone")
    void any_is_an_operation_like_any_other() {
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("""
                        version: 1
                        name: d
                        keyedBy: operationAndParameter
                        values:
                          any: [null]
                        """))
                .withMessageContaining("what is written under 'any' is not an object");

        ValueDictionary dictionary = read("""
                version: 1
                name: d
                keyedBy: operationAndParameter
                values:
                  any:
                    ownerId: [1]
                  getOwner:
                    ownerId: [2]
                """);
        assertThat(values(dictionary, "getOwner", "ownerId"))
                .describedAs("nothing written under 'any' reaches another operation")
                .containsExactly(JsonValue.of(2L));
        assertThat(values(dictionary, "any", "ownerId")).containsExactly(JsonValue.of(1L));
    }

    @Test
    @DisplayName("a file called what RESTest's own list to push with is called is refused, and a "
            + "file written the way 2.1 let somebody add to that list is told both things at once")
    void the_name_of_restests_own_list_is_refused() {
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("""
                        version: 1
                        name: fuzzing
                        keyedBy: operationAndParameter
                        values:
                          getOwner:
                            ownerId: [-1]
                        """))
                .withMessageContaining("is called 'fuzzing', which is the name of RESTest's own "
                        + "list of values to push with; give yours another name");
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("""
                        version: 1
                        name: fuzzing
                        keyedBy: type
                        values:
                          string: [x]
                        """))
                .withMessageContaining("keyed by 'type'")
                .withMessageContaining(", and is called 'fuzzing'");
    }

    @Test
    @DisplayName("RESTest's own list is read by kind of value, with a bucket for every kind")
    void restests_own_list_is_keyed_by_type() {
        ValuesByKind dictionary = readOwn("""
                {"version": 1, "name": "fuzzing", "keyedBy": "type", "values": {
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
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> readOwn("""
                        {"version": 1, "name": "fuzzing", "keyedBy": "operationAndParameter",
                         "values": {}}"""))
                .withMessageContaining("RESTest's own list is keyed by type");
    }

    @Test
    @DisplayName("a whole object is as ordinary a value as a word, which is what bodies will need")
    void values_may_be_objects_and_lists() {
        ValueDictionary dictionary = read("""
                {"version": 1, "name": "d", "keyedBy": "operationAndParameter", "values": {
                   "addOwner": {"body": [{"firstName": "George", "pets": [{"name": "Leo"}]}]}
                }}""");

        ValueRequest anOwner = ValueRequest.of(OperationId.of("addOwner"), ValueRequest.THE_BODY,
                ParameterLocation.BODY, ObjectSchema.of(Map.of()));

        assertThat(dictionary.valuesFor(anOwner)).singleElement()
                .isInstanceOf(JsonValue.JsonObject.class);
    }

    @Test
    @DisplayName("an operation written down with nothing under it is read as nothing to fill in, "
            + "rather than costing the file every value in it")
    void an_operation_with_nothing_under_it_is_read_as_empty() {
        // YAML reads a key followed by only a comment as null, not as an empty object. Refusing it
        // cost the whole file its values, and every dictionary of one campaign held such a block.
        ValueDictionary dictionary = read("""
                version: 1
                name: d
                keyedBy: operationAndParameter
                values:
                  GET /oops:
                    # nothing to fill: every place is settled by the document
                  GET /owners:
                    lastName: ["Franklin"]
                """);

        assertThat(values(dictionary, "GET /owners", "lastName"))
                .containsExactly(JsonValue.of("Franklin"));
        assertThat(values(dictionary, "GET /oops", "anything")).isEmpty();
    }

    @Test
    @DisplayName("values with nothing under them are a dictionary with no values, like values: {}")
    void values_with_nothing_under_them_is_read_as_empty() {
        ValueDictionary dictionary = read("""
                version: 1
                name: d
                keyedBy: operationAndParameter
                values:
                  # to be filled in
                """);

        assertThat(dictionary.entriesByOperation()).isEmpty();
        assertThat(dictionary.holdsNoValues()).isTrue();
    }

    @Test
    @DisplayName("nothing where a list of values belongs is refused, and the refusal says how to "
            + "write either thing it could have meant")
    void nothing_where_a_list_belongs_is_refused() {
        // Unlike an operation, here null is a value somebody might mean to send. Guessing between
        // "none" and "send null" would be wrong half the time and nobody would be told.
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("""
                        version: 1
                        name: d
                        keyedBy: operationAndParameter
                        values:
                          GET /owners:
                            lastName:
                        """))
                .withMessageContaining("nothing under 'lastName' in 'GET /owners'")
                .withMessageContaining("write [] for no values, or [null] to send null");
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> readOwn("""
                        version: 1
                        name: fuzzing
                        keyedBy: type
                        values:
                          string:
                        """))
                .withMessageContaining("nothing under 'string'");
    }

    @Test
    @DisplayName("an operation with something under it that is not a set of places is still refused")
    void an_operation_holding_something_else_is_still_refused() {
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("""
                        version: 1
                        name: d
                        keyedBy: operationAndParameter
                        values:
                          GET /owners: [Franklin]
                        """))
                .withMessageContaining("what is written under 'GET /owners' is not an object");
    }

    @Test
    @DisplayName("a file written in YAML and the same file written in JSON are the same dictionary")
    void yaml_and_json_are_read_the_same_way() {
        ValueDictionary asYaml = read("""
                version: 1
                name: d
                keyedBy: operationAndParameter
                values:
                  GET /owners:
                    lastName: [a, b]
                """);
        ValueDictionary asJson = read("""
                {"version": 1, "name": "d", "keyedBy": "operationAndParameter",
                 "values": {"GET /owners": {"lastName": ["a", "b"]}}}""");

        assertThat(values(asYaml, "GET /owners", "lastName"))
                .describedAs("JSON is a subset of YAML, so nobody has to be told which to write")
                .isEqualTo(values(asJson, "GET /owners", "lastName"))
                .containsExactly(JsonValue.of("a"), JsonValue.of("b"));
    }

    @Test
    @DisplayName("a key written twice is refused, rather than the second quietly replacing the first")
    void a_key_written_twice_is_refused() {
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("""
                        version: 1
                        name: d
                        keyedBy: operationAndParameter
                        values:
                          GET /owners:
                            lastName: [a]
                            lastName: [b]
                        """))
                .withMessageContaining("duplicate");
    }

    @Test
    @DisplayName("an unquoted word is a word, whatever YAML's older rules would have made of it")
    void words_that_look_like_something_else_stay_words() {
        ValueDictionary dictionary = read("""
                version: 1
                name: d
                keyedBy: operationAndParameter
                values:
                  GET /owners:
                    country: [no, "NO", on, off, yes, y, n, 2026-09-18, .inf, .nan]
                """);

        // YAML's older rules turn seven of these into true or false, silently. A list of country
        // codes holding NO would send false, and nothing would say so. Only what JSON itself has is
        // recognised here, so a word stays a word.
        assertThat(values(dictionary, "GET /owners", "country"))
                .containsExactly(JsonValue.of("no"), JsonValue.of("NO"), JsonValue.of("on"),
                        JsonValue.of("off"), JsonValue.of("yes"), JsonValue.of("y"),
                        JsonValue.of("n"), JsonValue.of("2026-09-18"), JsonValue.of(".inf"),
                        JsonValue.of(".nan"));
    }

    @Test
    @DisplayName("true, false and null still mean what they mean, and numbers keep every digit")
    void what_json_has_is_still_read_as_json_reads_it() {
        ValueDictionary dictionary = read("""
                version: 1
                name: d
                keyedBy: operationAndParameter
                values:
                  GET /widgets:
                    flag: [true, false, null]
                    amount: [99999999999999999999999999999999.999999, 1e308]
                """);

        assertThat(values(dictionary, "GET /widgets", "flag"))
                .containsExactly(JsonValue.TRUE, JsonValue.FALSE, JsonValue.NULL);
        assertThat(values(dictionary, "GET /widgets", "amount"))
                .describedAs("read as the nearest value sixty-four bits can hold, the first of "
                        + "these would come out a different number entirely")
                .contains(JsonValue.of(new java.math.BigDecimal(
                        "99999999999999999999999999999999.999999")));
    }

    @Test
    @DisplayName("a number the file tags as one and that is not a number is refused, not thrown at")
    void something_tagged_a_number_that_is_not_one_is_refused() {
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("""
                        version: 1
                        name: d
                        keyedBy: operationAndParameter
                        values:
                          GET /widgets:
                            amount: [!!float .inf]
                        """))
                .withMessageContaining("is not a number a request could carry");
    }

    @Test
    @DisplayName("a file written in a version this build does not know is refused, not half-read")
    void an_unknown_version_is_refused() {
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("""
                        {"version": 99, "name": "d", "keyedBy": "operationAndParameter",
                         "values": {}}"""))
                .withMessageContaining("version 99")
                .withMessageContaining("reads version 1");
    }

    @Test
    @DisplayName("what is wrong with a file is said in words, naming the file")
    void what_is_wrong_is_said_plainly() {
        // Plain text is a perfectly good YAML document - it is one long word - so what is wrong
        // with it is not that it could not be read but that what was read is not a dictionary.
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("this is a sentence, not a dictionary"))
                .withMessageContaining("a dictionary somebody wrote")
                .withMessageContaining("is not an object");
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("values: [unclosed"))
                .withMessageContaining("a dictionary somebody wrote")
                .withMessageContaining("could not be read");
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("""
                        {"version": 1, "name": "d", "keyedBy": "horoscope", "values": {}}"""))
                .withMessageContaining("horoscope");
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("""
                        {"version": 1, "name": "d", "keyedBy": "operationAndParameter"}"""))
                .withMessageContaining("no 'values'");
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("""
                        {"version": 1, "name": "d", "keyedBy": "operationAndParameter",
                         "values": {"getOwner": {"ownerId": "not a list"}}}"""))
                .withMessageContaining("other than a list")
                .withMessageContaining("'ownerId' in 'getOwner'");
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> read("""
                        {"version": 1, "name": "d", "keyBy": "operationAndParameter",
                         "values": {}}"""))
                .withMessageContaining("keyBy")
                .withMessageContaining("What belongs there is version, name, description, "
                        + "keyedBy, values");
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

    private static ValuesByKind readOwn(String text) {
        return DictionaryDocument.readOwn(text, "RESTest's own list");
    }
}

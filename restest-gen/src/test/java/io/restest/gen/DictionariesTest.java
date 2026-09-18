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

import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import io.restest.core.schema.BooleanSchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.StringSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Finding the lists of values a run should use, and saying what went wrong with the rest. */
class DictionariesTest {

    private static final ApiModel PET_SHOP = ApiModel.of("Pet shop", "1.0", List.of(
            Operation.of(HttpMethod.GET, "/owners/{ownerId}", List.of(
                    io.restest.core.model.Parameter.of("ownerId", ParameterLocation.PATH, true,
                            StringSchema.of())))));

    @Test
    @DisplayName("the list of awkward values RESTest carries can be read, and holds one for every "
            + "kind of value it might be asked about")
    void the_shipped_dictionary_is_readable() throws IOException {
        Dictionary fuzzing = Dictionaries.shipped();

        assertThat(fuzzing.name()).isEqualTo("fuzzing");
        assertThat(fuzzing.expects()).isEqualTo(Dictionary.Expectation.REFUSAL);
        // A file of ours that failed to load used to leave the tool quietly doing less than it
        // says it does, with nothing in the output to mention it. This is what makes that a broken
        // build rather than a disappointing run.
        for (CanonicalSchema kind : List.of(StringSchema.of(),
                NumberSchema.of(NumberKind.INTEGER), NumberSchema.of(NumberKind.NUMBER),
                BooleanSchema.of(), ObjectSchema.of(Map.of()),
                io.restest.core.schema.ArraySchema.of(StringSchema.of()))) {
            assertThat(fuzzing.valuesFor(asking(kind)))
                    .describedAs("nothing awkward to send for %s", kind.getClass().getSimpleName())
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("the awkward values are ones a request could actually be built with")
    void every_shipped_value_can_be_sent_somewhere() throws IOException {
        Dictionary fuzzing = Dictionaries.shipped();

        for (CanonicalSchema kind : List.of(StringSchema.of(),
                NumberSchema.of(NumberKind.INTEGER), BooleanSchema.of())) {
            assertThat(fuzzing.valuesFor(asking(kind)))
                    .describedAs("a value nothing could send would win the draw and have the whole "
                            + "attempt thrown away, so at least one has to be sendable everywhere")
                    .anyMatch(value -> RequestBuilder.canBeSentFrom(value, ParameterLocation.PATH))
                    .anyMatch(value -> RequestBuilder.canBeSentFrom(value, ParameterLocation.HEADER));
        }
    }

    @Test
    @DisplayName("a run always has the awkward values, whether or not it was given any file")
    void the_shipped_one_is_always_there(@TempDir Path directory) {
        assertThat(Dictionaries.gather(List.of(), PET_SHOP).dictionaries())
                .extracting(Dictionary::name).containsExactly("fuzzing");
        assertThat(Dictionaries.gather(List.of(), PET_SHOP).problems()).isEmpty();
    }

    @Test
    @DisplayName("a directory of files is read, every .json in it, in a settled order")
    void a_directory_is_read(@TempDir Path directory) throws IOException {
        write(directory.resolve("b-second.json"), "second", "type");
        write(directory.resolve("a-first.json"), "first", "type");
        Files.writeString(directory.resolve("notes.txt"), "not a dictionary");

        Dictionaries.Found found = Dictionaries.gather(List.of(directory), PET_SHOP);

        assertThat(found.dictionaries()).extracting(Dictionary::name)
                .describedAs("the shipped one first, then the files by name, so two runs of the "
                        + "same command read them in the same order")
                .containsExactly("fuzzing", "first", "second");
        assertThat(found.problems()).isEmpty();
    }

    @Test
    @DisplayName("a file that cannot be read costs its values and is said out loud, not the run")
    void an_unreadable_file_is_reported_and_survived(@TempDir Path directory) throws IOException {
        write(directory.resolve("good.json"), "good", "type");
        Files.writeString(directory.resolve("broken.json"), "{ this is not JSON");

        Dictionaries.Found found = Dictionaries.gather(List.of(directory), PET_SHOP);

        assertThat(found.dictionaries()).extracting(Dictionary::name)
                .containsExactly("fuzzing", "good");
        assertThat(found.problems()).singleElement(org.assertj.core.api.InstanceOfAssertFactories
                .STRING).contains("broken.json");
    }

    @Test
    @DisplayName("somewhere there is no dictionary at all is said, rather than quietly ignored")
    void a_place_with_nothing_in_it_is_reported(@TempDir Path directory) {
        assertThat(Dictionaries.gather(List.of(directory.resolve("nowhere.json")), PET_SHOP)
                .problems()).singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("no dictionary at");
        assertThat(Dictionaries.gather(List.of(directory), PET_SHOP).problems())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("holds no .json dictionary");
    }

    @Test
    @DisplayName("a dictionary written for operations this API does not have is a stale file, and "
            + "says so instead of silently doing nothing")
    void a_dictionary_naming_unknown_operations_is_reported(@TempDir Path directory)
            throws IOException {
        Files.writeString(directory.resolve("ids.json"), """
                {"version": 1, "name": "ids", "keyedBy": "operationAndParameter", "values": {
                   "GET /owners/{ownerId}": {"ownerId": [1]},
                   "getOwnerRenamedSince": {"ownerId": [2]}
                }}""");

        Dictionaries.Found found = Dictionaries.gather(List.of(directory), PET_SHOP);

        assertThat(found.dictionaries()).extracting(Dictionary::name)
                .describedAs("kept, because the entries that do match are perfectly good; whether "
                        + "they are then asked for values is the generator's business, and its own "
                        + "tests say so")
                .contains("ids");
        assertThat(found.problems()).singleElement(org.assertj.core.api.InstanceOfAssertFactories
                .STRING)
                .contains("getOwnerRenamedSince")
                .contains("never be used");
    }

    private static void write(Path file, String name, String keyedBy) throws IOException {
        Files.writeString(file, """
                {"version": 1, "name": "%s", "keyedBy": "%s", "values": {"string": ["x"]}}"""
                .formatted(name, keyedBy));
    }

    private static ValueRequest asking(CanonicalSchema schema) {
        return ValueRequest.of(OperationId.of("GET /widgets"), "q", ParameterLocation.QUERY,
                schema);
    }
}

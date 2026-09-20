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

    /**
     * One operation the document names and one it does not; a parameter with a list of values the
     * document declares and one of the same name without; a parameter with pieces inside it; and a
     * body with a piece the API only ever returns.
     */
    private static final ApiModel PET_CLINIC = ApiModel.of("Pet clinic", "1.0", List.of(
            Operation.of(HttpMethod.POST, "/owners")
                    .withId(io.restest.core.model.OperationId.of("addOwner"))
                    .withRequestBody(io.restest.core.model.RequestBodyModel.json(ObjectSchema.of(
                            Map.of("city", StringSchema.of(), "audit", onlyEverReturned())), true)),
            Operation.of(HttpMethod.GET, "/pets", List.of(
                    io.restest.core.model.Parameter.of("status", ParameterLocation.QUERY, true,
                            oneOf("available", "sold")),
                    io.restest.core.model.Parameter.of("tags", ParameterLocation.QUERY, false,
                            io.restest.core.schema.ArraySchema.of(StringSchema.of())))),
            Operation.of(HttpMethod.GET, "/pets/{petId}", List.of(
                    io.restest.core.model.Parameter.of("petId", ParameterLocation.PATH, true,
                            StringSchema.of()),
                    io.restest.core.model.Parameter.of("petId", ParameterLocation.QUERY, false,
                            oneOf("one", "two"))))));

    private static StringSchema oneOf(String... allowed) {
        return new StringSchema(io.restest.core.schema.SchemaMetadata.none().withEnumeration(
                java.util.Arrays.stream(allowed).map(io.restest.core.json.JsonValue::of)
                        .map(io.restest.core.json.JsonValue.class::cast).toList()),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.empty());
    }

    private static ObjectSchema onlyEverReturned() {
        return new ObjectSchema(io.restest.core.schema.SchemaMetadata.none()
                .withAccess(io.restest.core.schema.SchemaMetadata.Access.READ_ONLY),
                Map.of("by", StringSchema.of()), java.util.Set.of(), java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.empty());
    }


    @Test
    @DisplayName("the list of awkward values RESTest carries can be read, and holds one for every "
            + "kind of value it might be asked about")
    void the_shipped_dictionary_is_readable() throws IOException {
        Dictionary fuzzing = Dictionaries.shipped();

        assertThat(fuzzing.name())
                .describedAs("the built-in plan names this list, so the name is load-bearing")
                .isEqualTo("fuzzing");
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
    @DisplayName("what there is and whose it is are two answers that cannot come to disagree")
    void the_two_answers_are_one_answer(@TempDir Path directory) throws IOException {
        write(directory.resolve("mine.yaml"), "mine", "type");

        Dictionaries.Found found = Dictionaries.gather(List.of(directory), PET_SHOP);

        assertThat(found.dictionaries())
                .describedAs("worked out from the two halves rather than held beside them")
                .containsExactlyElementsOf(java.util.stream.Stream.concat(
                        found.shipped().stream(), found.fromTheUser().stream()).toList());
        assertThat(found.shipped()).isPresent();
        assertThat(found.namesFromTheUser()).containsExactly("mine");
    }

    @Test
    @DisplayName("a run always has the awkward values, whether or not it was given any file")
    void the_shipped_one_is_always_there(@TempDir Path directory) {
        assertThat(Dictionaries.gather(List.of(), PET_SHOP).dictionaries())
                .extracting(Dictionary::name).containsExactly("fuzzing");
        assertThat(Dictionaries.gather(List.of(), PET_SHOP).problems()).isEmpty();
    }

    @Test
    @DisplayName("a directory of files is read, every .yaml, .yml and .json in it, in a settled order")
    void a_directory_is_read(@TempDir Path directory) throws IOException {
        write(directory.resolve("b-second.yaml"), "second", "type");
        write(directory.resolve("a-first.yml"), "first", "type");
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
        write(directory.resolve("good.yaml"), "good", "type");
        Files.writeString(directory.resolve("broken.yaml"), "key: [unclosed");

        Dictionaries.Found found = Dictionaries.gather(List.of(directory), PET_SHOP);

        assertThat(found.dictionaries()).extracting(Dictionary::name)
                .containsExactly("fuzzing", "good");
        assertThat(found.problems()).singleElement(org.assertj.core.api.InstanceOfAssertFactories
                .STRING).contains("broken.yaml");
    }

    @Test
    @DisplayName("somewhere there is no dictionary at all is said, rather than quietly ignored")
    void a_place_with_nothing_in_it_is_reported(@TempDir Path directory) {
        assertThat(Dictionaries.gather(List.of(directory.resolve("nowhere.json")), PET_SHOP)
                .problems()).singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("no dictionary at");
        assertThat(Dictionaries.gather(List.of(directory), PET_SHOP).problems())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("holds no .yaml, .yml or .json dictionary");
    }

    @Test
    @DisplayName("a dictionary written for operations this API does not have is a stale file, and "
            + "says so instead of silently doing nothing")
    void a_dictionary_naming_unknown_operations_is_reported(@TempDir Path directory)
            throws IOException {
        Files.writeString(directory.resolve("ids.yaml"), """
                version: 1
                name: ids
                keyedBy: operationAndParameter
                values:
                  "GET /owners/{ownerId}":
                    ownerId: [1]
                  getOwnerRenamedSince:
                    ownerId: [2]
                """);

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

    @Test
    @DisplayName("an operation written down by its method and path is the operation, whether or "
            + "not the document also gives it an identifier")
    void an_operation_can_be_named_the_way_the_document_spells_it(@TempDir Path directory)
            throws IOException {
        Files.writeString(directory.resolve("ids.yaml"), """
                version: 1
                name: ids
                keyedBy: operationAndParameter
                values:
                  POST /owners:
                    body.city: [Seville]
                """);

        Dictionaries.Found found = Dictionaries.gather(List.of(directory), PET_CLINIC);

        assertThat(found.problems())
                .describedAs("anybody can read a method and a path off a document without having "
                        + "to check whether it declares an identifier, so both are accepted")
                .isEmpty();
        assertThat(found.fromTheUser()).singleElement()
                .extracting(dictionary -> ((ValueDictionary) dictionary).entriesByOperation()
                        .keySet())
                .describedAs("under the name the run prints, so nothing downstream has two names "
                        + "to think about")
                .isEqualTo(java.util.Set.of("addOwner"));
    }

    @Test
    @DisplayName("an entry naming a parameter or a piece of a body the operation does not have is "
            + "pointed at before a single request is sent")
    void entries_naming_nothing_are_reported(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("ids.yaml"), """
                version: 1
                name: ids
                keyedBy: operationAndParameter
                values:
                  addOwner:
                    body.city: [Seville]
                    body.postcode: ["41012"]
                    city: [Seville]
                """);

        assertThat(Dictionaries.gather(List.of(directory), PET_CLINIC).problems())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .describedAs("all of it is knowable from the document, so it is said then rather "
                        + "than after a run has been spent")
                .contains("2 of its 3 entries will never be used")
                .contains("body.postcode in addOwner")
                .contains("city in addOwner");
    }

    @Test
    @DisplayName("an entry for a place whose whole list of values the document declares is named "
            + "as one nothing will ever draw on")
    void entries_for_a_closed_list_are_reported(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("ids.yaml"), """
                version: 1
                name: ids
                keyedBy: operationAndParameter
                values:
                  GET /pets:
                    status: [whatever]
                """);

        assertThat(Dictionaries.gather(List.of(directory), PET_CLINIC).problems())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("a place whose whole list of values the document declares")
                .contains("status in GET /pets");
    }

    @Test
    @DisplayName("an entry for a piece of a body is named as unused when the same file also gives "
            + "that body whole")
    void pieces_of_a_body_given_whole_are_reported(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("ids.yaml"), """
                version: 1
                name: ids
                keyedBy: operationAndParameter
                values:
                  addOwner:
                    body: [{city: Seville}]
                    body.city: [Cordoba]
                """);

        assertThat(Dictionaries.gather(List.of(directory), PET_CLINIC).problems())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("supplied whole")
                .contains("body.city in addOwner");
    }

    @Test
    @DisplayName("a file whose every entry names a place the document has is not remarked upon")
    void a_file_that_is_right_is_left_alone(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("ids.yaml"), """
                version: 1
                name: ids
                keyedBy: operationAndParameter
                values:
                  addOwner:
                    body: [{city: Seville}]
                  GET /pets: {}
                """);

        assertThat(Dictionaries.gather(List.of(directory), PET_CLINIC).problems()).isEmpty();
    }

    @Test
    @DisplayName("an entry for something inside a parameter is not accused of naming nothing: a "
            + "parameter has pieces too, and values written for them are sent")
    void entries_inside_a_parameter_are_left_alone(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("ids.yaml"), """
                version: 1
                name: ids
                keyedBy: operationAndParameter
                values:
                  GET /pets:
                    "tags[]": [urgent]
                """);

        assertThat(Dictionaries.gather(List.of(directory), PET_CLINIC).problems())
                .describedAs("saying nothing is always safe here and saying the wrong thing is "
                        + "not: this entry fills every element of that list and is used")
                .isEmpty();
    }

    @Test
    @DisplayName("a name the document declares twice, with a closed list on only one of them, is "
            + "not judged: one entry feeds both")
    void a_name_declared_twice_is_not_judged(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("ids.yaml"), """
                version: 1
                name: ids
                keyedBy: operationAndParameter
                values:
                  GET /pets/{petId}:
                    petId: [7]
                """);

        assertThat(Dictionaries.gather(List.of(directory), PET_CLINIC).problems())
                .describedAs("the one in the query has a closed list and the one in the path does "
                        + "not, and this entry is what fills the path")
                .isEmpty();
    }

    @Test
    @DisplayName("an entry for a piece of a body the document says the API only ever sends back "
            + "is named as one nothing will use, and so is anything under it")
    void entries_under_a_read_only_property_are_reported(@TempDir Path directory)
            throws IOException {
        Files.writeString(directory.resolve("ids.yaml"), """
                version: 1
                name: ids
                keyedBy: operationAndParameter
                values:
                  addOwner:
                    body.audit.by: [somebody]
                """);

        assertThat(Dictionaries.gather(List.of(directory), PET_CLINIC).problems())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .describedAs("the document has that place; what it has not is any intention of "
                        + "being sent it")
                .contains("only ever sends back")
                .doesNotContain("no such parameter");
    }

    @Test
    @DisplayName("every entry under an operation this API does not have is counted, not the "
            + "operation")
    void a_stale_operation_costs_all_of_its_entries(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("ids.yaml"), """
                version: 1
                name: ids
                keyedBy: operationAndParameter
                values:
                  addOwner:
                    body.city: [Seville]
                  renamedSince:
                    a: [1]
                    b: [2]
                    c: [3]
                """);

        assertThat(Dictionaries.gather(List.of(directory), PET_CLINIC).problems())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .describedAs("three entries are dead, and saying one would under-report a whole "
                        + "block of them")
                .contains("3 of its 4 entries will never be used");
    }

    @DisplayName("a piece of a body is named as unused when any list this run holds gives that "
            + "body whole, whichever of the two files is read first")
    @org.junit.jupiter.params.ParameterizedTest(name = "{0} read first")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"whole", "pieces"})
    void pieces_of_a_body_given_whole_in_another_file_are_reported(String readFirst,
            @TempDir Path directory) throws IOException {
        // A directory is read in name order, so the name is how the order is chosen here.
        boolean wholeFirst = readFirst.equals("whole");
        Files.writeString(directory.resolve((wholeFirst ? "a" : "b") + "-whole.yaml"), """
                version: 1
                name: whole
                keyedBy: operationAndParameter
                values:
                  addOwner:
                    body: [{city: Seville}]
                """);
        Files.writeString(directory.resolve((wholeFirst ? "b" : "a") + "-pieces.yaml"), """
                version: 1
                name: pieces
                keyedBy: operationAndParameter
                values:
                  addOwner:
                    body.city: [Cordoba]
                """);

        assertThat(Dictionaries.gather(List.of(directory), PET_CLINIC).problems())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .describedAs("the precedence is between the lists a run holds, so the report has "
                        + "to be too, and it cannot depend on which file was read first")
                .contains("-pieces.yaml")
                .contains("supplied whole");
    }

    @DisplayName("one operation written under both of the names it answers to is said out loud, "
            + "and the entries under its identifier are the ones kept, whichever came first")
    @org.junit.jupiter.params.ParameterizedTest(name = "{0} first")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"POST /owners", "addOwner"})
    void one_operation_written_both_ways_is_reported(String first, @TempDir Path directory)
            throws IOException {
        String second = first.equals("addOwner") ? "POST /owners" : "addOwner";
        Files.writeString(directory.resolve("ids.yaml"), """
                version: 1
                name: ids
                keyedBy: operationAndParameter
                values:
                  "%s":
                    body.city: [%s]
                  "%s":
                    body.city: [%s]
                """.formatted(first, valueUnder(first), second, valueUnder(second)));

        Dictionaries.Found found = Dictionaries.gather(List.of(directory), PET_CLINIC);

        assertThat(found.problems())
                .describedAs("a file can say the same thing twice without the duplicate-key check "
                        + "seeing it, because the two keys are different strings")
                .anyMatch(problem -> problem.contains("both of the names it answers to"));
        assertThat(((ValueDictionary) found.fromTheUser().get(0)).entriesByOperation())
                .containsOnlyKeys("addOwner")
                .extractingByKey("addOwner")
                .extracting(entries -> entries.get("body.city"))
                .describedAs("which spelling wins cannot depend on which the file wrote first")
                .isEqualTo(List.of(io.restest.core.json.JsonValue.of("fromTheIdentifier")));
    }

    @Test
    @DisplayName("nothing under a shape that contains itself is called wrong, however deep the "
            + "entry goes")
    void a_shape_that_contains_itself_is_not_judged(@TempDir Path directory) throws IOException {
        ApiModel nested = ApiModel.of("Trees", "1.0", List.of(
                Operation.of(HttpMethod.POST, "/trees")
                        .withRequestBody(io.restest.core.model.RequestBodyModel.json(
                                new io.restest.core.schema.SchemaReference(
                                        io.restest.core.schema.SchemaMetadata.none(), "Node"),
                                true))))
                .withSchemas(Map.of("Node", ObjectSchema.of(Map.of(
                        "name", StringSchema.of(),
                        "child", new io.restest.core.schema.SchemaReference(
                                io.restest.core.schema.SchemaMetadata.none(), "Node")))));
        Files.writeString(directory.resolve("ids.yaml"), """
                version: 1
                name: ids
                keyedBy: operationAndParameter
                values:
                  POST /trees:
                    body.child.name: [a name one level down]
                    body.child.child.anything: [below where the shape repeats]
                """);

        assertThat(Dictionaries.gather(List.of(directory), nested).problems())
                .describedAs("the names below the point where a shape starts repeating go on for "
                        + "ever, so nothing down there can be called wrong")
                .isEmpty();
    }

    @Test
    @DisplayName("a misspelling above the point where a shape repeats is still caught: not being "
            + "able to judge one part of a document is not a reason to stop judging the rest")
    void a_misspelling_above_the_repeat_is_still_caught(@TempDir Path directory) throws IOException {
        ApiModel nested = ApiModel.of("Trees", "1.0", List.of(
                Operation.of(HttpMethod.POST, "/trees")
                        .withRequestBody(io.restest.core.model.RequestBodyModel.json(
                                new io.restest.core.schema.SchemaReference(
                                        io.restest.core.schema.SchemaMetadata.none(), "Node"),
                                true))))
                .withSchemas(Map.of("Node", ObjectSchema.of(Map.of(
                        "name", StringSchema.of(),
                        "child", new io.restest.core.schema.SchemaReference(
                                io.restest.core.schema.SchemaMetadata.none(), "Node")))));
        Files.writeString(directory.resolve("ids.yaml"), """
                version: 1
                name: ids
                keyedBy: operationAndParameter
                values:
                  POST /trees:
                    body.child.nonsense: [a name Node does not have]
                """);

        assertThat(Dictionaries.gather(List.of(directory), nested).problems())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("body.child.nonsense in POST /trees");
    }

    @Test
    @DisplayName("nothing under a shape the document leaves open is called wrong either")
    void a_shape_that_says_nothing_is_not_judged(@TempDir Path directory) throws IOException {
        ApiModel anything = ApiModel.of("Anything", "1.0", List.of(
                Operation.of(HttpMethod.POST, "/things")
                        .withRequestBody(io.restest.core.model.RequestBodyModel.json(
                                io.restest.core.schema.AnySchema.of(), true))));
        Files.writeString(directory.resolve("ids.yaml"), """
                version: 1
                name: ids
                keyedBy: operationAndParameter
                values:
                  POST /things:
                    body.whatever: [a value]
                """);

        assertThat(Dictionaries.gather(List.of(directory), anything).problems())
                .describedAs("a document that names nothing has ruled nothing out")
                .isEmpty();
    }

    @Test
    @DisplayName("a closed list inside a body settles that place too, whether the document writes "
            + "it there or points at a shape that has one")
    void a_closed_list_inside_a_body_is_reported(@TempDir Path directory) throws IOException {
        ApiModel pets = ApiModel.of("Pets", "1.0", List.of(
                Operation.of(HttpMethod.POST, "/pets")
                        .withRequestBody(io.restest.core.model.RequestBodyModel.json(
                                ObjectSchema.of(Map.of(
                                        "status", oneOf("available", "sold"),
                                        "kind", new io.restest.core.schema.SchemaReference(
                                                io.restest.core.schema.SchemaMetadata.none(),
                                                "Kind"))),
                                true))))
                .withSchemas(Map.of("Kind", oneOf("dog", "cat")));
        Files.writeString(directory.resolve("ids.yaml"), """
                version: 1
                name: ids
                keyedBy: operationAndParameter
                values:
                  POST /pets:
                    body.status: [neverSent]
                    body.kind: [neverSentEither]
                """);

        assertThat(Dictionaries.gather(List.of(directory), pets).problems())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .describedAs("a list of allowed values on the far side of a pointer is still a "
                        + "list of allowed values")
                .contains("2 for a place whose whole list of values the document declares")
                .contains("body.status in POST /pets")
                .contains("body.kind in POST /pets");
    }

    @Test
    @DisplayName("a choice between shapes settles nothing, however closed one of its branches is")
    void a_choice_between_shapes_is_not_reported(@TempDir Path directory) throws IOException {
        ApiModel pets = ApiModel.of("Pets", "1.0", List.of(
                Operation.of(HttpMethod.POST, "/pets")
                        .withRequestBody(io.restest.core.model.RequestBodyModel.json(
                                ObjectSchema.of(Map.of("label",
                                        new io.restest.core.schema.ChoiceSchema(
                                                io.restest.core.schema.SchemaMetadata.none(),
                                                List.of(oneOf("red", "blue"),
                                                        StringSchema.of())))),
                                true))));
        Files.writeString(directory.resolve("ids.yaml"), """
                version: 1
                name: ids
                keyedBy: operationAndParameter
                values:
                  POST /pets:
                    body.label: [green]
                """);

        assertThat(Dictionaries.gather(List.of(directory), pets).problems())
                .describedAs("what is asked about is the choice, whose own list of values is "
                        + "empty, so this entry is used and must not be called dead")
                .isEmpty();
    }

    @Test
    @DisplayName("a body the document writes out in full is sent as written, so the entries for "
            + "its pieces are named as ones nothing will use")
    void pieces_of_a_body_the_document_shows_in_full_are_reported(@TempDir Path directory)
            throws IOException {
        ApiModel owners = ApiModel.of("Owners", "1.0", List.of(
                Operation.of(HttpMethod.POST, "/owners")
                        .withRequestBody(new io.restest.core.model.RequestBodyModel(true,
                                Map.of("application/json", new io.restest.core.model.BodyContent(
                                        ObjectSchema.of(Map.of("city", StringSchema.of())),
                                        List.of(io.restest.core.json.JsonValue.object(Map.of(
                                                "city", io.restest.core.json.JsonValue
                                                        .of("Madison")))))),
                                java.util.Optional.empty()))));
        Files.writeString(directory.resolve("ids.yaml"), """
                version: 1
                name: ids
                keyedBy: operationAndParameter
                values:
                  POST /owners:
                    body.city: [Seville]
                """);

        assertThat(Dictionaries.gather(List.of(directory), owners).problems())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .describedAs("the sample is sent as the author wrote it, so the request is never "
                        + "taken apart and nothing inside it is asked for")
                .contains("writes out in full")
                .contains("body.city in POST /owners");
    }

    @Test
    @DisplayName("a sample body written on the shape counts as one the document shows in full, "
            + "just as one written beside the media type does")
    void a_sample_on_the_shape_settles_the_pieces_too(@TempDir Path directory) throws IOException {
        ApiModel owners = ApiModel.of("Owners", "1.0", List.of(
                Operation.of(HttpMethod.POST, "/owners")
                        .withRequestBody(io.restest.core.model.RequestBodyModel.json(
                                new ObjectSchema(io.restest.core.schema.SchemaMetadata.none()
                                        .withExamples(List.of(io.restest.core.json.JsonValue.object(
                                                Map.of("city", io.restest.core.json.JsonValue
                                                        .of("Madison"))))),
                                        Map.of("city", StringSchema.of()), java.util.Set.of(),
                                        java.util.Optional.empty(), java.util.Optional.empty(),
                                        java.util.Optional.empty()),
                                true))));
        Files.writeString(directory.resolve("ids.yaml"), """
                version: 1
                name: ids
                keyedBy: operationAndParameter
                values:
                  POST /owners:
                    body.city: [Seville]
                """);

        assertThat(Dictionaries.gather(List.of(directory), owners).problems())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .describedAs("whoever reads samples falls back from the media type's to the "
                        + "shape's, so both keep the request whole")
                .contains("writes out in full")
                .contains("body.city in POST /owners");
    }

    @Test
    @DisplayName("two lists answering to one name is said out loud, because a name is how a plan "
            + "picks one and how a report names one")
    void two_lists_with_one_name_are_reported(@TempDir Path directory) throws IOException {
        write(directory.resolve("a.yaml"), "same", "type");
        write(directory.resolve("b.yaml"), "same", "name");

        assertThat(Dictionaries.gather(List.of(directory), PET_SHOP).problems())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("more than one list of values is called 'same'");
    }

    @Test
    @DisplayName("a list of your own called what the built-in plan names is not a mistake, and is "
            + "not reported as one")
    void the_one_collision_the_format_blesses_is_silent(@TempDir Path directory) throws IOException {
        write(directory.resolve("mine.yaml"), "fuzzing", "type");

        Dictionaries.Found found = Dictionaries.gather(List.of(directory), PET_SHOP);

        assertThat(found.dictionaries()).extracting(Dictionary::name)
                .containsExactly("fuzzing", "fuzzing");
        assertThat(found.problems())
                .describedAs("the documented way to have a list of your own pushed at an API is to "
                        + "call it this, so saying it is wrong would make the instructions "
                        + "impossible to follow")
                .isEmpty();
    }

    @Test
    @DisplayName("a list holding values YAML would have turned into something else keeps them")
    void a_list_of_ordinary_words_survives_being_read(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("codes.yaml"), """
                version: 1
                name: codes
                keyedBy: name
                values:
                  country: [NO, SE, "ON"]
                """);

        Dictionary codes = Dictionaries.gather(List.of(directory), PET_SHOP).fromTheUser().get(0);

        assertThat(codes.valuesFor(ValueRequest.of(OperationId.of("GET /x"), "country",
                ParameterLocation.QUERY, StringSchema.of())))
                .describedAs("Norway is a country, not the word false")
                .containsExactly(JsonValue.of("NO"), JsonValue.of("SE"), JsonValue.of("ON"));
    }

    /** Which value goes under which spelling, so that the one that wins names itself. */
    private static String valueUnder(String spelling) {
        return spelling.equals("addOwner") ? "fromTheIdentifier" : "fromMethodAndPath";
    }

    private static void write(Path file, String name, String keyedBy) throws IOException {
        Files.writeString(file, """
                version: 1
                name: %s
                keyedBy: %s
                values:
                  string: [x]
                """.formatted(name, keyedBy));
    }

    private static ValueRequest asking(CanonicalSchema schema) {
        return ValueRequest.of(OperationId.of("GET /widgets"), "q", ParameterLocation.QUERY,
                schema);
    }
}

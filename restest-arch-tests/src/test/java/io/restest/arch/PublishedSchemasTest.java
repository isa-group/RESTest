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
package io.restest.arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.path.PathType;
import io.restest.core.json.JsonException;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.json.YamlText;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.gen.Campaigns;
import io.restest.gen.Dictionaries;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Holds the JSON Schemas RESTest publishes to the readers they describe.
 *
 * <p>A JSON Schema is a file that describes the shape of another file. RESTest publishes one for a
 * dictionary - the lists of values somebody hands a run - and one for a plan, so that a person's
 * editor, or a language model asked to write one, gets the shape right before a run rather than
 * after it. A schema that disagrees with RESTest's own reader is worse than none: it passes a file
 * the run then refuses, or refuses one the run would have read. So this asks both of them about the
 * same files and requires the same answer - about every whole example the documentation shows,
 * about the plan RESTest carries, and about files that each break one thing in a valid one - and
 * says exactly where they are allowed to differ: what only RESTest can judge, such as whether the
 * shares of a plan add up to a hundred, and the one place the schema is stricter.
 *
 * <p>It also keeps each schema to what the engines that hold a language model to a schema accept,
 * and checks that the address a schema gives itself names the version it ships with.
 *
 * <p>A file is read the way RESTest reads it, with {@link YamlText}, and handed to the validator as
 * the JSON {@link JsonText} writes of it, so that both judge the same values. RESTest's verdict is
 * the one a run reaches, from {@link Dictionaries#gather} and {@link Campaigns#gather}.
 */
class PublishedSchemasTest {

    /** The address a schema gives itself: the file, at the tag of the version it ships with. */
    private static final Pattern ID = Pattern.compile(
            "https://raw\\.githubusercontent\\.com/isa-group/RESTest/v([0-9.]+)/schemas/(.+)");

    /** The draft of JSON Schema both are written in, which editors and the engines all read. */
    private static final String DRAFT_07 = "http://json-schema.org/draft-07/schema#";

    /**
     * Every keyword a schema may use: the ones the open engines that hold a language model to a
     * schema all accept. Never oneOf, allOf, not, if, patternProperties or pattern.
     */
    private static final Set<String> KEYWORDS = Set.of("$schema", "$id", "$ref", "title",
            "description", "definitions", "type", "properties", "required",
            "additionalProperties", "items", "minItems", "enum", "const", "anyOf",
            "minLength", "minimum", "maximum");

    /** The keywords that only describe, and that some engines refuse on a schema with no type. */
    private static final Set<String> ANNOTATIONS = Set.of("title", "description");

    /** The pages that show a whole dictionary or plan, each of which must still show one. */
    private static final List<String> PAGES_WITH_A_WHOLE_FILE = List.of("README.md",
            "docs/dictionary-format.md", "docs/campaign-format.md", "docs/switches.md",
            "docs/manual/06-dictionaries.md", "docs/schemas.md");

    /** An API with no operations: every file is judged for its shape, not against an API. */
    private static final ApiModel NO_API = ApiModel.of("no API", "1", List.of());

    /**
     * The validator, which never fetches anything, explains itself in English, and names a place
     * in a file as an ordinary validator prints it to a person: {@code $.strategies[0].kind}.
     */
    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(
            Dialects.getDraft7(), builder -> builder
                    .schemaLoader(loader -> loader.fetchRemoteResources(false))
                    .schemaRegistryConfig(SchemaRegistryConfig.builder()
                            .locale(Locale.ROOT)
                            .pathType(PathType.LEGACY)
                            .build()));

    private static final Map<Format, Schema> CHECKERS = new EnumMap<>(Format.class);

    /** The group of sources the valid plan chooses between. */
    private static final String GROUP = "{example: 20, random: 30, dictionaries: 50}";

    /** Rows below whose refusals list the words RESTest has for what was misspelt. */
    private static final Change KIND_MISSPELT = new Change(Format.PLAN, "a kind misspelt",
            "kind: nominal", "kind: nominl", "of kind 'nominl'", "$.strategies[0].kind enum");
    private static final Change WORD_MISSPELT = new Change(Format.PLAN, "a word misspelt",
            "- source: random", "- source: exmaple", "'exmaple' is not one of RESTest's own sources",
            "$.strategies[1].sources[0].source enum");
    private static final Change METHOD_UNKNOWN = new Change(Format.PLAN,
            "a method that is not one", "[GET, POST]", "[GET, FETCH]",
            "'FETCH' is not an HTTP method", "$.operations.methods[1] enum");
    private static final Change DICTIONARY_MEMBER_UNKNOWN = new Change(Format.DICTIONARY,
            "a member nobody recognises", "name: owners\n", "name: owners\nlabel: owners\n",
            "does not recognise (label)", "$ additionalProperties");
    private static final Change PLAN_MEMBER_UNKNOWN = new Change(Format.PLAN,
            "a member nobody recognises", "operations:", "operation:",
            "does not recognise (operation)", "$ additionalProperties");
    private static final Change STRATEGY_MEMBER_UNKNOWN = new Change(Format.PLAN,
            "version 1's way of saying a strategy changes accepted requests",
            "    kind: nominal\n", "    kind: nominal\n    mutates: accepted\n",
            "does not recognise (mutates)", "$.strategies[0] additionalProperties");
    private static final Change SOURCE_MEMBER_UNKNOWN = new Change(Format.PLAN,
            "a source named by a misspelt word", "- source: random", "- sorce: random",
            "does not recognise (sorce)", "$.strategies[1].sources[0] additionalProperties");
    private static final Change OPERATIONS_MEMBER_UNKNOWN = new Change(Format.PLAN,
            "a member of operations nobody recognises", "  methods: [GET, POST]\n",
            "  methods: [GET, POST]\n  except: [getOwner]\n", "does not recognise (except)",
            "$.operations additionalProperties");

    @TempDir
    static Path files;

    /** The two kinds of file RESTest reads that have a schema. */
    enum Format {
        DICTIONARY("dictionary.schema.json", """
                version: 1
                name: owners
                description: Owners the clinic knows.
                keyedBy: operationAndParameter
                values:
                  getOwner:
                    ownerId: [1, 2, 3]
                  addOwner:
                    body.lastName: [Franklin, Davis]
                """),
        PLAN("campaign.schema.json", """
                version: 2
                strategies:
                  - name: nominal
                    share: 70
                    kind: nominal
                    sources:
                      - source: enum
                      - weighted: {example: 20, random: 30, dictionaries: 50}
                  - name: fuzzing
                    share: 30
                    kind: fuzzing
                    sources:
                      - source: random
                operations:
                  methods: [GET, POST]
                """);

        private final String file;
        private final String valid;

        Format(String file, String valid) {
            this.file = file;
            this.valid = valid;
        }

        /** The schema as it is written in the repository. */
        String written() {
            return RepositoryRoot.read(RepositoryRoot.locate().resolve("schemas").resolve(file));
        }

        /** The schema, read. */
        JsonValue.JsonObject schema() {
            return YamlText.asObject(JsonText.read(written()), "the schema", "schemas/" + file);
        }
    }

    /**
     * One file made from a valid one by one change, and what each reader is expected to say of it.
     *
     * @param format the kind of file changed, whose valid example the change starts from
     * @param what the change, in words, which names the row in a report
     * @param from what is changed, written once in that example
     * @param to what it is changed into
     * @param restestSays what RESTest's refusal, or one of the problems it reports, contains
     * @param breaks where the validator says the file is wrong and the rule of the schema it breaks
     *     there, such as {@code $.version const}, when it is wrong
     */
    record Change(Format format, String what, String from, String to, String restestSays,
            String breaks) {

        /** A change both readers take. */
        Change(Format format, String what, String from, String to) {
            this(format, what, from, to, null, null);
        }

        /** A change the schema takes and RESTest refuses, or reports. */
        Change(Format format, String what, String from, String to, String restestSays) {
            this(format, what, from, to, restestSays, null);
        }

        String file() {
            assertThat(format.valid.indexOf(from))
                    .describedAs("'%s' is supposed to change '%s', written once in the valid %s",
                            what, from, format)
                    .isNotNegative()
                    .isEqualTo(format.valid.lastIndexOf(from));
            return format.valid.replace(from, to);
        }

        Verdict restest() {
            return PublishedSchemasTest.restest(format, file());
        }

        /** Each place the validator says the file is wrong, with the rule it breaks there. */
        List<String> errors() {
            return validate(format, file()).stream()
                    .map(error -> error.getInstanceLocation() + " " + error.getKeyword())
                    .toList();
        }

        @Override
        public String toString() {
            return what;
        }
    }

    /** What RESTest made of a file: why it refused it, if it did, and what it said about it. */
    record Verdict(Optional<String> refusal, List<String> problems) {
    }

    @ParameterizedTest
    @EnumSource(Format.class)
    @DisplayName("each schema is a JSON Schema of draft-07, which every editor and engine reads")
    void each_schema_is_a_draft_07_schema(Format format) {
        String written = format.written();

        assertThat(REGISTRY.getSchema(SchemaLocation.of(DRAFT_07))
                .validate(written, InputFormat.JSON))
                .describedAs("schemas/%s is not a valid JSON Schema of draft-07", format.file)
                .isEmpty();
        assertThat(text(YamlText.asObject(JsonText.read(written), "the schema", format.file),
                "$schema"))
                .describedAs("schemas/%s says which draft it is written in", format.file)
                .isEqualTo(DRAFT_07);
    }

    @Test
    @DisplayName("the address each schema gives itself is its file at the tag of the version being "
            + "prepared, the same for both, so that an editor fetching it gets this version's")
    void each_schema_names_the_version_it_ships_with() {
        String checkout = RepositoryRoot.version();
        boolean release = !checkout.endsWith("-SNAPSHOT");
        ModuleDescriptor.Version thisCheckout = ModuleDescriptor.Version.parse(checkout);
        List<String> versions = new ArrayList<>();
        for (Format format : Format.values()) {
            String id = text(format.schema(), "$id");
            Matcher named = ID.matcher(id);
            String shouldBe = "https://raw.githubusercontent.com/isa-group/RESTest/v"
                    + checkout.replace("-SNAPSHOT", "") + "/schemas/" + format.file;
            assertThat(named.matches() && named.group(2).equals(format.file))
                    .describedAs("schemas/%s gives itself the address %s, which is not its own "
                            + "file at a version's tag, such as %s", format.file, id, shouldBe)
                    .isTrue();
            ModuleDescriptor.Version said = ModuleDescriptor.Version.parse(named.group(1));
            if (release) {
                // Exactly as written, not as equal numbers: v2.2 names no tag of 2.2.0.
                assertThat(named.group(1))
                        .describedAs("schemas/%s names version %s, and this checkout is the "
                                + "release of %s: write %s", format.file, said, checkout, shouldBe)
                        .isEqualTo(checkout);
            } else {
                assertThat(said)
                        .describedAs("schemas/%s names version %s, older than this checkout's %s: "
                                + "name the release being prepared, such as %s", format.file, said,
                                checkout, shouldBe)
                        .isGreaterThan(thisCheckout);
            }
            versions.add(named.group(1));
        }
        assertThat(versions.stream().distinct())
                .describedAs("the schemas name different versions (%s), and they ship together",
                        versions)
                .hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(Format.class)
    @DisplayName("each schema uses only what the engines that hold a language model to a schema "
            + "accept, and describes every member")
    void each_schema_keeps_to_what_the_engines_accept(Format format) {
        JsonValue.JsonObject schema = format.schema();
        List<String> wrong = new ArrayList<>();
        checkSubschema(schema, "#", schema, wrong);

        assertThat(wrong)
                .describedAs("schemas/%s strays from what the open engines (llama.cpp, XGrammar, "
                        + "Outlines, llguidance) all accept", format.file)
                .isEmpty();
    }

    @Test
    @DisplayName("every whole dictionary and plan the documentation shows is read by RESTest and "
            + "valid by its schema, and so is the plan RESTest carries")
    void every_whole_example_is_read_and_valid() throws IOException {
        Path root = RepositoryRoot.locate();
        List<String> wrong = new ArrayList<>();
        List<String> showing = new ArrayList<>();
        for (Path page : pages(root)) {
            String where = root.relativize(page).toString().replace('\\', '/');
            for (YamlBlocks.Block block : YamlBlocks.on(
                    RepositoryRoot.read(page).replace("\r\n", "\n"))) {
                Set<String> members = YamlBlocks.members(block.text());
                if (!members.contains("version") && !startsWithItsVersion(block.text())) {
                    // A piece of a file, which shows one part of the format rather than a file.
                    continue;
                }
                showing.add(where);
                if (members.isEmpty()) {
                    wrong.add(where + " shows a whole file that is not YAML RESTest can read: "
                            + whyNotRead(block.text()));
                    continue;
                }
                Optional<Format> format = formatOf(members);
                if (format.isEmpty()) {
                    wrong.add(where + " shows a whole file that is neither a dictionary nor a "
                            + "plan, which this test should be taught to check");
                    continue;
                }
                judge(format.get(), block.text(), where).ifPresent(wrong::add);
            }
        }
        judge(Format.PLAN, Campaigns.shippedText(), "the plan RESTest carries")
                .ifPresent(wrong::add);

        assertThat(wrong).describedAs("whole files the documentation shows").isEmpty();
        assertThat(showing)
                .describedAs("every one of these pages showed a whole file; a page that shows none "
                        + "now, or a way of finding them that finds none, proves nothing")
                .containsAll(PAGES_WITH_A_WHOLE_FILE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lenient")
    @DisplayName("what RESTest reads although it is written loosely is valid by the schema too")
    void what_restest_reads_loosely_is_valid(Change change) {
        assertThat(change.restest().refusal())
                .describedAs("RESTest reads a file with %s", change.what)
                .isEmpty();
        assertThat(change.errors())
                .describedAs("the schema refuses a file with %s, which RESTest reads", change.what)
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenShapes")
    @DisplayName("a file RESTest refuses for its shape is invalid by the schema, which names the "
            + "place and the rule")
    void a_file_refused_for_its_shape_is_invalid(Change change) {
        assertRefusedByRestest(change);
        assertThat(change.errors())
                .describedAs("the validator says where a file with %s is wrong", change.what)
                .contains(change.breaks);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("onlyRestestJudges")
    @DisplayName("what only RESTest can judge, such as shares that do not add up to a hundred, is "
            + "refused by RESTest and valid by the schema, which says so in words")
    void what_only_restest_judges_is_valid_by_the_schema(Change change) {
        assertRefusedByRestest(change);
        assertThat(change.errors())
                .describedAs("a file with %s is valid by the schema, which leaves it to RESTest; "
                        + "if the schema can say it now, move the row", change.what)
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("onlyReported")
    @DisplayName("what RESTest reads and only reports, such as a list nobody handed over, is valid "
            + "by the schema")
    void what_restest_only_reports_is_valid_by_the_schema(Change change) {
        Verdict verdict = change.restest();

        assertThat(verdict.refusal())
                .describedAs("RESTest reads a file with %s, and says so", change.what)
                .isEmpty();
        assertThat(verdict.problems())
                .describedAs("RESTest reports a file with %s", change.what)
                .anySatisfy(problem -> assertThat(problem).contains(change.restestSays));
        assertThat(change.errors())
                .describedAs("a file with %s is valid by the schema", change.what)
                .isEmpty();
    }

    @Test
    @DisplayName("a method written neither in capitals nor in small letters is read by RESTest and "
            + "invalid by the schema, the one place the schema is stricter")
    void a_method_in_mixed_case_is_read_and_invalid() {
        Change change = new Change(Format.PLAN, "a method in mixed case", "[GET, POST]",
                "[Get, POST]");

        assertThat(change.restest().refusal()).isEmpty();
        assertThat(change.errors()).contains("$.operations.methods[0] enum");
    }

    @Test
    @DisplayName("the words a schema offers for a kind, a source and a method are the words "
            + "RESTest tells a person who misspells one")
    void the_closed_lists_are_restests_words() {
        JsonValue.JsonObject definitions = object(Format.PLAN.schema(), "definitions");

        assertThat(words(object(definitions, "strategy", "properties", "kind")))
                .isEqualTo(listAfter(refusalOf(KIND_MISSPELT), "kind is one of "));
        List<String> sources = listAfter(refusalOf(WORD_MISSPELT), "sources, which are ");
        assertThat(words(object(definitions, "source", "properties", "source")))
                .isEqualTo(sources);
        assertThat(object(definitions, "group", "properties").members().keySet().stream()
                .filter(word -> !word.equals("dictionary")))
                .containsExactlyElementsOf(sources);
        List<String> methods = listAfter(refusalOf(METHOD_UNKNOWN), "They are ");
        assertThat(methods).isEqualTo(Arrays.stream(HttpMethod.values()).map(Enum::name).toList());
        assertThat(words(object(definitions, "operations", "properties", "methods", "items")))
                .isEqualTo(Stream.concat(methods.stream(),
                        methods.stream().map(method -> method.toLowerCase(Locale.ROOT))).toList());
    }

    @Test
    @DisplayName("the members a schema allows in each part of a file are the members RESTest names "
            + "when it refuses one it does not recognise there")
    void the_members_are_restests_members() {
        JsonValue.JsonObject dictionary = Format.DICTIONARY.schema();
        JsonValue.JsonObject plan = Format.PLAN.schema();
        JsonValue.JsonObject definitions = object(plan, "definitions");

        assertSameMembers(object(dictionary, "properties").members().keySet(),
                DICTIONARY_MEMBER_UNKNOWN, "a dictionary");
        assertSameMembers(object(plan, "properties").members().keySet(), PLAN_MEMBER_UNKNOWN,
                "a plan");
        assertSameMembers(object(definitions, "strategy", "properties").members().keySet(),
                STRATEGY_MEMBER_UNKNOWN, "a strategy");
        assertSameMembers(object(definitions, "operations", "properties").members().keySet(),
                OPERATIONS_MEMBER_UNKNOWN, "'operations'");
        // A source is written one of three ways, each an object with its one member.
        assertSameMembers(Stream.of("source", "dictionary", "weighted").map(way ->
                strings(object(definitions, way).member("required").orElseThrow()).getFirst())
                .toList(), SOURCE_MEMBER_UNKNOWN, "a source");
        assertThat(YamlText.asList(object(definitions, "step").member("anyOf").orElseThrow(),
                "'anyOf'", "a schema").stream()
                .map(way -> text(asObject(way, "a way of writing a source"), "$ref")))
                .containsExactly("#/definitions/source", "#/definitions/dictionary",
                        "#/definitions/weighted");
    }

    static Stream<Change> lenient() {
        Format d = Format.DICTIONARY;
        Format p = Format.PLAN;
        return Stream.of(
                new Change(d, "values with nothing under them",
                        d.valid.substring(d.valid.indexOf("values:")),
                        "values:\n  # to be filled in\n"),
                new Change(d, "an operation with only a comment under it",
                        "  addOwner:\n    body.lastName: [Franklin, Davis]\n",
                        "  addOwner:\n    # nothing to fill\n"),
                new Change(d, "a description with nothing after it",
                        "description: Owners the clinic knows.", "description:"),
                new Change(d, "the whole file written as JSON", d.valid, """
                        {"version": 1, "name": "owners", "keyedBy": "operationAndParameter",
                         "values": {"getOwner": {"ownerId": [1, 2, 3]}}}
                        """),
                new Change(d, "values of every kind", "[1, 2, 3]",
                        "[1, \"two\", 3.5, true, null, [], {owner: {id: 1}}]"),
                new Change(p, "methods in small letters", "[GET, POST]", "[get, post]"),
                new Change(p, "a group's sources in another order", GROUP,
                        "{dictionaries: 50, random: 30, example: 20}"),
                new Change(p, "dictionaries by name in a group", "dictionaries: 50",
                        "dictionary: {owners: 30, pets: 20}"),
                new Change(p, "no operations", "operations:\n  methods: [GET, POST]\n", ""));
    }

    static Stream<Change> brokenShapes() {
        Format d = Format.DICTIONARY;
        Format p = Format.PLAN;
        String values = d.valid.substring(d.valid.indexOf("values:"));
        String strategies = p.valid.substring(p.valid.indexOf("strategies:"),
                p.valid.indexOf("operations:"));
        return Stream.of(
                new Change(d, "no version", "version: 1\n", "", "no 'version'", "$ required"),
                new Change(d, "version 2", "version: 1", "version: 2",
                        "in version 2 of the format", "$.version const"),
                new Change(d, "the version written as text", "version: 1", "version: \"1\"",
                        "'version' is not a number", "$.version type"),
                new Change(d, "no name", "name: owners\n", "", "no 'name'", "$ required"),
                new Change(d, "a name that is a number", "name: owners", "name: 7",
                        "'name' is not text", "$.name type"),
                new Change(d, "a description that is a list",
                        "description: Owners the clinic knows.", "description: [owners, pets]",
                        "'description' is not text", "$.description type"),
                new Change(d, "values keyed by kind of value", "keyedBy: operationAndParameter",
                        "keyedBy: type", "keyed by 'type'", "$.keyedBy const"),
                new Change(d, "no values", values, "", "no 'values'", "$ required"),
                new Change(d, "values written as a list", values, "values: [1, 2]\n",
                        "the values of a dictionary is not an object", "$.values type"),
                DICTIONARY_MEMBER_UNKNOWN,
                new Change(d, "a $schema member", "version: 1\n",
                        "$schema: https://example.org/dictionary.json\nversion: 1\n",
                        "does not recognise ($schema)", "$ additionalProperties"),
                new Change(d, "an operation holding a list",
                        "  getOwner:\n    ownerId: [1, 2, 3]\n", "  getOwner: [1, 2, 3]\n",
                        "what is written under 'getOwner' is not an object",
                        "$.values.getOwner type"),
                new Change(d, "nothing where a list of values belongs", "ownerId: [1, 2, 3]",
                        "ownerId:", "nothing under 'ownerId' in 'getOwner'",
                        "$.values.getOwner.ownerId type"),
                new Change(d, "one value where a list of values belongs", "ownerId: [1, 2, 3]",
                        "ownerId: 7", "something other than a list of values under 'ownerId'",
                        "$.values.getOwner.ownerId type"),
                new Change(p, "version 1", "version: 2", "version: 1",
                        "in version 1 of the format", "$.version const"),
                new Change(p, "no strategies", strategies, "", "no 'strategies'", "$ required"),
                new Change(p, "no strategy", strategies, "strategies: []\n",
                        "no strategies in it", "$.strategies minItems"),
                new Change(p, "strategies written as a map", strategies,
                        "strategies:\n  nominal: {share: 100}\n", "'strategies' is not a list",
                        "$.strategies type"),
                PLAN_MEMBER_UNKNOWN,
                new Change(p, "no kind", "    kind: nominal\n", "", "says no 'kind'",
                        "$.strategies[0] required"),
                KIND_MISSPELT,
                new Change(p, "a share of 0", "share: 70", "share: 0", "'share' is 0",
                        "$.strategies[0].share minimum"),
                new Change(p, "a share of 101", "share: 70", "share: 101", "'share' is 101",
                        "$.strategies[0].share maximum"),
                new Change(p, "a share that is not whole", "share: 70", "share: 24.5",
                        "is 24.5, and a whole number belongs there", "$.strategies[0].share type"),
                new Change(p, "a strategy named with nothing", "name: nominal", "name: \"\"",
                        "a strategy has a name", "$.strategies[0].name minLength"),
                new Change(p, "no sources", "    sources:\n      - source: random\n", "",
                        "no 'sources'", "$.strategies[1] required"),
                new Change(p, "no source", "    sources:\n      - source: random\n",
                        "    sources: []\n", "has no sources", "$.strategies[1].sources minItems"),
                STRATEGY_MEMBER_UNKNOWN,
                WORD_MISSPELT,
                SOURCE_MEMBER_UNKNOWN,
                new Change(p, "RESTest's own list to push with named as a source",
                        "- source: random", "- source: fuzzing",
                        "is RESTest's own list of values to push with",
                        "$.strategies[1].sources[0].source enum"),
                new Change(p, "a source named two ways", "- source: random",
                        "- {source: random, dictionary: owners}", "named 2 ways at once",
                        "$.strategies[1].sources[0] additionalProperties"),
                new Change(p, "a source named no way", "- source: random", "- {}",
                        "says none of them", "$.strategies[1].sources[0] required"),
                new Change(p, "a weight outside a group", "- source: random",
                        "- {source: random, weight: 50}", "carries a 'weight'",
                        "$.strategies[1].sources[0] additionalProperties"),
                new Change(p, "dictionaries with weights outside a group", "- source: random",
                        "- dictionary: {owners: 40}", "gives 'dictionary' a map of weights",
                        "$.strategies[1].sources[0].dictionary type"),
                new Change(p, "a dictionary named with nothing", "- source: random",
                        "- dictionary: \"\"", "a list of values is named by its name",
                        "$.strategies[1].sources[0].dictionary minLength"),
                new Change(p, "a group written as a list", GROUP,
                        "[{source: example, weight: 50}, {source: random, weight: 50}]",
                        "is a map from each source to its weight",
                        "$.strategies[0].sources[1].weighted type"),
                new Change(p, "a word misspelt in a group", "example: 20", "exmaple: 20",
                        "names 'exmaple'",
                        "$.strategies[0].sources[1].weighted additionalProperties"),
                new Change(p, "every dictionary written as one dictionary in a group",
                        "dictionaries: 50", "dictionary: 50",
                        "names your dictionaries and the weight of each",
                        "$.strategies[0].sources[1].weighted.dictionary type"),
                new Change(p, "a weight of 0", "example: 20, random: 30", "example: 0, random: 50",
                        "is 0, and a share or a weight",
                        "$.strategies[0].sources[1].weighted.example minimum"),
                new Change(p, "a weight of 101", "dictionaries: 50", "dictionaries: 101",
                        "is 101, and a share or a weight",
                        "$.strategies[0].sources[1].weighted.dictionaries maximum"),
                METHOD_UNKNOWN,
                OPERATIONS_MEMBER_UNKNOWN);
    }

    static Stream<Change> onlyRestestJudges() {
        Format d = Format.DICTIONARY;
        Format p = Format.PLAN;
        return Stream.of(
                new Change(d, "a dictionary called fuzzing", "name: owners", "name: fuzzing",
                        "the name of RESTest's own list of values to push with"),
                new Change(p, "shares that do not add up to a hundred", "share: 70", "share: 60",
                        "add up to 100"),
                new Change(p, "weights that do not add up to a hundred", "dictionaries: 50",
                        "dictionaries: 40", "add up to 100"),
                new Change(p, "a group of one source", GROUP, "{random: 100}",
                        "has one source in it"),
                new Change(p, "a group of no source", GROUP, "{}", "names no source"),
                new Change(p, "no dictionary under dictionary in a group", "dictionaries: 50",
                        "dictionary: {}", "this one names none"),
                new Change(p, "a dictionary named with nothing in a group", "dictionaries: 50",
                        "dictionary: {\"\": 50}", "a list of values is named by its name"),
                new Change(p, "two strategies with one name", "name: fuzzing", "name: nominal",
                        "cannot share a name"),
                new Change(p, "a strategy named with spaces", "name: nominal", "name: \"  \"",
                        "a strategy has a name"),
                new Change(p, "a dictionary named with spaces", "- source: random",
                        "- dictionary: \"  \"", "a list of values is named by its name"),
                new Change(p, "RESTest's own list to push with named as a dictionary",
                        "- source: random", "- dictionary: fuzzing",
                        "is RESTest's own list of values to push with"),
                new Change(p, "RESTest's own list to push with named in a group",
                        "dictionaries: 50", "dictionary: {fuzzing: 50}",
                        "is RESTest's own list of values to push with"));
    }

    static Stream<Change> onlyReported() {
        return Stream.of(
                new Change(Format.DICTIONARY, "an operation the API does not have",
                        "  getOwner:", "  getOwnerRenamedSince:", "no such operation in this API"),
                new Change(Format.PLAN, "a dictionary nobody handed over", "- source: random",
                        "- dictionary: owners", "draws on a list of values called 'owners'"),
                new Change(Format.PLAN, "an operation the API does not have, under only",
                        "  methods: [GET, POST]\n", "  methods: [GET, POST]\n  only: [getOwner]\n",
                        "operation(s) this API does not have (getOwner)"));
    }

    private static void assertRefusedByRestest(Change change) {
        assertThat(change.restest().refusal())
                .describedAs("RESTest refuses a file with %s", change.what)
                .hasValueSatisfying(refusal -> assertThat(refusal).contains(change.restestSays));
    }

    private static void assertSameMembers(Collection<String> inTheSchema, Change row,
            String where) {
        assertThat(inTheSchema)
                .describedAs("the schema and RESTest allow different members in %s; change the "
                        + "schema with the reader, in the order RESTest names them", where)
                .containsExactlyElementsOf(membersNamedBy(row));
    }

    /** The members RESTest says belong where a row put one it does not recognise. */
    private static List<String> membersNamedBy(Change change) {
        return listAfter(refusalOf(change), "What belongs there is ");
    }

    /** What RESTest says, refusing the file a row makes. */
    private static String refusalOf(Change change) {
        return change.restest().refusal().orElseThrow();
    }

    /** The words RESTest lists after these, separated by commas, up to the end of the sentence. */
    private static List<String> listAfter(String said, String before) {
        int start = said.indexOf(before);
        assertThat(start).describedAs("'%s' was expected in: %s", before, said).isNotNegative();
        String rest = said.substring(start + before.length()).split("[;.]")[0];
        return Arrays.stream(rest.split(",")).map(String::trim).toList();
    }

    /** The words a closed list of the schema offers. */
    private static List<String> words(JsonValue.JsonObject schema) {
        return strings(schema.member("enum").orElseThrow(() ->
                new AssertionError("no 'enum' in " + schema.members().keySet())));
    }

    /** A list of words, as a schema writes one. */
    private static List<String> strings(JsonValue list) {
        return YamlText.asList(list, "a list of words", "a schema").stream()
                .map(word -> YamlText.asText(word, "a word in a list", "a schema"))
                .toList();
    }

    /**
     * Checks one part of a schema, and every part inside it, against what the engines accept.
     *
     * @param where the part's address inside the schema, for saying where something is wrong
     */
    private static void checkSubschema(JsonValue.JsonObject part, String where,
            JsonValue.JsonObject schema, List<String> wrong) {
        Set<String> keys = part.members().keySet();
        boolean described = keys.stream().anyMatch(ANNOTATIONS::contains);
        keys.stream().filter(key -> !KEYWORDS.contains(key))
                .forEach(key -> wrong.add(where + " uses '" + key + "'"));
        if (keys.contains("$ref") && keys.size() > 1) {
            wrong.add(where + " has something beside its $ref, which some engines ignore");
        }
        if (keys.contains("anyOf") && described) {
            wrong.add(where + " describes an anyOf, which some engines refuse with no type");
        }
        if (described && !keys.contains("type")) {
            wrong.add(where + " is described and has no type, which some engines refuse");
        }
        if (types(part).contains("object") && !keys.contains("additionalProperties")) {
            wrong.add(where + " may be an object and does not say whether it takes members it "
                    + "does not name, which engines answer differently when it is left out");
        }
        part.member("properties").ifPresent(properties -> asObject(properties, where).members()
                .forEach((name, held) -> {
                    JsonValue.JsonObject property = asObject(held, where);
                    if (!isDescribed(property, schema)) {
                        wrong.add(where + "/properties/" + name + " is not described");
                    }
                    checkSubschema(property, where + "/properties/" + name, schema, wrong);
                }));
        part.member("definitions").ifPresent(definitions -> asObject(definitions, where).members()
                .forEach((name, held) -> checkSubschema(asObject(held, where),
                        where + "/definitions/" + name, schema, wrong)));
        for (String key : List.of("items", "additionalProperties")) {
            if (part.member(key).orElse(JsonValue.NULL) instanceof JsonValue.JsonObject inside) {
                checkSubschema(inside, where + "/" + key, schema, wrong);
            }
        }
        List<JsonValue> choices = part.member("anyOf")
                .map(held -> YamlText.asList(held, "'anyOf'", where)).orElse(List.of());
        for (int i = 0; i < choices.size(); i++) {
            checkSubschema(asObject(choices.get(i), where), where + "/anyOf/" + i, schema, wrong);
        }
    }

    /** The types a part of a schema allows, whether written as one word or as a list of them. */
    private static List<String> types(JsonValue.JsonObject part) {
        return switch (part.member("type").orElse(JsonValue.NULL)) {
            case JsonValue.JsonString one -> List.of(one.value());
            case JsonValue.JsonArray several -> strings(several);
            default -> List.of();
        };
    }

    /** Whether a member says what it is, itself or through the definition it refers to. */
    private static boolean isDescribed(JsonValue.JsonObject property,
            JsonValue.JsonObject schema) {
        if (property.member("description").isPresent()) {
            return true;
        }
        return property.member("$ref").map(ref -> YamlText.asText(ref, "a '$ref'", "a schema"))
                .filter(ref -> ref.startsWith("#/definitions/"))
                .map(ref -> object(schema, "definitions",
                        ref.substring("#/definitions/".length())))
                .map(definition -> definition.member("description").isPresent())
                .orElse(false);
    }

    /**
     * Whether a block begins the way a whole file does, with its version once comments are passed:
     * so that one somebody broke, which no longer reads as YAML, is still judged as a file.
     */
    private static boolean startsWithItsVersion(String block) {
        return block.lines().map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .findFirst().filter(line -> line.startsWith("version:")).isPresent();
    }

    /** Why RESTest's own YAML reader cannot read a block, in its own words. */
    private static String whyNotRead(String block) {
        try {
            YamlText.read(block, "the block");
            return "it is not an object";
        } catch (JsonException notRead) {
            return notRead.getMessage();
        }
    }

    /** Which kind of file a whole one is, by the member only that kind has. */
    private static Optional<Format> formatOf(Set<String> members) {
        if (members.contains("keyedBy")) {
            return Optional.of(Format.DICTIONARY);
        }
        if (members.contains("strategies")) {
            return Optional.of(Format.PLAN);
        }
        return Optional.empty();
    }

    /** What is wrong with one whole file, said the way a reader of the failure can act on. */
    private static Optional<String> judge(Format format, String file, String where) {
        Optional<String> refusal = restest(format, file).refusal();
        if (refusal.isPresent()) {
            return Optional.of(where + " shows a " + format + " RESTest refuses: " + refusal.get());
        }
        List<Error> errors = validate(format, file);
        if (!errors.isEmpty()) {
            return Optional.of(where + " shows a " + format + " its schema refuses: " + errors);
        }
        return Optional.empty();
    }

    /** README.md, and every page under docs/ but the decision records, which are history. */
    private static List<Path> pages(Path root) throws IOException {
        Path decisions = root.resolve("docs").resolve("adr");
        try (Stream<Path> docs = Files.walk(root.resolve("docs"))) {
            return Stream.concat(Stream.of(root.resolve("README.md")), docs
                    .filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> !path.startsWith(decisions))
                    .sorted()).toList();
        }
    }

    /** What RESTest makes of a file handed to it the way a run is handed one. */
    private static Verdict restest(Format format, String file) {
        try {
            Path written = Files.createTempFile(files, format.name().toLowerCase(Locale.ROOT),
                    ".yaml");
            Files.writeString(written, file);
            // What RESTest says names the file it read, which here is a temporary one nobody
            // would recognise in a failure, so it is called what it is instead.
            String named = written.toString();
            if (format == Format.DICTIONARY) {
                Dictionaries.Found found = Dictionaries.gather(List.of(written), NO_API);
                return new Verdict(found.refused().stream().map(Dictionaries.Refused::reason)
                        .map(said -> said.replace(named, "the file")).findFirst(),
                        found.problems());
            }
            try {
                return new Verdict(Optional.empty(),
                        Campaigns.gather(Optional.of(written), NO_API, Set.of()).problems());
            } catch (JsonException refused) {
                return new Verdict(Optional.of(refused.getMessage().replace(named, "the file")),
                        List.of());
            }
        } catch (IOException cannotWrite) {
            throw new UncheckedIOException(cannotWrite);
        }
    }

    /** What the validator makes of a file, read the way RESTest reads it. */
    private static List<Error> validate(Format format, String file) {
        Schema checker = CHECKERS.computeIfAbsent(format,
                which -> REGISTRY.getSchema(which.written(), InputFormat.JSON));
        return checker.validate(JsonText.write(YamlText.read(file, "a file of the test")),
                InputFormat.JSON);
    }

    /** The object a path of members leads to inside a schema, or a failure naming the step. */
    private static JsonValue.JsonObject object(JsonValue.JsonObject in, String... path) {
        JsonValue.JsonObject at = in;
        for (String member : path) {
            JsonValue.JsonObject from = at;
            at = asObject(from.member(member).orElseThrow(() -> new AssertionError(
                    "no '" + member + "' in " + from.members().keySet())), "'" + member + "'");
        }
        return at;
    }

    private static JsonValue.JsonObject asObject(JsonValue value, String where) {
        return YamlText.asObject(value, where, "a schema");
    }

    private static String text(JsonValue.JsonObject in, String member) {
        return YamlText.asText(in.member(member).orElseThrow(() ->
                new AssertionError("no '" + member + "'")), "'" + member + "'", "a schema");
    }
}

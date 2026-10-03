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

import io.restest.core.execution.BodyValue;
import io.restest.core.execution.Intent;
import io.restest.core.execution.InteractionId;
import io.restest.core.execution.Mutation;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.json.JsonException;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.BooleanSchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import io.restest.core.settings.GenerationSettings;
import io.restest.core.settings.MutationSettings;
import io.restest.core.settings.Settings;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What each kind of change does to a request the API accepted, where it goes, and where it never
 * goes.
 *
 * <p>Each group switches one kind of change on and every other off, changes the same accepted
 * request many times with different starting numbers, and looks at everything that came out: every
 * change has to be the kind asked for, at a place it belongs, saying truthfully what it expects.
 */
class MutationsTest {

    private static final int DRAWS = 200;

    /** Every kind of change, as a run makes them unless told otherwise. */
    private static final MutationSettings EVERY_CHANGE = MutationSettings.defaults();

    /** The awkward values in the list RESTest carries, which values of the wrong kind come from. */
    private static final List<JsonValue> AWKWARD = carried();

    private static final NumberSchema LIMIT = new NumberSchema(SchemaMetadata.none(),
            NumberKind.INTEGER, Optional.of(BigDecimal.ONE), Optional.empty(),
            Optional.of(BigDecimal.valueOf(100)), Optional.empty(), Optional.empty(),
            Optional.empty());

    private static final StringSchema STATUS = new StringSchema(SchemaMetadata.none()
            .withEnumeration(List.of(JsonValue.of("available"), JsonValue.of("sold"))),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    private static final StringSchema CODE = new StringSchema(SchemaMetadata.none(),
            Optional.empty(), Optional.empty(), Optional.of("^[a-z]+$"), Optional.empty());

    /** Every kind of parameter a request can carry, and a few rules to break. */
    private static final Operation FIND_PETS = Operation.of(HttpMethod.GET, "/owners/{ownerId}/pets",
                    List.of(Parameter.of("ownerId", ParameterLocation.PATH, true,
                                    NumberSchema.of(NumberKind.INTEGER)),
                            Parameter.of("limit", ParameterLocation.QUERY, true, LIMIT),
                            Parameter.of("status", ParameterLocation.QUERY, true, STATUS),
                            Parameter.of("code", ParameterLocation.QUERY, false, CODE),
                            Parameter.of("q", ParameterLocation.QUERY, true, StringSchema.of()),
                            Parameter.of("page size", ParameterLocation.QUERY, true,
                                    StringSchema.of()),
                            Parameter.of("X-Trace", ParameterLocation.HEADER, true,
                                    StringSchema.of()),
                            Parameter.of("Accept", ParameterLocation.HEADER, true,
                                    StringSchema.of()),
                            Parameter.of("session", ParameterLocation.COOKIE, true,
                                    StringSchema.of()),
                            Parameter.of("limit", ParameterLocation.COOKIE, false,
                                    StringSchema.of())))
            .withId(OperationId.of("findPets"));

    private static final ObjectSchema PET = new ObjectSchema(SchemaMetadata.none(), properties(
            "name", new StringSchema(SchemaMetadata.none(), Optional.of(1), Optional.of(30),
                    Optional.empty(), Optional.empty()),
            "tag", StringSchema.of(),
            "age", new NumberSchema(SchemaMetadata.none(), NumberKind.INTEGER,
                    Optional.of(BigDecimal.ZERO), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty()),
            "tags", new ArraySchema(SchemaMetadata.none(), StringSchema.of(), Optional.empty(),
                    Optional.of(5), false),
            "owner", ObjectSchema.of(Map.of("email", new StringSchema(SchemaMetadata.none(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("email"))),
                    Set.of("email")),
            "nickname", new StringSchema(SchemaMetadata.none().withNullable(true),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
            "id", new NumberSchema(SchemaMetadata.none().withAccess(SchemaMetadata.Access.READ_ONLY),
                    NumberKind.INTEGER, Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty())),
            Set.of("name", "owner", "id"), Optional.empty(), Optional.empty(), Optional.empty());

    private static final Operation ADD_PET = Operation.of(HttpMethod.POST, "/pets")
            .withRequestBody(RequestBodyModel.json(PET, true))
            .withId(OperationId.of("addPet"));

    private static final Operation ADD_PET_BY_FORM = Operation.of(HttpMethod.POST, "/pets/form")
            .withRequestBody(RequestBodyModel.ofShapes(true,
                    Map.of("application/x-www-form-urlencoded", PET)))
            .withId(OperationId.of("addPetByForm"));

    /** Numbers of every kind a format names, and one that names none. */
    private static final ObjectSchema READING = ObjectSchema.of(properties(
            "count", number(NumberKind.INTEGER, "int32"),
            "total", number(NumberKind.INTEGER, null),
            "ratio", number(NumberKind.NUMBER, "double"),
            "weight", number(NumberKind.NUMBER, "float"),
            "level", new NumberSchema(SchemaMetadata.none(), NumberKind.INTEGER, Optional.empty(),
                    Optional.empty(), Optional.of(BigDecimal.TEN), Optional.empty(),
                    Optional.empty(), Optional.of("int32")),
            "grade", new NumberSchema(SchemaMetadata.none().withEnumeration(List.of(
                    JsonValue.of(1), JsonValue.of(2))), NumberKind.INTEGER, Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty()),
            "step", new NumberSchema(SchemaMetadata.none(), NumberKind.INTEGER, Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.of(BigDecimal.valueOf(5)), Optional.empty()),
            "byte", number(NumberKind.INTEGER, "uint8")), Set.of("count"));

    /** An operation whose body may be left out, full of numbers, with one more in the query. */
    private static final Operation ADD_READING = Operation.of(HttpMethod.POST, "/readings",
                    List.of(Parameter.of("since", ParameterLocation.QUERY, true,
                            number(NumberKind.INTEGER, "int64"))))
            .withRequestBody(RequestBodyModel.json(READING, false))
            .withId(OperationId.of("addReading"));

    private static final ApiModel API = ApiModel.of("Pets", "1.0",
            List.of(FIND_PETS, ADD_PET, ADD_PET_BY_FORM, ADD_READING));

    private static final AcceptedRequests.Accepted FOUND = new AcceptedRequests.Accepted(
            TestCase.of(FIND_PETS.id(), List.of(
                    value("ownerId", ParameterLocation.PATH, JsonValue.of(7)),
                    value("limit", ParameterLocation.QUERY, JsonValue.of(10)),
                    value("status", ParameterLocation.QUERY, JsonValue.of("available")),
                    value("code", ParameterLocation.QUERY, JsonValue.of("abc")),
                    value("q", ParameterLocation.QUERY, JsonValue.of("rex")),
                    value("page size", ParameterLocation.QUERY, JsonValue.of("20")),
                    value("X-Trace", ParameterLocation.HEADER, JsonValue.of("t-1")),
                    value("Accept", ParameterLocation.HEADER, JsonValue.of("application/json")),
                    value("session", ParameterLocation.COOKIE, JsonValue.of("s-1")))),
            InteractionId.generate());

    private static final JsonValue REX = JsonValue.object(members(
            "name", JsonValue.of("Rex"),
            "tag", JsonValue.of("dog"),
            "age", JsonValue.of(3),
            "tags", JsonValue.array(JsonValue.of("a"), JsonValue.of("b")),
            "owner", JsonValue.object(Map.of("email", JsonValue.of("ann@example.org"))),
            "nickname", JsonValue.of("R"),
            "id", JsonValue.of(9)));

    private static final AcceptedRequests.Accepted ADDED = new AcceptedRequests.Accepted(
            TestCase.of(ADD_PET.id(), List.of(), new BodyValue("application/json", REX,
                    new ValueOrigin.Generated("random"))),
            InteractionId.generate());

    private static final AcceptedRequests.Accepted READ = new AcceptedRequests.Accepted(
            TestCase.of(ADD_READING.id(), List.of(value("since", ParameterLocation.QUERY,
                            JsonValue.of(100))),
                    new BodyValue("application/json", JsonValue.object(members(
                            "count", JsonValue.of(3), "total", JsonValue.of(40),
                            "ratio", JsonValue.of(new BigDecimal("0.5")),
                            "weight", JsonValue.of(new BigDecimal("1.5")),
                            "level", JsonValue.of(4), "grade", JsonValue.of(2),
                            "step", JsonValue.of(10), "byte", JsonValue.of(7))),
                            new ValueOrigin.Generated("random"))),
            InteractionId.generate());

    @Nested
    @DisplayName("leaving out what is required")
    class DropRequired {

        @Test
        @DisplayName("a required query parameter, header or cookie; never the path, never a header "
                + "the client writes itself, never an optional one")
        void required_parameters_are_left_out() {
            List<TestCase> changed = changes("dropRequired", FIND_PETS, FOUND);

            assertThat(changed).allSatisfy(each -> {
                Mutation mutation = each.mutation().orElseThrow();
                assertThat(each.intent()).isEqualTo(Intent.REFUSAL_EXPECTED);
                assertThat(mutation.operator()).isEqualTo("dropRequired");
                assertThat(mutation.of()).isEqualTo(FOUND.from());
                assertThat(each.parameterValue(mutation.path(), mutation.location())).isEmpty();
                assertThat(each.parameterValues()).hasSize(FOUND.testCase().parameterValues()
                        .size() - 1);
                assertThat(mutation.description()).startsWith("left out the required");
            });
            assertThat(changed).extracting(each -> each.mutation().orElseThrow().path())
                    .containsOnly("limit", "status", "q", "page size", "X-Trace", "session")
                    .contains("limit", "X-Trace", "session");
        }

        @Test
        @DisplayName("a required property of the body, at any depth; never one the API only returns")
        void required_properties_are_left_out() {
            List<TestCase> changed = changes("dropRequired", ADD_PET, ADDED);

            assertThat(changed).extracting(each -> each.mutation().orElseThrow().path())
                    .containsOnly("body.name", "body.owner", "body.owner.email")
                    .contains("body.name", "body.owner.email");
            assertThat(changed).allSatisfy(each -> {
                JsonValue.JsonObject body = (JsonValue.JsonObject) each.body().orElseThrow()
                        .value();
                String path = each.mutation().orElseThrow().path();
                if (path.equals("body.owner.email")) {
                    assertThat(((JsonValue.JsonObject) body.members().get("owner")).members())
                            .isEmpty();
                } else {
                    assertThat(body.members()).doesNotContainKey(path.substring(5));
                }
                assertThat(each.body().orElseThrow().origin())
                        .describedAs("the body as a whole came from where it came from; the one "
                                + "thing changed is what the change records")
                        .isEqualTo(new ValueOrigin.Generated("random"));
            });
        }
    }

    @Nested
    @DisplayName("sending a required parameter somewhere else")
    class WrongLocation {

        @Test
        @DisplayName("to the query string, a header or a cookie other than its own, and never "
                + "where the operation declares something of that name")
        void a_required_parameter_is_moved() {
            List<TestCase> changed = changes("wrongLocation", FIND_PETS, FOUND);

            assertThat(changed).isNotEmpty().allSatisfy(each -> {
                Mutation mutation = each.mutation().orElseThrow();
                assertThat(each.intent()).isEqualTo(Intent.REFUSAL_EXPECTED);
                ParameterValue was = FOUND.testCase()
                        .parameterValue(mutation.path(), mutation.location()).orElseThrow();
                assertThat(each.parameterValue(mutation.path(), mutation.location())).isEmpty();
                ParameterValue moved = each.parameterValues().stream()
                        .filter(value -> value.name().equals(mutation.path())
                                && value.location() != mutation.location())
                        .findFirst().orElseThrow();
                assertThat(moved.value()).isEqualTo(was.value());
                assertThat(moved.origin()).isEqualTo(was.origin());
                if (mutation.path().equals("limit")) {
                    assertThat(moved.location())
                            .describedAs("a cookie called limit is already declared")
                            .isEqualTo(ParameterLocation.HEADER);
                }
            });
            assertThat(changed).extracting(each -> each.mutation().orElseThrow().path())
                    .describedAs("never the path; never Accept, which the client writes itself; "
                            + "never 'page size', whose name no header or cookie can carry")
                    .containsOnly("limit", "status", "q", "X-Trace", "session");
        }
    }

    @Nested
    @DisplayName("sending a value of another kind")
    class WrongType {

        @Test
        @DisplayName("a word that is no whole number where a parameter is one; nothing where a "
                + "parameter is a word, since any text is a word")
        void a_word_for_a_number() {
            List<TestCase> changed = changes("wrongType", FIND_PETS, FOUND);

            assertThat(changed).extracting(each -> each.mutation().orElseThrow().path())
                    .containsOnly("ownerId", "limit");
            assertThat(changed).allSatisfy(each -> {
                Mutation mutation = each.mutation().orElseThrow();
                ParameterValue sent = each.parameterValue(mutation.path(), mutation.location())
                        .orElseThrow();
                String word = ((JsonValue.JsonString) sent.value()).value();
                assertThat(word).describedAs("a word, and no whole number written as one")
                        .isNotEmpty().doesNotMatch("-?(0|[1-9][0-9]*)");
                assertThat(sent.origin()).isEqualTo(new ValueOrigin.Generated("wrongType"));
                assertThat(mutation.description()).contains("declared as a whole number");
            });
            assertThat(changed).extracting(each -> {
                Mutation mutation = each.mutation().orElseThrow();
                return each.parameterValue(mutation.path(), mutation.location()).orElseThrow()
                        .value();
            }).describedAs("not one word over and over, but many, a number with a fraction among "
                    + "them").contains(JsonValue.of("1.5"))
                    .satisfies(sent -> assertThat(new java.util.HashSet<>(sent))
                            .hasSizeGreaterThan(10));
        }

        @Test
        @DisplayName("words that are not what a number or a yes-or-no parameter is declared to be, "
                + "and only those")
        void the_words_refused_by_what_is_declared() {
            Operation find = Operation.of(HttpMethod.GET, "/find",
                            List.of(Parameter.of("ratio", ParameterLocation.QUERY, true,
                                            NumberSchema.of(NumberKind.NUMBER)),
                                    Parameter.of("all", ParameterLocation.QUERY, true,
                                            BooleanSchema.of())))
                    .withId(OperationId.of("find"));
            AcceptedRequests.Accepted found = new AcceptedRequests.Accepted(TestCase.of(find.id(),
                    List.of(value("ratio", ParameterLocation.QUERY, JsonValue.of(2)),
                            value("all", ParameterLocation.QUERY, JsonValue.TRUE))),
                    InteractionId.generate());

            ApiModel api = ApiModel.of("One", "1.0", List.of(find));
            List<TestCase> changed = new ArrayList<>();
            for (long seed = 0; seed < 600; seed++) {
                new Mutations(api, Settings.from(onlyTheSwitch("wrongType")).mutation(),
                        GenerationSettings.defaults(), new SplittableRandom(seed),
                                AWKWARD).changeOneThingIn(find, found).ifPresent(changed::add);
            }
            Map<String, List<String>> sent = new HashMap<>();
            for (TestCase each : changed) {
                Mutation mutation = each.mutation().orElseThrow();
                sent.computeIfAbsent(mutation.path(), path -> new ArrayList<>()).add(
                        ((JsonValue.JsonString) each.parameterValue(mutation.path(),
                                mutation.location()).orElseThrow().value()).value());
            }

            assertThat(sent.get("ratio")).describedAs("what reads as a number is never sent")
                    .isNotEmpty().doesNotContain("1.5", "1e3", "1", "-1", "0")
                    .contains("abc", "true");
            assertThat(sent.get("all")).describedAs("nor what reads as a yes-or-no")
                    .isNotEmpty().doesNotContain("true", "false")
                    .contains("1", "yes");
        }

        @Test
        @DisplayName("inside a body, any other kind the shape does not accept, but never null")
        void another_kind_inside_a_body() {
            List<TestCase> changed = changes("wrongType", ADD_PET, ADDED);

            assertThat(changed).isNotEmpty().allSatisfy(each -> {
                String path = each.mutation().orElseThrow().path();
                JsonValue sent = at(each, path);
                assertThat(isOfTheKind(sent, declaredInAPet(path)))
                        .describedAs("%s at %s", sent, path).isFalse();
                assertThat(sent).isNotInstanceOf(JsonValue.JsonNull.class);
            });
            assertThat(changed).extracting(each -> each.mutation().orElseThrow().path())
                    .contains("body.name", "body.age", "body.tags", "body.owner",
                            "body.owner.email", "body.tags[]")
                    .doesNotContain("body.id");
        }

        @Test
        @DisplayName("a number with a fraction where a whole number is declared, a number written "
                + "as a word, lists of empty lists, the awkward values the run holds - and none of "
                + "the declared kind")
        void many_other_kinds() {
            NumberSchema whole = NumberSchema.of(NumberKind.INTEGER);
            Operation addCount = Operation.of(HttpMethod.POST, "/count")
                    .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of("n", whole),
                            Set.of("n")), true))
                    .withId(OperationId.of("addCount"));

            ApiModel api = ApiModel.of("One", "1.0", List.of(addCount));
            AcceptedRequests.Accepted accepted = bodied(addCount,
                    JsonValue.object(Map.of("n", JsonValue.of(3))));
            List<JsonValue> sent = new ArrayList<>();
            for (long seed = 0; seed < 600; seed++) {
                new Mutations(api, Settings.from(onlyTheSwitch("wrongType")).mutation(),
                        GenerationSettings.defaults(), new SplittableRandom(seed),
                                AWKWARD).changeOneThingIn(addCount, accepted)
                        .ifPresent(each -> sent.add(at(each, "body.n")));
            }

            assertThat(sent).isNotEmpty().allSatisfy(each -> assertThat(
                    each instanceof JsonValue.JsonNumber number
                            && number.value().stripTrailingZeros().scale() <= 0)
                    .describedAs("a whole number was sent as one of the wrong kind: %s", each)
                    .isFalse());
            assertThat(sent).contains(JsonValue.of(new BigDecimal("1.5")), JsonValue.of("1"),
                    JsonValue.array(JsonValue.array(List.of())), JsonValue.of("🙂🙂🙂"));
            assertThat(new java.util.HashSet<>(sent)).hasSizeGreaterThan(20);
        }

        @Test
        @DisplayName("a list of awkward values handed over under the name the pushing strategy "
                + "uses lends its values too; any other list does not")
        void a_list_handed_over_lends_its_values() throws Exception {
            Dictionary ours = DictionaryDocument.read("""
                    version: 1
                    name: fuzzing
                    keyedBy: type
                    values:
                      string: ["only-in-our-list"]
                    """, "ours.yaml");
            Dictionary unrelated = DictionaryDocument.read("""
                    version: 1
                    name: good-values
                    keyedBy: type
                    values:
                      string: ["never-a-wrong-kind"]
                    """, "good.yaml");

            assertThat(Mutations.awkwardValuesIn(List.of(ours, unrelated)))
                    .contains(JsonValue.of("only-in-our-list"))
                    .doesNotContain(JsonValue.of("never-a-wrong-kind"));
            assertThat(Mutations.awkwardValuesIn(List.of(Dictionaries.shipped())))
                    .describedAs("the list RESTest carries, every kind of value in it")
                    .contains(JsonValue.of("🙂🙂🙂"), JsonValue.of(new BigDecimal("1.5")),
                            JsonValue.array(List.of()), JsonValue.object(Map.of()))
                    .doesNotContain(JsonValue.NULL);
        }

        /** Whether a value is of a shape's kind, worked out here rather than asked of the code. */
        private boolean isOfTheKind(JsonValue value, CanonicalSchema shape) {
            return switch (shape) {
                case StringSchema text -> value instanceof JsonValue.JsonString
                        || value instanceof JsonValue.JsonNull && text.metadata().nullable();
                case NumberSchema number when number.kind() == NumberKind.INTEGER ->
                        value instanceof JsonValue.JsonNumber written
                                && written.value().stripTrailingZeros().scale() <= 0;
                case NumberSchema ignored -> value instanceof JsonValue.JsonNumber;
                case ArraySchema ignored -> value instanceof JsonValue.JsonArray;
                case ObjectSchema ignored -> value instanceof JsonValue.JsonObject;
                default -> throw new IllegalArgumentException("no such kind in a pet: " + shape);
            };
        }

        @Test
        @DisplayName("words that arrive as written: in a header none the client would trim, and "
                + "none that cannot travel in one; in no parameter one as long as an oversized word")
        void words_that_arrive_as_written() {
            Operation get = Operation.of(HttpMethod.GET, "/items/{id}", List.of(
                            Parameter.of("id", ParameterLocation.PATH, true,
                                    NumberSchema.of(NumberKind.INTEGER)),
                            Parameter.of("X-Count", ParameterLocation.HEADER, true,
                                    NumberSchema.of(NumberKind.INTEGER)),
                            Parameter.of("page", ParameterLocation.QUERY, true,
                                    NumberSchema.of(NumberKind.INTEGER))))
                    .withId(OperationId.of("getItem"));
            AcceptedRequests.Accepted got = new AcceptedRequests.Accepted(TestCase.of(get.id(),
                    List.of(value("id", ParameterLocation.PATH, JsonValue.of(7)),
                            value("X-Count", ParameterLocation.HEADER, JsonValue.of(3)),
                            value("page", ParameterLocation.QUERY, JsonValue.of(1)))),
                    InteractionId.generate());
            ApiModel api = ApiModel.of("One", "1.0", List.of(get));

            Map<String, List<String>> sent = new HashMap<>();
            for (long seed = 0; seed < 600; seed++) {
                new Mutations(api, Settings.from(onlyTheSwitch("wrongType")).mutation(),
                        GenerationSettings.defaults(), new SplittableRandom(seed), AWKWARD)
                        .changeOneThingIn(get, got).ifPresent(each -> {
                            Mutation mutation = each.mutation().orElseThrow();
                            sent.computeIfAbsent(mutation.path(), path -> new ArrayList<>()).add(
                                    ((JsonValue.JsonString) each.parameterValue(mutation.path(),
                                            mutation.location()).orElseThrow().value()).value());
                        });
            }

            assertThat(sent).containsOnlyKeys("id", "X-Count", "page");
            assertThat(sent.get("X-Count")).isNotEmpty().allSatisfy(word -> {
                assertThat(word).describedAs("trimmed on the way, it would arrive as another word")
                        .isEqualTo(word.strip()).isNotEmpty();
                assertThat(RequestBuilder.canBeSentFrom(JsonValue.of(word),
                        ParameterLocation.HEADER)).describedAs(word).isTrue();
            }).doesNotContain(" 1", "1 ").contains("abc", "1.5");
            assertThat(sent.get("id")).describedAs("a space is written into an address, not lost")
                    .contains(" 1");
            for (String parameter : List.of("id", "X-Count", "page")) {
                assertThat(sent.get(parameter)).describedAs(parameter).allSatisfy(word ->
                        assertThat(word.length()).isLessThan(10_000));
            }
            assertThat(sent.get("page")).describedAs("1e3 is a whole number, however it is written")
                    .doesNotContain("1e3").contains("1.5");
        }

        private CanonicalSchema declaredInAPet(String path) {
            return switch (path) {
                case "body.tags[]" -> StringSchema.of();
                case "body.owner.email" -> StringSchema.of();
                default -> PET.properties().get(path.substring("body.".length()));
            };
        }

    }

    @Nested
    @DisplayName("stepping one past a limit")
    class OutsideABound {

        @Test
        @DisplayName("one below the smallest number allowed, one above the largest")
        void numbers_one_past() {
            List<TestCase> changed = changes("outsideABound", FIND_PETS, FOUND);

            assertThat(changed).extracting(each -> each.mutation().orElseThrow().path())
                    .containsOnly("limit");
            assertThat(changed).extracting(each -> each.parameterValue("limit",
                            ParameterLocation.QUERY).orElseThrow().value())
                    .containsOnly(JsonValue.of(BigDecimal.ZERO), JsonValue.of(BigDecimal.valueOf(101)))
                    .contains(JsonValue.of(BigDecimal.ZERO), JsonValue.of(BigDecimal.valueOf(101)));
        }

        @Test
        @DisplayName("one character fewer than the fewest, one more than the most, and one item "
                + "more than a list may hold")
        void lengths_and_counts_one_past() {
            List<TestCase> changed = changes("outsideABound", ADD_PET, ADDED);

            assertThat(changed).extracting(each -> each.mutation().orElseThrow().path())
                    .containsOnly("body.name", "body.age", "body.tags");
            assertThat(changed).allSatisfy(each -> {
                String path = each.mutation().orElseThrow().path();
                JsonValue sent = at(each, path);
                switch (path) {
                    case "body.name" -> assertThat(((JsonValue.JsonString) sent).value())
                            .isIn("", "Rexxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
                    case "body.age" -> assertThat(sent).isEqualTo(JsonValue.of(-1));
                    default -> assertThat(((JsonValue.JsonArray) sent).elements())
                            .containsExactly(JsonValue.of("a"), JsonValue.of("b"),
                                    JsonValue.of("a"), JsonValue.of("b"), JsonValue.of("a"),
                                    JsonValue.of("b"));
                }
            });
            assertThat(changed).extracting(each -> at(each, "body.name"))
                    .describedAs("thirty-one characters, one more than thirty")
                    .contains(JsonValue.of("Rex" + "x".repeat(28)));
        }
    }

    @Nested
    @DisplayName("stepping off a closed list, or against a spelling rule")
    class OffTheList {

        @Test
        @DisplayName("a word of the same kind that is not on the list")
        void off_the_list() {
            List<TestCase> changed = changes("breakAnEnumeration", FIND_PETS, FOUND);

            assertThat(changed).extracting(each -> each.parameterValue("status",
                            ParameterLocation.QUERY).orElseThrow().value())
                    .containsOnly(JsonValue.of("AVAILABLE"), JsonValue.of("availableX"))
                    .contains(JsonValue.of("AVAILABLE"), JsonValue.of("availableX"));
        }

        @Test
        @DisplayName("a word its pattern refuses, close to the one that was accepted")
        void against_the_pattern() {
            List<TestCase> changed = changes("breakAPattern", FIND_PETS, FOUND);

            assertThat(changed).isNotEmpty().allSatisfy(each -> {
                String sent = ((JsonValue.JsonString) each.parameterValue("code",
                        ParameterLocation.QUERY).orElseThrow().value()).value();
                assertThat(Pattern.compile("^[a-z]+$").matcher(sent).find()).isFalse();
                assertThat(each.mutation().orElseThrow().description())
                        .contains("which its pattern ^[a-z]+$ refuses");
            });
        }
    }

    @Nested
    @DisplayName("sending nothing, or an empty value")
    class NothingAndEmpty {

        @Test
        @DisplayName("null only inside a body, and only where the shape does not allow it")
        void null_where_it_is_not_allowed() {
            List<TestCase> changed = changes("sendNull", ADD_PET, ADDED);

            assertThat(changed).extracting(each -> each.mutation().orElseThrow().path())
                    .contains("body.name", "body.tag", "body.owner.email")
                    .doesNotContain("body.nickname", "body.id");
            assertThat(changes("sendNull", FIND_PETS, FOUND))
                    .describedAs("outside a body, nothing can say null")
                    .isEmpty();
        }

        @Test
        @DisplayName("an empty value where the document forbids one, never in the path")
        void empty_where_it_is_forbidden() {
            assertThat(changes("sendEmpty", FIND_PETS, FOUND))
                    .extracting(each -> each.mutation().orElseThrow().path())
                    .describedAs("an empty word is not a number, not on status's list, and not "
                            + "something code's pattern accepts")
                    .containsOnly("limit", "status", "code");
            List<TestCase> inTheBody = changes("sendEmpty", ADD_PET, ADDED);
            assertThat(inTheBody).extracting(each -> each.mutation().orElseThrow().path())
                    .containsOnly("body.name", "body.owner", "body.owner.email");
            assertThat(inTheBody).allSatisfy(each -> assertThat(each.intent())
                    .isEqualTo(Intent.REFUSAL_EXPECTED));
        }

    }

    @Nested
    @DisplayName("sending far too much")
    class FarTooMuch {

        @Test
        @DisplayName("beyond a stated most, as a violation")
        void beyond_a_stated_most() {
            List<TestCase> changed = changes("oversize", ADD_PET, ADDED);

            assertThat(changed).extracting(each -> each.mutation().orElseThrow().path())
                    .containsOnly("body.name", "body.tags");
            assertThat(changed).allSatisfy(each -> {
                assertThat(each.intent()).isEqualTo(Intent.REFUSAL_EXPECTED);
                JsonValue sent = at(each, each.mutation().orElseThrow().path());
                if (sent instanceof JsonValue.JsonString word) {
                    assertThat(word.value()).hasSize(10_000).startsWith("Rex");
                } else {
                    assertThat(((JsonValue.JsonArray) sent).elements()).hasSize(1_000);
                }
            });
        }

        @Test
        @DisplayName("an oversized value is as long as the settings say")
        void as_long_as_the_settings_say() {
            Map<String, String> given = onlyTheSwitch("oversize");
            given.put("mutation.oversizedLength", "40");
            List<TestCase> names = new ArrayList<>();
            for (long seed = 0; seed < DRAWS; seed++) {
                new Mutations(API, Settings.from(given).mutation(), GenerationSettings.defaults(),
                        new SplittableRandom(seed), AWKWARD)
                        .changeOneThingIn(ADD_PET, ADDED)
                        .filter(changed -> changed.mutation().orElseThrow().path()
                                .equals("body.name"))
                        .ifPresent(names::add);
            }

            assertThat(names).describedAs("the name, whose most is thirty").isNotEmpty()
                    .allSatisfy(changed -> {
                        assertThat(((JsonValue.JsonString) at(changed, "body.name")).value())
                                .hasSize(40);
                        assertThat(changed.mutation().orElseThrow().description())
                                .contains("40 characters");
                    });
        }
    }

    @Nested
    @DisplayName("the body as a whole")
    class TheWholeBody {

        @Test
        @DisplayName("a body of another kind than declared, never one the shape accepts")
        void a_root_of_the_wrong_kind() {
            List<TestCase> changed = changes("wrongRoot", ADD_PET, ADDED);

            assertThat(changed).isNotEmpty().allSatisfy(each -> {
                assertBodyAsAWhole(each, Intent.REFUSAL_EXPECTED);
                JsonValue sent = each.body().orElseThrow().value();
                assertThat(sent).isNotInstanceOf(JsonValue.JsonObject.class);
                assertThat(Shapes.couldSatisfy(API, sent, PET, 8)).isFalse();
                assertThat(each.body().orElseThrow().sentAs()).isEmpty();
                assertThat(each.mutation().orElseThrow().description()).endsWith("the body, "
                        + "declared as an object");
            });
            assertThat(changed).extracting(each -> each.body().orElseThrow().value())
                    .describedAs("one thing where several are expected, and the other way round")
                    .contains(JsonValue.array(List.of(REX)), JsonValue.of("abc"), JsonValue.TRUE);
            ArraySchema pets = new ArraySchema(SchemaMetadata.none(), PET, Optional.empty(),
                    Optional.empty(), false);
            Operation addPets = Operation.of(HttpMethod.POST, "/pets/many")
                    .withRequestBody(RequestBodyModel.json(pets, true))
                    .withId(OperationId.of("addPets"));
            assertThat(changesIn(addPets, bodied(addPets, JsonValue.array(List.of(REX))),
                    "wrongRoot"))
                    .extracting(each -> each.body().orElseThrow().value())
                    .contains(REX)
                    .doesNotContain(JsonValue.array(List.of(REX)));
        }

        @Test
        @DisplayName("no bytes at all where a body is required, and never where it may be left out")
        void an_empty_body_only_where_one_is_required() {
            List<TestCase> changed = changes("emptyBody", ADD_PET, ADDED);

            assertThat(changed).isNotEmpty().allSatisfy(each -> {
                assertBodyAsAWhole(each, Intent.REFUSAL_EXPECTED);
                BodyValue body = each.body().orElseThrow();
                assertThat(body.sentAs()).contains("");
                assertThat(body.mediaType()).isEqualTo("application/json");
                assertThat(body.value()).describedAs("what the empty body was made from")
                        .isEqualTo(REX);
            });
            assertThat(changes("emptyBody", ADD_READING, READ)).isEmpty();
        }

        @Test
        @DisplayName("text that is not JSON: the accepted body cut off halfway, plain words, a "
                + "word after it, a raw line break in it, a colon left out, a comma too many")
        void text_that_is_not_json() {
            List<TestCase> changed = changes("notJson", ADD_PET, ADDED);
            String accepted = JsonText.write(REX);
            String rawBreak = accepted.substring(0, 1) + "\"\n" + accepted.substring(2);
            String noColon = accepted.replaceFirst("\"name\":", "\"name\" ");
            String extraComma = accepted.substring(0, accepted.length() - 1) + ",}";

            assertThat(changed).isNotEmpty().allSatisfy(each -> {
                assertBodyAsAWhole(each, Intent.REFUSAL_EXPECTED);
                String sent = each.body().orElseThrow().sentAs().orElseThrow();
                assertThatExceptionOfType(JsonException.class)
                        .isThrownBy(() -> JsonText.checkOneValue(sent));
                assertThat(sent).describedAs(sent).isIn("this is not JSON",
                        accepted.substring(0, accepted.length() / 2), accepted + "bla", rawBreak,
                        noColon, extraComma);
            });
            assertThat(changed).extracting(each -> each.body().orElseThrow().sentAs()
                            .orElseThrow())
                    .contains("this is not JSON", accepted.substring(0, accepted.length() / 2),
                            accepted + "bla", rawBreak, noColon, extraComma);
            assertThat(changed).extracting(each -> each.mutation().orElseThrow().description())
                    .contains("sent the body cut off after " + accepted.length() / 2
                            + " characters of its " + accepted.length() + ", which is not JSON",
                            "sent the body followed by the word 'bla', which is not JSON",
                            "sent the body with a line break written raw inside its first piece "
                                    + "of text, which is not JSON",
                            "sent the body with the colon after its first property's name left "
                                    + "out, which is not JSON",
                            "sent the body with a comma after its last item, which is not JSON");
            Operation addEmpty = Operation.of(HttpMethod.POST, "/empty")
                    .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of(), Set.of()),
                            true))
                    .withId(OperationId.of("addEmpty"));
            assertThat(changesIn(addEmpty, bodied(addEmpty, JsonValue.object(Map.of())),
                    "notJson"))
                    .extracting(each -> each.body().orElseThrow().sentAs().orElseThrow())
                    .describedAs("two characters are enough to cut in half")
                    .contains("{");
            Operation addCount = Operation.of(HttpMethod.POST, "/count")
                    .withRequestBody(RequestBodyModel.json(NumberSchema.of(NumberKind.INTEGER),
                            true))
                    .withId(OperationId.of("addCount"));
            assertThat(changesIn(addCount, bodied(addCount, JsonValue.of(12)), "notJson"))
                    .extracting(each -> each.body().orElseThrow().sentAs().orElseThrow())
                    .describedAs("half of 12 is 1, which is JSON, so it is not sent as though "
                            + "it were not; and a number has no text, no colon and no last item")
                    .containsOnly("this is not JSON", "12bla");
        }

        @Test
        @DisplayName("the accepted body unchanged, under a media type the operation does not take")
        void under_the_wrong_media_type() {
            List<TestCase> changed = changes("wrongContentType", ADD_PET, ADDED);

            assertThat(changed).isNotEmpty().allSatisfy(each -> {
                assertBodyAsAWhole(each, Intent.REFUSAL_EXPECTED);
                assertThat(each.body().orElseThrow().sentAs()).contains(JsonText.write(REX));
            });
            assertThat(changed).extracting(each -> each.body().orElseThrow().mediaType())
                    .containsOnly("text/plain", "application/xml",
                            "application/x-www-form-urlencoded");

            Operation ranges = Operation.of(HttpMethod.POST, "/pets/ranges")
                    .withRequestBody(RequestBodyModel.ofShapes(true, Map.of(
                            "application/json", PET, "text/*", PET,
                            "application/x-www-form-urlencoded; charset=utf-8", PET)))
                    .withId(OperationId.of("addPetInRanges"));
            assertThat(changesIn(ranges, bodied(ranges, REX), "wrongContentType"))
                    .extracting(each -> each.body().orElseThrow().mediaType())
                    .describedAs("never one the operation offers, nor one a range it offers "
                            + "covers")
                    .containsOnly("application/xml");
            Operation anything = Operation.of(HttpMethod.POST, "/pets/anything")
                    .withRequestBody(RequestBodyModel.ofShapes(true, Map.of(
                            "application/json", PET, "*/*", PET)))
                    .withId(OperationId.of("addPetAsAnything"));
            assertThat(changesIn(anything, bodied(anything, REX), "wrongContentType")).isEmpty();
            Operation ownHeader = Operation.of(HttpMethod.POST, "/pets/own",
                            List.of(Parameter.of("content-type", ParameterLocation.HEADER, false,
                                    StringSchema.of())))
                    .withRequestBody(RequestBodyModel.json(PET, true))
                    .withId(OperationId.of("addPetWithItsOwnHeader"));
            assertThat(changesIn(ownHeader, bodied(ownHeader, REX), "wrongContentType"))
                    .describedAs("a Content-Type the document declares itself would be sent in "
                            + "place of the one changed")
                    .isEmpty();
            Operation otherHeader = Operation.of(HttpMethod.POST, "/pets/traced",
                            List.of(Parameter.of("X-Trace", ParameterLocation.HEADER, false,
                                    StringSchema.of())))
                    .withRequestBody(RequestBodyModel.json(PET, true))
                    .withId(OperationId.of("addTracedPet"));
            assertThat(changesIn(otherHeader, bodied(otherHeader, REX), "wrongContentType"))
                    .describedAs("any other header leaves the media type to the tool")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("none of the changes to one value is ever made to the body as a whole")
        void the_body_as_a_whole_is_for_its_own_changes() {
            StringSchema shortWord = new StringSchema(SchemaMetadata.none(), Optional.of(1),
                    Optional.of(3), Optional.empty(), Optional.empty());
            Operation addWord = Operation.of(HttpMethod.POST, "/word")
                    .withRequestBody(RequestBodyModel.json(shortWord, true))
                    .withId(OperationId.of("addWord"));
            for (Mutations.Operator operator : Mutations.Operator.values()) {
                List<TestCase> changed = new ArrayList<>(changesIn(addWord,
                        bodied(addWord, JsonValue.of("ab")), operator.written()));
                changed.addAll(changesIn(ADD_PET, ADDED, operator.written()));
                boolean asAWhole = changed.stream().anyMatch(each -> each.mutation()
                        .orElseThrow().path().equals("body"));
                assertThat(asAWhole).describedAs(operator.written())
                        .isEqualTo(List.of("wrongRoot", "emptyBody", "notJson",
                                "wrongContentType").contains(operator.written()));
            }
        }

        @Test
        @DisplayName("where the top of the body is a choice, never a kind one of its shapes accepts")
        void a_choice_at_the_top() {
            ArraySchema pets = new ArraySchema(SchemaMetadata.none(), PET, Optional.empty(),
                    Optional.empty(), false);
            Operation addOneOrMany = Operation.of(HttpMethod.POST, "/pets/one-or-many")
                    .withRequestBody(RequestBodyModel.json(ChoiceSchema.of(List.of(PET, pets)),
                            true))
                    .withId(OperationId.of("addOneOrMany"));

            List<TestCase> changed = changesIn(addOneOrMany, bodied(addOneOrMany, REX),
                    "wrongRoot");

            assertThat(changed).isNotEmpty()
                    .extracting(each -> each.body().orElseThrow().value())
                    .doesNotContain(JsonValue.array(List.of(REX)));
            assertThat(changed).allSatisfy(each -> assertThat(each.mutation().orElseThrow()
                    .description()).endsWith("declared as one of several shapes"));
        }

        @Test
        @DisplayName("a body whose shape is a name pointing nowhere, or round in a circle, still "
                + "gets the changes that need no shape, and nothing hangs")
        void a_body_nobody_could_read_the_shape_of() {
            Operation addLost = Operation.of(HttpMethod.POST, "/lost")
                    .withRequestBody(RequestBodyModel.json(SchemaReference.to("Missing"), true))
                    .withId(OperationId.of("addLost"));
            Operation addRound = Operation.of(HttpMethod.POST, "/round")
                    .withRequestBody(RequestBodyModel.json(SchemaReference.to("A"), true))
                    .withId(OperationId.of("addRound"));
            ApiModel odd = ApiModel.of("Odd", "1.0", List.of(addLost, addRound))
                    .withSchemas(Map.of("A", SchemaReference.to("B"),
                            "B", SchemaReference.to("A")));

            for (Operation operation : List.of(addLost, addRound)) {
                AcceptedRequests.Accepted accepted = bodied(operation, REX);
                List<String> made = new ArrayList<>();
                for (long seed = 0; seed < 60; seed++) {
                    new Mutations(odd, EVERY_CHANGE, GenerationSettings.defaults(),
                            new SplittableRandom(seed),
                                    AWKWARD).changeOneThingIn(operation, accepted)
                            .ifPresent(each -> made.add(each.mutation().orElseThrow().operator()));
                }
                assertThat(made).describedAs(operation.path())
                        .containsOnly("emptyBody", "notJson", "wrongContentType")
                        .contains("emptyBody", "notJson", "wrongContentType");
            }
        }

        @Test
        @DisplayName("text is cut between characters, never through one")
        void cut_between_characters() {
            Operation addWord = Operation.of(HttpMethod.POST, "/word")
                    .withRequestBody(RequestBodyModel.json(StringSchema.of(), true))
                    .withId(OperationId.of("addWord"));
            String faces = "\uD83D\uDE00\uD83D\uDE01\uD83D\uDE02\uD83D\uDE03";

            List<String> cut = changesIn(addWord, bodied(addWord, JsonValue.of(faces)), "notJson")
                    .stream().filter(each -> each.mutation().orElseThrow().description()
                            .startsWith("sent the body cut off"))
                    .map(each -> each.body().orElseThrow().sentAs().orElseThrow())
                    .toList();

            assertThat(cut).isNotEmpty().allSatisfy(text -> {
                assertThat(Character.isHighSurrogate(text.charAt(text.length() - 1)))
                        .describedAs("half a character is not text").isFalse();
                assertThat(text.codePointCount(0, text.length())).isEqualTo(3);
            });
        }

        private void assertBodyAsAWhole(TestCase changed, Intent intent) {
            Mutation mutation = changed.mutation().orElseThrow();
            assertThat(changed.intent()).isEqualTo(intent);
            assertThat(mutation.location()).isEqualTo(ParameterLocation.BODY);
            assertThat(mutation.path()).isEqualTo("body");
            assertThat(mutation.of()).isNotNull();
        }
    }

    @Nested
    @DisplayName("a word that only looks like its format")
    class NotQuiteTheFormat {

        private final Operation book = Operation.of(HttpMethod.POST, "/bookings/{code}",
                        List.of(Parameter.of("code", ParameterLocation.PATH, true,
                                        StringSchema.ofFormat("uuid")),
                                Parameter.of("on", ParameterLocation.QUERY, true,
                                        StringSchema.ofFormat("date"))))
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(properties(
                        "email", StringSchema.ofFormat("email"),
                        "at", StringSchema.ofFormat("date-time"),
                        "site", StringSchema.ofFormat("url"),
                        "colour", StringSchema.ofFormat("colour"),
                        "kind", new StringSchema(SchemaMetadata.none().withEnumeration(List.of(
                                JsonValue.of("2021-01-05"))), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.of("date"))), Set.of()), true))
                .withId(OperationId.of("book"));

        private final AcceptedRequests.Accepted booked = new AcceptedRequests.Accepted(
                TestCase.of(book.id(), List.of(
                                value("code", ParameterLocation.PATH,
                                        JsonValue.of("123e4567-e89b-12d3-a456-426614174000")),
                                value("on", ParameterLocation.QUERY, JsonValue.of("2021-01-05"))),
                        new BodyValue("application/json", JsonValue.object(members(
                                "email", JsonValue.of("ann@example.org"),
                                "at", JsonValue.of("2021-01-05T10:00:00Z"),
                                "site", JsonValue.of("https://example.org"),
                                "colour", JsonValue.of("red"),
                                "kind", JsonValue.of("2021-01-05"))),
                                new ValueOrigin.Generated("random"))),
                InteractionId.generate());

        @Test
        @DisplayName("an impossible date, an address with nothing after its dot, an identifier too "
                + "short: one of the format's near misses, in a parameter or in a body")
        void near_misses_of_the_format() {
            List<TestCase> changed = changesIn(book, booked, "breakAFormat");

            assertThat(changed).isNotEmpty().allSatisfy(each -> {
                Mutation mutation = each.mutation().orElseThrow();
                assertThat(each.intent()).isEqualTo(Intent.REFUSAL_EXPECTED);
                assertThat(mutation.operator()).isEqualTo("breakAFormat");
                assertThat(mutation.description()).contains(", which is not a valid ");
            });
            Map<String, List<String>> sent = new HashMap<>();
            for (TestCase each : changed) {
                Mutation mutation = each.mutation().orElseThrow();
                JsonValue value = mutation.location() == ParameterLocation.BODY
                        ? at(each, mutation.path())
                        : each.parameterValue(mutation.path(), mutation.location()).orElseThrow()
                                .value();
                sent.computeIfAbsent(mutation.path(), path -> new ArrayList<>())
                        .add(((JsonValue.JsonString) value).value());
            }
            assertThat(sent).describedAs("a format with no fixed rules is not judged, and a closed "
                    + "list is broken by a change of its own")
                    .containsOnlyKeys("code", "on", "body.email", "body.at", "body.site");
            assertThat(sent.get("on")).isSubsetOf("2021-02-30", "2021-13-01", "2021-00-10",
                    "2021-1-5", "2021/01/05");
            assertThat(sent.get("body.email")).isSubsetOf("a@b.", "@example.com", "user@",
                    "a@@example.com", "user.example.com", "user@exa mple.com");
            assertThat(sent.get("body.site")).describedAs("a url is a uri by another name")
                    .isSubsetOf("http//example.com", "://example.com", "http://exa mple.com");
            assertThat(sent.get("code")).isSubsetOf("123e4567-e89b-12d3-a456",
                    "123e4567-e89b-12d3-a456-42661417400g", "123e4567e89b12d3a456426614174000x");
            assertThat(sent.get("body.at")).isNotEmpty();
            assertThat(sent.get("on")).describedAs("not one a strict reader of dates reads")
                    .isNotEmpty().allSatisfy(word -> assertThatExceptionOfType(
                            java.time.format.DateTimeParseException.class)
                            .isThrownBy(() -> java.time.LocalDate.parse(word)));
            assertThat(sent.get("body.at")).describedAs("nor of a moment in time")
                    .allSatisfy(word -> assertThatExceptionOfType(
                            java.time.format.DateTimeParseException.class)
                            .isThrownBy(() -> java.time.OffsetDateTime.parse(word)));
            assertThat(sent.get("code")).describedAs("nor of an identifier")
                    .isNotEmpty().allSatisfy(word -> assertThatExceptionOfType(
                            IllegalArgumentException.class)
                            .isThrownBy(() -> java.util.UUID.fromString(word)));
            assertThat(sent.get("body.site")).describedAs("nor a whole web address")
                    .allSatisfy(word -> assertThat(isAnAbsoluteAddress(word)).isFalse());
        }

        private boolean isAnAbsoluteAddress(String word) {
            try {
                return new java.net.URI(word).isAbsolute();
            } catch (java.net.URISyntaxException notOne) {
                return false;
            }
        }

        @Test
        @DisplayName("switched off with its own switch, the others carrying on")
        void switched_off_on_its_own() {
            Map<String, String> allButIt = new HashMap<>();
            allButIt.put("mutation.breakAFormat", "false");
            List<TestCase> changed = draw(Settings.from(allButIt).mutation(), book, booked);

            assertThat(changed).isNotEmpty().extracting(each -> each.mutation().orElseThrow()
                    .operator()).doesNotContain("breakAFormat");
        }
    }

    @Nested
    @DisplayName("the edges of a kind of number")
    class TheEdgesOfANumber {

        @Test
        @DisplayName("past what the kind of number its format names can hold, as a violation, in "
                + "a body or a parameter")
        void beyond_its_width() {
            List<TestCase> changed = changes("beyondItsWidth", ADD_READING, READ);

            assertThat(changed).allSatisfy(each -> assertThat(each.intent())
                    .isEqualTo(Intent.REFUSAL_EXPECTED));
            BigDecimal farPast = new BigDecimal("987654321".repeat(1_112).substring(0, 10_000));
            assertThat(sentAt(changed, "body.count")).containsOnly(
                    new BigDecimal("2147483648"), new BigDecimal("-2147483649"), farPast);
            assertThat(sentAt(changed, "body.level"))
                    .describedAs("a stated most does not stop it: past the width is past that too")
                    .contains(new BigDecimal("2147483648"));
            assertThat(sentAt(changed, "body.ratio")).containsOnly(
                    new BigDecimal("1.7976931348623157E309"),
                    new BigDecimal("-1.7976931348623157E309"), farPast);
            assertThat(sentAt(changed, "body.weight")).containsOnly(
                    new BigDecimal("3.4028235E39"), new BigDecimal("-3.4028235E39"), farPast);
            assertThat(sentAt(changed, "since"))
                    .describedAs("in a web address, a number that long is an address too long")
                    .containsOnly(new BigDecimal("9223372036854775808"),
                            new BigDecimal("-9223372036854775809"));
            assertThat(changed).extracting(each -> each.mutation().orElseThrow().path())
                    .describedAs("no format, or one whose edges are not known, names no width")
                    .doesNotContain("body.total", "body.byte");
            assertThat(changed).extracting(each -> each.mutation().orElseThrow().description())
                    .contains("sent 2147483648 for body.count, one past the largest an int32 "
                            + "can hold", "sent a number 10000 digits long for body.count, far "
                            + "past what an int32 can hold");
        }

        @Test
        @DisplayName("never a header the client writes its own value into, which no change would "
                + "reach")
        void never_a_header_the_client_writes_over() {
            StringSchema json = new StringSchema(SchemaMetadata.none().withEnumeration(List.of(
                    JsonValue.of("application/json"))), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty());
            Operation upload = Operation.of(HttpMethod.PUT, "/blobs",
                            List.of(Parameter.of("Content-Length", ParameterLocation.HEADER, true,
                                            number(NumberKind.INTEGER, "int64")),
                                    Parameter.of("Content-Type", ParameterLocation.HEADER, true,
                                            json)))
                    .withRequestBody(RequestBodyModel.json(READING, true))
                    .withId(OperationId.of("upload"));
            AcceptedRequests.Accepted sent = new AcceptedRequests.Accepted(TestCase.of(
                    upload.id(), List.of(
                            value("Content-Length", ParameterLocation.HEADER, JsonValue.of(80)),
                            value("Content-Type", ParameterLocation.HEADER,
                                    JsonValue.of("application/json"))),
                    READ.testCase().body().orElseThrow()), InteractionId.generate());

            for (Mutations.Operator operator : Mutations.Operator.values()) {
                assertThat(changesIn(upload, sent, operator.written()))
                        .extracting(each -> each.mutation().orElseThrow().location())
                        .describedAs(operator.written())
                        .doesNotContain(ParameterLocation.HEADER);
            }
        }

        @Test
        @DisplayName("not past a width where the document's own bound lets that number through")
        void not_where_the_document_lets_it_through() {
            NumberSchema unsigned = new NumberSchema(SchemaMetadata.none(), NumberKind.INTEGER,
                    Optional.empty(), Optional.empty(), Optional.of(new BigDecimal("4294967295")),
                    Optional.empty(), Optional.empty(), Optional.of("int32"));
            Operation addCount = Operation.of(HttpMethod.POST, "/count")
                    .withRequestBody(RequestBodyModel.json(unsigned, true))
                    .withId(OperationId.of("addCount"));

            assertThat(changesIn(addCount, bodied(addCount, JsonValue.of(12)), "beyondItsWidth"))
                    .isNotEmpty()
                    .extracting(each -> ((JsonValue.JsonNumber) each.body().orElseThrow().value())
                            .value())
                    .describedAs("not what the bound lets through, but the other side, and far "
                            + "past the bound itself")
                    .containsOnly(new BigDecimal("-2147483649"),
                            new BigDecimal("987654321".repeat(1_112).substring(0, 10_000)));
        }

        @Test
        @DisplayName("a body that is one number is a number like any other")
        void a_body_that_is_one_number() {
            Operation addCount = Operation.of(HttpMethod.POST, "/count")
                    .withRequestBody(RequestBodyModel.json(number(NumberKind.INTEGER, "int32"),
                            true))
                    .withId(OperationId.of("addCount"));

            assertThat(changesIn(addCount, bodied(addCount, JsonValue.of(12)), "beyondItsWidth"))
                    .isNotEmpty()
                    .allSatisfy(each -> {
                        assertThat(each.mutation().orElseThrow().path()).isEqualTo("body");
                        assertThat(each.mutation().orElseThrow().description())
                                .containsAnyOf(" for the body, one past the ",
                                        " for the body, far past what an int32 can hold");
                    });
        }

        private List<BigDecimal> sentAt(List<TestCase> changed, String path) {
            List<BigDecimal> sent = new ArrayList<>();
            for (TestCase each : changed) {
                Mutation mutation = each.mutation().orElseThrow();
                if (!mutation.path().equals(path)) {
                    continue;
                }
                JsonValue value = mutation.location() == ParameterLocation.BODY
                        ? ((JsonValue.JsonObject) each.body().orElseThrow().value()).members()
                                .get(path.substring("body.".length()))
                        : each.parameterValue(path, mutation.location()).orElseThrow().value();
                sent.add(((JsonValue.JsonNumber) value).value());
            }
            return sent;
        }
    }

    @Nested
    @DisplayName("switching changes off, and what is never touched")
    class SwitchedOff {

        @Test
        @DisplayName("left as they are, the settings make changes that each expect a refusal")
        void by_default_each_expects_a_refusal() {
            assertThat(draw(MutationSettings.defaults(), ADD_PET, ADDED))
                    .isNotEmpty()
                    .allSatisfy(each -> assertThat(each.intent())
                            .isEqualTo(Intent.REFUSAL_EXPECTED));
        }

        @Test
        @DisplayName("with every change off nothing is changed at all")
        void every_change_off_changes_nothing() {
            MutationSettings off = MutationSettings.defaults().withNothingChanged();

            assertThat(draw(off, ADD_PET, ADDED)).isEmpty();
            assertThat(draw(off, FIND_PETS, FOUND)).isEmpty();
        }

        @Test
        @DisplayName("every kind of change, all on, and every one of them is different from what "
                + "was accepted and names it")
        void everything_on() {
            List<TestCase> changed = new ArrayList<>(draw(EVERY_CHANGE, ADD_PET, ADDED));
            changed.addAll(draw(EVERY_CHANGE, FIND_PETS, FOUND));
            changed.addAll(draw(EVERY_CHANGE, ADD_READING, READ));

            assertThat(changed).extracting(each -> each.mutation().orElseThrow().operator())
                    .containsAll(java.util.Arrays.stream(Mutations.Operator.values())
                            .map(Mutations.Operator::written).toList());
            assertThat(changed).allSatisfy(each -> {
                TestCase base = each.operation().equals(ADD_PET.id()) ? ADDED.testCase()
                        : each.operation().equals(ADD_READING.id()) ? READ.testCase()
                        : FOUND.testCase();
                assertThat(each.parameterValues().equals(base.parameterValues())
                        && each.body().equals(base.body()))
                        .describedAs("a change that changes nothing is not a change")
                        .isFalse();
                assertThat(each.id()).isNotEqualTo(base.id());
            });
        }

        @Test
        @DisplayName("a body sent as the fields of a web form is left alone")
        void a_form_is_left_alone() {
            AcceptedRequests.Accepted byForm = new AcceptedRequests.Accepted(
                    TestCase.of(ADD_PET_BY_FORM.id(), List.of(), new BodyValue(
                            "application/x-www-form-urlencoded", REX,
                            new ValueOrigin.Generated("random"))),
                    InteractionId.generate());

            assertThat(draw(EVERY_CHANGE, ADD_PET_BY_FORM, byForm)).isEmpty();
        }
    }

    @Nested
    @DisplayName("documents that are odd, or wrong")
    class OddDocuments {

        private final ObjectSchema cyclic = ObjectSchema.of(properties(
                "loop", SchemaReference.to("A"),
                "label", StringSchema.of()), Set.of("loop"));

        private final ApiModel round = ApiModel.of("Round", "1.0", List.of(Operation.of(
                HttpMethod.POST, "/things").withRequestBody(RequestBodyModel.json(cyclic, true))
                .withId(OperationId.of("addThing"))))
                .withSchemas(Map.of("A", SchemaReference.to("B"), "B", SchemaReference.to("A")));

        @Test
        @DisplayName("a property whose shape is a name going round in a circle is only ever left "
                + "out, never judged, and nothing hangs")
        void a_circle_of_names() {
            Operation addThing = round.operations().get(0);
            AcceptedRequests.Accepted accepted = new AcceptedRequests.Accepted(
                    TestCase.of(addThing.id(), List.of(), new BodyValue("application/json",
                            JsonValue.object(members("loop", JsonValue.of("x"), "label",
                                    JsonValue.of("l"))), new ValueOrigin.Generated("random"))),
                    InteractionId.generate());
            List<TestCase> changed = new ArrayList<>();
            for (long seed = 0; seed < DRAWS; seed++) {
                new Mutations(round, EVERY_CHANGE, GenerationSettings.defaults(),
                        new SplittableRandom(seed), AWKWARD)
                        .changeOneThingIn(addThing, accepted)
                        .ifPresent(changed::add);
            }

            assertThat(changed).filteredOn(each -> each.mutation().orElseThrow().path()
                            .equals("body.loop"))
                    .isNotEmpty()
                    .allSatisfy(each -> assertThat(each.mutation().orElseThrow().operator())
                            .isEqualTo("dropRequired"));
        }

        @Test
        @DisplayName("a pattern this platform cannot read breaks nothing and forbids nothing")
        void a_pattern_nobody_can_read() {
            Operation search = Operation.of(HttpMethod.GET, "/search", List.of(Parameter.of("q",
                    ParameterLocation.QUERY, true, new StringSchema(SchemaMetadata.none(),
                            Optional.empty(), Optional.empty(), Optional.of("(unclosed"),
                            Optional.empty())))).withId(OperationId.of("search"));
            AcceptedRequests.Accepted accepted = accepted(search,
                    value("q", ParameterLocation.QUERY, JsonValue.of("rex")));

            assertThat(changesIn(search, accepted, "breakAPattern")).isEmpty();
            assertThat(changesIn(search, accepted, "sendEmpty"))
                    .describedAs("a rule nobody can read is not a rule against the empty word")
                    .isEmpty();
        }

        @Test
        @DisplayName("an exclusive bound is sent as the bound itself")
        void exclusive_bounds() {
            Operation page = Operation.of(HttpMethod.GET, "/page", List.of(Parameter.of("n",
                    ParameterLocation.QUERY, true, new NumberSchema(SchemaMetadata.none(),
                            NumberKind.NUMBER, Optional.empty(), Optional.of(BigDecimal.ZERO),
                            Optional.empty(), Optional.of(BigDecimal.TEN), Optional.empty(),
                            Optional.empty())))).withId(OperationId.of("page"));

            assertThat(changesIn(page, accepted(page, value("n", ParameterLocation.QUERY,
                    JsonValue.of(5))), "outsideABound"))
                    .extracting(each -> each.parameterValue("n", ParameterLocation.QUERY)
                            .orElseThrow().value())
                    .containsOnly(JsonValue.of(BigDecimal.ZERO), JsonValue.of(BigDecimal.TEN))
                    .contains(JsonValue.of(BigDecimal.ZERO), JsonValue.of(BigDecimal.TEN));
        }

        @Test
        @DisplayName("a longest length beyond what the tool builds is not stepped past")
        void a_huge_longest_length() {
            Operation note = Operation.of(HttpMethod.GET, "/note", List.of(Parameter.of("text",
                    ParameterLocation.QUERY, true, new StringSchema(SchemaMetadata.none(),
                            Optional.empty(), Optional.of(Integer.MAX_VALUE), Optional.empty(),
                            Optional.empty())))).withId(OperationId.of("note"));

            assertThat(changesIn(note, accepted(note, value("text", ParameterLocation.QUERY,
                    JsonValue.of("hi"))), "outsideABound")).isEmpty();
        }

        @Test
        @DisplayName("a word is lengthened by whole characters, even ones written as two")
        void whole_characters() {
            Operation note = Operation.of(HttpMethod.GET, "/note", List.of(Parameter.of("text",
                    ParameterLocation.QUERY, true, new StringSchema(SchemaMetadata.none(),
                            Optional.empty(), Optional.of(3), Optional.empty(),
                            Optional.empty())))).withId(OperationId.of("note"));

            assertThat(changesIn(note, accepted(note, value("text", ParameterLocation.QUERY,
                    JsonValue.of("a\uD83D\uDE42"))), "outsideABound"))
                    .extracting(each -> ((JsonValue.JsonString) each.parameterValue("text",
                            ParameterLocation.QUERY).orElseThrow().value()).value())
                    .containsOnly("a\uD83D\uDE42\uD83D\uDE42\uD83D\uDE42");
        }

        @Test
        @DisplayName("a closed list of numbers is stepped off above its largest; one of words and "
                + "numbers mixed, or with null in it, is left alone")
        void lists_of_every_kind() {
            assertThat(offTheList(List.of(JsonValue.of(3), JsonValue.of(7))))
                    .containsOnly(JsonValue.of(BigDecimal.valueOf(8)));
            assertThat(offTheList(List.of(JsonValue.TRUE))).containsOnly(JsonValue.FALSE);
            assertThat(offTheList(List.of(JsonValue.TRUE, JsonValue.FALSE))).isEmpty();
            assertThat(offTheList(List.of(JsonValue.of("a"), JsonValue.of(1)))).isEmpty();
            assertThat(offTheList(List.of(JsonValue.of("a"), JsonValue.NULL))).isEmpty();
        }

        @Test
        @DisplayName("null is not sent where a name points at a shape that allows it, or a choice "
                + "offers one that does")
        void null_allowed_somewhere_inside() {
            ObjectSchema holder = ObjectSchema.of(properties(
                    "byName", SchemaReference.to("MaybeWord"),
                    "choice", new io.restest.core.schema.ChoiceSchema(SchemaMetadata.none(),
                            List.of(NumberSchema.of(NumberKind.INTEGER), new StringSchema(
                                    SchemaMetadata.none().withNullable(true), Optional.empty(),
                                    Optional.empty(), Optional.empty(), Optional.empty()))),
                    "plain", StringSchema.of()), Set.of());
            Operation add = Operation.of(HttpMethod.POST, "/holders")
                    .withRequestBody(RequestBodyModel.json(holder, true))
                    .withId(OperationId.of("addHolder"));
            ApiModel api = ApiModel.of("Holders", "1.0", List.of(add)).withSchemas(Map.of(
                    "MaybeWord", new StringSchema(SchemaMetadata.none().withNullable(true),
                            Optional.empty(), Optional.empty(), Optional.empty(),
                            Optional.empty())));
            AcceptedRequests.Accepted accepted = new AcceptedRequests.Accepted(TestCase.of(
                    add.id(), List.of(), new BodyValue("application/json", JsonValue.object(
                            members("byName", JsonValue.of("w"), "choice", JsonValue.of(1),
                                    "plain", JsonValue.of("p"))),
                            new ValueOrigin.Generated("random"))), InteractionId.generate());
            List<TestCase> changed = new ArrayList<>();
            for (long seed = 0; seed < DRAWS; seed++) {
                new Mutations(api, Settings.from(onlyTheSwitch("sendNull")).mutation(),
                        GenerationSettings.defaults(), new SplittableRandom(seed),
                                AWKWARD).changeOneThingIn(add, accepted).ifPresent(changed::add);
            }

            assertThat(changed).extracting(each -> each.mutation().orElseThrow().path())
                    .containsOnly("body.plain");
        }

        @Test
        @DisplayName("a list outside the body is never stepped down to nothing, which would drop "
                + "it from the request rather than send it one item short")
        void a_list_parameter_is_not_emptied() {
            Operation byIds = Operation.of(HttpMethod.GET, "/pets", List.of(Parameter.of("ids",
                    ParameterLocation.QUERY, true, new ArraySchema(SchemaMetadata.none(),
                            StringSchema.of(), Optional.of(1), Optional.empty(), false))))
                    .withId(OperationId.of("byIds"));
            Operation byPairs = Operation.of(HttpMethod.GET, "/pairs", List.of(Parameter.of("ids",
                    ParameterLocation.QUERY, true, new ArraySchema(SchemaMetadata.none(),
                            StringSchema.of(), Optional.of(2), Optional.empty(), false))))
                    .withId(OperationId.of("byPairs"));

            assertThat(changesIn(byIds, accepted(byIds, value("ids", ParameterLocation.QUERY,
                    JsonValue.array(JsonValue.of("1"), JsonValue.of("2")))), "outsideABound"))
                    .isEmpty();
            assertThat(changesIn(byPairs, accepted(byPairs, value("ids", ParameterLocation.QUERY,
                    JsonValue.array(JsonValue.of("1"), JsonValue.of("2")))), "outsideABound"))
                    .extracting(each -> each.parameterValue("ids", ParameterLocation.QUERY)
                            .orElseThrow().value())
                    .containsOnly(JsonValue.array(JsonValue.of("1")));
        }

        @Test
        @DisplayName("items that must all differ are not repeated to make a list longer")
        void unique_items_are_not_repeated() {
            ObjectSchema tagged = ObjectSchema.of(properties("tags", new ArraySchema(
                    SchemaMetadata.none(), StringSchema.of(), Optional.empty(), Optional.of(2),
                    true)), Set.of());
            Operation tag = Operation.of(HttpMethod.POST, "/tags")
                    .withRequestBody(RequestBodyModel.json(tagged, true))
                    .withId(OperationId.of("tag"));
            ApiModel api = ApiModel.of("Tags", "1.0", List.of(tag));
            AcceptedRequests.Accepted accepted = new AcceptedRequests.Accepted(TestCase.of(
                    tag.id(), List.of(), new BodyValue("application/json", JsonValue.object(
                            members("tags", JsonValue.array(JsonValue.of("a"), JsonValue.of("b")))),
                            new ValueOrigin.Generated("random"))), InteractionId.generate());

            for (String operator : List.of("outsideABound", "oversize")) {
                List<TestCase> changed = new ArrayList<>();
                for (long seed = 0; seed < 20; seed++) {
                    new Mutations(api, Settings.from(onlyTheSwitch(operator)).mutation(),
                            GenerationSettings.defaults(), new SplittableRandom(seed),
                                    AWKWARD).changeOneThingIn(tag, accepted).ifPresent(changed::add);
                }
                assertThat(changed).extracting(each -> each.mutation().orElseThrow().path())
                        .describedAs(operator)
                        .doesNotContain("body.tags");
            }
        }

        @Test
        @DisplayName("nothing deeper inside a body than the tool ever builds is a place")
        void as_deep_as_the_tool_builds() {
            Map<String, String> shallow = onlyTheSwitch("wrongType");
            Settings settings = Settings.from(shallow);
            GenerationSettings oneDeep = Settings.from(Map.of("generation.optionalNestingDepth",
                    "1", "generation.hardNestingDepth", "1")).generation();
            List<TestCase> changed = new ArrayList<>();
            for (long seed = 0; seed < DRAWS; seed++) {
                new Mutations(API, settings.mutation(), oneDeep, new SplittableRandom(seed),
                                AWKWARD).changeOneThingIn(ADD_PET, ADDED).ifPresent(changed::add);
            }

            assertThat(changed).isNotEmpty()
                    .extracting(each -> each.mutation().orElseThrow().path())
                    .noneMatch(path -> path.chars().filter(c -> c == '.').count() > 1
                            || path.contains("[]"));
        }

        private List<JsonValue> offTheList(List<JsonValue> accepted) {
            StringSchema listed = new StringSchema(SchemaMetadata.none().withEnumeration(accepted),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
            ObjectSchema holder = ObjectSchema.of(properties("v", listed), Set.of());
            Operation add = Operation.of(HttpMethod.POST, "/v")
                    .withRequestBody(RequestBodyModel.json(holder, true))
                    .withId(OperationId.of("addV"));
            AcceptedRequests.Accepted base = new AcceptedRequests.Accepted(TestCase.of(add.id(),
                    List.of(), new BodyValue("application/json", JsonValue.object(members("v",
                            accepted.get(0))), new ValueOrigin.Generated("random"))),
                    InteractionId.generate());
            List<JsonValue> sent = new ArrayList<>();
            for (long seed = 0; seed < 20; seed++) {
                new Mutations(ApiModel.of("V", "1.0", List.of(add)),
                        Settings.from(onlyTheSwitch("breakAnEnumeration")).mutation(),
                        GenerationSettings.defaults(), new SplittableRandom(seed),
                                AWKWARD).changeOneThingIn(add, base).ifPresent(changed -> sent.add(
                                ((JsonValue.JsonObject) changed.body().orElseThrow().value())
                                        .members().get("v")));
            }
            return sent;
        }
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static AcceptedRequests.Accepted bodied(Operation operation, JsonValue body) {
        return new AcceptedRequests.Accepted(TestCase.of(operation.id(), List.of(),
                new BodyValue("application/json", body, new ValueOrigin.Generated("random"))),
                InteractionId.generate());
    }

    private static NumberSchema number(NumberKind kind, String format) {
        return new NumberSchema(SchemaMetadata.none(), kind, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.ofNullable(format));
    }

    private static AcceptedRequests.Accepted accepted(Operation operation,
            ParameterValue... values) {
        return new AcceptedRequests.Accepted(TestCase.of(operation.id(), List.of(values)),
                InteractionId.generate());
    }

    /** Every change of one kind for an operation of its own, with only that kind switched on. */
    private static List<TestCase> changesIn(Operation operation,
            AcceptedRequests.Accepted accepted, String operator) {
        ApiModel api = ApiModel.of("One", "1.0", List.of(operation));
        List<TestCase> changed = new ArrayList<>();
        for (long seed = 0; seed < 40; seed++) {
            new Mutations(api, Settings.from(onlyTheSwitch(operator)).mutation(),
                    GenerationSettings.defaults(), new SplittableRandom(seed),
                            AWKWARD).changeOneThingIn(operation, accepted).ifPresent(changed::add);
        }
        return changed;
    }

    /** Every change that comes out of many starting numbers with only this kind switched on. */
    private static List<TestCase> changes(String operator, Operation operation,
            AcceptedRequests.Accepted accepted) {
        List<TestCase> changed = draw(Settings.from(onlyTheSwitch(operator)).mutation(), operation,
                accepted);
        assertThat(changed).allSatisfy(each -> assertThat(each.mutation().orElseThrow()
                .operator()).isEqualTo(operator));
        return changed;
    }

    private static List<TestCase> draw(MutationSettings settings, Operation operation,
            AcceptedRequests.Accepted accepted) {
        List<TestCase> changed = new ArrayList<>();
        for (long seed = 0; seed < DRAWS; seed++) {
            new Mutations(API, settings, GenerationSettings.defaults(), new SplittableRandom(seed),
                            AWKWARD).changeOneThingIn(operation, accepted).ifPresent(changed::add);
        }
        return changed;
    }

    private static Map<String, String> onlyTheSwitch(String operator) {
        Map<String, String> given = new HashMap<>(Map.of("mutation.violations", "true"));
        for (Mutations.Operator each : Mutations.Operator.values()) {
            given.put("mutation." + each.written(), String.valueOf(each.written().equals(operator)));
        }
        return given;
    }

    /** The value at the end of a path such as body.owner.email or body.tags[] in a request body. */
    private static JsonValue at(TestCase testCase, String path) {
        JsonValue here = testCase.body().orElseThrow().value();
        for (String step : path.substring("body.".length()).split("\\.")) {
            boolean element = step.endsWith("[]");
            String name = element ? step.substring(0, step.length() - 2) : step;
            here = ((JsonValue.JsonObject) here).members().get(name);
            if (element) {
                // Whichever element was changed is the one that differs from what was accepted,
                // or any of them if the list itself was replaced.
                JsonValue.JsonArray list = (JsonValue.JsonArray) here;
                JsonValue.JsonArray was = (JsonValue.JsonArray) ((JsonValue.JsonObject)
                        ADDED.testCase().body().orElseThrow().value()).members().get(name);
                JsonValue differing = list.elements().get(0);
                for (int at = 0; at < list.elements().size(); at++) {
                    if (!list.elements().get(at).equals(was.elements().get(at))) {
                        differing = list.elements().get(at);
                    }
                }
                here = differing;
            }
        }
        return here;
    }

    private static List<JsonValue> carried() {
        try {
            return Mutations.awkwardValuesIn(List.of(Dictionaries.shipped()));
        } catch (java.io.IOException cannotRead) {
            throw new java.io.UncheckedIOException(cannotRead);
        }
    }

    private static ParameterValue value(String name, ParameterLocation location, JsonValue value) {
        return ParameterValue.of(name, location, value, new ValueOrigin.Generated("random"));
    }

    private static Map<String, CanonicalSchema> properties(Object... pairs) {
        Map<String, CanonicalSchema> built = new LinkedHashMap<>();
        for (int at = 0; at < pairs.length; at += 2) {
            built.put((String) pairs[at], (CanonicalSchema) pairs[at + 1]);
        }
        return built;
    }

    private static Map<String, JsonValue> members(Object... pairs) {
        Map<String, JsonValue> built = new LinkedHashMap<>();
        for (int at = 0; at < pairs.length; at += 2) {
            built.put((String) pairs[at], (JsonValue) pairs[at + 1]);
        }
        return built;
    }
}

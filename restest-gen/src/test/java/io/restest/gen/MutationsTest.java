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

import io.restest.core.execution.BodyValue;
import io.restest.core.execution.Intent;
import io.restest.core.execution.InteractionId;
import io.restest.core.execution.Mutation;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.CanonicalSchema;
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

    private static final ApiModel API = ApiModel.of("Pets", "1.0",
            List.of(FIND_PETS, ADD_PET, ADD_PET_BY_FORM));

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
        @DisplayName("a word where a parameter is a number; nothing where a parameter is a word, "
                + "since any text is a word")
        void a_word_for_a_number() {
            List<TestCase> changed = changes("wrongType", FIND_PETS, FOUND);

            assertThat(changed).extracting(each -> each.mutation().orElseThrow().path())
                    .containsOnly("ownerId", "limit");
            assertThat(changed).allSatisfy(each -> {
                Mutation mutation = each.mutation().orElseThrow();
                ParameterValue sent = each.parameterValue(mutation.path(), mutation.location())
                        .orElseThrow();
                assertThat(sent.value()).isEqualTo(JsonValue.of("abc"));
                assertThat(sent.origin()).isEqualTo(new ValueOrigin.Generated("wrongType"));
                assertThat(mutation.description()).contains("declared as a number");
            });
        }

        @Test
        @DisplayName("inside a body, any other kind the shape does not accept, but never null")
        void another_kind_inside_a_body() {
            List<TestCase> changed = changes("wrongType", ADD_PET, ADDED);

            assertThat(changed).isNotEmpty().allSatisfy(each -> {
                String path = each.mutation().orElseThrow().path();
                JsonValue sent = at(each, path);
                JsonValue was = at(ADDED.testCase(), path);
                assertThat(sent.getClass()).isNotEqualTo(was.getClass());
                assertThat(sent).isNotInstanceOf(JsonValue.JsonNull.class);
            });
            assertThat(changed).extracting(each -> each.mutation().orElseThrow().path())
                    .contains("body.name", "body.age", "body.tags", "body.owner",
                            "body.owner.email", "body.tags[]")
                    .doesNotContain("body.id");
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

        @Test
        @DisplayName("an empty value where nothing forbids one is a probe, which expects nothing")
        void empty_where_nothing_forbids_it() {
            List<TestCase> changed = changes("emptyWithNoRule", FIND_PETS, FOUND);

            assertThat(changed).extracting(each -> each.mutation().orElseThrow().path())
                    .contains("q", "X-Trace", "session")
                    .doesNotContain("ownerId", "limit", "status");
            assertThat(changed).allSatisfy(each -> {
                assertThat(each.intent()).isEqualTo(Intent.UNKNOWN);
                assertThat(each.mutation().orElseThrow().description())
                        .contains("nothing says may not be empty");
            });
            assertThat(changes("emptyWithNoRule", ADD_PET, ADDED))
                    .extracting(each -> each.mutation().orElseThrow().path())
                    .contains("body.tag", "body.tags", "body.nickname")
                    .doesNotContain("body.name", "body.owner.email");
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
        @DisplayName("where no most is stated and nothing else would be broken, as a probe")
        void where_no_most_is_stated() {
            List<TestCase> changed = changes("oversizeWithNoLimit", ADD_PET, ADDED);

            assertThat(changed).extracting(each -> each.mutation().orElseThrow().path())
                    .describedAs("not the name, which has a most, nor the e-mail address, which "
                            + "ten thousand characters would stop being")
                    .containsOnly("body.tag", "body.nickname", "body.tags[]");
            assertThat(changed).allSatisfy(each -> assertThat(each.intent())
                    .isEqualTo(Intent.UNKNOWN));
        }

        @Test
        @DisplayName("an oversized value is as long as the settings say")
        void as_long_as_the_settings_say() {
            Map<String, String> given = onlyTheSwitch("oversizeWithNoLimit");
            given.put("mutation.oversizedLength", "12");
            Mutations mutations = new Mutations(API, Settings.from(given).mutation(),
                    GenerationSettings.defaults(), new SplittableRandom(3));

            TestCase changed = mutations.changeOneThingIn(FIND_PETS, FOUND).orElseThrow();

            Mutation mutation = changed.mutation().orElseThrow();
            assertThat(((JsonValue.JsonString) changed.parameterValue(mutation.path(),
                    mutation.location()).orElseThrow().value()).value()).hasSize(12);
            assertThat(mutation.description()).contains("12 characters");
        }
    }

    @Nested
    @DisplayName("the families and what is never touched")
    class Families {

        @Test
        @DisplayName("with violations off only probes come out, and the other way round")
        void each_family_switches_off_alone() {
            Map<String, String> probesOnly = new HashMap<>(Map.of("mutation.violations", "false"));
            Map<String, String> violationsOnly = new HashMap<>(Map.of("mutation.probes", "false"));

            assertThat(draw(Settings.from(probesOnly).mutation(), ADD_PET, ADDED))
                    .isNotEmpty()
                    .allSatisfy(each -> assertThat(each.intent()).isEqualTo(Intent.UNKNOWN))
                    .extracting(each -> each.mutation().orElseThrow().operator())
                    .containsOnly("oversizeWithNoLimit", "emptyWithNoRule");
            assertThat(draw(Settings.from(violationsOnly).mutation(), ADD_PET, ADDED))
                    .isNotEmpty()
                    .allSatisfy(each -> assertThat(each.intent())
                            .isEqualTo(Intent.REFUSAL_EXPECTED));
        }

        @Test
        @DisplayName("with both off nothing is changed at all")
        void both_off_changes_nothing() {
            MutationSettings off = MutationSettings.defaults().withNothingChanged();

            assertThat(draw(off, ADD_PET, ADDED)).isEmpty();
            assertThat(draw(off, FIND_PETS, FOUND)).isEmpty();
        }

        @Test
        @DisplayName("every kind of change, all on, and every one of them is different from what "
                + "was accepted and names it")
        void everything_on() {
            List<TestCase> changed = new ArrayList<>(draw(MutationSettings.defaults(), ADD_PET,
                    ADDED));
            changed.addAll(draw(MutationSettings.defaults(), FIND_PETS, FOUND));

            assertThat(changed).extracting(each -> each.mutation().orElseThrow().operator())
                    .containsAll(java.util.Arrays.stream(Mutations.Operator.values())
                            .map(Mutations.Operator::written).toList());
            assertThat(changed).allSatisfy(each -> {
                TestCase base = each.operation().equals(ADD_PET.id())
                        ? ADDED.testCase() : FOUND.testCase();
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

            assertThat(draw(MutationSettings.defaults(), ADD_PET_BY_FORM, byForm)).isEmpty();
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
                new Mutations(round, MutationSettings.defaults(), GenerationSettings.defaults(),
                        new SplittableRandom(seed)).changeOneThingIn(addThing, accepted)
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
                        GenerationSettings.defaults(), new SplittableRandom(seed))
                        .changeOneThingIn(add, accepted).ifPresent(changed::add);
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

            for (String operator : List.of("outsideABound", "oversize", "oversizeWithNoLimit")) {
                List<TestCase> changed = new ArrayList<>();
                for (long seed = 0; seed < 20; seed++) {
                    new Mutations(api, Settings.from(onlyTheSwitch(operator)).mutation(),
                            GenerationSettings.defaults(), new SplittableRandom(seed))
                            .changeOneThingIn(tag, accepted).ifPresent(changed::add);
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
                new Mutations(API, settings.mutation(), oneDeep, new SplittableRandom(seed))
                        .changeOneThingIn(ADD_PET, ADDED).ifPresent(changed::add);
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
                        GenerationSettings.defaults(), new SplittableRandom(seed))
                        .changeOneThingIn(add, base).ifPresent(changed -> sent.add(
                                ((JsonValue.JsonObject) changed.body().orElseThrow().value())
                                        .members().get("v")));
            }
            return sent;
        }
    }

    // --- helpers ---------------------------------------------------------------------------------

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
                    GenerationSettings.defaults(), new SplittableRandom(seed))
                    .changeOneThingIn(operation, accepted).ifPresent(changed::add);
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
            new Mutations(API, settings, GenerationSettings.defaults(), new SplittableRandom(seed))
                    .changeOneThingIn(operation, accepted).ifPresent(changed::add);
        }
        return changed;
    }

    private static Map<String, String> onlyTheSwitch(String operator) {
        Map<String, String> given = new HashMap<>();
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

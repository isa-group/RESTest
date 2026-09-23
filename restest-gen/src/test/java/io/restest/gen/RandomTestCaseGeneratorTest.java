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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.BodyContent;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.ParameterStyle;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.NothingSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.StringSchema;
import io.restest.core.schema.UnsupportedSchema;
import io.restest.core.settings.Settings;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Deciding what to try against an API, without anybody having configured anything. */
class RandomTestCaseGeneratorTest {

    /**
     * The operations seed 20260912 picks, in order, from three that carry no parameters.
     *
     * <p>Recorded from a run, not derived from anything. It is the seed's meaning written down.
     *
     * <p>Rewritten once, on purpose, when a run stopped building every request the same way and
     * started deciding first what kind of request it was making. That decision draws on the run's
     * randomness before anything else does, so every number written down before it names a
     * different run now. Changing this line is what taking that decision looks like.
     */
    private static final String PINNED_ORDER =
            "stores vets stores pets vets stores stores pets stores stores "
            + "vets stores pets vets vets stores pets pets vets vets";

    private static final Operation LIST_PETS = Operation.of(HttpMethod.GET, "/pets", List.of(
            Parameter.of("status", ParameterLocation.QUERY, true, StringSchema.of()),
            Parameter.of("limit", ParameterLocation.QUERY, false, StringSchema.of())));

    /** An operation whose body must be sent, and can be: an ordinary JSON one. */
    private static final Operation CREATE_PET = Operation.of(HttpMethod.POST, "/pets")
            .withRequestBody(RequestBodyModel.json(
                    ObjectSchema.of(Map.of("name", StringSchema.of()), Set.of("name")), true));

    /** An operation whose body must be sent and is offered only as a file upload. */
    private static final Operation UPLOAD_PHOTO = Operation.of(HttpMethod.POST, "/pets/photo")
            .withRequestBody(RequestBodyModel.ofShapes(true,
                    Map.of("multipart/form-data", ObjectSchema.of(Map.of()))));

    /** Every one of these can be left out; nothing here is ever required. */
    private static final List<String> FOUR_OPTIONAL_NAMES = List.of("a", "b", "c", "d");

    private static final Operation FOUR_OPTIONAL_PARAMETERS = Operation.of(HttpMethod.GET, "/search",
            FOUR_OPTIONAL_NAMES.stream()
                    .map(name -> Parameter.of(name, ParameterLocation.QUERY, false, StringSchema.of()))
                    .toList());

    /** Two parameters an API insists on, eight it merely accepts, declared in between them. */
    private static final List<String> EIGHT_OPTIONAL_NAMES =
            List.of("opt1", "opt2", "opt3", "opt4", "opt5", "opt6", "opt7", "opt8");

    private static final Operation TWO_REQUIRED_EIGHT_OPTIONAL = Operation.of(HttpMethod.GET,
            "/search", List.of(
                    Parameter.of("q", ParameterLocation.QUERY, true, StringSchema.of()),
                    Parameter.of("opt1", ParameterLocation.QUERY, false, StringSchema.of()),
                    Parameter.of("opt2", ParameterLocation.QUERY, false, StringSchema.of()),
                    Parameter.of("opt3", ParameterLocation.QUERY, false, StringSchema.of()),
                    Parameter.of("opt4", ParameterLocation.QUERY, false, StringSchema.of()),
                    Parameter.of("sort", ParameterLocation.QUERY, true, StringSchema.of()),
                    Parameter.of("opt5", ParameterLocation.QUERY, false, StringSchema.of()),
                    Parameter.of("opt6", ParameterLocation.QUERY, false, StringSchema.of()),
                    Parameter.of("opt7", ParameterLocation.QUERY, false, StringSchema.of()),
                    Parameter.of("opt8", ParameterLocation.QUERY, false, StringSchema.of())));

    /** Two optional parameters nothing can invent a value for, alongside two ordinary ones. */
    private static final Operation MIXED_FILLABILITY = Operation.of(HttpMethod.GET, "/search", List.of(
            Parameter.of("fillableOne", ParameterLocation.QUERY, false, StringSchema.of()),
            Parameter.of("unfillableOne", ParameterLocation.QUERY, false,
                    UnsupportedSchema.of("oneOf is not folded in yet")),
            Parameter.of("fillableTwo", ParameterLocation.QUERY, false, StringSchema.of()),
            Parameter.of("unfillableTwo", ParameterLocation.QUERY, false,
                    UnsupportedSchema.of("oneOf is not folded in yet"))));

    @Test
    @DisplayName("every parameter the API requires is given a value")
    void required_parameters_are_always_filled_in() {
        RandomTestCaseGenerator generator = generatorFor(LIST_PETS);

        TestCase testCase = generator.generate(LIST_PETS).orElseThrow();

        assertThat(testCase.parameterValue("status", ParameterLocation.QUERY)).isPresent();
        assertThat(testCase.operation()).isEqualTo(LIST_PETS.id());
    }

    @Test
    @DisplayName("a parameter the API does not require is sometimes sent and sometimes not")
    void optional_parameters_vary() {
        RandomTestCaseGenerator generator = generatorFor(LIST_PETS);

        List<Boolean> sent = IntStream.range(0, 40)
                .mapToObj(i -> generator.generate(LIST_PETS).orElseThrow())
                .map(testCase -> testCase.parameterValue("limit", ParameterLocation.QUERY)
                        .isPresent())
                .toList();

        assertThat(sent).describedAs("an API behaves differently depending on which optional "
                        + "parameters arrive, so a run has to try it both ways")
                .contains(true).contains(false);
    }

    @Test
    @DisplayName("the request asking for nothing beyond what is required stops being almost "
            + "impossible to draw, however many optional parameters an operation offers")
    void the_required_only_request_is_no_longer_rare() {
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(
                model(TWO_REQUIRED_EIGHT_OPTIONAL), 20260923L, List.of(),
                planOf(step(Campaign.Builtin.RANDOM)));

        List<TestCase> draws = IntStream.range(0, 4000)
                .mapToObj(i -> generator.generate(TWO_REQUIRED_EIGHT_OPTIONAL).orElseThrow())
                .toList();

        assertThat(draws).describedAs("both parameters the API insists on are never left out, "
                        + "whatever else is decided about the eight it merely accepts")
                .allSatisfy(testCase -> {
                    assertThat(testCase.parameterValue("q", ParameterLocation.QUERY)).isPresent();
                    assertThat(testCase.parameterValue("sort", ParameterLocation.QUERY)).isPresent();
                });
        long requiredOnly = draws.stream()
                .filter(testCase -> EIGHT_OPTIONAL_NAMES.stream().noneMatch(name -> testCase
                        .parameterValue(name, ParameterLocation.QUERY).isPresent()))
                .count();
        assertThat(requiredOnly).describedAs("with eight optional parameters, deciding each one "
                        + "with its own independent coin drew this about once in 256 attempts; "
                        + "deciding how many first draws it about half the time, whatever the "
                        + "count - out of %d draws, %d carried none of the eight", draws.size(),
                        requiredOnly)
                .isBetween(1200L, 2800L);
    }

    @Test
    @DisplayName("which optional parameters are chosen does not favour the ones declared first")
    void which_optional_parameters_are_chosen_is_not_positional() {
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model(FOUR_OPTIONAL_PARAMETERS),
                20260923L, List.of(), planOf(step(Campaign.Builtin.RANDOM)));

        List<TestCase> draws = IntStream.range(0, 4000)
                .mapToObj(i -> generator.generate(FOUR_OPTIONAL_PARAMETERS).orElseThrow())
                .toList();

        List<Long> countPerParameter = FOUR_OPTIONAL_NAMES.stream()
                .map(name -> draws.stream()
                        .filter(testCase -> testCase.parameterValue(name, ParameterLocation.QUERY)
                                .isPresent())
                        .count())
                .toList();

        assertThat(Collections.max(countPerParameter) - Collections.min(countPerParameter))
                .describedAs("how often each of the four was included: %s - a mechanism that "
                        + "settled how many first and then kept adding the earliest-declared ones "
                        + "would spread these far apart instead of keeping them close",
                        countPerParameter)
                .isLessThan(400L);
    }

    @Test
    @DisplayName("with no chance of continuing, a request never carries more than what is required")
    void no_continue_chance_sends_only_what_is_required() {
        Settings none = Settings.from(Map.of("generation.optionalParameterContinueChance", "0"));
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model(FOUR_OPTIONAL_PARAMETERS),
                20260923L, List.of(), planOf(step(Campaign.Builtin.RANDOM)), none);

        assertThat(IntStream.range(0, 50)
                .mapToObj(i -> generator.generate(FOUR_OPTIONAL_PARAMETERS).orElseThrow()))
                .allSatisfy(testCase -> assertThat(FOUR_OPTIONAL_NAMES).noneMatch(name -> testCase
                        .parameterValue(name, ParameterLocation.QUERY).isPresent()));
    }

    @Test
    @DisplayName("with every optional parameter a candidate, one nothing can fill a value for is "
            + "still left out silently rather than failing the request")
    void full_continue_chance_still_drops_what_cannot_be_filled() {
        Settings all = Settings.from(Map.of("generation.optionalParameterContinueChance", "1"));
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model(MIXED_FILLABILITY),
                20260923L, List.of(), planOf(step(Campaign.Builtin.RANDOM)), all);

        assertThat(IntStream.range(0, 50)
                .mapToObj(i -> generator.generate(MIXED_FILLABILITY).orElseThrow()))
                .allSatisfy(testCase -> {
                    assertThat(testCase.parameterValue("fillableOne", ParameterLocation.QUERY))
                            .isPresent();
                    assertThat(testCase.parameterValue("fillableTwo", ParameterLocation.QUERY))
                            .isPresent();
                    assertThat(testCase.parameterValue("unfillableOne", ParameterLocation.QUERY))
                            .isEmpty();
                    assertThat(testCase.parameterValue("unfillableTwo", ParameterLocation.QUERY))
                            .isEmpty();
                });
    }

    @Test
    @DisplayName("how many optional parameters are included follows the intended distribution, not "
            + "merely something else that happens to send none of them about as often")
    void the_full_distribution_of_how_many_matches_what_was_intended() {
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model(FOUR_OPTIONAL_PARAMETERS),
                20260923L, List.of(), planOf(step(Campaign.Builtin.RANDOM)));

        long[] howManyIncluded = new long[FOUR_OPTIONAL_NAMES.size() + 1];
        for (int draw = 0; draw < 8000; draw++) {
            TestCase testCase = generator.generate(FOUR_OPTIONAL_PARAMETERS).orElseThrow();
            int included = (int) FOUR_OPTIONAL_NAMES.stream()
                    .filter(name -> testCase.parameterValue(name, ParameterLocation.QUERY).isPresent())
                    .count();
            howManyIncluded[included]++;
        }

        // Deciding each of the four independently, at whatever single rate happens to send none
        // of them about half the time too, would come out as roughly 2,750 / 3,350 / 1,550 / 350 /
        // 20 here instead - closer on some of these than a size drawn one at a time would suggest,
        // which is exactly why the first two counts, not just the share sending none, are what
        // tells the two apart.
        assertThat(howManyIncluded[0]).describedAs("none of the four, out of 8000 draws: %s",
                        Arrays.toString(howManyIncluded))
                .isBetween(3600L, 4400L);
        assertThat(howManyIncluded[1]).describedAs("exactly one of the four, out of 8000 draws: %s",
                        Arrays.toString(howManyIncluded))
                .isBetween(1600L, 2400L);
    }

    @Test
    @DisplayName("whether an optional body is sent follows its own setting, not the one deciding "
            + "how many optional parameters go in")
    void the_optional_body_chance_is_independent_of_the_parameter_count_chance() {
        Operation search = Operation.of(HttpMethod.POST, "/pets/search")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(
                        Map.of("term", StringSchema.of())), false));
        Settings neverBodyAlwaysParameters = Settings.from(Map.of(
                "generation.optionalBodyChance", "0",
                "generation.optionalParameterContinueChance", "1"));
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model(search), 20260923L,
                List.of(), planOf(step(Campaign.Builtin.RANDOM)), neverBodyAlwaysParameters);

        assertThat(IntStream.range(0, 50).mapToObj(i -> generator.generate(search).orElseThrow()))
                .describedAs("the body's own chance is at 0 and the parameter count's is at 1 - if "
                        + "body() read the wrong one, every request here would carry a body")
                .allSatisfy(testCase -> assertThat(testCase.body()).isEmpty());
    }

    @Test
    @DisplayName("every value in a test case says where it came from")
    void every_value_carries_its_origin() {
        Operation withDeclaredValues = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("status", ParameterLocation.QUERY, true, new StringSchema(
                        SchemaMetadata.none().withEnumeration(List.of(JsonValue.of("available"))),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())),
                Parameter.of("name", ParameterLocation.QUERY, true, StringSchema.of())));

        TestCase testCase = generatorFor(withDeclaredValues).generate(withDeclaredValues)
                .orElseThrow();

        assertThat(testCase.parameterValue("status", ParameterLocation.QUERY).orElseThrow()
                .origin()).isEqualTo(
                        ValueOrigin.declared(ValueOrigin.Declared.Statement.ENUMERATION));
        assertThat(testCase.parameterValue("name", ParameterLocation.QUERY).orElseThrow().origin())
                .isEqualTo(new ValueOrigin.Generated("random"));
        assertThat(testCase.parameterValues()).allSatisfy(value ->
                assertThat(value.origin()).isNotNull());
    }

    @Test
    @DisplayName("the same starting number produces the same run, which is what makes a surprise "
            + "worth investigating")
    void the_same_seed_produces_the_same_test_cases() {
        List<String> first = run(1234L);
        List<String> second = run(1234L);
        List<String> other = run(4321L);

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(other);
    }

    @Test
    @DisplayName("a number is tied to one source of randomness, so changing that source is a "
            + "decision somebody has to take on purpose")
    void the_run_a_seed_produces_is_pinned_to_one_source_of_randomness() {
        Operation pets = Operation.of(HttpMethod.GET, "/pets");
        Operation stores = Operation.of(HttpMethod.GET, "/stores");
        Operation vets = Operation.of(HttpMethod.GET, "/vets");

        String chosen = order(generatorFor(pets, stores, vets));

        // Written out rather than computed, which is the whole point: a computed expectation would
        // agree with whatever the code does. These three operations carry no parameters, so nothing
        // but the choice of operation draws on the run's randomness - which keeps this sentence
        // about the source of randomness alone, and leaves it untouched by every later change to
        // how values are invented.
        assertThat(chosen)
                .describedAs("A number printed by a run means the run it produced, and it means "
                        + "that on somebody else's machine too. Swap the source of randomness, or "
                        + "disturb how the number reaches it, and every seed ever written down - in "
                        + "a bug report, in a paper, in a build log - quietly names a different "
                        + "run. That is a decision to take deliberately and write down, so this "
                        + "test exists to make it impossible to take by accident.")
                .isEqualTo(PINNED_ORDER);
    }

    /** Which operation each attempt picked, as one line. */
    private static String order(RandomTestCaseGenerator generator) {
        return IntStream.range(0, 20)
                .mapToObj(attempt -> generator.generate().orElseThrow().operation().toString())
                .map(operation -> operation.substring(operation.lastIndexOf('/') + 1))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    @Test
    @DisplayName("an operation that needs a body is given one, in the media type it asks for")
    void an_operation_needing_a_body_is_given_one() {
        RandomTestCaseGenerator generator = generatorFor(LIST_PETS, CREATE_PET);

        assertThat(generator.untestableOperations()).isEmpty();
        TestCase testCase = generator.generate(CREATE_PET).orElseThrow();
        assertThat(testCase.body()).isPresent();
        assertThat(testCase.body().orElseThrow().mediaType()).isEqualTo("application/json");
        assertThat(testCase.body().orElseThrow().value())
                .isInstanceOf(JsonValue.JsonObject.class);
        assertThat(((JsonValue.JsonObject) testCase.body().orElseThrow().value()).members())
                .describedAs("the property the document requires is in every body sent")
                .containsKey("name");
    }

    @Test
    @DisplayName("an operation that only accepts a body is sent one some of the time")
    void an_optional_body_is_sometimes_left_out() {
        Operation search = Operation.of(HttpMethod.POST, "/pets/search")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(
                        Map.of("term", StringSchema.of())), false));
        RandomTestCaseGenerator generator = generatorFor(search);

        List<Boolean> sent = IntStream.range(0, 40)
                .mapToObj(draw -> generator.generate(search).orElseThrow().body().isPresent())
                .distinct()
                .toList();

        assertThat(sent)
                .describedAs("an API answers differently with and without a body, and a tool that "
                        + "always sent one would only ever see one of those answers")
                .containsExactlyInAnyOrder(true, false);
    }

    @Test
    @DisplayName("a body offered only as a web form is sent as one")
    void a_form_body_is_sent_as_a_form() {
        Operation addFeature = Operation.of(HttpMethod.POST, "/features")
                .withRequestBody(RequestBodyModel.ofShapes(true, Map.of(
                        "application/x-www-form-urlencoded",
                        ObjectSchema.of(Map.of("name", StringSchema.of()), Set.of("name")))));
        RandomTestCaseGenerator generator = generatorFor(addFeature);

        TestCase testCase = generator.generate(addFeature).orElseThrow();

        assertThat(testCase.body().orElseThrow().mediaType())
                .isEqualTo("application/x-www-form-urlencoded");
    }

    @Test
    @DisplayName("a body offered only in a way that cannot be written is named, not guessed at")
    void a_body_we_cannot_write_is_reported_by_its_media_type() {
        RandomTestCaseGenerator generator = generatorFor(LIST_PETS, UPLOAD_PHOTO);

        assertThat(generator.testableOperations()).containsExactly(LIST_PETS);
        assertThat(generator.untestableOperations())
                .containsOnlyKeys(UPLOAD_PHOTO.id())
                .extractingByKey(UPLOAD_PHOTO.id(), org.assertj.core.api.InstanceOfAssertFactories
                        .STRING).contains("multipart/form-data");
    }

    @Test
    @DisplayName("an operation that merely accepts a body we cannot write is still tested without one")
    void an_optional_body_we_cannot_write_costs_nothing() {
        Operation upload = Operation.of(HttpMethod.POST, "/pets/{petId}/photo", List.of(
                Parameter.of("petId", ParameterLocation.PATH, true, StringSchema.of())))
                .withRequestBody(RequestBodyModel.ofShapes(false,
                        Map.of("multipart/form-data", ObjectSchema.of(Map.of()))));
        RandomTestCaseGenerator generator = generatorFor(upload);

        assertThat(generator.untestableOperations()).isEmpty();
        assertThat(generator.generate(upload).orElseThrow().body())
                .describedAs("the document says a body may be left out, and leaving it out is a "
                        + "request worth sending")
                .isEmpty();
    }

    @Test
    @DisplayName("a GET or a HEAD that insists on a body is named as untestable, rather than sent "
            + "for the whole run and never answered")
    void a_get_or_a_head_insisting_on_a_body_is_reported() {
        Operation search = Operation.of(HttpMethod.GET, "/pets/search")
                .withRequestBody(RequestBodyModel.json(
                        ObjectSchema.of(Map.of("name", StringSchema.of()), Set.of("name")), true));
        Operation probe = Operation.of(HttpMethod.HEAD, "/pets/search")
                .withRequestBody(RequestBodyModel.json(StringSchema.of(), true));
        RandomTestCaseGenerator generator = generatorFor(LIST_PETS, search, probe);

        assertThat(generator.testableOperations()).containsExactly(LIST_PETS);
        assertThat(generator.untestableOperations())
                .containsOnlyKeys(search.id(), probe.id())
                .containsEntry(search.id(),
                        "it requires a request body, and RESTest cannot send one with a GET request")
                .containsEntry(probe.id(),
                        "it requires a request body, and RESTest cannot send one with a HEAD request");
        assertThat(generator.generate(search))
                .describedAs("the client that sends requests refuses to build a GET with a body, "
                        + "so a test case for one would be thrown away before it left the machine")
                .isEmpty();
    }

    @Test
    @DisplayName("an operation RESTest cannot send costs the others nothing: they are tested exactly "
            + "as they would be without it")
    void an_operation_that_cannot_be_sent_draws_nothing() {
        Operation search = Operation.of(HttpMethod.GET, "/pets/search")
                .withRequestBody(RequestBodyModel.json(
                        ObjectSchema.of(Map.of("name", StringSchema.of()), Set.of("name")), true));
        RandomTestCaseGenerator alone = generatorFor(LIST_PETS);
        RandomTestCaseGenerator beside = generatorFor(search, LIST_PETS);

        List<String> fromAlone = IntStream.range(0, 20)
                .mapToObj(draw -> describe(alone.generate().orElseThrow()))
                .toList();
        List<String> fromBeside = IntStream.range(0, 20)
                .mapToObj(draw -> describe(beside.generate().orElseThrow()))
                .toList();

        assertThat(fromBeside)
                .describedAs("whether it can be sent is read off the document, so no value is "
                        + "drawn for it first, and every later decision comes from the numbers it "
                        + "would have come from anyway")
                .isEqualTo(fromAlone);
    }

    @Test
    @DisplayName("a GET that merely accepts a body is still counted among the operations that can be "
            + "tested")
    void a_get_merely_accepting_a_body_is_still_counted_testable() {
        Operation search = Operation.of(HttpMethod.GET, "/pets/search")
                .withRequestBody(RequestBodyModel.json(
                        ObjectSchema.of(Map.of("name", StringSchema.of())), false));
        RandomTestCaseGenerator generator = generatorFor(search);

        // Counted, and rightly: a request without that body is one the document allows. What this
        // does not say is that every request for it goes without the body. An ordinary request
        // still draws it, on the same chance as any body an operation merely accepts, and the
        // client refuses each request that carries it - a known gap, left for a change of its own.
        assertThat(generator.testableOperations()).containsExactly(search);
        assertThat(generator.untestableOperations()).isEmpty();
    }

    @Test
    @DisplayName("a property the API only ever returns is never sent in a body")
    void a_read_only_property_is_never_sent() {
        Operation createPet = Operation.of(HttpMethod.POST, "/pets")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of(
                        "id", new StringSchema(
                                SchemaMetadata.none().withAccess(SchemaMetadata.Access.READ_ONLY),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty()),
                        "name", StringSchema.of()), Set.of("id", "name")), true));
        // Only the requests meant to be accepted. The rest of a run pushes at the API with values
        // chosen to be refused, and a body that is not an object at all is one of them.
        RandomTestCaseGenerator generator =
                new RandomTestCaseGenerator(model(createPet), 20260912L, List.of());

        for (int draw = 0; draw < 20; draw++) {
            JsonValue body = generator.generate(createPet).orElseThrow().body().orElseThrow()
                    .value();
            assertThat(((JsonValue.JsonObject) body).members())
                    .describedAs("an identifier the API hands out is not ours to send, even where "
                            + "the shape calls it required")
                    .containsKey("name")
                    .doesNotContainKey("id");
        }
    }

    @Test
    @DisplayName("a sample body the document writes down is sent as the author wrote it")
    void a_sample_body_is_sent() {
        JsonValue sample = JsonValue.object(Map.of("name", JsonValue.of("Bobby")));
        Operation createPet = Operation.of(HttpMethod.POST, "/pets")
                .withRequestBody(new RequestBodyModel(true, Map.of("application/json",
                        new BodyContent(ObjectSchema.of(Map.of("name", StringSchema.of())),
                                List.of(sample))), Optional.empty()));
        RandomTestCaseGenerator generator = generatorFor(createPet);

        List<JsonValue> bodies = IntStream.range(0, 20)
                .mapToObj(draw -> generator.generate(createPet).orElseThrow().body().orElseThrow()
                        .value())
                .distinct()
                .toList();

        assertThat(bodies).contains(sample);
    }

    @Test
    @DisplayName("an operation whose sample identifier is nothing at all still sends requests")
    void a_sample_that_is_nothing_does_not_cost_an_operation_its_whole_budget() {
        Operation getOwner = Operation.of(HttpMethod.GET, "/owners/{ownerId}", List.of(
                Parameter.of("ownerId", ParameterLocation.PATH, true, StringSchema.of())
                        .withExamples(List.of(JsonValue.NULL))));
        RandomTestCaseGenerator generator = generatorFor(getOwner);

        assertThat(generator.untestableOperations()).isEmpty();
        for (int draw = 0; draw < 20; draw++) {
            TestCase testCase = generator.generate(getOwner).orElseThrow();
            // Assembling is what would throw: an empty piece of a path closes the gap instead of
            // filling it, and every request for the operation would be thrown away unattributed.
            assertThat(RequestBuilder.build(getOwner, testCase, "http://localhost:8080").url())
                    .startsWith("http://localhost:8080/owners/")
                    .isNotEqualTo("http://localhost:8080/owners/");
        }
    }

    @Test
    @DisplayName("an operation whose parameters cannot be written into a request is reported")
    void an_operation_that_cannot_be_assembled_is_reported() {
        Operation deepObject = Operation.of(HttpMethod.GET, "/search", List.of(
                new Parameter("filter", ParameterLocation.QUERY, false, ObjectSchema.of(Map.of()),
                        ParameterStyle.DEEP_OBJECT, true, Optional.empty(), Optional.empty(),
                        List.of())));

        assertThat(generatorFor(deepObject).untestableOperations())
                .containsOnlyKeys(deepObject.id());
    }

    @Test
    @DisplayName("an operation requiring a value nobody could invent is reported, not attempted")
    void an_operation_needing_an_unreadable_value_is_reported() {
        Operation unreadable = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("filter", ParameterLocation.QUERY, true,
                        UnsupportedSchema.of("oneOf is not folded in yet"))));

        assertThat(generatorFor(unreadable).untestableOperations())
                .extractingByKey(unreadable.id(), org.assertj.core.api.InstanceOfAssertFactories
                        .STRING).contains("could not be read");
    }

    @Test
    @DisplayName("an optional parameter nobody can invent a value for is simply left out")
    void an_optional_parameter_nobody_can_fill_is_omitted() {
        Operation operation = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("filter", ParameterLocation.QUERY, false,
                        UnsupportedSchema.of("oneOf is not folded in yet")),
                Parameter.of("status", ParameterLocation.QUERY, true, StringSchema.of())));
        RandomTestCaseGenerator generator = generatorFor(operation);

        assertThat(generator.testableOperations()).containsExactly(operation);
        TestCase testCase = generator.generate(operation).orElseThrow();
        assertThat(testCase.parameterValue("filter", ParameterLocation.QUERY)).isEmpty();
        assertThat(testCase.parameterValue("status", ParameterLocation.QUERY)).isPresent();
    }

    @Test
    @DisplayName("an operation is picked at random from the ones that can be attempted")
    void an_operation_is_chosen_among_the_testable_ones() {
        Operation pets = Operation.of(HttpMethod.GET, "/pets");
        Operation stores = Operation.of(HttpMethod.GET, "/stores");
        RandomTestCaseGenerator generator = generatorFor(pets, stores);

        List<OperationId> chosen = IntStream.range(0, 30)
                .mapToObj(i -> generator.generate().orElseThrow().operation())
                .distinct()
                .toList();

        assertThat(chosen).containsExactlyInAnyOrder(pets.id(), stores.id());
    }

    @Test
    @DisplayName("an API with nothing that can be attempted says so instead of failing")
    void an_api_with_nothing_testable_generates_nothing() {
        assertThat(generatorFor(UPLOAD_PHOTO).generate()).isEmpty();
    }

    @Test
    @DisplayName("two generators in one program do not disturb each other")
    void two_generators_are_independent() {
        RandomTestCaseGenerator one = new RandomTestCaseGenerator(model(LIST_PETS), 99L);
        RandomTestCaseGenerator two = new RandomTestCaseGenerator(model(LIST_PETS), 99L);

        one.generate(LIST_PETS);
        one.generate(LIST_PETS);
        String fromOne = statusIn(one.generate(LIST_PETS).orElseThrow());

        two.generate(LIST_PETS);
        two.generate(LIST_PETS);
        String fromTwo = statusIn(two.generate(LIST_PETS).orElseThrow());

        assertThat(fromOne).isEqualTo(fromTwo);
        assertThat(one.seed()).isEqualTo(99L);
    }

    @Test
    @DisplayName("an operation already reported as untestable is not quietly attempted anyway")
    void an_untestable_operation_is_not_attempted() {
        RandomTestCaseGenerator generator = generatorFor(UPLOAD_PHOTO);

        assertThat(generator.generate(UPLOAD_PHOTO))
                .describedAs("a request whose body could not be written is not a test, and asking "
                        + "for one anyway does not produce a half-built one")
                .isEmpty();
    }

    @Test
    @DisplayName("an operation whose required parameter nothing can fill is reported, not attempted "
            + "on every draw and silently failing")
    void an_operation_nobody_can_fill_is_reported() {
        Operation huge = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("token", ParameterLocation.QUERY, true, new io.restest.core.schema
                        .StringSchema(SchemaMetadata.none(), Optional.of(50_000), Optional.empty(),
                        Optional.empty(), Optional.empty()))));
        RandomTestCaseGenerator generator = generatorFor(huge);

        assertThat(generator.testableOperations()).isEmpty();
        assertThat(generator.untestableOperations())
                .extractingByKey(huge.id(), org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("no value could be found");
    }

    private List<String> run(long seed) {
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model(LIST_PETS), seed);
        return IntStream.range(0, 10)
                .mapToObj(i -> generator.generate().orElseThrow())
                .map(RandomTestCaseGeneratorTest::describe)
                .toList();
    }

    private static String describe(TestCase testCase) {
        return testCase.operation() + " " + testCase.parameterValues().stream()
                .map(value -> value.name() + "=" + value.value())
                .toList();
    }

    private static String statusIn(TestCase testCase) {
        return testCase.parameterValue("status", ParameterLocation.QUERY)
                .map(ParameterValue::value).map(Object::toString).orElse("");
    }

    @Test
    @DisplayName("the operations that cannot be tested are listed in the order the document declares them")
    void untestable_operations_keep_document_order() {
        Operation[] refused = new Operation[6];
        for (int i = 0; i < refused.length; i++) {
            refused[i] = Operation.of(HttpMethod.GET, "/path" + i, List.of(
                    Parameter.of("filter", ParameterLocation.QUERY, true,
                            UnsupportedSchema.of("oneOf is not folded in yet"))));
        }

        // The command line quotes the first of these as its example of what is wrong with the
        // document, so the order is not cosmetic: a map whose order is decided per process would
        // make the same command explain itself differently from one run to the next.
        assertThat(generatorFor(refused).untestableOperations().keySet())
                .containsExactly(refused[0].id(), refused[1].id(), refused[2].id(),
                        refused[3].id(), refused[4].id(), refused[5].id());
    }

    @Test
    @DisplayName("an operation whose only possible value could never be sent is said to be "
            + "untestable, rather than counted and never tested")
    void a_value_that_could_never_be_sent_is_not_offered() {
        // An object with nothing declared in it has nothing to put inside, so the only value
        // anybody could invent for it is the empty object - and an empty piece of a path closes
        // the gap instead of filling it, turning a request for one pet into a request for every
        // pet. The same is true of a string allowed no characters and a list allowed no items.
        //
        // This used to be a known gap, written down here as one: a value was found, the operation
        // was offered as testable, and every request it ever produced was thrown away when it was
        // assembled - for the whole of the run. Saying "no value could be found" is the honest
        // answer, and it puts the operation in the list the run reports rather than in the list it
        // claims to be testing.
        for (io.restest.core.schema.CanonicalSchema nothingSendable : List.of(
                ObjectSchema.of(Map.of()),
                new StringSchema(SchemaMetadata.none(), Optional.empty(), Optional.of(0),
                        Optional.empty(), Optional.empty()),
                new io.restest.core.schema.ArraySchema(SchemaMetadata.none(), StringSchema.of(),
                        Optional.empty(), Optional.of(0), false))) {
            Operation operation = Operation.of(HttpMethod.GET, "/pets/{petId}", List.of(
                    Parameter.of("petId", ParameterLocation.PATH, true, nothingSendable)));

            assertThat(generatorFor(operation).untestableOperations())
                    .describedAs("%s in a path", nothingSendable.getClass().getSimpleName())
                    .containsOnlyKeys(operation.id());
        }
    }

    @Test
    @DisplayName("part of a run is spent pushing at the API with values nobody would send")
    void some_requests_push_at_the_api() {
        Operation search = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("name", ParameterLocation.QUERY, true, StringSchema.of()),
                Parameter.of("age", ParameterLocation.QUERY, true,
                        io.restest.core.schema.NumberSchema.of(
                                io.restest.core.schema.NumberKind.INTEGER))));
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(
                ApiModel.of("Pets", "1.0", List.of(search)), 4242L,
                List.of(awkward("", -1)));

        int pushing = 0;
        for (int draw = 0; draw < 200; draw++) {
            TestCase testCase = generator.generate(search).orElseThrow();
            boolean fromTheListToPushWith = testCase.parameterValues().stream()
                    .map(io.restest.core.execution.ParameterValue::origin)
                    .anyMatch(origin -> origin.equals(
                            new io.restest.core.execution.ValueOrigin.Generated("fuzzing")));
            if (fromTheListToPushWith) {
                pushing++;
                // Every parameter at once, never a mixture: an API stops reading at the first thing
                // it does not like, so a request with one awkward value among good ones would teach
                // nothing that this one does not.
                assertThat(testCase.parameterValues()).allSatisfy(value ->
                        assertThat(value.origin()).isEqualTo(
                                new io.restest.core.execution.ValueOrigin.Generated("fuzzing")));
            }
        }

        assertThat(pushing)
                .describedAs("a quarter of 200 is 50, and a draw this size lands within a few of it")
                .isBetween(30, 75);
        assertThat(generator.sourcesThatPushAtTheApi()).containsExactly("fuzzing");
    }

    @Test
    @DisplayName("the share asked for is the share delivered, whatever it is and however many "
            + "lists of awkward values are in play")
    void the_share_asked_for_is_the_share_delivered() {
        // The number people will change first, so it is the number that has to be right. Bands are
        // wide enough that a fair draw never trips them and narrow enough that a share of one and
        // a share of three could not both pass.
        assertThat(howOftenRefused(0, 1)).isZero();
        assertThat(howOftenRefused(25, 1)).isBetween(180, 320);
        assertThat(howOftenRefused(50, 1)).isBetween(430, 570);
        assertThat(howOftenRefused(75, 1)).isBetween(680, 820);
        assertThat(howOftenRefused(100, 1)).isEqualTo(1000);

        // Three lists divide the same quarter between them rather than taking a quarter each.
        assertThat(howOftenRefused(25, 3))
                .describedAs("asking for a quarter has to mean a quarter whether one list of "
                        + "awkward values is in play or three")
                .isBetween(180, 320);
    }

    /** How many of a thousand requests were built to be refused, at this share and this many lists. */
    private static int howOftenRefused(int share, int lists) {
        Operation search = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("name", ParameterLocation.QUERY, true, StringSchema.of())));
        List<Dictionary> awkward = IntStream.range(0, lists)
                .mapToObj(each -> awkward("", -1, "fuzzing"))
                .map(Dictionary.class::cast)
                .toList();
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(
                ApiModel.of("Pets", "1.0", List.of(search)), 20260918L, awkward, share);
        java.util.Set<String> refusing = generator.sourcesThatPushAtTheApi();

        return (int) IntStream.range(0, 1000)
                .mapToObj(draw -> generator.generate(search).orElseThrow())
                .filter(testCase -> testCase.parameterValues().stream()
                        .map(io.restest.core.execution.ParameterValue::origin)
                        .anyMatch(origin -> origin instanceof io.restest.core.execution.ValueOrigin
                                .Generated made && refusing.contains(made.source())))
                .count();
    }

    @Test
    @DisplayName("a list of values somebody wrote for this API is actually sent, which is the whole "
            + "point of being able to hand one over")
    void a_list_of_good_values_is_used() {
        Operation search = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("name", ParameterLocation.QUERY, true, StringSchema.of())));
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(
                ApiModel.of("Pets", "1.0", List.of(search)), 4242L,
                List.of(ofOurOwn("Leo")), chaining());

        assertThat(generator.generate(search).orElseThrow()
                .parameterValue("name", ParameterLocation.QUERY).orElseThrow().value())
                .describedAs("a plan that asks the lists before anything else sends what they say. "
                        + "The plan RESTest carries weighs them against the rest instead, which is "
                        + "its own decision and measured where it is made")
                .isEqualTo(io.restest.core.json.JsonValue.of("Leo"));
    }

    @Test
    @DisplayName("a list written for one named parameter is asked before the document's own sample, "
            + "and a list written for a whole kind of value is asked after it")
    void how_much_a_list_knows_decides_when_it_is_asked() {
        io.restest.core.schema.CanonicalSchema sampled = new StringSchema(
                SchemaMetadata.none().withExamples(
                        List.of(io.restest.core.json.JsonValue.of("from the document"))),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        Operation search = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("name", ParameterLocation.QUERY, true, sampled)));
        ApiModel pets = ApiModel.of("Pets", "1.0", List.of(search));

        // Within one step of a plan, this ranking is the tool's own and no plan has to state it:
        // a list somebody wrote for this parameter knows more about this value than a list of
        // every piece of text in the world, so it is asked first.
        assertThat(sent(new RandomTestCaseGenerator(pets, 4242L,
                List.of(keyed("type", "for any text at all"),
                        keyed("name", "for this parameter")), chaining()), search))
                .describedAs("somebody wrote it for this parameter, so it is asked before a list "
                        + "written for a whole kind of value, whatever order the files arrived in")
                .isEqualTo("for this parameter");

        // Where a list sits against the document's own sample is the plan's business now, and
        // both sides of it are sayable. This is the arrangement the tool used to hard-wire.
        assertThat(sent(new RandomTestCaseGenerator(pets, 4242L,
                List.of(keyed("type", "for any text at all")),
                planOf(theList("ours"), step(Campaign.Builtin.EXAMPLE))), search))
                .describedAs("named before the document, a list is asked before it")
                .isEqualTo("for any text at all");
        assertThat(sent(new RandomTestCaseGenerator(pets, 4242L,
                List.of(keyed("type", "for any text at all")),
                planOf(step(Campaign.Builtin.EXAMPLE), theList("ours"))), search))
                .describedAs("named after it, a list for every piece of text knows less than the "
                        + "document's own sample of this one, and the sample wins")
                .isEqualTo("from the document");
    }

    @Test
    @DisplayName("a weighted plan does not pin a parameter to one value: where the document offers "
            + "one sample, invention still gets a turn")
    void a_weighted_plan_varies_what_a_pinned_parameter_receives() {
        io.restest.core.schema.CanonicalSchema sampled = new StringSchema(
                SchemaMetadata.none().withExamples(
                        List.of(io.restest.core.json.JsonValue.of("the one sample"))),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        Operation search = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("name", ParameterLocation.QUERY, true, sampled)));
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(
                ApiModel.of("Pets", "1.0", List.of(search)), 4242L, List.of(),
                planOf(new Campaign.Entry.Group(List.of(
                        new Campaign.Share(
                                new Campaign.Source.Builtin(Campaign.Builtin.EXAMPLE), 50),
                        new Campaign.Share(
                                new Campaign.Source.Builtin(Campaign.Builtin.RANDOM), 50)))));

        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (int draw = 0; draw < 40; draw++) {
            seen.add(sent(generator, search));
        }
        assertThat(seen)
                .describedAs("the whole reason a plan can weight its sources instead of ordering "
                        + "them: asked in turn, the sample would be the only value ever sent")
                .contains("the one sample")
                .hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("nothing overrides the closed list of values a document says it accepts")
    void a_closed_list_of_accepted_values_is_never_overridden() {
        io.restest.core.schema.CanonicalSchema onlyThese = new StringSchema(
                SchemaMetadata.none().withEnumeration(List.of(
                        io.restest.core.json.JsonValue.of("available"),
                        io.restest.core.json.JsonValue.of("sold"))),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        Operation search = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("status", ParameterLocation.QUERY, true, onlyThese)));
        ApiModel pets = ApiModel.of("Pets", "1.0", List.of(search));
        // Written for this very parameter, so by every other rule here it would win. A closed list
        // is not advice, though: it is the whole set of values the API takes, and sending anything
        // else is sending a value the document says is not allowed.
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(pets, 4242L,
                List.of(keyed("name", "not on the list")), 0);

        for (int draw = 0; draw < 50; draw++) {
            assertThat(sent(generator, search, "status")).isIn("available", "sold");
        }
    }

    @Test
    @DisplayName("a list written for one piece of a request body fills that piece, which is where "
            + "most of what a run sends actually lives")
    void a_list_reaches_inside_a_request_body() {
        Operation addOwner = Operation.of(HttpMethod.POST, "/owners")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of(
                        "city", StringSchema.of(),
                        "telephone", StringSchema.of()), Set.of("city", "telephone")), true));
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model(addOwner), 4242L,
                List.of(DictionaryDocument.read("""
                        {"version": 1, "name": "ours", "keyedBy": "operationAndParameter",
                         "values": {"POST /owners": {"body.city": ["Seville"]}}}""", "ours")), 0);

        for (int draw = 0; draw < 10; draw++) {
            JsonValue body = generator.generate(addOwner).orElseThrow().body().orElseThrow()
                    .value();
            assertThat(((JsonValue.JsonObject) body).member("city"))
                    .describedAs("the entry names one piece of this operation's body, and that is "
                            + "the piece it fills")
                    .contains(JsonValue.of("Seville"));
        }
    }

    @Test
    @DisplayName("a list written for a name alone fills that name wherever it turns up, a piece of "
            + "a body included")
    void a_list_written_for_a_name_reaches_inside_a_body_too() {
        Operation addOwner = Operation.of(HttpMethod.POST, "/owners")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of(
                        "city", StringSchema.of()), Set.of("city")), true));
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model(addOwner), 4242L,
                List.of(DictionaryDocument.read("""
                        {"version": 1, "name": "ours", "keyedBy": "name",
                         "values": {"city": ["Seville"]}}""", "ours")), 0);

        JsonValue body = generator.generate(addOwner).orElseThrow().body().orElseThrow().value();

        assertThat(((JsonValue.JsonObject) body).member("city")).contains(JsonValue.of("Seville"));
    }

    @Test
    @DisplayName("a list that gives the whole body gives the whole body: the pieces of it somebody "
            + "also wrote down are not poked into the object they wrote")
    void the_whole_body_beats_the_pieces_of_it() {
        Operation addOwner = Operation.of(HttpMethod.POST, "/owners")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of(
                        "city", StringSchema.of()), Set.of("city")), true));
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model(addOwner), 4242L,
                List.of(DictionaryDocument.read("""
                        {"version": 1, "name": "ours", "keyedBy": "operationAndParameter",
                         "values": {"POST /owners": {
                            "body": [{"city": "whole"}], "body.city": ["a piece"]}}}""",
                        "ours")), chaining());

        for (int draw = 0; draw < 10; draw++) {
            JsonValue body = generator.generate(addOwner).orElseThrow().body().orElseThrow()
                    .value();
            assertThat(((JsonValue.JsonObject) body).member("city"))
                    .describedAs("somebody who writes a whole body means that body; rewriting a "
                            + "piece of it would destroy the reason for writing it whole")
                    .contains(JsonValue.of("whole"));
        }
    }

    @Test
    @DisplayName("a parameter has pieces too: a list written for every element of one, or for a "
            + "property of one, fills them")
    void a_list_reaches_inside_a_parameter() {
        Operation search = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("tags", ParameterLocation.QUERY, true,
                        io.restest.core.schema.ArraySchema.of(StringSchema.of())),
                Parameter.of("filter", ParameterLocation.QUERY, true,
                        ObjectSchema.of(Map.of("city", StringSchema.of()), Set.of("city")))));
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model(search), 4242L,
                List.of(DictionaryDocument.read("""
                        {"version": 1, "name": "ours", "keyedBy": "operationAndParameter",
                         "values": {"GET /pets": {
                            "tags[]": ["urgent"], "filter.city": ["Seville"]}}}""", "ours")), 0);

        TestCase testCase = generator.generate(search).orElseThrow();

        assertThat(((JsonValue.JsonArray) testCase
                .parameterValue("tags", ParameterLocation.QUERY).orElseThrow().value()).elements())
                .isNotEmpty()
                .allSatisfy(element -> assertThat(element).isEqualTo(JsonValue.of("urgent")));
        assertThat(((JsonValue.JsonObject) testCase
                .parameterValue("filter", ParameterLocation.QUERY).orElseThrow().value())
                .member("city"))
                .contains(JsonValue.of("Seville"));
    }

    @Test
    @DisplayName("a list written for every element of a list inside a body fills every element")
    void a_list_reaches_inside_a_list_inside_a_body() {
        Operation addOwner = Operation.of(HttpMethod.POST, "/owners")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of(
                        "tags", new io.restest.core.schema.ArraySchema(SchemaMetadata.none(),
                                ObjectSchema.of(Map.of("label", StringSchema.of()),
                                        Set.of("label")),
                                Optional.of(1), Optional.of(3), false)),
                        Set.of("tags")), true));
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model(addOwner), 4242L,
                List.of(DictionaryDocument.read("""
                        {"version": 1, "name": "ours", "keyedBy": "operationAndParameter",
                         "values": {"POST /owners": {"body.tags[].label": ["urgent"]}}}""",
                        "ours")), 0);

        JsonValue body = generator.generate(addOwner).orElseThrow().body().orElseThrow().value();

        assertThat(((JsonValue.JsonArray) ((JsonValue.JsonObject) body).member("tags")
                .orElseThrow()).elements())
                .describedAs("a list of values is for every element, there being no one element "
                        + "somebody could have meant")
                .isNotEmpty()
                .allSatisfy(element -> assertThat(((JsonValue.JsonObject) element).member("label"))
                        .contains(JsonValue.of("urgent")));
    }

    @Test
    @DisplayName("a closed list of values on the far side of a pointer still beats a list somebody "
            + "wrote, inside a body as it does everywhere else")
    void a_closed_list_behind_a_pointer_is_still_a_closed_list() {
        Operation addPet = Operation.of(HttpMethod.POST, "/pets")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of(
                        "status", new SchemaReference(SchemaMetadata.none(), "PetStatus")),
                        Set.of("status")), true));
        ApiModel pets = ApiModel.of("Pets", "1.0", List.of(addPet)).withSchemas(Map.of("PetStatus",
                new StringSchema(SchemaMetadata.none().withEnumeration(
                        List.of(JsonValue.of("available"), JsonValue.of("sold"))),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())));
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(pets, 4242L,
                List.of(DictionaryDocument.read("""
                        {"version": 1, "name": "ours", "keyedBy": "operationAndParameter",
                         "values": {"POST /pets": {"body.status": ["neverSent"]}}}""", "ours")), 0);

        for (int draw = 0; draw < 20; draw++) {
            JsonValue body = generator.generate(addPet).orElseThrow().body().orElseThrow().value();
            assertThat(((JsonValue.JsonObject) body).member("status"))
                    .describedAs("the document named the only values the API takes; a pointer to "
                            + "the shape that says so does not make them advice")
                    .isIn(Optional.of(JsonValue.of("available")), Optional.of(JsonValue.of("sold")));
        }
    }

    @Test
    @DisplayName("a name the document declares twice gets the entry in both places, so the one "
            + "without a closed list of its own is filled from the list")
    void a_name_declared_twice_is_filled_where_it_can_be() {
        Operation updatePet = Operation.of(HttpMethod.PUT, "/pets/{petId}", List.of(
                Parameter.of("petId", ParameterLocation.PATH, true, StringSchema.of()),
                Parameter.of("petId", ParameterLocation.QUERY, true,
                        new StringSchema(SchemaMetadata.none().withEnumeration(
                                List.of(JsonValue.of("one"), JsonValue.of("two"))),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty()))));
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model(updatePet), 4242L,
                List.of(DictionaryDocument.read("""
                        {"version": 1, "name": "ours", "keyedBy": "operationAndParameter",
                         "values": {"PUT /pets/{petId}": {"petId": ["7"]}}}""", "ours")),
                chaining());

        TestCase testCase = generator.generate(updatePet).orElseThrow();

        assertThat(testCase.parameterValue("petId", ParameterLocation.PATH).orElseThrow().value())
                .describedAs("this is why an entry for a name declared twice is never called dead")
                .isEqualTo(JsonValue.of("7"));
        assertThat(testCase.parameterValue("petId", ParameterLocation.QUERY).orElseThrow().value())
                .describedAs("and this is why it cannot be called live either")
                .isIn(JsonValue.of("one"), JsonValue.of("two"));
    }

    @Test
    @DisplayName("a shape the document named and one written out where it is used behave the same "
            + "way at the depth where nothing more is built")
    void a_pointer_at_the_depth_limit_is_still_asked_about() {
        io.restest.core.schema.CanonicalSchema leaf =
                new SchemaReference(SchemaMetadata.none(), "Leaf");
        Operation deep = Operation.of(HttpMethod.POST, "/deep")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(
                        Map.of("a", ObjectSchema.of(Map.of("b", ObjectSchema.of(
                                Map.of("c", ObjectSchema.of(Map.of("d", leaf), Set.of("d"))),
                                Set.of("c"))), Set.of("b"))), Set.of("a")), true));
        ApiModel model = ApiModel.of("Deep", "1.0", List.of(deep))
                .withSchemas(Map.of("Leaf", StringSchema.of()));
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model, 4242L,
                List.of(DictionaryDocument.read("""
                        {"version": 1, "name": "ours", "keyedBy": "operationAndParameter",
                         "values": {"POST /deep": {"body.a.b.c.d": ["from the list"]}}}""",
                        "ours")), 0);

        assertThat(generator.untestableOperations())
                .describedAs("being too deep to invent anything more is not a reason to stop "
                        + "asking whoever might know the value")
                .isEmpty();
        JsonValue body = generator.generate(deep).orElseThrow().body().orElseThrow().value();
        assertThat(((JsonValue.JsonObject) ((JsonValue.JsonObject) ((JsonValue.JsonObject)
                ((JsonValue.JsonObject) body).member("a").orElseThrow()).member("b").orElseThrow())
                .member("c").orElseThrow()).member("d"))
                .contains(JsonValue.of("from the list"));
    }

    @Test
    @DisplayName("a plan keeps the run to the operations it names, and what it left alone is kept "
            + "apart from what the document made impossible")
    void a_plan_narrows_which_operations_are_tested() {
        Operation listing = Operation.of(HttpMethod.GET, "/pets");
        Operation adding = Operation.of(HttpMethod.POST, "/pets");
        ApiModel pets = ApiModel.of("Pets", "1.0", List.of(listing, adding));
        Campaign safeOnly = new Campaign(
                List.of(new Campaign.PlannedStrategy("nominal", 100,
                        List.of(step(Campaign.Builtin.RANDOM)))),
                WhichOperations.of(java.util.Set.of(HttpMethod.GET), List.of()));

        RandomTestCaseGenerator generator =
                new RandomTestCaseGenerator(pets, 1L, List.of(), safeOnly);

        assertThat(generator.testableOperations()).containsExactly(listing);
        assertThat(generator.operationsThePlanSetAside())
                .describedAs("somebody asked for this one to be left alone, which is not the same "
                        + "as the document making it impossible, and a run that confused the two "
                        + "would report a plan working exactly as intended as a fault in the API")
                .containsExactly(adding.id());
        assertThat(generator.untestableOperations()).isEmpty();
    }

    @Test
    @DisplayName("the strategy that pushes is left out when there is no list to push with, and a "
            + "strategy that merely lost a list of its own is not")
    void a_strategy_that_cannot_do_its_job_is_dropped() {
        io.restest.core.schema.CanonicalSchema onlyThese = new StringSchema(
                SchemaMetadata.none().withEnumeration(List.of(
                        JsonValue.of("available"), JsonValue.of("sold"))),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        Operation search = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("status", ParameterLocation.QUERY, true, onlyThese)));
        ApiModel pets = ApiModel.of("Pets", "1.0", List.of(search));
        // A plan of the shape anybody writes for a list of their own, beside one that pushes.
        Campaign plan = new Campaign(List.of(
                new Campaign.PlannedStrategy("nominal", 50,
                        List.of(theList("my-good-values"), step(Campaign.Builtin.ENUM))),
                new Campaign.PlannedStrategy("pushing", 50,
                        List.of(theList("fuzzing"), step(Campaign.Builtin.RANDOM)))),
                WhichOperations.everything());

        RandomTestCaseGenerator withAListToPushWith = new RandomTestCaseGenerator(pets, 1L,
                List.of(Dictionaries.fuzzing().orElseThrow()), plan);
        assertThat(withAListToPushWith.sourcesThatPushAtTheApi())
                .describedAs("'my-good-values' was not handed over either, and that must not cost "
                        + "the nominal strategy its place")
                .containsExactly("fuzzing");
        assertThat(sentOften(withAListToPushWith, search))
                .describedAs("half the run is still the nominal strategy, which still has the "
                        + "document's closed list to ask")
                .contains("available");

        RandomTestCaseGenerator withNothingToPushWith =
                new RandomTestCaseGenerator(pets, 1L, List.of(), plan);
        assertThat(withNothingToPushWith.sourcesThatPushAtTheApi())
                .describedAs("nothing awkward to send, so the strategy that would have sent it is "
                        + "left out rather than spending half the run on invented values under "
                        + "another name")
                .isEmpty();
        assertThat(sentOften(withNothingToPushWith, search))
                .describedAs("and every request now comes from the nominal strategy, so the "
                        + "document's closed list is honoured every time - which is the thing "
                        + "the dropped strategy had no step for")
                .containsExactlyInAnyOrder("available", "sold");
    }

    @Test
    @DisplayName("a strategy every source of which turned out to be missing is refused, since it "
            + "would be drawn for its share and abandon every request unbuilt")
    void a_strategy_that_can_fill_nothing_is_refused() {
        ApiModel pets = ApiModel.of("Pets", "1.0",
                List.of(Operation.of(HttpMethod.GET, "/pets")));
        Campaign namesOnlyWhatIsMissing = new Campaign(
                List.of(new Campaign.PlannedStrategy("mine", 100, List.of(theList("nowhere")))),
                WhichOperations.everything());

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> new RandomTestCaseGenerator(pets, 1L, List.of(),
                        namesOnlyWhatIsMissing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("could not fill in a single value");
    }

    @Test
    @DisplayName("a plan that can do nothing at all is refused, rather than quietly swapped for "
            + "a different one")
    void a_plan_that_can_do_nothing_is_refused() {
        ApiModel pets = ApiModel.of("Pets", "1.0",
                List.of(Operation.of(HttpMethod.GET, "/pets")));
        Campaign everythingPushes = new Campaign(
                List.of(new Campaign.PlannedStrategy("pushing", 100,
                        List.of(theList("fuzzing"), step(Campaign.Builtin.RANDOM)))),
                WhichOperations.everything());

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> new RandomTestCaseGenerator(pets, 1L, List.of(),
                        everythingPushes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nothing for the run to do");
    }

    /**
     * Every distinct value this operation's {@code status} parameter received over many draws.
     *
     * <p>Written out rather than reusing the helper above because a strategy that pushes sends
     * whatever it likes - an empty word, a null - and the point here is exactly what came out.
     */
    private static java.util.Set<String> sentOften(RandomTestCaseGenerator generator,
            Operation operation) {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (int draw = 0; draw < 60; draw++) {
            generator.generate(operation)
                    .flatMap(testCase -> testCase.parameterValue("status",
                            ParameterLocation.QUERY))
                    .ifPresent(value -> seen.add(value.value()
                            instanceof io.restest.core.json.JsonValue.JsonString text
                            ? text.value() : String.valueOf(value.value())));
        }
        return seen;
    }

    /**
     * Every source asked in turn, the first answer taken, with the run's own lists after the
     * closed list and before the document.
     *
     * <p>The arrangement the tool had before a plan could be written down, and still the one that
     * makes a preference absolute rather than likely. The tests that are about which source is
     * <em>preferred</em> use this, because a weighted plan answers that question with a
     * probability and an assertion cannot be made about one draw.
     */
    private static Campaign chaining() {
        return planOf(step(Campaign.Builtin.ENUM), everyListGiven(),
                step(Campaign.Builtin.EXAMPLE), step(Campaign.Builtin.DEFAULT),
                step(Campaign.Builtin.RANDOM));
    }

    /** One strategy taking the whole run, asking these in turn. */
    private static Campaign planOf(Campaign.Entry... sources) {
        return new Campaign(
                List.of(new Campaign.PlannedStrategy("nominal", 100, List.of(sources))),
                WhichOperations.everything());
    }

    private static Campaign.Entry step(Campaign.Builtin source) {
        return new Campaign.Entry.Single(new Campaign.Source.Builtin(source));
    }

    private static Campaign.Entry everyListGiven() {
        return new Campaign.Entry.Single(new Campaign.Source.EveryListGiven());
    }

    private static Campaign.Entry theList(String named) {
        return new Campaign.Entry.Single(new Campaign.Source.OneList(named));
    }

    private static String sent(RandomTestCaseGenerator generator, Operation operation) {
        return sent(generator, operation, "name");
    }

    private static String sent(RandomTestCaseGenerator generator, Operation operation,
            String parameter) {
        return ((io.restest.core.json.JsonValue.JsonString) generator.generate(operation)
                .orElseThrow().parameterValue(parameter, ParameterLocation.QUERY).orElseThrow()
                .value()).value();
    }

    /** A list of values somebody put together believing the API takes them. */
    private static Dictionary ofOurOwn(String value) {
        return keyed("type", value);
    }

    private static Dictionary keyed(String keyedBy, String value) {
        String key = switch (keyedBy) {
            case "name" -> "name";
            case "schema" -> "Status";
            default -> "string";
        };
        return DictionaryDocument.read("""
                {"version": 1, "name": "ours", "keyedBy": "%s", "values": {"%s": ["%s"]}}""".formatted(keyedBy, key, value), "ours");
    }

    @Test
    @DisplayName("a list to push with, in a run told to do no pushing, is named as one nothing "
            + "will be sent from")
    void a_list_that_cancels_itself_out_is_named() {
        Operation search = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("name", ParameterLocation.QUERY, true, StringSchema.of())));
        ApiModel pets = ApiModel.of("Pets", "1.0", List.of(search));

        assertThat(new RandomTestCaseGenerator(pets, 1L, List.of(awkward("", -1)), 0)
                .listsGivenButNotUsed())
                .describedAs("two things were asked for that cancel, and one of them is probably a "
                        + "mistake")
                .containsExactly("fuzzing");
        assertThat(new RandomTestCaseGenerator(pets, 1L, List.of(awkward("", -1)), 25)
                .listsGivenButNotUsed()).isEmpty();
        assertThat(new RandomTestCaseGenerator(pets, 1L, List.of(), 0)
                .listsGivenButNotUsed()).isEmpty();
    }

    @Test
    @DisplayName("a run given no list to push with builds every request to work")
    void without_such_a_list_nothing_pushes() {
        Operation search = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("name", ParameterLocation.QUERY, true, StringSchema.of())));
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(
                ApiModel.of("Pets", "1.0", List.of(search)), 4242L, List.of());

        assertThat(generator.sourcesThatPushAtTheApi()).isEmpty();
        for (int draw = 0; draw < 50; draw++) {
            assertThat(generator.generate(search).orElseThrow().parameterValues())
                    .allSatisfy(value -> assertThat(value.origin())
                            .isNotEqualTo(new io.restest.core.execution.ValueOrigin
                                    .Generated("awkward")));
        }
    }

    /** A list of values to push at an API with, named the way the built-in plan names one. */
    private static Dictionary awkward(String forText, long forWholeNumbers) {
        return awkward(forText, forWholeNumbers, "fuzzing");
    }

    private static Dictionary awkward(String forText, long forWholeNumbers, String called) {
        return new Dictionary() {
            @Override
            public List<io.restest.core.json.JsonValue> valuesFor(
                    io.restest.core.gen.ValueRequest request) {
                return request.schema() instanceof StringSchema
                        ? List.of(io.restest.core.json.JsonValue.of(forText))
                        : List.of(io.restest.core.json.JsonValue.of(forWholeNumbers));
            }

            @Override
            public String name() {
                return called;
            }

            @Override
            public boolean isAboutOneValueInParticular() {
                return false;
            }
        };
    }

    @Test
    @DisplayName("a list of values written for a named shape is sent as a whole body")
    void a_dictionary_keyed_by_shape_answers_for_a_body() {
        Operation createOwner = Operation.of(HttpMethod.POST, "/owners")
                .withRequestBody(RequestBodyModel.json(SchemaReference.to("Owner"), true));
        ApiModel model = ApiModel.of("Test API", "1.0", List.of(createOwner))
                .withSchemas(Map.of("Owner", ObjectSchema.of(
                        Map.of("firstName", StringSchema.of()), Set.of("firstName"))));
        ValueDictionary written = DictionaryDocument.read("""
                {"version": 1, "name": "owners", "keyedBy": "schema", "values": {
                   "Owner": [{"firstName": "George", "lastName": "Franklin"}]
                }}""", "a dictionary somebody wrote");
        RandomTestCaseGenerator generator =
                new RandomTestCaseGenerator(model, 20260918L, List.of(written));

        List<JsonValue> bodies = IntStream.range(0, 20)
                .mapToObj(draw -> generator.generate(createOwner).orElseThrow().body().orElseThrow()
                        .value())
                .distinct()
                .toList();

        assertThat(bodies)
                .describedAs("the format already said a value may be a whole object; this is the "
                        + "first thing that wanted one")
                .contains(JsonValue.object(new java.util.LinkedHashMap<>(Map.of(
                        "firstName", JsonValue.of("George"),
                        "lastName", JsonValue.of("Franklin")))));
    }

    @Test
    @DisplayName("a body whose description allows no value at all is named, one reason each")
    void a_body_nothing_could_satisfy_is_reported() {
        Operation allowsNothing = Operation.of(HttpMethod.POST, "/a")
                .withRequestBody(RequestBodyModel.json(NothingSchema.of(), true));
        Operation unreadable = Operation.of(HttpMethod.POST, "/b")
                .withRequestBody(RequestBodyModel.json(
                        UnsupportedSchema.of("oneOf is not folded in yet"), true));
        Operation nothingCanFill = Operation.of(HttpMethod.POST, "/c")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of("token",
                        new StringSchema(SchemaMetadata.none(), Optional.of(50_000),
                                Optional.empty(), Optional.empty(), Optional.empty())),
                        Set.of("token")), true));
        Operation notAnObject = Operation.of(HttpMethod.POST, "/d")
                .withRequestBody(RequestBodyModel.ofShapes(true,
                        Map.of("application/x-www-form-urlencoded", StringSchema.of())));

        Map<OperationId, String> refused = new RandomTestCaseGenerator(
                model(allowsNothing, unreadable, nothingCanFill, notAnObject), 20260918L)
                .untestableOperations();

        assertThat(refused.get(allowsNothing.id())).contains("allows no value at all");
        assertThat(refused.get(unreadable.id())).contains("could not be read");
        assertThat(refused.get(nothingCanFill.id())).contains("no value could be found");
        assertThat(refused.get(notAnObject.id()))
                .describedAs("a web form is named after the fields of an object, and a word has "
                        + "none")
                .contains("no fields to name");
    }

    @Test
    @DisplayName("a web form described as a choice between objects is tested, not refused on sight")
    void a_form_body_that_could_be_an_object_is_not_refused() {
        Operation createOwner = Operation.of(HttpMethod.POST, "/owners")
                .withRequestBody(RequestBodyModel.ofShapes(true,
                        Map.of("application/x-www-form-urlencoded", ChoiceSchema.of(List.of(
                                ObjectSchema.of(Map.of("firstName", StringSchema.of())),
                                ObjectSchema.of(Map.of("company", StringSchema.of())))))));
        RandomTestCaseGenerator generator =
                new RandomTestCaseGenerator(model(createOwner), 20260918L, List.of());

        assertThat(generator.untestableOperations())
                .describedAs("a shape saying the body is one object or another produces objects, "
                        + "and refusing it for not being an object itself would skip an operation "
                        + "this can test")
                .isEmpty();
        assertThat(generator.generate(createOwner).orElseThrow().body().orElseThrow().value())
                .isInstanceOf(JsonValue.JsonObject.class);
    }

    @Test
    @DisplayName("a body is pushed at the API as often as any other value is")
    void part_of_the_time_the_body_pushes_at_the_api() {
        RandomTestCaseGenerator generator = generatorFor(CREATE_PET);

        List<Boolean> anObjectWithAName = IntStream.range(0, 60)
                .mapToObj(draw -> generator.generate(CREATE_PET).orElseThrow().body().orElseThrow()
                        .value())
                .map(body -> body instanceof JsonValue.JsonObject object
                        && object.members().containsKey("name"))
                .distinct()
                .toList();

        assertThat(anObjectWithAName)
                .describedAs("a run spends part of its time pushing at the API, and a whole body "
                        + "drawn from the list of awkward values - a null, an empty object - is "
                        + "what that looks like for an operation that takes one")
                .containsExactlyInAnyOrder(true, false);
    }

    @Test
    @DisplayName("a run listens to its own replies only when the plan asks it to")
    void nothing_listens_unless_the_plan_asks() {
        RandomTestCaseGenerator asksForIt = new RandomTestCaseGenerator(model(LIST_PETS), 20260922L,
                List.of(), planOf(step(Campaign.Builtin.OBSERVED), step(Campaign.Builtin.RANDOM)));
        RandomTestCaseGenerator doesNot = new RandomTestCaseGenerator(model(LIST_PETS), 20260922L,
                List.of(), planOf(step(Campaign.Builtin.RANDOM)));

        assertThat(asksForIt.whatListensToTheRun()).isPresent();
        assertThat(doesNot.whatListensToTheRun())
                .describedAs("a run that does not watch its own replies is one the same starting "
                        + "number repeats exactly, and most runs should stay that way")
                .isEmpty();
    }

    @Test
    @DisplayName("what the API sent back is what goes out next, once it has been heard")
    void what_the_api_returned_is_sent_back() {
        Operation getPet = Operation.of(HttpMethod.GET, "/pets/{petId}", List.of(
                        Parameter.of("petId", ParameterLocation.PATH, true, StringSchema.of())))
                .withId(OperationId.of("getPet"))
                .withResponses(List.of(io.restest.core.model.ResponseModel.json("200",
                        ObjectSchema.of(Map.of("petId", StringSchema.of())))));
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model(getPet), 20260922L,
                List.of(), planOf(step(Campaign.Builtin.OBSERVED), step(Campaign.Builtin.RANDOM)));

        generator.whatListensToTheRun().orElseThrow().on(
                new io.restest.core.event.RunEvent.InteractionCompleted(java.time.Instant.EPOCH,
                        io.restest.core.execution.Interaction.answered(
                                TestCase.of(OperationId.of("getPet"), List.of()),
                                io.restest.core.execution.HttpRequestRecord.of(HttpMethod.GET,
                                        "https://api.example/pets/1"),
                                new io.restest.core.execution.HttpResponseRecord(
                                        io.restest.core.execution.StatusLine.of(200),
                                        List.of(io.restest.core.execution.Header.of("Content-Type",
                                                "application/json")),
                                        Optional.of(io.restest.core.execution.Payload.text(
                                                "{\"petId\": \"real-one\"}", "application/json"))),
                                java.time.Instant.EPOCH, java.time.Duration.ofMillis(3))));

        ParameterValue sent = generator.generate(getPet).orElseThrow()
                .parameterValue("petId", ParameterLocation.PATH).orElseThrow();

        assertThat(((JsonValue.JsonString) sent.value()).value())
                .describedAs("an identifier that came out of the API is one that exists, where an "
                        + "invented one reaches a 404")
                .isEqualTo("real-one");
        assertThat(sent.origin())
                .describedAs("and it says which exchange it was read out of")
                .isInstanceOf(ValueOrigin.Derived.class);
    }

    @Test
    @DisplayName("the value changed inside a thing the API returned is one the document allows")
    void the_changed_value_still_obeys_the_document() {
        StringSchema onlyThree = new StringSchema(
                SchemaMetadata.none().withEnumeration(List.of(JsonValue.of("available"),
                        JsonValue.of("pending"), JsonValue.of("sold"))),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        ObjectSchema pet = ObjectSchema.of(Map.of("status", onlyThree));
        Operation updatePet = Operation.of(HttpMethod.PUT, "/pets")
                .withId(OperationId.of("updatePet"))
                .withRequestBody(RequestBodyModel.json(SchemaReference.to("Pet"), true))
                .withResponses(List.of(
                        io.restest.core.model.ResponseModel.json("200",
                                SchemaReference.to("Pet"))));
        ApiModel api = ApiModel.of("Test API", "1.0", List.of(updatePet))
                .withSchemas(Map.of("Pet", pet));
        // Arranged the way the plan RESTest ships arranges it: the closed list of values a
        // document states is asked first and answers for a value that has one. Without a step like
        // that, the only thing that can supply a replacement for a value inside a remembered thing
        // is this same memory, which holds the value already there and nothing else - so the thing
        // is offered unchanged, correctly and uselessly.
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(api, 20260922L, List.of(),
                planOf(step(Campaign.Builtin.ENUM), step(Campaign.Builtin.OBSERVED),
                        step(Campaign.Builtin.RANDOM)));
        generator.whatListensToTheRun().orElseThrow().on(
                new io.restest.core.event.RunEvent.InteractionCompleted(java.time.Instant.EPOCH,
                        io.restest.core.execution.Interaction.answered(
                                TestCase.of(OperationId.of("updatePet"), List.of()),
                                io.restest.core.execution.HttpRequestRecord.of(HttpMethod.PUT,
                                        "https://api.example/pets"),
                                new io.restest.core.execution.HttpResponseRecord(
                                        io.restest.core.execution.StatusLine.of(200),
                                        List.of(io.restest.core.execution.Header.of("Content-Type",
                                                "application/json")),
                                        Optional.of(io.restest.core.execution.Payload.text(
                                                "{\"status\": \"sold\"}", "application/json"))),
                                java.time.Instant.EPOCH, java.time.Duration.ofMillis(3))));

        List<String> sent = IntStream.range(0, 40)
                .mapToObj(draw -> generator.generate(updatePet).orElseThrow().body().orElseThrow())
                .map(body -> ((JsonValue.JsonString) ((JsonValue.JsonObject) body.value())
                        .members().get("status")).value())
                .distinct()
                .toList();

        assertThat(sent)
                .describedAs("the one value in a returned thing that gets replaced is filled by "
                        + "the whole strategy, not by invention alone: fitting the shape and "
                        + "being on the list the document says it accepts are different things")
                .isNotEmpty()
                .allSatisfy(status -> assertThat(status)
                        .isIn("available", "pending", "sold"));
        assertThat(sent)
                .describedAs("and what goes out is not an echo of what came back: a copy of "
                        + "something that already exists asks the API to make a duplicate, which "
                        + "is the one outcome changing a value exists to avoid")
                .contains("available", "pending");
    }

    @Test
    @DisplayName("a document whose shapes point at each other does not end the run")
    void two_shapes_pointing_at_each_other_do_not_end_the_run() {
        // A document nobody would call wrong: two names for one shape, each written as a pointer
        // to the other. It parses without a single reported issue, and following it without
        // counting the hops runs out of room on the thread building the request.
        Operation addThing = Operation.of(HttpMethod.POST, "/things")
                .withId(OperationId.of("addThing"))
                .withRequestBody(RequestBodyModel.json(SchemaReference.to("A"), true))
                .withResponses(List.of(
                        io.restest.core.model.ResponseModel.json("200", SchemaReference.to("A"))));
        ApiModel roundAndRound = ApiModel.of("Test API", "1.0", List.of(addThing))
                .withSchemas(Map.of("A", SchemaReference.to("B"), "B", SchemaReference.to("A")));
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(roundAndRound, 20260922L,
                List.of(), planOf(step(Campaign.Builtin.OBSERVED), step(Campaign.Builtin.RANDOM)));
        generator.whatListensToTheRun().orElseThrow().on(
                new io.restest.core.event.RunEvent.InteractionCompleted(java.time.Instant.EPOCH,
                        io.restest.core.execution.Interaction.answered(
                                TestCase.of(OperationId.of("addThing"), List.of()),
                                io.restest.core.execution.HttpRequestRecord.of(HttpMethod.POST,
                                        "https://api.example/things"),
                                new io.restest.core.execution.HttpResponseRecord(
                                        io.restest.core.execution.StatusLine.of(200),
                                        List.of(io.restest.core.execution.Header.of("Content-Type",
                                                "application/json")),
                                        Optional.of(io.restest.core.execution.Payload.text(
                                                "{\"name\": \"a thing\"}", "application/json"))),
                                java.time.Instant.EPOCH, java.time.Duration.ofMillis(3))));

        assertThatCode(() -> {
            for (int attempt = 0; attempt < 50; attempt++) {
                generator.generate(addThing);
            }
        })
                .describedAs("a document RESTest cannot make sense of costs the operation it is "
                        + "in, never the run: this one is not even malformed")
                .doesNotThrowAnyException();
    }

    private static RandomTestCaseGenerator generatorFor(Operation... operations) {
        return new RandomTestCaseGenerator(model(operations), 20260912L);
    }

    private static ApiModel model(Operation... operations) {
        return ApiModel.of("Test API", "1.0", List.of(operations));
    }
}

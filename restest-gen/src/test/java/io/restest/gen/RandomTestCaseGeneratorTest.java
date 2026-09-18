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
import io.restest.core.model.ParameterStyle;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.StringSchema;
import io.restest.core.schema.UnsupportedSchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    @DisplayName("an operation that needs a body cannot be attempted yet, and says so by name")
    void an_operation_needing_a_body_is_reported() {
        Operation createPet = Operation.of(HttpMethod.POST, "/pets")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of()), true));
        RandomTestCaseGenerator generator = generatorFor(LIST_PETS, createPet);

        assertThat(generator.testableOperations()).containsExactly(LIST_PETS);
        assertThat(generator.untestableOperations())
                .containsOnlyKeys(createPet.id())
                .extractingByKey(createPet.id(), org.assertj.core.api.InstanceOfAssertFactories
                        .STRING).contains("requires a request body");
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
        Operation needsBody = Operation.of(HttpMethod.POST, "/pets")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of()), true));

        assertThat(generatorFor(needsBody).generate()).isEmpty();
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
        Operation createPet = Operation.of(HttpMethod.POST, "/pets")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of()), true));
        RandomTestCaseGenerator generator = generatorFor(createPet);

        assertThat(generator.generate(createPet))
                .describedAs("a body-less POST to an operation that requires one is a request "
                        + "nobody could send, not a test")
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
                List.of(ofOurOwn("Leo")), 0);

        assertThat(generator.generate(search).orElseThrow()
                .parameterValue("name", ParameterLocation.QUERY).orElseThrow().value())
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

        assertThat(sent(new RandomTestCaseGenerator(pets, 4242L,
                List.of(keyed("name", "for this parameter")), 0), search))
                .describedAs("somebody wrote it for this parameter, so it knows more than a sample "
                        + "written for the shape")
                .isEqualTo("for this parameter");
        assertThat(sent(new RandomTestCaseGenerator(pets, 4242L,
                List.of(keyed("type", "for any text at all")), 0), search))
                .describedAs("a list for every piece of text knows less than the document's own "
                        + "sample of this one")
                .isEqualTo("from the document");
        assertThat(sent(new RandomTestCaseGenerator(pets, 4242L,
                List.of(keyed("schema", "for this shape")), 0), search))
                .describedAs("a document declares a shape once and however many parameters refer "
                        + "to it get the same one, so a list for a shape is about a kind of value "
                        + "and the document's sample of this parameter still goes first")
                .isEqualTo("from the document");
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

    private static RandomTestCaseGenerator generatorFor(Operation... operations) {
        return new RandomTestCaseGenerator(model(operations), 20260912L);
    }

    private static ApiModel model(Operation... operations) {
        return ApiModel.of("Test API", "1.0", List.of(operations));
    }
}

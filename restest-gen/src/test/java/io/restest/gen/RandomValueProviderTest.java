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

import io.restest.core.gen.GeneratedValue;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.schema.AnySchema;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.BooleanSchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.NothingSchema;
import io.restest.core.schema.NullSchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import io.restest.core.schema.UnsupportedSchema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Whatever this invents has to satisfy the description it was invented from. Most of these run many
 * times over, because a generator that is right nine times out of ten is wrong.
 */
class RandomValueProviderTest {

    private static final ApiModel EMPTY = ApiModel.of("Test API", "1.0", List.of());

    /**
     * One provider for the whole class, drawing from one source of numbers.
     *
     * <p>Shared deliberately. A field built per test would be handed a freshly seeded source, and
     * every repetition of a repeated test would then make exactly the same draws as the first - so
     * twenty repetitions would be twenty copies of one assertion, and the sentence above this class
     * about running many times over would not be true of anything in it.
     */
    private static final RandomValueProvider provider =
            new RandomValueProvider(EMPTY, Schemas.fixedRandom());

    @RepeatedTest(50)
    @DisplayName("a string is invented between the lengths the specification allows")
    void a_string_respects_its_declared_lengths() {
        StringSchema schema = new StringSchema(SchemaMetadata.none(), Optional.of(3),
                Optional.of(7), Optional.empty(), Optional.empty());

        String value = ((JsonValue.JsonString) invent(schema)).value();

        assertThat(value.length()).isBetween(3, 7);
    }

    @RepeatedTest(20)
    @DisplayName("a string nobody described is still worth sending, so it is not empty")
    void a_string_with_no_limits_is_not_empty() {
        assertThat(((JsonValue.JsonString) invent(StringSchema.of())).value()).isNotEmpty();
    }

    @RepeatedTest(20)
    @DisplayName("a string that must be empty is empty")
    void a_string_limited_to_nothing_is_empty() {
        StringSchema schema = new StringSchema(SchemaMetadata.none(), Optional.empty(),
                Optional.of(0), Optional.empty(), Optional.empty());

        assertThat(((JsonValue.JsonString) invent(schema)).value()).isEmpty();
    }

    @RepeatedTest(50)
    @DisplayName("a whole number is invented inside its bounds")
    void an_integer_respects_its_bounds() {
        NumberSchema schema = NumberSchema.between(NumberKind.INTEGER,
                BigDecimal.valueOf(10), BigDecimal.valueOf(20));

        BigDecimal value = ((JsonValue.JsonNumber) invent(schema)).value();

        assertThat(value.intValueExact()).isBetween(10, 20);
        assertThat(value.scale()).describedAs("a whole number has no decimals").isLessThanOrEqualTo(0);
    }

    @RepeatedTest(50)
    @DisplayName("a bound the value must stay strictly inside is respected")
    void exclusive_bounds_are_respected() {
        NumberSchema schema = new NumberSchema(SchemaMetadata.none(), NumberKind.INTEGER,
                Optional.empty(), Optional.of(BigDecimal.valueOf(4)), Optional.empty(),
                Optional.of(BigDecimal.valueOf(9)), Optional.empty(), Optional.empty());

        BigDecimal value = ((JsonValue.JsonNumber) invent(schema)).value();

        assertThat(value.intValueExact()).isBetween(5, 8);
    }

    @RepeatedTest(50)
    @DisplayName("a number that must be a multiple of something is one")
    void a_step_is_respected() {
        NumberSchema schema = new NumberSchema(SchemaMetadata.none(), NumberKind.INTEGER,
                Optional.of(BigDecimal.TEN), Optional.empty(), Optional.of(BigDecimal.valueOf(100)),
                Optional.empty(), Optional.of(BigDecimal.valueOf(5)), Optional.empty());

        BigDecimal value = ((JsonValue.JsonNumber) invent(schema)).value();

        assertThat(value.remainder(BigDecimal.valueOf(5)).signum()).isZero();
        assertThat(value.intValueExact()).isBetween(10, 100);
    }

    @Test
    @DisplayName("bounds and a step that between them allow nothing are declined, not fudged")
    void an_impossible_number_is_declined() {
        NumberSchema nothingFits = new NumberSchema(SchemaMetadata.none(), NumberKind.INTEGER,
                Optional.of(BigDecimal.valueOf(3)), Optional.empty(),
                Optional.of(BigDecimal.valueOf(7)), Optional.empty(),
                Optional.of(BigDecimal.valueOf(10)), Optional.empty());

        assertThat(provider.offer(Schemas.asking(nothingFits))).isEmpty();
    }

    @RepeatedTest(20)
    @DisplayName("a list is invented with as many items as the specification allows, each of them valid")
    void a_list_respects_its_size_and_its_items() {
        ArraySchema schema = new ArraySchema(SchemaMetadata.none(),
                NumberSchema.between(NumberKind.INTEGER, BigDecimal.ONE, BigDecimal.TEN),
                Optional.of(2), Optional.of(3), false);

        List<JsonValue> elements = ((JsonValue.JsonArray) invent(schema)).elements();

        assertThat(elements).hasSizeBetween(2, 3)
                .allSatisfy(element -> assertThat(element).isInstanceOf(JsonValue.JsonNumber.class));
    }

    @RepeatedTest(20)
    @DisplayName("a list whose items must all differ has no repeats")
    void a_list_of_unique_items_has_no_repeats() {
        ArraySchema schema = new ArraySchema(SchemaMetadata.none(), BooleanSchema.of(),
                Optional.of(0), Optional.of(2), true);

        List<JsonValue> elements = ((JsonValue.JsonArray) invent(schema)).elements();

        assertThat(elements).doesNotHaveDuplicates();
    }

    @RepeatedTest(20)
    @DisplayName("an object always has the properties the API requires")
    void an_object_has_its_required_properties() {
        ObjectSchema schema = ObjectSchema.of(Map.of(
                        "id", StringSchema.of(),
                        "name", StringSchema.of(),
                        "nickname", StringSchema.of()),
                Set.of("id", "name"));

        Map<String, JsonValue> members = ((JsonValue.JsonObject) invent(schema)).members();

        assertThat(members).containsKeys("id", "name");
    }

    @Test
    @DisplayName("an object whose required property can hold no value cannot be invented either")
    void an_object_needing_the_impossible_is_declined() {
        ObjectSchema schema = ObjectSchema.of(Map.of("id", NothingSchema.of()), Set.of("id"));

        assertThat(provider.offer(Schemas.asking(schema))).isEmpty();
    }

    @Test
    @DisplayName("a shape that allows no value at all, and one nobody could read, are both declined")
    void impossible_and_unreadable_shapes_are_declined() {
        assertThat(provider.offer(Schemas.asking(NothingSchema.of()))).isEmpty();
        assertThat(provider.offer(Schemas.asking(
                UnsupportedSchema.of("oneOf is not folded in yet")))).isEmpty();
    }

    @Test
    @DisplayName("the simple shapes are invented as themselves")
    void the_simple_shapes_are_invented() {
        assertThat(invent(BooleanSchema.of())).isInstanceOf(JsonValue.JsonBoolean.class);
        assertThat(invent(NullSchema.of())).isEqualTo(JsonValue.NULL);
        assertThat(invent(AnySchema.of())).isNotNull();
    }

    @Test
    @DisplayName("a shape defined elsewhere by name is looked up and invented from")
    void a_named_shape_is_followed() {
        ApiModel model = ApiModel.of("Test API", "1.0", List.of())
                .withSchemas(Map.of("Widget", ObjectSchema.of(
                        Map.of("id", StringSchema.of()), Set.of("id"))));
        RandomValueProvider following = new RandomValueProvider(model, Schemas.fixedRandom());

        JsonValue value = following.offer(Schemas.asking(SchemaReference.to("Widget")))
                .orElseThrow().value();

        assertThat(((JsonValue.JsonObject) value).members()).containsKey("id");
    }

    @Test
    @DisplayName("a shape that contains itself is wound up instead of followed for ever")
    void a_self_referring_shape_terminates() {
        ApiModel model = ApiModel.of("Test API", "1.0", List.of())
                .withSchemas(Map.of("Comment", ObjectSchema.of(Map.of(
                        "text", StringSchema.of(),
                        "replies", ArraySchema.of(SchemaReference.to("Comment"))),
                        Set.of("text"))));
        RandomValueProvider following = new RandomValueProvider(model, Schemas.fixedRandom());

        Optional<GeneratedValue> value = following.offer(
                Schemas.asking(SchemaReference.to("Comment")));

        assertThat(value).describedAs("it must finish, and it must produce something").isPresent();
    }

    @Test
    @DisplayName("every invented value says that something invented it")
    void an_invented_value_says_where_it_came_from() {
        GeneratedValue value = provider.offer(Schemas.asking(StringSchema.of())).orElseThrow();

        assertThat(value.origin())
                .isEqualTo(new io.restest.core.execution.ValueOrigin.Generated("random"));
    }

    @Test
    @DisplayName("the same starting number invents the same values")
    void the_same_seed_invents_the_same_values() {
        List<String> first = invented();
        List<String> second = invented();

        assertThat(first).isEqualTo(second);
    }

    private List<String> invented() {
        RandomValueProvider repeatable = new RandomValueProvider(EMPTY, Schemas.fixedRandom());
        return IntStream.range(0, 20)
                .mapToObj(i -> repeatable.offer(Schemas.asking(StringSchema.of())).orElseThrow())
                .map(value -> ((JsonValue.JsonString) value.value()).value())
                .toList();
    }

    // --- what review found this generator getting wrong ----------------------------------------

    @RepeatedTest(20)
    @DisplayName("the allowed values of a list's items are honoured, not only the list's own")
    void a_list_of_allowed_values_uses_them() {
        io.restest.core.schema.StringSchema status = new StringSchema(
                SchemaMetadata.none().withEnumeration(List.of(
                        JsonValue.of("available"), JsonValue.of("sold"))),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        ArraySchema schema = new ArraySchema(SchemaMetadata.none(), status, Optional.of(1),
                Optional.of(3), false);

        assertThat(SchemaSatisfaction.violations(invent(schema), schema, EMPTY))
                .describedAs("a list of statuses is useless if its contents are made up")
                .isEmpty();
    }

    @RepeatedTest(20)
    @DisplayName("the allowed values of an object's properties are honoured too")
    void an_objects_allowed_values_are_used() {
        ObjectSchema schema = ObjectSchema.of(Map.of("status", new StringSchema(
                        SchemaMetadata.none().withEnumeration(List.of(JsonValue.of("open"))),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())),
                Set.of("status"));

        assertThat(SchemaSatisfaction.violations(invent(schema), schema, EMPTY)).isEmpty();
    }

    @Test
    @DisplayName("a string allowed to be as long as a number can count does not end the run")
    void an_enormous_allowed_length_does_not_break() {
        StringSchema schema = new StringSchema(SchemaMetadata.none(), Optional.of(0),
                Optional.of(Integer.MAX_VALUE), Optional.empty(), Optional.empty());

        String value = ((JsonValue.JsonString) invent(schema)).value();

        assertThat(value).describedAs("kept short: the API is being tested, not loaded")
                .hasSizeLessThan(100);
    }

    @Test
    @DisplayName("a string the specification insists must be enormous is declined, not built")
    void an_enormous_demanded_length_is_declined() {
        StringSchema schema = new StringSchema(SchemaMetadata.none(), Optional.of(50_000),
                Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(provider.offer(Schemas.asking(schema))).isEmpty();
    }

    @Test
    @DisplayName("a shape nested deeply inside itself is wound up, not built to its full size")
    void deep_inline_nesting_stays_small() {
        CanonicalSchema deep = StringSchema.of();
        for (int i = 0; i < 12; i++) {
            deep = new ArraySchema(SchemaMetadata.none(), deep, Optional.of(3), Optional.of(3),
                    false);
        }
        CanonicalSchema schema = deep;

        long started = System.nanoTime();
        Optional<GeneratedValue> value = provider.offer(Schemas.asking(schema));
        long took = System.nanoTime() - started;

        assertThat(java.time.Duration.ofNanos(took))
                .describedAs("this must not cost the run its budget or the API its patience")
                .isLessThan(java.time.Duration.ofSeconds(1));
        value.ifPresent(built -> assertThat(built.value().toString().length()).isLessThan(100_000));
    }

    @RepeatedTest(30)
    @DisplayName("a bound that must be exceeded wins over a looser one on the same side")
    void the_tighter_of_two_bounds_is_obeyed() {
        NumberSchema schema = new NumberSchema(SchemaMetadata.none(), NumberKind.INTEGER,
                Optional.of(BigDecimal.ONE), Optional.of(BigDecimal.TEN),
                Optional.of(BigDecimal.valueOf(20)), Optional.empty(), Optional.empty(),
                Optional.empty());

        BigDecimal value = ((JsonValue.JsonNumber) invent(schema)).value();

        assertThat(value.intValueExact())
                .describedAs("no smaller than 1 but strictly above 10 means at least 11")
                .isBetween(11, 20);
    }

    @RepeatedTest(20)
    @DisplayName("a list that must be long is built long, not abandoned")
    void a_list_longer_than_the_usual_ceiling_is_built() {
        ArraySchema schema = new ArraySchema(SchemaMetadata.none(), StringSchema.of(),
                Optional.of(5), Optional.of(10), false);

        List<JsonValue> elements = ((JsonValue.JsonArray) invent(schema)).elements();

        assertThat(elements).hasSizeBetween(5, 10);
    }

    @RepeatedTest(20)
    @DisplayName("a list of two values that must differ is built, not given up on")
    void a_short_list_of_unique_items_is_built() {
        ArraySchema schema = new ArraySchema(SchemaMetadata.none(), BooleanSchema.of(),
                Optional.of(2), Optional.of(2), true);

        assertThat(SchemaSatisfaction.violations(invent(schema), schema, EMPTY)).isEmpty();
    }

    @RepeatedTest(20)
    @DisplayName("an object is given as many properties as the specification requires, and no more")
    void the_number_of_properties_is_respected() {
        ObjectSchema atLeastThree = new ObjectSchema(SchemaMetadata.none(),
                Map.of("a", StringSchema.of(), "b", StringSchema.of(), "c", StringSchema.of(),
                        "d", StringSchema.of()),
                Set.of(), Optional.empty(), Optional.of(3), Optional.empty());
        ObjectSchema atMostOne = new ObjectSchema(SchemaMetadata.none(),
                Map.of("a", StringSchema.of(), "b", StringSchema.of(), "c", StringSchema.of()),
                Set.of(), Optional.empty(), Optional.empty(), Optional.of(1));

        assertThat(SchemaSatisfaction.violations(invent(atLeastThree), atLeastThree, EMPTY))
                .isEmpty();
        assertThat(SchemaSatisfaction.violations(invent(atMostOne), atMostOne, EMPTY)).isEmpty();
    }

    @RepeatedTest(30)
    @DisplayName("a number allowed decimals gets them, so a price or a rate is really exercised")
    void a_fractional_number_is_not_always_whole() {
        NumberSchema schema = NumberSchema.between(NumberKind.NUMBER, BigDecimal.ZERO,
                BigDecimal.ONE);

        BigDecimal value = ((JsonValue.JsonNumber) invent(schema)).value();

        assertThat(value).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
    }

    @Test
    @DisplayName("over a run, a number allowed decimals is not always the same whole number")
    void fractional_numbers_vary() {
        NumberSchema schema = NumberSchema.between(NumberKind.NUMBER, BigDecimal.ZERO,
                BigDecimal.ONE);

        java.util.Set<String> seen = IntStream.range(0, 50)
                .mapToObj(i -> ((JsonValue.JsonNumber) invent(schema)).value().toPlainString())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(seen).describedAs("an API storing a rate is never exercised by only 0")
                .hasSizeGreaterThan(5);
    }

    @Test
    @DisplayName("a sample for a whole list is not offered again for each of its elements")
    void a_sample_of_a_list_is_not_a_sample_of_its_elements() {
        // The question reaches this provider carrying the parameter's own sample, which is what
        // happens once anything ahead of it in the chain declines. Every element must be invented;
        // handing the whole list down would make each element a copy of the list.
        io.restest.core.gen.ValueRequest tags = io.restest.core.gen.ValueRequest.of(
                io.restest.core.model.OperationId.of("GET /pets"), "tags",
                io.restest.core.model.ParameterLocation.QUERY,
                ArraySchema.of(StringSchema.of()),
                List.of(JsonValue.array(JsonValue.of("cat"), JsonValue.of("dog"))));
        // Asked about what is inside a value, the same sources the run asks are asked - the one
        // that reads samples included, which is what a real run wires up.
        RandomValueProvider askingAboutSamplesToo = new RandomValueProvider(EMPTY,
                Schemas.fixedRandom(), new ExampleValueProvider(Schemas.fixedRandom()));

        JsonValue built = askingAboutSamplesToo.offer(tags).orElseThrow().value();

        assertThat(built).isInstanceOf(JsonValue.JsonArray.class);
        assertThat(((JsonValue.JsonArray) built).elements())
                .describedAs("a sample list of two words is a sample of the list, not of each "
                        + "word in it")
                .isNotEmpty()
                .allSatisfy(element -> assertThat(element)
                        .isInstanceOf(JsonValue.JsonString.class));
    }

    @RepeatedTest(30)
    @DisplayName("a value that goes in the path is never nothing at all")
    void a_path_value_is_never_nothing() {
        StringSchema nullable = new StringSchema(SchemaMetadata.none().withNullable(true),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        io.restest.core.gen.ValueRequest inThePath = io.restest.core.gen.ValueRequest.of(
                io.restest.core.model.OperationId.of("GET /pets/{petId}"), "petId",
                io.restest.core.model.ParameterLocation.PATH, nullable);

        assertThat(provider.offer(inThePath).orElseThrow().value())
                .describedAs("nothing in the path would address a different resource entirely")
                .isNotEqualTo(JsonValue.NULL);
    }

    @Test
    @DisplayName("every shape a choice offers gets used, not only the one the document listed first")
    void a_choice_spreads_across_the_shapes_it_offers() {
        ChoiceSchema aWordOrANumber = ChoiceSchema.of(List.of(
                StringSchema.of(), NumberSchema.of(NumberKind.INTEGER)));

        Set<String> kinds = IntStream.range(0, 60)
                .mapToObj(attempt -> invent(aWordOrANumber).getClass().getSimpleName())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(kinds)
                .describedAs("taking the first shape would make what gets tested depend on the "
                        + "order the document happened to list them in")
                .hasSize(2);
    }

    @RepeatedTest(20)
    @DisplayName("a shape that can offer nothing sends the choice to the next one, not to nothing")
    void a_choice_tries_another_shape_when_one_declines() {
        // Nothing satisfies the first shape, and the document plainly allows the second. Giving up
        // on the shape that was drawn would abandon a value the API accepts - and for a required
        // parameter, abandoning a value costs the whole operation.
        ChoiceSchema impossibleOrANumber = ChoiceSchema.of(List.of(
                NothingSchema.of(), NumberSchema.of(NumberKind.INTEGER)));

        assertThat(invent(impossibleOrANumber)).isInstanceOf(JsonValue.JsonNumber.class);
    }

    @RepeatedTest(30)
    @DisplayName("a choice in the path never chooses the shape whose only value is nothing at all")
    void a_choice_in_the_path_never_chooses_nothing() {
        // How OpenAPI 3.1 says "or null", there being no `nullable` keyword any more.
        ChoiceSchema aWordOrNothing = ChoiceSchema.of(List.of(
                StringSchema.of(), new NullSchema(SchemaMetadata.none())));
        io.restest.core.gen.ValueRequest inThePath = io.restest.core.gen.ValueRequest.of(
                io.restest.core.model.OperationId.of("GET /pets/{petId}"), "petId",
                io.restest.core.model.ParameterLocation.PATH, aWordOrNothing);

        assertThat(provider.offer(inThePath).orElseThrow().value())
                .isNotEqualTo(JsonValue.NULL);
    }

    /**
     * What a document says about the <em>characters</em> of a value, and the order the two
     * statements are honoured in.
     *
     * <p>A shape can name the kind of its value, state the spelling it demands, both, or neither,
     * and each of the four has to end somewhere sensible. These are the tests of that choice, at the
     * level where it is made: the pieces it is made of are tested on their own elsewhere, and
     * neither of those tests would notice the branches being reordered.
     */
    @Nested
    @DisplayName("the characters a document says a value is made of")
    class Characters {

        @RepeatedTest(20)
        @DisplayName("a value whose kind the document names is of that kind")
        void a_named_kind_is_honoured() {
            String value = text(StringSchema.ofFormat("date-time"));

            assertThatCode(() -> java.time.OffsetDateTime.parse(value))
                    .describedAs("%s is not a moment in time", value)
                    .doesNotThrowAnyException();
        }

        @RepeatedTest(20)
        @DisplayName("a value spelled the way the document demands")
        void a_stated_spelling_is_honoured() {
            String value = text(spelled("^[A-Z]{3}-[0-9]{4}$"));

            assertThat(value).matches("^[A-Z]{3}-[0-9]{4}$");
        }

        @RepeatedTest(20)
        @DisplayName("a kind too long for the shape gives way to something that fits")
        void a_kind_that_does_not_fit_gives_way() {
            // A moment in time is twenty characters. This shape will take eight.
            StringSchema tooShortForADateTime = new StringSchema(SchemaMetadata.none(),
                    Optional.empty(), Optional.of(8), Optional.empty(), Optional.of("date-time"));

            String value = text(tooShortForADateTime);

            assertThat(value).hasSizeLessThanOrEqualTo(8);
            assertThatCode(() -> java.time.OffsetDateTime.parse(value))
                    .describedAs("%s fits, but nothing of that kind could have", value)
                    .isInstanceOf(java.time.format.DateTimeParseException.class);
        }

        @RepeatedTest(20)
        @DisplayName("a kind the spelling rule refuses gives way to the rule")
        void a_kind_the_spelling_refuses_gives_way() {
            // An e-mail address is what the kind asks for; the rule says three capital letters,
            // which no e-mail address is. The rule is the stricter statement and it wins.
            StringSchema both = new StringSchema(SchemaMetadata.none(), Optional.empty(),
                    Optional.empty(), Optional.of("^[A-Z]{3}$"), Optional.of("email"));

            assertThat(text(both)).matches("^[A-Z]{3}$");
        }

        @RepeatedTest(20)
        @DisplayName("a kind the spelling rule accepts is kept, because it says more")
        void a_kind_the_spelling_accepts_is_kept() {
            // The one shape in the whole corpus that states both: an e-mail address, and a rule
            // asking only that there be an at-sign in it.
            StringSchema both = new StringSchema(SchemaMetadata.none(), Optional.empty(),
                    Optional.empty(), Optional.of("@"), Optional.of("email"));

            assertThat(text(both))
                    .describedAs("the rule allows it, so the better value is the one kept")
                    .endsWith("@example.com");
        }

        @RepeatedTest(10)
        @DisplayName("a spelling rule nobody can read is carried on without, not stopped at")
        void an_unreadable_rule_leaves_an_ordinary_word() {
            String value = text(spelled("^(?=.*[A-Z])[A-Za-z0-9]{8}$"));

            assertThat(value)
                    .describedAs("losing the parameter over a notation nobody understands helps "
                            + "nobody; an ordinary word at least exercises the operation")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("a rule that can be read and cannot be met leaves no value at all")
        void an_unsatisfiable_rule_leaves_nothing() {
            // Three capital letters, in a shape that also insists on ten characters.
            StringSchema impossible = new StringSchema(SchemaMetadata.none(), Optional.of(10),
                    Optional.of(10), Optional.of("^[A-Z]{3}$"), Optional.empty());

            assertThat(provider.offer(Schemas.asking(impossible)))
                    .describedAs("a value the document itself refuses is worse than no value")
                    .isEmpty();
        }

        @RepeatedTest(10)
        @DisplayName("a rule with no end of its own still yields a value worth sending")
        void an_unbounded_rule_stays_short() {
            assertThat(text(spelled("^(((a+)+)+)+$")))
                    .describedAs("a value nobody can read teaches nothing a short one does not")
                    .hasSizeLessThanOrEqualTo(64);
        }

        private StringSchema spelled(String rule) {
            return new StringSchema(SchemaMetadata.none(), Optional.empty(), Optional.empty(),
                    Optional.of(rule), Optional.empty());
        }

        private String text(StringSchema schema) {
            return ((JsonValue.JsonString) invent(schema)).value();
        }
    }

    private JsonValue invent(CanonicalSchema schema) {
        return provider.offer(Schemas.asking(schema)).orElseThrow().value();
    }
}

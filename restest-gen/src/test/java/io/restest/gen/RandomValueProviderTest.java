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

import io.restest.core.gen.GeneratedValue;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.schema.AnySchema;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.BooleanSchema;
import io.restest.core.schema.CanonicalSchema;
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
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Whatever this invents has to satisfy the description it was invented from. Most of these run many
 * times over, because a generator that is right nine times out of ten is wrong.
 */
class RandomValueProviderTest {

    private static final ApiModel EMPTY = ApiModel.of("Test API", "1.0", List.of());

    private final RandomValueProvider provider = new RandomValueProvider(EMPTY,
            Schemas.fixedRandom());

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

    private JsonValue invent(CanonicalSchema schema) {
        return provider.offer(Schemas.asking(schema)).orElseThrow().value();
    }
}

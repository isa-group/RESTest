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
package io.restest.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import io.restest.core.json.JsonValue;
import io.restest.core.schema.AnySchema;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.BooleanSchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.NothingSchema;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One case per rule {@link AllOfMerger} applies when a document describes a value as being several
 * shapes at once. The rule behind almost all of them is the same - both halves have to hold, so the
 * stricter one wins - and the cases worth the most are the three where the answer is not a value:
 * nothing fits, we cannot work it out, and the halves refer to each other in a circle.
 */
class AllOfMergerTest {

    @Test
    @DisplayName("two halves describing different properties become one shape with all of them")
    void properties_of_both_halves_are_kept() {
        ObjectSchema named = object(Map.of("name", string()), Set.of("name"));
        ObjectSchema identified = object(Map.of("id", string()), Set.of());

        assertThat(AllOfMerger.merge(named, identified)).asInstanceOf(type(ObjectSchema.class))
                .satisfies(merged -> {
                    assertThat(merged.properties()).containsOnlyKeys("name", "id");
                    assertThat(merged.required()).containsExactly("name");
                });
    }

    @Test
    @DisplayName("a property both halves describe is itself combined, not picked from one of them")
    void a_property_described_twice_is_combined() {
        ObjectSchema loose = object(Map.of("telephone",
                new StringSchema(SchemaMetadata.none(), Optional.empty(), Optional.of(50),
                        Optional.empty(), Optional.empty())), Set.of());
        ObjectSchema strict = object(Map.of("telephone",
                new StringSchema(SchemaMetadata.none(), Optional.empty(), Optional.of(20),
                        Optional.empty(), Optional.empty())), Set.of());

        CanonicalSchema merged = AllOfMerger.merge(loose, strict);

        assertThat(((ObjectSchema) merged).properties().get("telephone"))
                .asInstanceOf(type(StringSchema.class))
                .extracting(StringSchema::maxLength).isEqualTo(Optional.of(20));
    }

    @Test
    @DisplayName("when two halves bound the same value, the stricter bound is the answer")
    void the_stricter_bound_wins() {
        StringSchema atLeastTwo = new StringSchema(SchemaMetadata.none(), Optional.of(2),
                Optional.of(50), Optional.empty(), Optional.empty());
        StringSchema atMostTwenty = new StringSchema(SchemaMetadata.none(), Optional.of(1),
                Optional.of(20), Optional.empty(), Optional.empty());

        assertThat(AllOfMerger.merge(atLeastTwo, atMostTwenty)).asInstanceOf(type(StringSchema.class))
                .satisfies(merged -> {
                    assertThat(merged.minLength()).isEqualTo(Optional.of(2));
                    assertThat(merged.maxLength()).isEqualTo(Optional.of(20));
                });
    }

    @Test
    @DisplayName("bounds that leave no room describe a value that cannot exist, and say so")
    void contradictory_bounds_leave_nothing() {
        StringSchema atLeastFive = new StringSchema(SchemaMetadata.none(), Optional.of(5),
                Optional.empty(), Optional.empty(), Optional.empty());
        StringSchema atMostThree = new StringSchema(SchemaMetadata.none(), Optional.empty(),
                Optional.of(3), Optional.empty(), Optional.empty());

        assertThat(AllOfMerger.merge(atLeastFive, atMostThree)).isInstanceOf(NothingSchema.class);
    }

    @Test
    @DisplayName("two different patterns have an answer we cannot work out, which is said rather than guessed")
    void two_patterns_are_reported_rather_than_picked_between() {
        StringSchema lowercase = new StringSchema(SchemaMetadata.none(), Optional.empty(),
                Optional.empty(), Optional.of("^[a-z]+$"), Optional.empty());
        StringSchema threeLong = new StringSchema(SchemaMetadata.none(), Optional.empty(),
                Optional.empty(), Optional.of("^.{3}$"), Optional.empty());

        assertThat(AllOfMerger.merge(lowercase, threeLong)).asInstanceOf(type(UnsupportedSchema.class))
                .extracting(UnsupportedSchema::reason).asString()
                .contains("^[a-z]+$").contains("^.{3}$");
    }

    @Test
    @DisplayName("a value cannot be a word and a number at once, which is understood, not unreadable")
    void two_different_types_leave_nothing() {
        assertThat(AllOfMerger.merge(string(), NumberSchema.of(NumberKind.INTEGER)))
                .isInstanceOf(NothingSchema.class);
    }

    @Test
    @DisplayName("numbers keep the narrower range, and a whole number is the stricter kind")
    void numeric_bounds_are_narrowed() {
        NumberSchema wide = NumberSchema.between(NumberKind.NUMBER, BigDecimal.ZERO,
                BigDecimal.valueOf(100));
        NumberSchema narrow = NumberSchema.between(NumberKind.INTEGER, BigDecimal.TEN,
                BigDecimal.valueOf(50));

        assertThat(AllOfMerger.merge(wide, narrow)).asInstanceOf(type(NumberSchema.class))
                .satisfies(merged -> {
                    assertThat(merged.kind()).isEqualTo(NumberKind.INTEGER);
                    assertThat(merged.minimum()).isEqualTo(Optional.of(BigDecimal.TEN));
                    assertThat(merged.maximum()).isEqualTo(Optional.of(BigDecimal.valueOf(50)));
                });
    }

    @Test
    @DisplayName("numeric bounds that cross leave no number, and an exclusive bound is the stricter kind")
    void numeric_bounds_that_cross_leave_nothing() {
        NumberSchema atLeastTwoHundred = NumberSchema.between(NumberKind.INTEGER,
                BigDecimal.valueOf(200), BigDecimal.valueOf(300));
        NumberSchema atMostOneHundred = NumberSchema.between(NumberKind.INTEGER, BigDecimal.ZERO,
                BigDecimal.valueOf(100));

        assertThat(AllOfMerger.merge(atLeastTwoHundred, atMostOneHundred))
                .isInstanceOf(NothingSchema.class);
    }

    @Test
    @DisplayName("only the values both halves allow survive, and none surviving is a value that cannot exist")
    void enumerations_are_narrowed_to_what_both_halves_allow() {
        StringSchema first = enumerated("a", "b", "c");
        StringSchema second = enumerated("b", "c", "d");

        assertThat(AllOfMerger.merge(first, second).metadata().enumeration())
                .containsExactly(JsonValue.of("b"), JsonValue.of("c"));
        assertThat(AllOfMerger.merge(enumerated("a"), enumerated("z")))
                .isInstanceOf(NothingSchema.class);
    }

    @Test
    @DisplayName("null is acceptable only when every half accepts it")
    void null_survives_only_if_both_halves_allow_it() {
        StringSchema accepts = new StringSchema(SchemaMetadata.none().withNullable(true),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(AllOfMerger.merge(accepts, accepts).metadata().nullable()).isTrue();
        assertThat(AllOfMerger.merge(accepts, string()).metadata().nullable()).isFalse();
    }

    @Test
    @DisplayName("a half that forbids unnamed properties wins over one that says nothing about them")
    void the_stricter_rule_for_unnamed_properties_wins() {
        ObjectSchema silent = object(Map.of(), Set.of());
        ObjectSchema closed = new ObjectSchema(SchemaMetadata.none(), Map.of(), Set.of(),
                Optional.of(NothingSchema.of()), Optional.empty(), Optional.empty());

        assertThat(AllOfMerger.merge(silent, closed)).asInstanceOf(type(ObjectSchema.class))
                .extracting(ObjectSchema::additionalProperties)
                .isEqualTo(Optional.of(NothingSchema.of()));
    }

    @Test
    @DisplayName("arrays combine what every element must look like, and keep the narrower length")
    void arrays_combine_their_element_shapes() {
        ArraySchema first = new ArraySchema(SchemaMetadata.none(), string(), Optional.of(1),
                Optional.of(10), false);
        ArraySchema second = new ArraySchema(SchemaMetadata.none(),
                object(Map.of(), Set.of()), Optional.of(2), Optional.of(5), true);

        assertThat(AllOfMerger.merge(first, second)).asInstanceOf(type(ArraySchema.class))
                .satisfies(merged -> {
                    assertThat(merged.minItems()).isEqualTo(Optional.of(2));
                    assertThat(merged.maxItems()).isEqualTo(Optional.of(5));
                    assertThat(merged.uniqueItems()).isTrue();
                    // A word and an object at once: the elements are what cannot exist, not the array.
                    assertThat(merged.items()).isInstanceOf(NothingSchema.class);
                });
    }

    @Test
    @DisplayName("a half that names no type constrains nothing, but still contributes what it says")
    void a_half_without_a_type_contributes_only_its_facts() {
        AnySchema nullable = new AnySchema(SchemaMetadata.none().withNullable(true));
        StringSchema bounded = new StringSchema(SchemaMetadata.none().withNullable(true),
                Optional.of(3), Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(AllOfMerger.merge(nullable, bounded)).asInstanceOf(type(StringSchema.class))
                .satisfies(merged -> {
                    assertThat(merged.minLength()).isEqualTo(Optional.of(3));
                    assertThat(merged.metadata().nullable()).isTrue();
                });
    }

    @Test
    @DisplayName("a half nobody could read makes the whole combination unreadable, with its reason")
    void an_unreadable_half_carries_its_reason_outwards() {
        UnsupportedSchema unreadable = UnsupportedSchema.of("a tuple-form array");

        assertThat(AllOfMerger.merge(string(), unreadable)).asInstanceOf(type(UnsupportedSchema.class))
                .extracting(UnsupportedSchema::reason).isEqualTo("a tuple-form array");
    }

    @Test
    @DisplayName("two halves naming the same shape elsewhere keep that name; two different ones cannot")
    void named_halves_are_kept_only_when_they_agree() {
        SchemaReference pet = new SchemaReference(SchemaMetadata.none(), "Pet");
        SchemaReference owner = new SchemaReference(SchemaMetadata.none(), "Owner");

        assertThat(AllOfMerger.merge(pet, pet)).isEqualTo(pet);
        assertThat(AllOfMerger.merge(pet, owner)).isInstanceOf(UnsupportedSchema.class);
    }

    @Test
    @DisplayName("nothing to combine is a shape that accepts anything, not a failure")
    void folding_no_halves_accepts_anything() {
        assertThat(AllOfMerger.fold(List.of())).isInstanceOf(AnySchema.class);
    }

    @Test
    @DisplayName("combining does not quietly withdraw what a single half allows")
    void folding_one_half_changes_nothing_about_it() {
        StringSchema acceptsNull = new StringSchema(SchemaMetadata.none().withNullable(true),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        // Every fact here narrows when two halves disagree, so starting the fold from a shape that
        // states nothing must not count as a half that disagrees: a lone half has nothing to be
        // narrowed against, and combining it with nothing has to give it back unchanged.
        assertThat(AllOfMerger.fold(List.of(acceptsNull))).isEqualTo(acceptsNull);
    }

    @Test
    @DisplayName("more than two halves are combined one after another")
    void more_than_two_halves_are_folded_in_turn() {
        CanonicalSchema folded = AllOfMerger.fold(List.of(
                object(Map.of("a", string()), Set.of()),
                object(Map.of("b", string()), Set.of()),
                object(Map.of("c", string()), Set.of("c"))));

        assertThat(folded).asInstanceOf(type(ObjectSchema.class)).satisfies(merged -> {
            assertThat(merged.properties()).containsOnlyKeys("a", "b", "c");
            assertThat(merged.required()).containsExactly("c");
        });
    }

    @Test
    @DisplayName("a half that can be satisfied by no value at all makes the whole combination so")
    void nothing_absorbs_everything() {
        assertThat(AllOfMerger.merge(NothingSchema.of(), new BooleanSchema(SchemaMetadata.none())))
                .isInstanceOf(NothingSchema.class);
    }

    @Test
    @DisplayName("what a half allows for properties it did not name reaches the other half's properties")
    void a_rule_for_unnamed_properties_reaches_the_other_halfs_properties() {
        ObjectSchema closedAroundName = new ObjectSchema(SchemaMetadata.none(),
                Map.of("name", string()), Set.of(), Optional.of(NothingSchema.of()),
                Optional.empty(), Optional.empty());
        ObjectSchema alsoWantsAnIdentifier = new ObjectSchema(SchemaMetadata.none(),
                Map.of("id", string()), Set.of("id"), Optional.empty(), Optional.empty(),
                Optional.empty());

        // The first half forbids everything it did not name, which includes the second half's 'id';
        // the second half insists on it. Between them they describe an object nothing satisfies.
        assertThat(AllOfMerger.merge(closedAroundName, alsoWantsAnIdentifier))
                .isInstanceOf(NothingSchema.class);

        ObjectSchema wordsForUnnamedProperties = new ObjectSchema(SchemaMetadata.none(), Map.of(),
                Set.of(), Optional.of(string()), Optional.empty(), Optional.empty());
        ObjectSchema wantsANumberedAge = new ObjectSchema(SchemaMetadata.none(),
                Map.of("age", NumberSchema.of(NumberKind.INTEGER)), Set.of(), Optional.empty(),
                Optional.empty(), Optional.empty());

        assertThat(AllOfMerger.merge(wordsForUnnamedProperties, wantsANumberedAge))
                .asInstanceOf(type(ObjectSchema.class))
                .extracting(merged -> merged.properties().get("age"))
                .isInstanceOf(NothingSchema.class);
    }

    @Test
    @DisplayName("an object that must carry a property no value satisfies is an object nothing satisfies")
    void an_impossible_required_property_makes_the_object_impossible() {
        ObjectSchema wantsA = new ObjectSchema(SchemaMetadata.none(),
                Map.of("status", enumerated("a")), Set.of("status"), Optional.empty(),
                Optional.empty(), Optional.empty());
        ObjectSchema wantsB = new ObjectSchema(SchemaMetadata.none(),
                Map.of("status", enumerated("b")), Set.of(), Optional.empty(), Optional.empty(),
                Optional.empty());

        assertThat(AllOfMerger.merge(wantsA, wantsB)).isInstanceOf(NothingSchema.class);
    }

    @Test
    @DisplayName("a default one half states and the other forbids is dropped, not sent")
    void a_default_the_combination_forbids_is_dropped() {
        StringSchema withLongDefault = new StringSchema(
                SchemaMetadata.none().withDefault(JsonValue.of("abcdefghij")), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
        StringSchema atMostThree = new StringSchema(SchemaMetadata.none(), Optional.empty(),
                Optional.of(3), Optional.empty(), Optional.empty());

        assertThat(AllOfMerger.merge(withLongDefault, atMostThree).metadata().defaultValue())
                .isEmpty();
    }

    @Test
    @DisplayName("a default the combination still accepts is kept")
    void a_default_that_still_fits_is_kept() {
        StringSchema withShortDefault = new StringSchema(
                SchemaMetadata.none().withDefault(JsonValue.of("ab")), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
        StringSchema atMostThree = new StringSchema(SchemaMetadata.none(), Optional.empty(),
                Optional.of(3), Optional.empty(), Optional.empty());

        assertThat(AllOfMerger.merge(withShortDefault, atMostThree).metadata().defaultValue())
                .contains(JsonValue.of("ab"));
    }

    @Test
    @DisplayName("which direction a value may travel does not depend on which half was written first")
    void access_does_not_depend_on_the_order_of_the_halves() {
        StringSchema onlyReturned = new StringSchema(
                SchemaMetadata.none().withAccess(SchemaMetadata.Access.READ_ONLY), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
        StringSchema onlySent = new StringSchema(
                SchemaMetadata.none().withAccess(SchemaMetadata.Access.WRITE_ONLY), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(AllOfMerger.merge(onlyReturned, onlySent).metadata().access())
                .isEqualTo(AllOfMerger.merge(onlySent, onlyReturned).metadata().access())
                .isEqualTo(SchemaMetadata.Access.READ_ONLY);
    }

    @Test
    @DisplayName("two different steps, and two different formats, are reported rather than picked between")
    void conflicting_number_facts_are_reported() {
        NumberSchema everyThird = new NumberSchema(SchemaMetadata.none(), NumberKind.INTEGER,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(BigDecimal.valueOf(3)), Optional.empty());
        NumberSchema everyFifth = new NumberSchema(SchemaMetadata.none(), NumberKind.INTEGER,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(BigDecimal.valueOf(5)), Optional.empty());

        assertThat(AllOfMerger.merge(everyThird, everyFifth))
                .asInstanceOf(type(UnsupportedSchema.class))
                .extracting(UnsupportedSchema::reason).asString().contains("multiple");

        NumberSchema small = new NumberSchema(SchemaMetadata.none(), NumberKind.INTEGER,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of("int32"));
        NumberSchema large = new NumberSchema(SchemaMetadata.none(), NumberKind.INTEGER,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of("int64"));

        // Every int32 is an int64, so this is a combination we decline to work out - not one that
        // no value satisfies. The reason has to say the first and must not claim the second.
        assertThat(AllOfMerger.merge(small, large)).asInstanceOf(type(UnsupportedSchema.class))
                .extracting(UnsupportedSchema::reason).asString()
                .contains("not supported yet").doesNotContain("no value can be");
    }

    @Test
    @DisplayName("a half named elsewhere and a half written out in place cannot be combined here")
    void a_named_half_and_a_written_out_half_are_reported() {
        assertThat(AllOfMerger.merge(new SchemaReference(SchemaMetadata.none(), "Pet"), string()))
                .isInstanceOf(UnsupportedSchema.class);
    }

    @Test
    @DisplayName("bounds on how many properties an object may have are narrowed, and may leave nothing")
    void property_counts_are_narrowed() {
        ObjectSchema atLeastThree = new ObjectSchema(SchemaMetadata.none(), Map.of(), Set.of(),
                Optional.empty(), Optional.of(3), Optional.empty());
        ObjectSchema atMostOne = new ObjectSchema(SchemaMetadata.none(), Map.of(), Set.of(),
                Optional.empty(), Optional.empty(), Optional.of(1));

        assertThat(AllOfMerger.merge(atLeastThree, atMostOne)).isInstanceOf(NothingSchema.class);
    }

    @Test
    @DisplayName("an exclusive bound meeting an inclusive one at the same number leaves nothing")
    void exclusive_bounds_that_touch_leave_nothing() {
        NumberSchema aboveFive = new NumberSchema(SchemaMetadata.none(), NumberKind.INTEGER,
                Optional.empty(), Optional.of(BigDecimal.valueOf(5)), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
        NumberSchema atMostFive = new NumberSchema(SchemaMetadata.none(), NumberKind.INTEGER,
                Optional.empty(), Optional.empty(), Optional.of(BigDecimal.valueOf(5)),
                Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(AllOfMerger.merge(aboveFive, atMostFive)).isInstanceOf(NothingSchema.class);
    }

    @Test
    @DisplayName("a choice as one half of a combination is reported unread, never called impossible")
    void a_choice_as_a_half_of_a_combination_is_reported() {
        ChoiceSchema aNumberOrAWord = ChoiceSchema.of(List.of(
                NumberSchema.of(NumberKind.INTEGER), string()));

        // The merge is a chain of instanceof tests rather than a switch over the sealed interface,
        // so nothing made the compiler ask about this shape. Without its own arm a choice falls
        // through to the last one, which answers "no value satisfies this" about a document that
        // said nothing of the kind - and no document in the corpus would have caught it.
        assertThat(AllOfMerger.merge(aNumberOrAWord, string()))
                .asInstanceOf(type(UnsupportedSchema.class))
                .extracting(UnsupportedSchema::reason).asString().contains("choice between shapes");
        assertThat(AllOfMerger.merge(string(), aNumberOrAWord))
                .isInstanceOf(UnsupportedSchema.class);
    }

    @Test
    @DisplayName("a half that states nothing leaves a choice as it found it")
    void a_choice_survives_being_combined_with_nothing() {
        ChoiceSchema aNumberOrAWord = ChoiceSchema.of(List.of(
                NumberSchema.of(NumberKind.INTEGER), string()));

        assertThat(AllOfMerger.merge(AnySchema.of(), aNumberOrAWord)).isEqualTo(aNumberOrAWord);
    }

    @Test
    @DisplayName("both halves' sample values survive being combined, because a sample narrows nothing")
    void samples_from_both_halves_are_kept() {
        StringSchema named = new StringSchema(
                SchemaMetadata.none().withExamples(List.of(JsonValue.of("Davis"))),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        StringSchema alsoNamed = new StringSchema(
                SchemaMetadata.none().withExamples(List.of(JsonValue.of("Roe"))),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(AllOfMerger.merge(named, alsoNamed).metadata().examples())
                .containsExactly(JsonValue.of("Davis"), JsonValue.of("Roe"));
    }

    @Test
    @DisplayName("a sample the combination itself rules out is dropped, as a default in the same "
            + "position already is")
    void a_sample_the_combination_rules_out_is_dropped() {
        StringSchema sampled = new StringSchema(
                SchemaMetadata.none().withExamples(List.of(
                        JsonValue.of("a much longer word"), JsonValue.of("ok"))),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        StringSchema short_ = new StringSchema(SchemaMetadata.none(), Optional.empty(),
                Optional.of(4), Optional.empty(), Optional.empty());

        assertThat(AllOfMerger.merge(sampled, short_).metadata().examples())
                .describedAs("we made this contradiction by putting the halves together, so "
                        + "removing it restores what the document said")
                .containsExactly(JsonValue.of("ok"));
    }

    private static StringSchema string() {
        return new StringSchema(SchemaMetadata.none(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    private static StringSchema enumerated(String... values) {
        List<JsonValue> allowed = List.of(values).stream()
                .<JsonValue>map(JsonValue::of).toList();
        return new StringSchema(SchemaMetadata.none().withEnumeration(allowed), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static ObjectSchema object(Map<String, CanonicalSchema> properties, Set<String> required) {
        return new ObjectSchema(SchemaMetadata.none(), properties, required, Optional.empty(),
                Optional.empty(), Optional.empty());
    }
}

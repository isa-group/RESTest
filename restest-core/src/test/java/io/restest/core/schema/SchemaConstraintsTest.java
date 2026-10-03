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
package io.restest.core.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What the model refuses to hold.
 *
 * <p>The line is drawn at values no request could ever satisfy. Those are refused here so that the
 * parser can report them once, against the operation they came from, rather than every generator
 * downstream re-discovering the contradiction or quietly producing nothing.
 */
class SchemaConstraintsTest {

    @Nested
    @DisplayName("strings")
    class Strings {

        @Test
        @DisplayName("a minimum length above the maximum is refused, naming both")
        void impossible_length_range_is_refused() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new StringSchema(SchemaMetadata.none(), Optional.of(5),
                            Optional.of(3), Optional.empty(), Optional.empty()))
                    .withMessageContaining("minLength (5)")
                    .withMessageContaining("maxLength (3)");
        }

        @Test
        @DisplayName("a negative length is refused")
        void negative_length_is_refused() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new StringSchema(SchemaMetadata.none(), Optional.of(-1),
                            Optional.empty(), Optional.empty(), Optional.empty()));
        }

        @Test
        @DisplayName("the most ordinary schema there is - two consistent length bounds - is accepted")
        void two_consistent_bounds_are_accepted() {
            StringSchema name = new StringSchema(SchemaMetadata.none(), Optional.of(2),
                    Optional.of(64), Optional.empty(), Optional.empty());

            assertThat(name.minLength()).contains(2);
            assertThat(name.maxLength()).contains(64);
        }

        @Test
        @DisplayName("a pattern is kept exactly as the document wrote it, not compiled")
        void a_pattern_is_not_compiled() {
            StringSchema schema = new StringSchema(SchemaMetadata.none(), Optional.empty(),
                    Optional.empty(), Optional.of("^\\p{L}+(?<!x)$"), Optional.empty());

            assertThat(schema.pattern()).contains("^\\p{L}+(?<!x)$");
        }

        @Test
        @DisplayName("one length bound on its own is accepted")
        void a_single_length_bound_is_enough() {
            StringSchema onlyMinimum = new StringSchema(SchemaMetadata.none(), Optional.of(1),
                    Optional.empty(), Optional.empty(), Optional.empty());
            StringSchema onlyMaximum = new StringSchema(SchemaMetadata.none(), Optional.empty(),
                    Optional.of(64), Optional.empty(), Optional.empty());

            assertThat(onlyMinimum.minLength()).contains(1);
            assertThat(onlyMaximum.maxLength()).contains(64);
        }

        @Test
        @DisplayName("a format name is kept even when nobody has standardised it")
        void an_unknown_format_survives() {
            assertThat(StringSchema.ofFormat("iban").format()).contains("iban");
        }
    }

    @Nested
    @DisplayName("numbers")
    class Numbers {

        @Test
        @DisplayName("a minimum above the maximum is refused")
        void impossible_range_is_refused() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> NumberSchema.between(NumberKind.NUMBER,
                            new BigDecimal("10"), new BigDecimal("1")))
                    .withMessageContaining("minimum (10)");
        }

        @Test
        @DisplayName("an exclusive bound is a number, so a 3.1 document does not lose it")
        void an_exclusive_bound_carries_its_value() {
            NumberSchema above5 = new NumberSchema(SchemaMetadata.none(), NumberKind.NUMBER,
                    Optional.empty(), Optional.of(new BigDecimal("5")), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty());

            assertThat(above5.exclusiveMinimum()).contains(new BigDecimal("5"));
            assertThat(above5.minimum()).isEmpty();
            assertThat(above5.maximum()).isEmpty();
            assertThat(above5.exclusiveMaximum()).isEmpty();
        }

        @Test
        @DisplayName("bounds that touch are accepted when inclusive and refused when exclusive")
        void an_exclusive_bound_is_compared_more_strictly() {
            assertThat(NumberSchema.between(NumberKind.INTEGER, new BigDecimal("5"),
                    new BigDecimal("5")).minimum()).contains(new BigDecimal("5"));

            assertThatIllegalArgumentException().isThrownBy(() -> new NumberSchema(
                    SchemaMetadata.none(), NumberKind.INTEGER, Optional.empty(),
                    Optional.of(new BigDecimal("5")), Optional.of(new BigDecimal("5")),
                    Optional.empty(), Optional.empty(), Optional.empty()));
        }

        @Test
        @DisplayName("a multiple-of that is zero or negative is refused")
        void impossible_multiple_is_refused() {
            assertThatIllegalArgumentException().isThrownBy(() -> multipleOf(BigDecimal.ZERO));
            assertThatIllegalArgumentException().isThrownBy(() -> multipleOf(new BigDecimal("-2")));
        }

        @Test
        @DisplayName("one bound on its own is accepted: most documents state only one")
        void a_single_bound_is_enough() {
            NumberSchema onlyMinimum = new NumberSchema(SchemaMetadata.none(), NumberKind.NUMBER,
                    Optional.of(new BigDecimal("0")), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.of(new BigDecimal("0.5")), Optional.of("double"));

            assertThat(onlyMinimum.minimum()).contains(new BigDecimal("0"));
            assertThat(onlyMinimum.multipleOf()).contains(new BigDecimal("0.5"));
            assertThat(onlyMinimum.maximum()).isEmpty();
        }

        @Test
        @DisplayName("an exclusive lower bound below an exclusive upper bound leaves room")
        void two_exclusive_bounds_can_coexist() {
            NumberSchema between = new NumberSchema(SchemaMetadata.none(), NumberKind.NUMBER,
                    Optional.empty(), Optional.of(new BigDecimal("0")), Optional.empty(),
                    Optional.of(new BigDecimal("1")), Optional.empty(), Optional.empty());

            assertThat(between.exclusiveMinimum()).contains(new BigDecimal("0"));
            assertThat(between.exclusiveMaximum()).contains(new BigDecimal("1"));
        }

        @Test
        @DisplayName("a range no value satisfies for arithmetic reasons is accepted, deliberately")
        void satisfiability_is_not_promised() {
            NumberSchema noIntegerFits = new NumberSchema(SchemaMetadata.none(),
                    NumberKind.INTEGER, Optional.of(new BigDecimal("1.2")), Optional.empty(),
                    Optional.of(new BigDecimal("1.8")), Optional.empty(), Optional.empty(),
                    Optional.empty());

            assertThat(noIntegerFits.kind()).isEqualTo(NumberKind.INTEGER);
        }

        @Test
        @DisplayName("a bound is kept exactly, so the boundary walk can send precisely it")
        void bounds_are_exact() {
            NumberSchema schema = NumberSchema.between(NumberKind.INTEGER,
                    new BigDecimal("-9007199254740993"), new BigDecimal("9007199254740993"));

            assertThat(schema.maximum().orElseThrow().toPlainString())
                    .isEqualTo("9007199254740993");
        }

        @Test
        @DisplayName("whole and fractional numbers are one record with a kind, not two records")
        void the_kind_says_whether_the_value_is_whole() {
            assertThat(NumberSchema.of(NumberKind.INTEGER).kind()).isEqualTo(NumberKind.INTEGER);
            assertThat(NumberSchema.of(NumberKind.NUMBER).kind()).isEqualTo(NumberKind.NUMBER);
        }

        private static NumberSchema multipleOf(BigDecimal factor) {
            return new NumberSchema(SchemaMetadata.none(), NumberKind.INTEGER, Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(factor),
                    Optional.empty());
        }
    }

    @Nested
    @DisplayName("arrays")
    class Arrays {

        @Test
        @DisplayName("a minimum length above the maximum is refused")
        void impossible_size_range_is_refused() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ArraySchema(SchemaMetadata.none(), StringSchema.of(),
                            Optional.of(4), Optional.of(2), false));
        }

        @Test
        @DisplayName("a maximum on its own is accepted")
        void a_single_size_bound_is_enough() {
            ArraySchema atMostThree = new ArraySchema(SchemaMetadata.none(), StringSchema.of(),
                    Optional.empty(), Optional.of(3), true);

            assertThat(atMostThree.maxItems()).contains(3);
            assertThat(atMostThree.uniqueItems()).isTrue();
        }

        @Test
        @DisplayName("an array of strings knows the shape of its elements")
        void items_are_kept() {
            assertThat(ArraySchema.of(StringSchema.of()).items()).isEqualTo(StringSchema.of());
        }
    }

    @Nested
    @DisplayName("objects")
    class Objects {

        @Test
        @DisplayName("properties keep the order they were declared in, whatever the map was")
        void properties_keep_declaration_order() {
            Map<String, CanonicalSchema> declared = new LinkedHashMap<>();
            declared.put("zebra", StringSchema.of());
            declared.put("aardvark", StringSchema.of());
            declared.put("moose", StringSchema.of());

            ObjectSchema schema = ObjectSchema.of(declared);

            assertThat(schema.properties().keySet())
                    .containsExactly("zebra", "aardvark", "moose");
        }

        @Test
        @DisplayName("changing the map afterwards does not change the schema")
        void properties_are_copied() {
            Map<String, CanonicalSchema> declared = new LinkedHashMap<>();
            declared.put("name", StringSchema.of());

            ObjectSchema schema = ObjectSchema.of(declared);
            declared.put("age", NumberSchema.of(NumberKind.INTEGER));

            assertThat(schema.properties()).containsOnlyKeys("name");
        }

        @Test
        @DisplayName("a required name that no property declares is kept, not thrown away")
        void a_required_name_without_a_property_is_the_document_s_mistake_to_report() {
            ObjectSchema schema = ObjectSchema.of(Map.of("name", StringSchema.of()),
                    Set.of("name", "nickname"));

            assertThat(schema.isRequired("nickname")).isTrue();
            assertThat(schema.property("nickname")).isEmpty();
        }

        @Test
        @DisplayName("a null property or a null required name is refused rather than held")
        void nulls_are_refused() {
            Map<String, CanonicalSchema> withNullValue = new LinkedHashMap<>();
            withNullValue.put("name", null);
            Set<String> withNullName = new java.util.LinkedHashSet<>();
            withNullName.add(null);

            Map<String, CanonicalSchema> withNullKey = new LinkedHashMap<>();
            withNullKey.put(null, StringSchema.of());

            assertThatExceptionOfType(NullPointerException.class)
                    .isThrownBy(() -> ObjectSchema.of(withNullValue));
            assertThatExceptionOfType(NullPointerException.class)
                    .isThrownBy(() -> ObjectSchema.of(withNullKey));
            assertThatExceptionOfType(NullPointerException.class)
                    .isThrownBy(() -> ObjectSchema.of(Map.of(), withNullName));
        }

        @Test
        @DisplayName("'additionalProperties: false' is a statement the model can hold")
        void a_closed_object_is_representable() {
            ObjectSchema closed = ObjectSchema.of(Map.of("name", StringSchema.of())).closed();

            assertThat(closed.isClosed()).isTrue();
            assertThat(closed.additionalProperties()).contains(NothingSchema.of());
        }

        @Test
        @DisplayName("silence about additional properties is not the same as forbidding them")
        void silence_is_not_a_prohibition() {
            ObjectSchema silent = ObjectSchema.of(Map.of("name", StringSchema.of()));
            ObjectSchema shaped = new ObjectSchema(SchemaMetadata.none(),
                    Map.of("name", StringSchema.of()), Set.of(),
                    Optional.of(StringSchema.of()), Optional.empty(), Optional.empty());

            assertThat(silent.isClosed()).isFalse();
            assertThat(silent.additionalProperties()).isEmpty();
            assertThat(shaped.isClosed()).isFalse();
            assertThat(shaped.additionalProperties()).contains(StringSchema.of());
        }

        @Test
        @DisplayName("an object cannot be changed through the map it hands back")
        void properties_are_unmodifiable() {
            ObjectSchema schema = ObjectSchema.of(Map.of("name", StringSchema.of()));

            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(
                    () -> schema.properties().put("age", NumberSchema.of(NumberKind.INTEGER)));
        }

        @Test
        @DisplayName("a property count range that cannot be satisfied is refused")
        void impossible_property_count_is_refused() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ObjectSchema(SchemaMetadata.none(), Map.of(), Set.of(),
                            Optional.empty(), Optional.of(3), Optional.of(1)));
        }
    }
}

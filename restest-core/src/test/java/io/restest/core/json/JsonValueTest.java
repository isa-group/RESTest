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
package io.restest.core.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.restest.core.json.JsonValue.JsonArray;
import io.restest.core.json.JsonValue.JsonBoolean;
import io.restest.core.json.JsonValue.JsonNull;
import io.restest.core.json.JsonValue.JsonNumber;
import io.restest.core.json.JsonValue.JsonObject;
import io.restest.core.json.JsonValue.JsonString;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JsonValueTest {

    @Nested
    @DisplayName("numbers")
    class Numbers {

        @Test
        @DisplayName("1, 1.0 and 1.00 are the same number, as JSON says they are")
        void trailing_zeros_do_not_make_a_different_number() {
            assertThat(JsonValue.of(new BigDecimal("1.00")))
                    .isEqualTo(JsonValue.of(new BigDecimal("1.0")))
                    .isEqualTo(JsonValue.of(1L));
        }

        @Test
        @DisplayName("zero written with a scale is still zero")
        void zero_is_normalised_too() {
            assertThat(JsonValue.of(new BigDecimal("0.00"))).isEqualTo(JsonValue.of(0L));
        }

        @Test
        @DisplayName("a number is kept exactly, however many digits it has")
        void precision_beyond_a_double_survives() {
            BigDecimal beyondDouble = new BigDecimal("9007199254740993");

            assertThat(JsonValue.of(beyondDouble).value().toPlainString())
                    .isEqualTo("9007199254740993");
        }

        @Test
        @DisplayName("a number cannot be null")
        void null_is_refused() {
            assertThatNullPointerException().isThrownBy(() -> new JsonNumber(null));
        }
    }

    @Nested
    @DisplayName("arrays and objects")
    class Composites {

        @Test
        @DisplayName("changing the list an array was built from does not change the array")
        void an_array_copies_what_it_is_given() {
            List<JsonValue> source = new ArrayList<>(List.of(JsonValue.of("a")));

            JsonArray array = JsonValue.array(source);
            source.add(JsonValue.of("b"));

            assertThat(array.elements()).containsExactly(JsonValue.of("a"));
        }

        @Test
        @DisplayName("an array cannot be changed through the list it hands back")
        void an_array_hands_back_an_unmodifiable_list() {
            JsonArray array = JsonValue.array(JsonValue.of("a"));

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> array.elements().add(JsonValue.of("b")));
        }

        @Test
        @DisplayName("an object keeps its members in the order they were declared")
        void an_object_keeps_declaration_order() {
            Map<String, JsonValue> source = new LinkedHashMap<>();
            source.put("zebra", JsonValue.of(1L));
            source.put("aardvark", JsonValue.of(2L));
            source.put("moose", JsonValue.of(3L));

            JsonObject object = JsonValue.object(source);

            assertThat(object.members().keySet()).containsExactly("zebra", "aardvark", "moose");
        }

        @Test
        @DisplayName("changing the map an object was built from does not change the object")
        void an_object_copies_what_it_is_given() {
            Map<String, JsonValue> source = new LinkedHashMap<>();
            source.put("name", JsonValue.of("Bruno"));

            JsonObject object = JsonValue.object(source);
            source.put("age", JsonValue.of(3L));

            assertThat(object.members()).containsOnlyKeys("name");
        }

        @Test
        @DisplayName("a member can be asked for by name")
        void a_member_is_found_by_name() {
            JsonObject object = JsonValue.object(Map.of("name", JsonValue.of("Bruno")));

            assertThat(object.member("name")).contains(JsonValue.of("Bruno"));
            assertThat(object.member("absent")).isEmpty();
        }

        @Test
        @DisplayName("an object refuses a null member value rather than holding one")
        void a_null_member_is_refused() {
            Map<String, JsonValue> source = new LinkedHashMap<>();
            source.put("name", null);

            assertThatNullPointerException().isThrownBy(() -> JsonValue.object(source));
        }
    }

    @Test
    @DisplayName("the null, true and false literals are shared and compare equal to their own kind")
    void literals_are_values() {
        assertThat(JsonValue.NULL).isEqualTo(new JsonNull());
        assertThat(JsonValue.of(true)).isSameAs(JsonValue.TRUE);
        assertThat(JsonValue.of(false)).isSameAs(JsonValue.FALSE);
        assertThat(JsonValue.TRUE).isNotEqualTo(JsonValue.FALSE);
    }

    @Test
    @DisplayName("every kind of value can be told apart without a default case")
    void the_hierarchy_is_exhaustive() {
        List<JsonValue> everyKind = List.of(
                JsonValue.NULL,
                JsonValue.of(true),
                JsonValue.of(42L),
                JsonValue.of("text"),
                JsonValue.array(JsonValue.of(1L)),
                JsonValue.object(Map.of("a", JsonValue.of(1L))));

        assertThat(everyKind).map(JsonValueTest::describe)
                .containsExactly("null", "boolean", "number", "string", "array", "object");
    }

    /**
     * The point of this method is that it compiles: a switch over a sealed interface with no
     * default case fails to compile the day a variant is added and not handled here. Every
     * generator and every oracle downstream gets the same protection.
     */
    private static String describe(JsonValue value) {
        return switch (value) {
            case JsonNull ignored -> "null";
            case JsonBoolean ignored -> "boolean";
            case JsonNumber ignored -> "number";
            case JsonString ignored -> "string";
            case JsonArray ignored -> "array";
            case JsonObject ignored -> "object";
        };
    }
}

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
package io.restest.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.restest.core.json.JsonValue;
import io.restest.core.store.InteractionStoreException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Whatever RESTest can hold as a JSON value has to survive being written to a file and read back,
 * because a stored run is only useful if it is the run that happened.
 */
class JsonTest {

    static List<JsonValue> values() {
        return List.of(
                JsonValue.NULL,
                JsonValue.TRUE,
                JsonValue.FALSE,
                JsonValue.of(""),
                JsonValue.of("a widget"),
                JsonValue.of("quotes \" backslashes \\ newlines \n tabs \t"),
                JsonValue.of("café — éàü, emoji 🐛"),
                JsonValue.of(0),
                JsonValue.of(-1),
                JsonValue.of(Long.MAX_VALUE),
                JsonValue.of(new BigDecimal("0.1")),
                JsonValue.of(new BigDecimal("-12345678901234567890.0987654321")),
                JsonValue.array(),
                JsonValue.array(JsonValue.of(1), JsonValue.NULL, JsonValue.of("two")),
                JsonValue.object(Map.of()),
                nested());
    }

    private static JsonValue nested() {
        Map<String, JsonValue> inner = new LinkedHashMap<>();
        inner.put("id", JsonValue.of("w-1"));
        inner.put("tags", JsonValue.array(JsonValue.of("a"), JsonValue.of("b")));
        Map<String, JsonValue> outer = new LinkedHashMap<>();
        outer.put("widget", JsonValue.object(inner));
        outer.put("count", JsonValue.of(2));
        outer.put("missing", JsonValue.NULL);
        return JsonValue.object(outer);
    }

    @ParameterizedTest
    @MethodSource("values")
    @DisplayName("every kind of value comes back as itself")
    void a_value_survives_being_written_and_read(JsonValue value) {
        assertThat(Json.read(Json.write(value))).isEqualTo(value);
    }

    @Test
    @DisplayName("a number too large for a computer's usual arithmetic keeps every digit")
    void a_large_number_is_not_rounded() {
        JsonValue identifier = JsonValue.of(new BigDecimal("90071992547409911"));

        JsonValue.JsonNumber read = (JsonValue.JsonNumber) Json.read(Json.write(identifier));

        assertThat(read.value().toPlainString())
                .describedAs("an identifier read back as a slightly different number would make a "
                        + "stored run describe a request nobody sent")
                .isEqualTo("90071992547409911");
    }

    @Test
    @DisplayName("the order an object's members were written in is the order they come back")
    void member_order_is_kept() {
        assertThat(((JsonValue.JsonObject) Json.read(Json.write(nested()))).members().keySet())
                .containsExactly("widget", "count", "missing");
    }

    @Test
    @DisplayName("text that is not JSON at all is reported, not guessed at")
    void broken_text_is_reported() {
        assertThatExceptionOfType(InteractionStoreException.class)
                .isThrownBy(() -> Json.read("{\"unfinished\": "));
        assertThatExceptionOfType(InteractionStoreException.class)
                .isThrownBy(() -> Json.read(""))
                .withMessageContaining("Empty");
    }
}

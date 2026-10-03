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
class JsonTextTest {

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
        assertThat(JsonText.read(JsonText.write(value))).isEqualTo(value);
    }

    @Test
    @DisplayName("a number too large for a computer's usual arithmetic keeps every digit")
    void a_large_number_is_not_rounded() {
        JsonValue identifier = JsonValue.of(new BigDecimal("90071992547409911"));

        JsonValue.JsonNumber read = (JsonValue.JsonNumber) JsonText.read(JsonText.write(identifier));

        assertThat(read.value().toPlainString())
                .describedAs("an identifier read back as a slightly different number would make a "
                        + "stored run describe a request nobody sent")
                .isEqualTo("90071992547409911");
    }

    @Test
    @DisplayName("the order an object's members were written in is the order they come back")
    void member_order_is_kept() {
        assertThat(((JsonValue.JsonObject) JsonText.read(JsonText.write(nested()))).members().keySet())
                .containsExactly("widget", "count", "missing");
    }

    @Test
    @DisplayName("text that is not JSON at all is reported, not guessed at")
    void broken_text_is_reported() {
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> JsonText.read("{\"unfinished\": "));
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> JsonText.read(""))
                .withMessageContaining("Empty");
    }

    @Test
    @DisplayName("text carrying on after the value ends is refused, not quietly ignored")
    void anything_after_the_value_is_refused() {
        // Every one of these is something a broken API really sends: the handler wrote its payload
        // twice, an error page was appended to a reply that had already begun, a warning was
        // printed into the middle of an answer. Reading as far as the first value and stopping
        // would call all three well formed, and whatever judges the reply would then find nothing
        // wrong with it.
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> JsonText.read("{\"a\": 1}{\"a\": 2}"))
                .withMessageContaining("carried on");
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> JsonText.read("{\"a\": 1}\n<html>Fatal error</html>"));
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> JsonText.read("1 2"));
        // And the same rule stops a value being read out of the front of something that is not one.
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> JsonText.read("12 Monkeys"));

        // Whitespace around a value is still part of writing it down, not text carrying on.
        assertThat(JsonText.read("  {\"a\": 1}  ")).isEqualTo(JsonText.read("{\"a\":1}"));
    }

    @Test
    @DisplayName("checking without building answers the same question as reading")
    void checking_agrees_with_reading() {
        // Whoever only wants to know whether a reply is JSON should not pay for a whole value that
        // is thrown away an instant later, so there are two ways in. They have to agree, or the
        // cheap one becomes a second opinion nobody asked for.
        List<String> texts = List.of("{\"a\": 1}", "[1, 2, 3]", "\"just a string\"", "17", "null",
                "  {\"a\": 1}  ", "{\"a\": 1}{\"a\": 2}", "{\"a\": 1} then junk", "12 Monkeys",
                "1 2", "", "{\"unfinished\": ", "<html>no</html>");

        for (String text : texts) {
            boolean readable = true;
            try {
                JsonText.read(text);
            } catch (JsonException notOneValue) {
                readable = false;
            }
            boolean checkable = true;
            try {
                JsonText.checkOneValue(text);
            } catch (JsonException notOneValue) {
                checkable = false;
            }
            assertThat(checkable).describedAs("checking '%s'", text).isEqualTo(readable);
        }
    }
}

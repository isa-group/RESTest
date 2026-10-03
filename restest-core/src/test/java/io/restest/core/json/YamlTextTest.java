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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Reading the files a person writes by hand for RESTest: a list of values, a plan, a file of
 * settings.
 *
 * <p>Most of what is checked here is what the reader refuses to do. YAML is a forgiving format with
 * a long memory, and two of the things it does by itself would quietly change what somebody meant -
 * a heading written twice, and a word like {@code NO} turning into false. Both would load without
 * complaint and send the wrong thing for a whole run.
 */
class YamlTextTest {

    @Nested
    @DisplayName("what it reads")
    class WhatItReads {

        @Test
        @DisplayName("the six kinds of value JSON has, and nothing else")
        void the_six_kinds() {
            JsonValue read = YamlText.read("""
                    text: a word
                    number: 1.5
                    whole: 42
                    yes: true
                    nothing: null
                    list: [1, 2]
                    thing:
                      inside: here
                    """, "a file");

            JsonValue.JsonObject held = (JsonValue.JsonObject) read;
            assertThat(held.members().get("text")).isEqualTo(JsonValue.of("a word"));
            assertThat(held.members().get("number")).isEqualTo(JsonValue.of(new BigDecimal("1.5")));
            assertThat(held.members().get("whole")).isEqualTo(JsonValue.of(42L));
            assertThat(held.members().get("yes")).isEqualTo(JsonValue.of(true));
            assertThat(held.members().get("nothing")).isEqualTo(JsonValue.NULL);
            assertThat(held.members().get("list")).isInstanceOf(JsonValue.JsonArray.class);
            assertThat(held.members().get("thing")).isInstanceOf(JsonValue.JsonObject.class);
        }

        @Test
        @DisplayName("a file written as JSON reads the same way, since JSON is YAML")
        void json_is_yaml() {
            assertThat(YamlText.read("{\"a\": [1, \"two\"]}", "a file"))
                    .isEqualTo(YamlText.read("a:\n  - 1\n  - two\n", "a file"));
        }

        @Test
        @DisplayName("an empty file holds nothing rather than failing")
        void an_empty_file() {
            assertThat(YamlText.read("", "a file")).isEqualTo(JsonValue.NULL);
        }

        @ParameterizedTest
        @ValueSource(strings = {"NO", "no", "off", "y", "ON", "yes", "n"})
        @DisplayName("a word YAML's older rules would turn into true or false stays a word")
        void words_that_are_not_yes_or_no(String word) {
            JsonValue read = YamlText.read("country: " + word, "a file");

            assertThat(((JsonValue.JsonObject) read).members().get("country"))
                    .describedAs("a list of country codes holding NO would otherwise send false, "
                            + "and nothing would warn anybody")
                    .isEqualTo(JsonValue.of(word));
        }

        @Test
        @DisplayName("a number is kept exactly as it was written, however long")
        void numbers_are_kept_exactly() {
            JsonValue read = YamlText.read("big: 123456789012345678901234567890.5", "a file");

            assertThat(((JsonValue.JsonObject) read).members().get("big"))
                    .describedAs("a list of values to push at an API with is precisely where "
                            + "somebody writes a number too long for sixty-four bits on purpose")
                    .isEqualTo(JsonValue.of(new BigDecimal("123456789012345678901234567890.5")));
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class WhatItRefuses {

        @Test
        @DisplayName("a heading written twice, rather than quietly using the second one")
        void a_key_written_twice() {
            assertThatThrownBy(() -> YamlText.read("a: 1\na: 2\n", "my file"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("my file");
        }

        @Test
        @DisplayName("text that is not YAML at all, naming where it came from")
        void text_that_is_not_yaml() {
            assertThatThrownBy(() -> YamlText.read("a: [1, 2\n", "my file"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("my file")
                    .hasMessageContaining("could not be read");
        }

        @Test
        @DisplayName("a kind of value YAML has and a request could not carry")
        void a_kind_no_request_could_carry() {
            assertThatThrownBy(() -> YamlText.read("when: !!timestamp 2026-09-22", "my file"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("not something that can be sent");
        }

        @Test
        @DisplayName("a date written plainly is a piece of text, since nothing here reads a kind "
                + "into a word that does not say what it is")
        void a_plain_date_is_text() {
            assertThat(((JsonValue.JsonObject) YamlText.read("when: 2026-09-22", "my file"))
                    .members().get("when")).isEqualTo(JsonValue.of("2026-09-22"));
        }

        @Test
        @DisplayName("a number the file tagged as one and that is not a number a request could "
                + "carry")
        void a_number_that_is_not_one() {
            assertThatThrownBy(() -> YamlText.read("odd: !!float .inf", "my file"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("not a number a request could carry");
        }

        @Test
        @DisplayName("nothing in the file is treated as an instruction to build anything")
        void nothing_is_an_instruction() {
            assertThatThrownBy(() -> YamlText.read(
                    "held: !!java.io.File [/etc/passwd]", "my file"))
                    .isInstanceOf(JsonException.class);
        }
    }

    @Nested
    @DisplayName("asking what a value is")
    class AskingWhatAValueIs {

        @Test
        @DisplayName("an object, a list, a piece of text and a whole number, when it is one")
        void when_it_is_what_was_asked_for() {
            JsonValue.JsonObject thing = YamlText.asObject(
                    YamlText.read("a: 1", "f"), "the file", "f");
            assertThat(thing.members()).containsKey("a");

            assertThat(YamlText.asList(JsonValue.array(List.of(JsonValue.of(1L))), "it", "f"))
                    .hasSize(1);
            assertThat(YamlText.asText(JsonValue.of("word"), "it", "f")).isEqualTo("word");
            assertThat(YamlText.asWholeNumber(JsonValue.of(7L), "it", "f")).isEqualTo(7L);
        }

        @Test
        @DisplayName("and a refusal naming what was there instead, when it is not")
        void when_it_is_something_else() {
            assertThatThrownBy(() -> YamlText.asObject(JsonValue.of("word"), "the plan", "f"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("the plan is not an object");
            assertThatThrownBy(() -> YamlText.asList(JsonValue.of("word"), "the sources", "f"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("the sources is not a list");
            assertThatThrownBy(() -> YamlText.asText(JsonValue.of(1L), "the name", "f"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("the name is not text");
            assertThatThrownBy(() -> YamlText.asWholeNumber(JsonValue.of("word"), "version", "f"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("version is not a number");
        }

        @Test
        @DisplayName("a number with a fraction is not a whole number, rather than being rounded")
        void a_fraction_is_not_a_whole_number() {
            assertThatThrownBy(() -> YamlText.asWholeNumber(
                    JsonValue.of(new BigDecimal("1.9")), "version", "f"))
                    .describedAs("a file declaring version 1.9 is not a version 1 file")
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("a whole number belongs there");
        }
    }

    @Test
    @DisplayName("what it is handed has to be there at all")
    void nothing_is_not_a_file() {
        assertThatThrownBy(() -> YamlText.read(null, "a file"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> YamlText.read("a: 1", null))
                .isInstanceOf(NullPointerException.class);
        assertThatCode(() -> YamlText.read("a: 1", "a file")).doesNotThrowAnyException();
    }
}

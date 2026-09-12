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
package io.restest.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.StringSchema;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ResponseModelTest {

    @Test
    @DisplayName("a range written in lower case is the same range")
    void a_status_key_is_normalised() {
        assertThat(ResponseModel.empty("2xx").status()).isEqualTo("2XX");
        assertThat(ResponseModel.empty("Default").status()).isEqualTo("default");
    }

    @ParameterizedTest
    @ValueSource(strings = {"600", "20X", "99", "2xxx", "anything", " "})
    @DisplayName("a status key OpenAPI does not allow is refused, saying what is allowed")
    void an_impossible_status_key_is_refused(String status) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ResponseModel.empty(status))
                .withMessageContaining("200");
    }

    @Test
    @DisplayName("a declared header is found however either side capitalises it")
    void a_header_is_found_case_insensitively() {
        ResponseModel response = new ResponseModel("200", Map.of(),
                Map.of("X-Rate-Limit", HeaderModel.of(NumberSchema.of(NumberKind.INTEGER), true)),
                Optional.empty());

        assertThat(response.header("x-rate-limit")).isPresent();
        assertThat(response.header("X-RATE-LIMIT").orElseThrow().required()).isTrue();
        assertThat(response.header("X-Other")).isEmpty();
    }

    @Test
    @DisplayName("a header keeps the document's own casing, so a report reads as the document does")
    void a_header_name_is_kept_as_written() {
        ResponseModel response = new ResponseModel("200", Map.of(),
                Map.of("X-Rate-Limit", HeaderModel.of(StringSchema.of(), false)), Optional.empty());

        assertThat(response.headers().keySet()).containsExactly("X-Rate-Limit");
    }

    @Test
    @DisplayName("two header names differing only in case are one header, and are refused")
    void a_header_declared_twice_is_refused() {
        Map<String, HeaderModel> headers = new LinkedHashMap<>();
        headers.put("X-Rate-Limit", HeaderModel.of(StringSchema.of(), true));
        headers.put("x-rate-limit", HeaderModel.of(StringSchema.of(), false));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ResponseModel("200", Map.of(), headers, Optional.empty()))
                .withMessageContaining("case-insensitive");
    }

    @Test
    @DisplayName("whether a header is promised is kept, because that is what 'missing' means")
    void a_header_knows_whether_it_is_promised() {
        HeaderModel promised = HeaderModel.of(StringSchema.of(), true);
        HeaderModel optional = HeaderModel.of(StringSchema.of(), false);

        assertThat(promised.required()).isTrue();
        assertThat(optional.required()).isFalse();
    }
}

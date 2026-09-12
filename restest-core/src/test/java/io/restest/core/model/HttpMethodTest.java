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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HttpMethodTest {

    @Test
    @DisplayName("a method is recognised however the document capitalises it")
    void a_method_is_recognised_in_any_case() {
        assertThat(HttpMethod.named("get")).contains(HttpMethod.GET);
        assertThat(HttpMethod.named(" PoSt ")).contains(HttpMethod.POST);
    }

    @Test
    @DisplayName("the QUERY method OpenAPI 3.2 adds is one we know")
    void the_newest_method_is_known() {
        assertThat(HttpMethod.named("query")).contains(HttpMethod.QUERY);
    }

    @Test
    @DisplayName("a method we do not know comes back empty rather than throwing")
    void an_unknown_method_is_not_an_exception() {
        assertThat(HttpMethod.named("frobnicate")).isEmpty();
        assertThat(HttpMethod.named(null)).isEmpty();
        assertThat(HttpMethod.named("")).isEmpty();
    }

    @Test
    @DisplayName("the nine methods OpenAPI allows are known, and nothing else is")
    void the_known_methods_are_the_documented_ones() {
        assertThat(HttpMethod.values()).hasSize(9);
        assertThat(HttpMethod.named("connect")).isEmpty();
    }
}

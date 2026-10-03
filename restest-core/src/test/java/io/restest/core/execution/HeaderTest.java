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
package io.restest.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HeaderTest {

    @Test
    @DisplayName("a header carries its name and value")
    void a_header_is_a_plain_tuple() {
        Header header = Header.of("X-Trace", "abc");

        assertThat(header.name()).isEqualTo("X-Trace");
        assertThat(header.value()).isEqualTo("abc");
    }

    @Test
    @DisplayName("a header must have a name")
    void a_name_is_required() {
        assertThatIllegalArgumentException().isThrownBy(() -> Header.of(" ", "abc"));
    }
}

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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OperationIdTest {

    @Test
    @DisplayName("an operation with no declared identifier gets one made from its method and path")
    void an_identifier_is_synthesised_from_the_document() {
        OperationId id = OperationId.synthesised(HttpMethod.GET, "/pets/{petId}");

        assertThat(id.value()).isEqualTo("GET /pets/{petId}");
    }

    @Test
    @DisplayName("the same document yields the same identifier, whatever order it was read in")
    void a_synthesised_identifier_is_stable() {
        assertThat(OperationId.synthesised(HttpMethod.POST, "/pets"))
                .isEqualTo(OperationId.synthesised(HttpMethod.POST, "/pets"));
        assertThat(OperationId.synthesised(HttpMethod.POST, "/pets"))
                .isNotEqualTo(OperationId.synthesised(HttpMethod.GET, "/pets"));
    }

    @Test
    @DisplayName("a blank identifier is refused: everything downstream keys on it")
    void a_blank_identifier_is_refused() {
        assertThatIllegalArgumentException().isThrownBy(() -> OperationId.of(" "));
    }

    @Test
    @DisplayName("an identifier prints as itself, so reports read as the document does")
    void an_identifier_prints_as_its_value() {
        assertThat(OperationId.of("getPetById")).hasToString("getPetById");
    }
}

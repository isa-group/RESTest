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

class TestCaseIdTest {

    @Test
    @DisplayName("two generated identifiers are different")
    void generated_identifiers_are_not_shared() {
        assertThat(TestCaseId.generate()).isNotEqualTo(TestCaseId.generate());
    }

    @Test
    @DisplayName("a specific identifier can be built for a value read back from storage")
    void a_specific_identifier_is_buildable() {
        assertThat(TestCaseId.of("tc-1").value()).isEqualTo("tc-1");
    }

    @Test
    @DisplayName("an identifier prints as itself")
    void an_identifier_prints_as_its_value() {
        assertThat(TestCaseId.of("tc-1")).hasToString("tc-1");
    }

    @Test
    @DisplayName("a blank identifier is refused")
    void a_blank_identifier_is_refused() {
        assertThatIllegalArgumentException().isThrownBy(() -> TestCaseId.of(" "));
    }
}

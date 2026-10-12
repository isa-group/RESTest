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
package io.restest.core.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks what a kind of fault that is not in the shared catalogue is called: one a tool defines for
 * itself, in the numbers the catalogue leaves to tools.
 */
class FaultCategoryTest {

    /** A kind nobody has agreed a shared name for, as RESTest might one day define. */
    private static final FaultCategory OWN_KIND = new FaultCategory() {
        @Override
        public int code() {
            return 950;
        }

        @Override
        public String descriptiveName() {
            return "A kind of RESTest's own";
        }

        @Override
        public String testCaseLabel() {
            return "ownKind";
        }
    };

    @Test
    @DisplayName("a kind of a tool's own has a key no key of the catalogue can be mistaken for")
    void a_kind_of_our_own_has_a_key_of_its_own() {
        assertThat(OWN_KIND.id()).isEqualTo("RESTEST_950");
        assertThat(OWN_KIND.label()).isEqualTo("F950:A kind of RESTest's own");
        assertThat(WfcFault.values()).extracting(FaultCategory::id)
                .noneMatch(id -> id.startsWith("RESTEST_"));
    }
}

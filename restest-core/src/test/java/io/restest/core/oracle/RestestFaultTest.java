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

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Checks that the kinds RESTest numbers itself cannot be mistaken for the catalogue's. */
class RestestFaultTest {

    @Test
    @DisplayName("every kind of RESTest's own is numbered in the 900s the catalogue leaves to tools")
    void every_kind_is_in_the_range_left_to_tools() {
        assertThat(RestestFault.values())
                .allSatisfy(kind -> assertThat(kind.code()).isBetween(900, 999));
    }

    @Test
    @DisplayName("no kind of RESTest's own shares a number with a kind of the catalogue")
    void no_number_is_the_catalogues() {
        assertThat(RestestFault.values()).extracting(FaultCategory::code)
                .doesNotHaveDuplicates()
                .noneMatch(code -> WfcFault.byCode(code).isPresent());
    }

    @Test
    @DisplayName("each kind's key says it is RESTest's, and in words")
    void each_key_is_restests_and_in_words() {
        assertThat(RestestFault.UNDECLARED_STATUS_CODE.id())
                .isEqualTo("RESTEST_UNDECLARED_STATUS_CODE");
        assertThat(RestestFault.UNDECLARED_CONTENT_TYPE.id())
                .isEqualTo("RESTEST_UNDECLARED_CONTENT_TYPE");
        assertThat(Arrays.stream(RestestFault.values()).map(FaultCategory::id))
                .noneMatch(id -> Arrays.stream(WfcFault.values()).anyMatch(
                        fault -> fault.id().equals(id)));
    }

    @Test
    @DisplayName("each kind is written in one piece as every kind is: F, its number, its name")
    void each_kind_is_labelled_like_the_catalogues() {
        assertThat(RestestFault.UNDECLARED_STATUS_CODE.label())
                .isEqualTo("F920:A Status Code The Document Does Not Declare");
        assertThat(RestestFault.UNDECLARED_CONTENT_TYPE.label())
                .isEqualTo("F921:A Content Type The Document Does Not Declare");
        assertThat(RestestFault.UNDECLARED_STATUS_CODE.testCaseLabel())
                .isEqualTo("returnsUndeclaredStatusCode");
        assertThat(RestestFault.UNDECLARED_CONTENT_TYPE.testCaseLabel())
                .isEqualTo("returnsUndeclaredContentType");
    }
}

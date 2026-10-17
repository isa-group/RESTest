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
package io.restest.oracles;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.model.ApiModel;
import io.restest.core.model.OperationId;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.WfcFault;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OtherServerErrorOracleTest {

    private static final OperationId LIST_PETS = OperationId.of("GET /pets");
    private static final ApiModel API = ApiModel.of("Pets", "1.0.0", List.of());

    private final OtherServerErrorOracle oracle = new OtherServerErrorOracle();

    @ParameterizedTest
    @ValueSource(ints = {502, 503, 504, 599})
    @DisplayName("a server error other than 500 and 501 is reported, saying which")
    void another_server_error_is_reported(int status) {
        List<Finding> found = oracle.judge(
                Attempts.answered(LIST_PETS, "/pets", status, "text/html", "<h1>Bad gateway</h1>"),
                API);

        assertThat(found).hasSize(1);
        Finding finding = found.get(0);
        assertThat(finding.category()).isEqualTo(WfcFault.HTTP_OTHER_STATUS_5XX);
        assertThat(finding.operation()).isEqualTo(LIST_PETS);
        assertThat(finding.summary()).startsWith("the API answered " + status + ",");
        assertThat(finding.details()).isEmpty();
    }

    @Test
    @DisplayName("500 is the other rule's, so a 500 is not reported twice")
    void a_500_is_left_to_its_own_rule() {
        assertThat(oracle.judge(
                Attempts.answered(LIST_PETS, "/pets", 500, "application/json", "{}"), API))
                .isEmpty();
    }

    @Test
    @DisplayName("501 is about what the document promises, not a failure, and is left to its own "
            + "rule")
    void a_501_is_left_alone() {
        assertThat(oracle.judge(
                Attempts.answered(LIST_PETS, "/pets", 501, "application/json", "{}"), API))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 204, 301, 404, 429, 499, 600, 999})
    @DisplayName("anything outside 502 to 599 is left alone, a code of 600 or more included")
    void anything_else_is_left_alone(int status) {
        assertThat(oracle.judge(
                Attempts.answered(LIST_PETS, "/pets", status, "application/json", "{}"), API))
                .isEmpty();
    }

    @Test
    @DisplayName("a reply that broke off is not evidence about the API, whatever its status line said")
    void a_reply_that_broke_off_is_left_alone() {
        assertThat(oracle.judge(Attempts.brokenOff(LIST_PETS, "/pets", 503), API)).isEmpty();
    }

    @Test
    @DisplayName("a request that got no answer at all is not the API answering a server error")
    void a_request_with_no_answer_is_not_a_fault_here() {
        assertThat(oracle.judge(Attempts.neverAnswered(LIST_PETS, "/pets"), API)).isEmpty();
    }

    @Test
    @DisplayName("the rule says what it is called and what it checks")
    void the_rule_describes_itself() {
        assertThat(oracle.name()).isEqualTo("other-server-error");
        assertThat(oracle.description()).contains("500").contains("501");
    }
}

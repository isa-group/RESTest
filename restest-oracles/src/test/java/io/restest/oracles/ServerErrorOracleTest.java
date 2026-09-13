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

class ServerErrorOracleTest {

    private static final OperationId LIST_PETS = OperationId.of("GET /pets");
    private static final ApiModel API = ApiModel.of("Pets", "1.0.0", List.of());

    private final ServerErrorOracle oracle = new ServerErrorOracle();

    @Test
    @DisplayName("an API that answers 500 is reported as having fallen over")
    void a_500_is_reported() {
        List<Finding> found = oracle.judge(
                Attempts.answered(LIST_PETS, "/pets", 500, "application/json", "{}"), API);

        assertThat(found).hasSize(1);
        Finding finding = found.get(0);
        assertThat(finding.category()).isEqualTo(WfcFault.HTTP_STATUS_500);
        assertThat(finding.operation()).isEqualTo(LIST_PETS);
        assertThat(finding.summary()).contains("500");
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 204, 301, 400, 401, 404, 409, 422, 429})
    @DisplayName("anything that is not a server error is left alone")
    void other_status_codes_are_left_alone(int status) {
        assertThat(oracle.judge(
                Attempts.answered(LIST_PETS, "/pets", status, "application/json", "{}"), API))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {501, 502, 503, 504})
    @DisplayName("the other 500s are left to rules that can tell whose fault they are")
    void the_other_server_status_codes_are_not_reported(int status) {
        assertThat(oracle.judge(
                Attempts.answered(LIST_PETS, "/pets", status, "application/json", "{}"), API))
                .isEmpty();
    }

    @Test
    @DisplayName("a request that got no answer at all is not the API answering 500")
    void a_request_with_no_answer_is_not_a_fault_here() {
        assertThat(oracle.judge(Attempts.neverAnswered(LIST_PETS, "/pets"), API)).isEmpty();
    }

    @Test
    @DisplayName("the rule says what it is called and what it checks")
    void the_rule_describes_itself() {
        assertThat(oracle.name()).isEqualTo("server-error");
        assertThat(oracle.description()).contains("500");
    }
}

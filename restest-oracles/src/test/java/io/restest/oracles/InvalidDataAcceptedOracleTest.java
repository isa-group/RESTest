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

import io.restest.core.execution.Intent;
import io.restest.core.execution.TestCase;
import io.restest.core.model.ApiModel;
import io.restest.core.model.OperationId;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.WfcFault;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class InvalidDataAcceptedOracleTest {

    private static final OperationId ADD_PET = OperationId.of("POST /pets");
    private static final ApiModel API = ApiModel.of("Pets", "1.0.0", List.of());

    /** A request with the required name left out, which the API should refuse. */
    private static final TestCase NAME_LEFT_OUT = Attempts.changed(ADD_PET,
            Intent.REFUSAL_EXPECTED, "dropRequired", "left out the required body.name");

    private final InvalidDataAcceptedOracle oracle = new InvalidDataAcceptedOracle();

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 204, 299})
    @DisplayName("a request broken on purpose and answered with a success is reported, saying what "
            + "was broken")
    void a_broken_request_accepted_is_reported(int status) {
        List<Finding> found = oracle.judge(Attempts.answered(NAME_LEFT_OUT, "/pets", status), API);

        assertThat(found).hasSize(1);
        Finding finding = found.get(0);
        assertThat(finding.category()).isEqualTo(WfcFault.SCHEMA_VALIDATION_BYPASS);
        assertThat(finding.operation()).isEqualTo(ADD_PET);
        assertThat(finding.summary()).isEqualTo("the API accepted a request it should have "
                + "refused: left out the required body.name");
        assertThat(finding.context()).isEmpty();
        assertThat(finding.details()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 199, 300, 304, 400, 404, 415, 422, 500, 503})
    @DisplayName("anything but a success is left alone: a refusal was expected, and a server error "
            + "is the other rules' to report")
    void anything_but_a_success_is_left_alone(int status) {
        assertThat(oracle.judge(Attempts.answered(NAME_LEFT_OUT, "/pets", status), API)).isEmpty();
    }

    @Test
    @DisplayName("a value that breaks only the format the document names counts like any other "
            + "change the document forbids")
    void a_value_off_its_format_counts() {
        TestCase notADate = Attempts.changed(ADD_PET, Intent.REFUSAL_EXPECTED, "breakAFormat",
                "sent \"2021-02-30\" for body.born, which is not a valid date");

        assertThat(oracle.judge(Attempts.answered(notADate, "/pets", 201), API))
                .extracting(Finding::summary)
                .containsExactly("the API accepted a request it should have refused: sent "
                        + "\"2021-02-30\" for body.born, which is not a valid date");
    }

    @Test
    @DisplayName("a changed request that expected nothing in particular is left alone, so it is "
            + "the expectation that is read and not only the change")
    void a_change_that_expected_nothing_is_left_alone() {
        TestCase nothingExpected = Attempts.changed(ADD_PET, Intent.UNKNOWN, "dropRequired",
                "left out the required body.name");

        assertThat(oracle.judge(Attempts.answered(nothingExpected, "/pets", 200), API)).isEmpty();
    }

    @Test
    @DisplayName("reading again what the API said it deleted, and getting it, is a different kind "
            + "of fault, about deleting, and left to the rule for that")
    void a_step_of_a_series_is_left_alone() {
        TestCase readAgain = Attempts.readAfterDelete(OperationId.of("GET /pets/{petId}"));

        assertThat(oracle.judge(Attempts.answered(readAgain, "/pets/7", 200), API)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Intent.class, names = {"UNKNOWN", "PUSHING", "ACCEPTABLE"})
    @DisplayName("a request built from nothing is left alone whatever it expected")
    void a_request_built_from_nothing_is_left_alone(Intent intent) {
        TestCase built = TestCase.of(ADD_PET, List.of(), Optional.empty(), intent);

        assertThat(oracle.judge(Attempts.answered(built, "/pets", 200), API)).isEmpty();
    }

    @Test
    @DisplayName("a reply that broke off is not evidence about the API, whatever its status line said")
    void a_reply_that_broke_off_is_left_alone() {
        assertThat(oracle.judge(Attempts.brokenOff(NAME_LEFT_OUT, "/pets", 201), API)).isEmpty();
    }

    @Test
    @DisplayName("a request that got no answer was not accepted")
    void a_request_with_no_answer_is_left_alone() {
        assertThat(oracle.judge(Attempts.neverAnswered(NAME_LEFT_OUT, "/pets"), API)).isEmpty();
    }

    @Test
    @DisplayName("the rule says what it is called and what it checks")
    void the_rule_describes_itself() {
        assertThat(oracle.name()).isEqualTo("invalid-data-accepted");
        assertThat(oracle.description()).contains("accepted").contains("broken on purpose");
    }
}

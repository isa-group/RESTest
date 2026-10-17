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
import io.restest.core.execution.InteractionOutcome.TransportFailure.Wait;
import io.restest.core.execution.TestCase;
import io.restest.core.model.ApiModel;
import io.restest.core.model.OperationId;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.WfcFault;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class NoReplyInTimeOracleTest {

    private static final OperationId FETCH = OperationId.of("GET /fetch");
    private static final ApiModel API = ApiModel.of("Pets", "1.0.0", List.of());

    private final NoReplyInTimeOracle oracle = new NoReplyInTimeOracle();

    @Test
    @DisplayName("a request the API had on an open connection and never answered is reported, "
            + "saying how long RESTest waited")
    void a_request_the_api_sat_on_is_reported() {
        List<Finding> found = oracle.judge(
                Attempts.timedOut(FETCH, "/fetch", Wait.REPLY, Duration.ofMillis(30_011)), API);

        assertThat(found).hasSize(1);
        Finding finding = found.get(0);
        assertThat(finding.category()).isEqualTo(WfcFault.SECURITY_CALL_TIMEOUT);
        assertThat(finding.operation()).isEqualTo(FETCH);
        assertThat(finding.summary()).isEqualTo("no reply within 30.0s: a connection to the API "
                + "was open, and no status line came back before RESTest stopped waiting");
        assertThat(finding.details()).isEmpty();
    }

    @Test
    @DisplayName("the time said is the attempt's own, to a tenth of a second")
    void the_time_said_is_the_attempts_own() {
        assertThat(oracle.judge(Attempts.timedOut(FETCH, "/fetch", Wait.REPLY,
                Duration.ofMillis(1_960)), API).get(0).summary())
                .startsWith("no reply within 2.0s:");
    }

    @Test
    @DisplayName("a connection that could not be made in time is not reported: the request never "
            + "reached the API")
    void a_connection_not_made_in_time_is_left_alone() {
        assertThat(oracle.judge(Attempts.timedOut(FETCH, "/fetch", Wait.CONNECTION,
                Duration.ofSeconds(10)), API)).isEmpty();
    }

    @Test
    @DisplayName("a request with no reply for any other reason - refused, cut - is not reported")
    void a_failure_that_was_no_timeout_is_left_alone() {
        assertThat(oracle.judge(Attempts.neverAnswered(FETCH, "/fetch"), API)).isEmpty();
    }

    @Test
    @DisplayName("a reply whose first line arrived is not reported, however long the rest took")
    void a_reply_that_broke_off_is_left_alone() {
        assertThat(oracle.judge(Attempts.brokenOff(FETCH, "/fetch", 200), API)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 404, 500, 503})
    @DisplayName("a reply, whatever it said, is not reported")
    void a_reply_is_left_alone(int status) {
        assertThat(oracle.judge(
                Attempts.answered(FETCH, "/fetch", status, "application/json", "{}"), API))
                .isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Intent.class)
    @DisplayName("whatever the request was meant to show, a request the API sat on is reported")
    void every_intent_is_reported(Intent intent) {
        // A refusal is expected only of a request changed on purpose, so that one is made so.
        TestCase testCase = intent == Intent.REFUSAL_EXPECTED
                ? Attempts.changed(FETCH, intent, "dropRequired", "left out the required body.name")
                : TestCase.of(FETCH, List.of(), Optional.empty(), intent);

        assertThat(oracle.judge(Attempts.timedOut(testCase, "/fetch", Wait.REPLY,
                Duration.ofSeconds(30)), API))
                .extracting(Finding::category)
                .containsExactly(WfcFault.SECURITY_CALL_TIMEOUT);
    }

    @Test
    @DisplayName("the rule says what it is called and what it checks")
    void the_rule_describes_itself() {
        assertThat(oracle.name()).isEqualTo("no-reply-in-time");
        assertThat(oracle.description()).contains("no reply").contains("stopped waiting");
    }
}

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
package io.restest.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The number benchmarks rank testing tools on, and the edges of how it is counted.
 *
 * <p>Worth its own tests rather than only being exercised through the reports that print it, because
 * every edge here is a way the published number could be quietly wrong: counting a code that is not
 * a server error, counting one broken operation once per reply, or losing a reply that broke off
 * after its status line.
 */
class ServerErrorsTest {

    @Test
    @DisplayName("one operation broken many times over is one broken operation")
    void an_operation_is_counted_once_however_often_it_breaks() {
        ServerErrors errors = new ServerErrors();
        for (int again = 0; again < 1_000; again++) {
            errors.note(answered("GET /pets", 500));
        }

        // A run spends its whole budget, so counting replies would measure how long the run was
        // rather than how much of the API is broken.
        assertThat(errors.operationsAnswering500()).isEqualTo(1);
        assertThat(errors.operationsAnsweringAny5xx()).isEqualTo(1);
    }

    @Test
    @DisplayName("the 5xx family is counted apart from the 500 the catalogue names")
    void the_family_and_the_named_fault_are_counted_apart() {
        ServerErrors errors = new ServerErrors();
        errors.note(answered("GET /pets", 500));
        errors.note(answered("GET /shelters", 503));
        errors.note(answered("GET /vets", 200));

        assertThat(errors.operationsAnswering500()).isEqualTo(1);
        assertThat(errors.operationsAnsweringAny5xx()).isEqualTo(2);
    }

    @Test
    @DisplayName("only the 5xx family counts, and the family has both an upper and a lower edge")
    void only_the_family_counts() {
        ServerErrors errors = new ServerErrors();
        errors.note(answered("GET /a", 499));
        errors.note(answered("GET /b", 600));
        // Nothing in the model stops an API answering this, real ones do it, and the shared
        // catalogue carries a fault for exactly that. It is not a server error and must not be
        // counted as one, which is the half of the range test a reader is likeliest to delete.
        errors.note(answered("GET /c", 999));

        assertThat(errors.none()).isTrue();
        assertThat(errors.operationsAnsweringAny5xx()).isZero();

        errors.note(answered("GET /d", 500));
        errors.note(answered("GET /e", 599));

        assertThat(errors.operationsAnsweringAny5xx()).isEqualTo(2);
        assertThat(errors.operationsAnswering500()).isEqualTo(1);
    }

    @Test
    @DisplayName("a reply that broke off after saying 500 still said 500")
    void a_reply_cut_short_after_its_status_line_still_counts() {
        ServerErrors errors = new ServerErrors();
        errors.note(cutShortAfter("GET /pets", 500));
        errors.note(cutShortWithNoStatus("GET /shelters"));

        // The status line arrives first and is complete long before the body it precedes. An API
        // that answered 500 and then dropped the connection did answer 500, and a count of how the
        // API behaved that dropped those would read lower than the truth.
        assertThat(errors.operationsAnswering500()).isEqualTo(1);
        assertThat(errors.operationsAnsweringAny5xx()).isEqualTo(1);
    }

    private static Interaction answered(String operation, int status) {
        return Runs.attempt(operation, "/x", status);
    }

    private static Interaction cutShortAfter(String operation, int status) {
        return Interaction.malformedResponse(testCase(operation), request(),
                "declared Content-Length exceeds the bytes actually sent",
                Optional.of(StatusLine.of(status)),
                List.of(Header.of("Content-Type", "application/json")), Optional.empty(),
                Instant.EPOCH, Duration.ofMillis(3));
    }

    private static Interaction cutShortWithNoStatus(String operation) {
        return Interaction.malformedResponse(testCase(operation), request(),
                "nothing that could be read as a status line arrived", Optional.empty(),
                List.of(), Optional.empty(), Instant.EPOCH, Duration.ofMillis(3));
    }

    private static TestCase testCase(String operation) {
        return TestCase.of(OperationId.of(operation), List.of());
    }

    private static HttpRequestRecord request() {
        return HttpRequestRecord.of(HttpMethod.GET, Runs.BASE + "/x");
    }
}

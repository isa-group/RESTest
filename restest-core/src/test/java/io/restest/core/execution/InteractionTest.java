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

import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InteractionTest {

    private static final TestCase TEST_CASE = TestCase.of(
            OperationId.synthesised(HttpMethod.GET, "/pets/{petId}"), List.of());
    private static final HttpRequestRecord REQUEST = HttpRequestRecord.of(HttpMethod.GET,
            "https://api.example.com/pets/42");

    @Test
    @DisplayName("an answered interaction carries the response it received")
    void an_answered_interaction_carries_its_response() {
        HttpResponseRecord ok = HttpResponseRecord.of(200);

        Interaction interaction = Interaction.answered(TEST_CASE, REQUEST, ok, Instant.now(),
                Duration.ofMillis(120));

        assertThat(interaction.isAnswered()).isTrue();
        assertThat(interaction.response()).contains(ok);
    }

    @Test
    @DisplayName("a transport failure carries why, and no response")
    void a_transport_failure_carries_no_response() {
        Interaction interaction = Interaction.transportFailure(TEST_CASE, REQUEST,
                "connection refused", Instant.now(), Duration.ofSeconds(1));

        assertThat(interaction.isAnswered()).isFalse();
        assertThat(interaction.response()).isEmpty();
        assertThat(((InteractionOutcome.TransportFailure) interaction.outcome()).reason())
                .isEqualTo("connection refused");
    }

    @Test
    @DisplayName("a transport failure must say what went wrong")
    void a_transport_failure_requires_a_reason() {
        assertThatIllegalArgumentException().isThrownBy(() -> Interaction.transportFailure(
                TEST_CASE, REQUEST, " ", Instant.now(), Duration.ZERO));
    }

    @Test
    @DisplayName("elapsed time cannot be negative")
    void elapsed_time_cannot_be_negative() {
        assertThatIllegalArgumentException().isThrownBy(() -> Interaction.answered(TEST_CASE,
                REQUEST, HttpResponseRecord.of(200), Instant.now(), Duration.ofMillis(-1)));
    }

    @Test
    @DisplayName("each interaction gets its own identifier")
    void identifiers_are_not_shared() {
        Interaction first = Interaction.answered(TEST_CASE, REQUEST, HttpResponseRecord.of(200),
                Instant.now(), Duration.ZERO);
        Interaction second = Interaction.answered(TEST_CASE, REQUEST, HttpResponseRecord.of(200),
                Instant.now(), Duration.ZERO);

        assertThat(first.id()).isNotEqualTo(second.id());
    }

    /**
     * Compiling is the assertion: a switch with no default over InteractionOutcome stops compiling
     * the day a third outcome is added and not handled here.
     */
    @Test
    @DisplayName("both outcomes can be told apart without a default case")
    void the_hierarchy_is_exhaustive() {
        InteractionOutcome answered = new InteractionOutcome.Answered(HttpResponseRecord.of(200));
        InteractionOutcome failed = new InteractionOutcome.TransportFailure("timed out");

        assertThat(describe(answered)).isEqualTo("answered: 200");
        assertThat(describe(failed)).isEqualTo("failed: timed out");
    }

    private static String describe(InteractionOutcome outcome) {
        return switch (outcome) {
            case InteractionOutcome.Answered a -> "answered: " + a.response().statusCode();
            case InteractionOutcome.TransportFailure f -> "failed: " + f.reason();
        };
    }
}

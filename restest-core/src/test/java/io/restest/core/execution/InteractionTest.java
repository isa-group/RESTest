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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    @DisplayName("a malformed response carries no well-formed response, and is not a transport failure")
    void a_malformed_response_is_a_third_outcome() {
        Interaction interaction = Interaction.malformedResponse(TEST_CASE, REQUEST,
                "chunked encoding ended without a final zero-length chunk", Optional.empty(),
                List.of(), Optional.empty(), Instant.now(), Duration.ofMillis(50));

        assertThat(interaction.isAnswered()).isFalse();
        assertThat(interaction.response()).isEmpty();
        assertThat(interaction.outcome()).isInstanceOf(InteractionOutcome.MalformedResponse.class);
    }

    @Test
    @DisplayName("a malformed response keeps the whole status line when it parsed, not only the code")
    void a_malformed_response_keeps_what_did_parse() {
        StatusLine statusLine = new StatusLine(200, Optional.of("OK"), Optional.of("HTTP/1.1"));

        Interaction interaction = Interaction.malformedResponse(TEST_CASE, REQUEST,
                "declared Content-Length exceeds the bytes actually sent",
                Optional.of(statusLine),
                List.of(Header.of("Content-Type", "application/json")), Optional.empty(),
                Instant.now(), Duration.ZERO);

        InteractionOutcome.MalformedResponse outcome =
                (InteractionOutcome.MalformedResponse) interaction.outcome();
        assertThat(outcome.statusLine()).contains(statusLine);
        assertThat(outcome.headers()).extracting(Header::name).containsExactly("Content-Type");
    }

    @Test
    @DisplayName("a malformed response's declared-but-undelivered length is read from its headers, "
            + "not from the partial payload's own wireLength")
    void a_declared_length_the_response_failed_to_honour_is_visible_via_headers() {
        // Only 3 bytes actually arrived over the wire before the connection broke - nothing was
        // dropped by our own storage, so the partial payload carries no wireLength of its own. The
        // gap between what the API promised and what it delivered is visible by comparing this
        // payload's size against the Content-Length header, which is what an oracle judging this
        // outcome is expected to do.
        Payload partial = Payload.of(new byte[] {1, 2, 3}, Payload.UNKNOWN_MEDIA_TYPE);

        Interaction interaction = Interaction.malformedResponse(TEST_CASE, REQUEST,
                "declared Content-Length (10) exceeds the bytes actually sent (3)",
                Optional.of(new StatusLine(200, Optional.empty(), Optional.empty())),
                List.of(Header.of("Content-Length", "10")), Optional.of(partial), Instant.now(),
                Duration.ZERO);

        InteractionOutcome.MalformedResponse outcome =
                (InteractionOutcome.MalformedResponse) interaction.outcome();
        assertThat(outcome.partial()).contains(partial);
        assertThat(outcome.partial().orElseThrow().wireLength()).isEmpty();
        assertThat(outcome.headers()).extracting(Header::value).containsExactly("10");
    }

    @Test
    @DisplayName("a malformed response must say what was wrong with it")
    void a_malformed_response_requires_a_reason() {
        assertThatIllegalArgumentException().isThrownBy(() -> Interaction.malformedResponse(
                TEST_CASE, REQUEST, " ", Optional.empty(), List.of(), Optional.empty(),
                Instant.now(), Duration.ZERO));
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
     * the day a fourth outcome is added and not handled here.
     */
    @Test
    @DisplayName("all three outcomes can be told apart without a default case")
    void the_hierarchy_is_exhaustive() {
        InteractionOutcome answered = new InteractionOutcome.Answered(HttpResponseRecord.of(200));
        InteractionOutcome malformed = new InteractionOutcome.MalformedResponse(
                "bad framing", Optional.empty(), List.of(), Optional.empty());
        InteractionOutcome failed = new InteractionOutcome.TransportFailure("timed out");

        assertThat(describe(answered)).isEqualTo("answered: 200");
        assertThat(describe(malformed)).isEqualTo("malformed: bad framing");
        assertThat(describe(failed)).isEqualTo("failed: timed out");
    }

    @Test
    @DisplayName("a malformed response's optional components cannot be null")
    void malformed_response_components_cannot_be_null() {
        assertThatNullPointerException().isThrownBy(() -> new InteractionOutcome.MalformedResponse(
                "reason", null, List.of(), Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new InteractionOutcome.MalformedResponse(
                "reason", Optional.empty(), null, Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new InteractionOutcome.MalformedResponse(
                "reason", Optional.empty(), List.of(), null));
    }

    private static String describe(InteractionOutcome outcome) {
        return switch (outcome) {
            case InteractionOutcome.Answered a -> "answered: " + a.response().statusCode();
            case InteractionOutcome.MalformedResponse m -> "malformed: " + m.reason();
            case InteractionOutcome.TransportFailure f -> "failed: " + f.reason();
        };
    }
}

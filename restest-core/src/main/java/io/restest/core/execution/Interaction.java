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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A record of one real attempt to call the API: which {@link TestCase} was sent, the exact request
 * that went out, and what came back.
 *
 * <p>Every time RESTest actually sends a request to the API being tested, it keeps one of these:
 * the exact bytes sent and received, how long it took, which test case produced the request, and
 * where every value in that request came from. This is meant to be the record that gets saved, that
 * a later check (a "did the API answer correctly?" judgement) is made against, and that can be
 * re-examined afterwards without sending the request again.
 *
 * <p>An {@code Interaction} exists only for a request that was genuinely sent. A {@link TestCase}
 * that was planned but never actually attempted - for example because time ran out first - produces
 * no interaction at all: there is nothing to describe and nothing to report, so none is invented.
 *
 * <p>Nothing here limits how many interactions one {@link TestCase} may end up producing: a retry, a
 * repeated attempt, or a redirect followed by the underlying HTTP client can each become their own
 * interaction. That flexibility is why, when one step's value is read from an earlier response (see
 * {@link ValueOrigin.Derived}), it points at the identifier of that specific interaction rather than
 * at the test case that produced it - it keeps naming the one exchange whose data was actually used,
 * whatever else later happens to the test case.
 *
 * <p>A step that depends on an earlier one (a "stateful" step - for example, creating something and
 * then reading it back) is not a different kind of interaction. It is simply one whose
 * {@link TestCase} happens to use a value read from an earlier interaction. A whole chain of such
 * steps is just a chain of interaction identifiers, not a separate structure this type needs to
 * represent.
 *
 * @param id this interaction's identity, stable across storage and re-analysis
 * @param testCase the test case that produced this attempt
 * @param request the exact request sent
 * @param outcome what happened: a response, a malformed one, or none
 * @param sentAt when the request was sent
 * @param elapsed how long the attempt took, from send to outcome
 */
public record Interaction(
        InteractionId id,
        TestCase testCase,
        HttpRequestRecord request,
        InteractionOutcome outcome,
        Instant sentAt,
        Duration elapsed) {

    public Interaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(testCase, "testCase");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(sentAt, "sentAt");
        Objects.requireNonNull(elapsed, "elapsed");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed time cannot be negative: " + elapsed);
        }
    }

    /** A fresh interaction that received a well-formed response. */
    public static Interaction answered(TestCase testCase, HttpRequestRecord request,
            HttpResponseRecord response, Instant sentAt, Duration elapsed) {
        return new Interaction(InteractionId.generate(), testCase, request,
                new InteractionOutcome.Answered(response), sentAt, elapsed);
    }

    /** A fresh interaction whose response broke HTTP framing before it could be read as one. */
    public static Interaction malformedResponse(TestCase testCase, HttpRequestRecord request,
            String reason, Optional<StatusLine> statusLine, List<Header> headers,
            Optional<Payload> partial, Instant sentAt, Duration elapsed) {
        return new Interaction(InteractionId.generate(), testCase, request,
                new InteractionOutcome.MalformedResponse(reason, statusLine, headers, partial),
                sentAt, elapsed);
    }

    /** A fresh interaction that received no response at all. */
    public static Interaction transportFailure(TestCase testCase, HttpRequestRecord request,
            String reason, Instant sentAt, Duration elapsed) {
        return new Interaction(InteractionId.generate(), testCase, request,
                new InteractionOutcome.TransportFailure(reason), sentAt, elapsed);
    }

    /** The response received, if the attempt got a well-formed one. */
    public Optional<HttpResponseRecord> response() {
        return outcome instanceof InteractionOutcome.Answered answered
                ? Optional.of(answered.response())
                : Optional.empty();
    }

    /** Whether this attempt received a well-formed response. */
    public boolean isAnswered() {
        return outcome instanceof InteractionOutcome.Answered;
    }
}

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
import java.util.Objects;
import java.util.Optional;

/**
 * What we did to the API, and what came back: one {@link TestCase}, sent, and its outcome.
 *
 * <p>ADR-0006 requires every interaction to be persisted - "exact request and response bytes,
 * timings, the test case that produced it, and the provenance of each parameter value" - and this is
 * that record. The engine (M1.3) constructs one per attempt; the store (M1.4) persists it unchanged;
 * oracles (M1.6) judge it; {@code restest recheck} (M3.3) re-judges a stored one against a different
 * oracle set, offline.
 *
 * <p>A stateful step's {@link Interaction} is not a different shape from a stateless one's - it is
 * one whose {@link TestCase} happens to carry a {@link ValueOrigin.Derived}. The interaction that
 * produced the value another step derives from is named by its {@link #id()}, so a chain of steps is
 * a chain of these identifiers, discoverable from the store, rather than a container this type
 * introduces.
 *
 * @param id this interaction's identity, stable across storage and re-analysis
 * @param testCase the test case that produced this attempt
 * @param request the exact request sent
 * @param outcome what happened: a response, or none
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

    /** A fresh interaction that received a response. */
    public static Interaction answered(TestCase testCase, HttpRequestRecord request,
            HttpResponseRecord response, Instant sentAt, Duration elapsed) {
        return new Interaction(InteractionId.generate(), testCase, request,
                new InteractionOutcome.Answered(response), sentAt, elapsed);
    }

    /** A fresh interaction that received no response. */
    public static Interaction transportFailure(TestCase testCase, HttpRequestRecord request,
            String reason, Instant sentAt, Duration elapsed) {
        return new Interaction(InteractionId.generate(), testCase, request,
                new InteractionOutcome.TransportFailure(reason), sentAt, elapsed);
    }

    /** The response received, if the attempt got one. */
    public Optional<HttpResponseRecord> response() {
        return outcome instanceof InteractionOutcome.Answered answered
                ? Optional.of(answered.response())
                : Optional.empty();
    }

    /** Whether this attempt received a response at all. */
    public boolean isAnswered() {
        return outcome instanceof InteractionOutcome.Answered;
    }
}

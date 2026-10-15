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

import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Intent;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionId;
import io.restest.core.execution.Mutation;
import io.restest.core.execution.Payload;
import io.restest.core.execution.SequenceStep;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Attempts against an API, made up by hand, for the rules in this module to be pointed at. */
final class Attempts {

    static final String BASE = "https://api.example";

    private Attempts() {
    }

    /** A reply that came back with a body of the given kind. */
    static Interaction answered(OperationId operation, String url, int status, String contentType,
            String body) {
        return Interaction.answered(testCase(operation), request(url),
                new HttpResponseRecord(StatusLine.of(status),
                        List.of(Header.of("Content-Type", contentType)),
                        Optional.of(Payload.of(body.getBytes(StandardCharsets.UTF_8),
                                contentType))),
                Instant.EPOCH, Duration.ofMillis(11));
    }

    /** A reply that came back with no body at all, only a claim about what kind it would be. */
    static Interaction answeredWithoutBody(OperationId operation, String url, int status,
            String contentType) {
        return Interaction.answered(testCase(operation), request(url),
                new HttpResponseRecord(StatusLine.of(status),
                        List.of(Header.of("Content-Type", contentType)),
                        Optional.of(Payload.empty(contentType))),
                Instant.EPOCH, Duration.ofMillis(11));
    }

    /** A reply whose body was too large to keep whole, so only part of it was. */
    static Interaction answeredWithPartOfTheBody(OperationId operation, String url,
            int status, String contentType, String kept, long actualLength) {
        return Interaction.answered(testCase(operation), request(url),
                new HttpResponseRecord(StatusLine.of(status),
                        List.of(Header.of("Content-Type", contentType)),
                        Optional.of(Payload.partial(kept.getBytes(StandardCharsets.UTF_8),
                                contentType, actualLength))),
                Instant.EPOCH, Duration.ofMillis(11));
    }

    /** A reply that named no kind at all. */
    static Interaction answeredWithoutContentType(OperationId operation, String url, int status) {
        return answered(testCase(operation), url, status);
    }

    /** A request that never got an answer. */
    static Interaction neverAnswered(OperationId operation, String url) {
        return neverAnswered(testCase(operation), url);
    }

    /** This test case, sent, and never answered. */
    static Interaction neverAnswered(TestCase testCase, String url) {
        return Interaction.transportFailure(testCase, request(url),
                "the connection was refused", Instant.EPOCH, Duration.ofMillis(11));
    }

    /** This test case, sent, and answered with this status and no body. */
    static Interaction answered(TestCase testCase, String url, int status) {
        return Interaction.answered(testCase, request(url), HttpResponseRecord.of(status),
                Instant.EPOCH, Duration.ofMillis(11));
    }

    /** A reply that broke off after its status line. */
    static Interaction brokenOff(OperationId operation, String url, int status) {
        return brokenOff(testCase(operation), url, status);
    }

    /** This test case, sent, and answered with this status before the reply broke off. */
    static Interaction brokenOff(TestCase testCase, String url, int status) {
        return Interaction.malformedResponse(testCase, request(url),
                "the reply stopped after 3 bytes", Optional.of(StatusLine.of(status)), List.of(),
                Optional.empty(), Instant.EPOCH, Duration.ofMillis(11));
    }

    /**
     * A request made by changing one thing in a request the API had accepted, saying what was
     * expected of it.
     *
     * @param operator the kind of change, by the name of its switch, such as {@code dropRequired}
     * @param description the change in words
     */
    static TestCase changed(OperationId operation, Intent intent, String operator,
            String description) {
        return TestCase.changed(operation, List.of(), Optional.empty(), intent,
                new Mutation(InteractionId.generate(), operator, ParameterLocation.BODY,
                        "body.name", description));
    }

    /**
     * A later step of a series, reading again what the step before it deleted, which expects to be
     * refused.
     */
    static TestCase readAfterDelete(OperationId operation) {
        return TestCase.stepOf(operation, List.of(), Optional.empty(), Intent.REFUSAL_EXPECTED,
                new SequenceStep("readAfterDelete", 4, List.of(InteractionId.generate()),
                        "read it again: the API said it deleted it, so it should be gone"));
    }

    private static TestCase testCase(OperationId operation) {
        return TestCase.of(operation, List.of());
    }

    private static HttpRequestRecord request(String url) {
        return HttpRequestRecord.of(HttpMethod.GET, BASE + url);
    }
}

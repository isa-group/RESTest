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
package io.restest.store;

import io.restest.core.execution.BodyValue;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.Payload;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.json.JsonValue;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import io.restest.core.execution.ParameterValue;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Interactions built by hand for these tests, standing in for the ones a real run produces. The
 * generator that invents them and the engine that sends them are tested elsewhere; what matters here
 * is only that whatever is put in comes back out.
 */
final class Interactions {

    static final Instant NOON = Instant.parse("2026-09-12T12:00:00Z");

    private Interactions() {
    }

    /** A plain answered request: the ordinary case, and the one most rows will be. */
    static Interaction answered(String operation, int statusCode) {
        return answered(operation, statusCode, NOON);
    }

    static Interaction answered(String operation, int statusCode, Instant sentAt) {
        return Interaction.answered(
                testCase(operation),
                HttpRequestRecord.of(HttpMethod.GET, "http://localhost:8080/widgets"),
                new HttpResponseRecord(StatusLine.of(statusCode), List.of(), Optional.empty()),
                sentAt,
                Duration.ofMillis(42));
    }

    /** An attempt that never got an answer. */
    static Interaction failed(String operation) {
        return Interaction.transportFailure(
                testCase(operation),
                HttpRequestRecord.of(HttpMethod.GET, "http://localhost:8080/widgets"),
                "ConnectException: connection refused",
                NOON,
                Duration.ofMillis(7));
    }

    /** A reply that started correctly and stopped halfway. */
    static Interaction malformed(String operation, int statusCode) {
        return Interaction.malformedResponse(
                testCase(operation),
                HttpRequestRecord.of(HttpMethod.GET, "http://localhost:8080/widgets"),
                "the reply stopped after 4 bytes: SocketException",
                Optional.of(new StatusLine(statusCode, Optional.of("OK"), Optional.of("http/1.1"))),
                List.of(Header.of("Content-Type", "application/json")),
                Optional.of(Payload.partial("half".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "application/json", 100)),
                NOON,
                Duration.ofMillis(13));
    }

    /**
     * Everything a single interaction can carry at once: repeated headers, a body that was sent, a
     * reply that was truncated, parameter values from all three kinds of source.
     */
    static Interaction elaborate() {
        TestCase testCase = new TestCase(
                io.restest.core.execution.TestCaseId.of("test-case-1"),
                OperationId.of("POST /widgets"),
                List.of(
                        ParameterValue.of("widgetId", ParameterLocation.PATH,
                                JsonValue.of("w-1"), ValueOrigin.DECLARED),
                        ParameterValue.of("verbose", ParameterLocation.QUERY,
                                JsonValue.TRUE, new ValueOrigin.Generated("random")),
                        ParameterValue.of("X-Trace", ParameterLocation.HEADER,
                                JsonValue.of(new java.math.BigDecimal("90071992547409911")),
                                new ValueOrigin.Derived(
                                        io.restest.core.execution.InteractionId.of("earlier-one"),
                                        "the identifier the create call returned"))),
                Optional.of(new BodyValue("application/json",
                        JsonValue.object(new java.util.LinkedHashMap<>(java.util.Map.of(
                                "name", JsonValue.of("a widget")))),
                        new ValueOrigin.Generated("random"))));

        HttpRequestRecord request = new HttpRequestRecord(HttpMethod.POST,
                "http://localhost:8080/widgets?verbose=true",
                List.of(Header.of("Accept", "application/json"),
                        Header.of("Accept", "text/plain"),
                        Header.of("Content-Type", "application/json")),
                Optional.of(Payload.text("{\"name\":\"a widget\"}", "application/json")));

        HttpResponseRecord response = new HttpResponseRecord(
                new StatusLine(201, Optional.of("Created"), Optional.of("http/1.1")),
                List.of(Header.of("Location", "/widgets/w-1"),
                        Header.of("Set-Cookie", "a=1"),
                        Header.of("Set-Cookie", "b=2")),
                Optional.of(Payload.partial("{\"id\":\"w-1\"".getBytes(
                        java.nio.charset.StandardCharsets.UTF_8), "application/json", 4096)));

        return Interaction.answered(testCase, request, response, NOON, Duration.ofMillis(123));
    }

    /** A reply that is not text at all, so it cannot be stored as text without changing it. */
    static Interaction withBinaryReply() {
        byte[] notText = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        return Interaction.answered(
                testCase("GET /image"),
                HttpRequestRecord.of(HttpMethod.GET, "http://localhost:8080/image"),
                new HttpResponseRecord(StatusLine.of(200), List.of(),
                        Optional.of(Payload.of(notText, "image/jpeg"))),
                NOON,
                Duration.ofMillis(5));
    }

    static TestCase testCase(String operation) {
        return TestCase.of(OperationId.of(operation), List.of());
    }
}

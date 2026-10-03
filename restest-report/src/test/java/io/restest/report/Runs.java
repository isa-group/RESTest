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

import io.restest.core.exec.EngineStatistics;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.Payload;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.WfcFault;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/** Runs and findings made up by hand, for the reports in this module to be pointed at. */
final class Runs {

    static final String BASE = "https://api.example";
    static final String BODY = "{\"id\": \"seven\"}";

    private Runs() {
    }

    static Interaction attempt(String operation, String path, int status) {
        return Interaction.answered(
                TestCase.of(OperationId.of(operation), List.of()),
                new HttpRequestRecord(HttpMethod.GET, BASE + path,
                        List.of(Header.of("Accept", "application/json")), Optional.empty()),
                new HttpResponseRecord(StatusLine.of(status),
                        List.of(Header.of("Content-Type", "application/json")),
                        Optional.of(Payload.of(BODY.getBytes(StandardCharsets.UTF_8),
                                "application/json"))),
                Instant.parse("2026-09-13T10:00:00Z"), Duration.ofMillis(42));
    }

    /** The reply to one particular test case, so that its plan and its answer can be matched. */
    static Interaction answering(TestCase testCase, int status) {
        return Interaction.answered(testCase,
                new HttpRequestRecord(HttpMethod.GET, BASE + "/x", List.of(), Optional.empty()),
                new HttpResponseRecord(StatusLine.of(status), List.of(), Optional.empty()),
                Instant.parse("2026-09-13T10:00:00Z"), Duration.ofMillis(42));
    }

    /** One particular test case that never got a reply at all. */
    static Interaction neverAnswering(TestCase testCase) {
        return Interaction.transportFailure(testCase,
                new HttpRequestRecord(HttpMethod.GET, BASE + "/x", List.of(), Optional.empty()),
                "the connection was refused", Instant.parse("2026-09-13T10:00:00Z"),
                Duration.ofMillis(42));
    }

    static Finding fellOver() {
        return Finding.of(WfcFault.HTTP_STATUS_500, attempt("GET /pets", "/pets", 500),
                "the API answered 500, so it fell over while handling this request");
    }

    static Finding wrongShape(int details) {
        return Finding.of(WfcFault.SCHEMA_INVALID_RESPONSE,
                        attempt("GET /pets/{petId}", "/pets/7", 200),
                        "the body does not match the shape the specification declares for it")
                .withDetails(IntStream.range(0, details)
                        .mapToObj(i -> "/field" + i + ": string found, integer expected")
                        .toList());
    }

    /** The same kind of fault, on whichever operation is asked for. */
    static Finding fellOver(String operation) {
        return Finding.of(WfcFault.HTTP_STATUS_500, attempt(operation, "/x", 500),
                "the API answered 500, so it fell over while handling this request");
    }

    /** The other kind, likewise. */
    static Finding wrongShape(String operation) {
        return Finding.of(WfcFault.SCHEMA_INVALID_RESPONSE, attempt(operation, "/x", 200),
                "the body does not match the shape the specification declares for it");
    }

    /** A fault whose reply was big, which is how the report's limit in bytes is reached. */
    static Finding fellOverWithBody(String operation, int bytes) {
        String body = "x".repeat(bytes);
        Interaction attempt = Interaction.answered(
                TestCase.of(OperationId.of(operation), List.of()),
                new HttpRequestRecord(HttpMethod.GET, BASE + "/x", List.of(), Optional.empty()),
                new HttpResponseRecord(StatusLine.of(500), List.of(),
                        Optional.of(Payload.of(body.getBytes(StandardCharsets.UTF_8), "text/plain"))),
                Instant.parse("2026-09-13T10:00:00Z"), Duration.ofMillis(42));
        return Finding.of(WfcFault.HTTP_STATUS_500, attempt, "the API answered 500");
    }

    /** A fault on an attempt that never got a reply at all, so there is no status code. */
    static Finding neverAnswered(String operation) {
        return Finding.of(WfcFault.HTTP_STATUS_500,
                Interaction.transportFailure(
                        TestCase.of(OperationId.of(operation), List.of()),
                        new HttpRequestRecord(HttpMethod.GET, BASE + "/x", List.of(),
                                Optional.empty()),
                        "the connection was closed before anything came back",
                        Instant.parse("2026-09-13T10:00:00Z"), Duration.ofMillis(42)),
                "nothing came back");
    }

    static EngineStatistics engine() {
        return new EngineStatistics(20, Duration.ofSeconds(10), Duration.ofSeconds(3),
                Duration.ofSeconds(4), 4, 8);
    }
}

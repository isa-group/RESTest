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
package io.restest.cli;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An API that falls over when the body it requires arrives with nothing in it, and a run that finds
 * out.
 *
 * <p>The API here stores notes, and answers any note it is sent. Send it a body of no bytes at all
 * and it fails instead of saying a note is needed: the sort of bug that lives in the code that reads
 * a body, before the code that handles a note is ever reached - the body read without checking
 * there was one. No request built from the document looks like that, because the document says a
 * body is required and every such request sends one. A request of awkward values sends one too,
 * however awkward - an empty word, even {@code null}, is still something written down - and changing
 * one value inside an accepted body leaves the rest of it there. Only the body broken as a whole
 * reaches the code that breaks.
 *
 * <p>The same command, against the same API, with the same number to start from, finds the server
 * error with that change switched on and misses it with it off. Every other change to an accepted
 * request is switched off in both runs. Which change is made is drawn by chance, and two seconds on
 * a slow machine change only a handful of requests - three, once, on a Windows runner - among which
 * the one that finds the fault need not be. With the others off, every change made is that one.
 */
class BodiesOfTheWrongShapeTest {

    private static final String SPECIFICATION = """
            openapi: 3.0.3
            info: {title: Careless notes, version: "1.0"}
            paths:
              /notes:
                post:
                  operationId: addNote
                  requestBody:
                    required: true
                    content:
                      application/json:
                        schema:
                          type: object
                          required: [text]
                          properties:
                            text: {type: string}
                  responses:
                    "201": {description: the stored note}
            """;

    /** How long this stand-in takes to answer, in milliseconds, for the reason the fuzzing test gives. */
    private static final int ANSWERS_IN = 20;

    /** Every change made to an accepted request but the one this test is about. */
    private static final List<String> THE_OTHER_CHANGES = List.of("dropRequired", "wrongLocation",
            "wrongType", "outsideABound", "breakAnEnumeration", "breakAPattern", "sendNull",
            "sendEmpty", "oversize", "wrongRoot", "notJson", "wrongContentType", "beyondItsWidth");

    private static WireMockServer api;

    @BeforeAll
    static void start() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
        api.stubFor(post(urlPathEqualTo("/notes")).atPriority(5)
                .willReturn(aResponse().withStatus(201).withFixedDelay(ANSWERS_IN)
                        .withHeader("Content-Type", "application/json").withBody("{\"id\":1}")));
        // The body read without checking there was one.
        api.stubFor(post(urlPathEqualTo("/notes")).atPriority(1)
                .withRequestBody(absent())
                .willReturn(aResponse().withStatus(500).withFixedDelay(ANSWERS_IN)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("java.io.EOFException: the note ended before it began")));
    }

    @AfterAll
    static void stop() {
        if (api != null) {
            api.stop();
        }
    }

    @Test
    @DisplayName("a server error only a body of no bytes reaches is found by breaking an accepted "
            + "body as a whole, and missed with that change switched off")
    void breaking_the_body_as_a_whole_is_what_finds_it(@TempDir Path directory)
            throws IOException {
        Path document = Files.writeString(directory.resolve("openapi.yaml"), SPECIFICATION);

        String withChanges = run(directory.resolve("with"), document);

        String without = run(directory.resolve("without"), document,
                "--set", "mutation.emptyBody=false");

        assertThat(withChanges)
                .describedAs("a body with nothing in it is what makes this API fail")
                .contains("answered 500")
                .contains("changed one thing in a request the API had accepted");
        assertThat(without)
                .describedAs("every other request sends a body with something in it, and "
                        + "something is all this API needs")
                .doesNotContain("answered 500");
    }

    private static String run(Path out, Path document, String... extra) {
        List<String> arguments = new ArrayList<>(List.of(
                "run", document.toString(),
                "--url", api.baseUrl(),
                "--budget", "2s",
                "--seed", "20260928",
                "--out", out.toString()));
        for (String other : THE_OTHER_CHANGES) {
            arguments.addAll(List.of("--set", "mutation." + other + "=false"));
        }
        arguments.addAll(List.of(extra));
        StringWriter screen = new StringWriter();
        try (PrintWriter writer = new PrintWriter(screen, true)) {
            Restest.run(arguments.toArray(new String[0]), writer, writer);
        }
        return screen.toString();
    }
}

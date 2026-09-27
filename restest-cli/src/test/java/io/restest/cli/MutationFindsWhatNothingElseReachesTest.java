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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
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
 * An API that falls over when something it requires is missing, and a run that finds out.
 *
 * <p>The API here says a list of orders needs a customer, and answers any request carrying one.
 * Leave the customer out and it fails, instead of saying the request is incomplete - an everyday
 * kind of bug, the sort that comes from reading a parameter and using it without checking it was
 * there. No request built from the document ever looks like that, because the document says the
 * customer is required and every such request carries one; and a request of awkward values carries
 * one too, however awkward. Only a request the API accepted, sent again with that one thing left
 * out, reaches the code that breaks.
 *
 * <p>The comparison is the point. The same command, against the same API, with the same number to
 * start from, finds the server error with the changes switched on and misses it with them off.
 */
class MutationFindsWhatNothingElseReachesTest {

    private static final String SPECIFICATION = """
            openapi: 3.0.3
            info: {title: Careless orders, version: "1.0"}
            paths:
              /orders:
                get:
                  operationId: listOrders
                  parameters:
                    - name: customer
                      in: query
                      required: true
                      schema: {type: string}
                  responses:
                    "200": {description: the customer's orders}
            """;

    /** How long this stand-in takes to answer, in milliseconds, for the reason the fuzzing test gives. */
    private static final int ANSWERS_IN = 20;

    private static WireMockServer api;

    @BeforeAll
    static void start() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
        api.stubFor(get(urlPathEqualTo("/orders")).atPriority(5)
                .willReturn(aResponse().withStatus(200).withFixedDelay(ANSWERS_IN)
                        .withHeader("Content-Type", "application/json").withBody("[]")));
        // The customer read without checking it was sent.
        api.stubFor(get(urlPathEqualTo("/orders")).atPriority(1)
                .withQueryParam("customer", absent())
                .willReturn(aResponse().withStatus(500).withFixedDelay(ANSWERS_IN)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("java.lang.NullPointerException: customer")));
    }

    @AfterAll
    static void stop() {
        if (api != null) {
            api.stop();
        }
    }

    @Test
    @DisplayName("a server error only a missing required value reaches is found by changing an "
            + "accepted request, and missed with the changes switched off")
    void changing_an_accepted_request_is_what_finds_it(@TempDir Path directory)
            throws IOException {
        Path document = Files.writeString(directory.resolve("openapi.yaml"), SPECIFICATION);

        String withChanges = run(directory.resolve("with"), document);
        String withoutThem = run(directory.resolve("without"), document,
                "--set", "mutation.violations=false", "--set", "mutation.probes=false");

        assertThat(withChanges)
                .describedAs("the customer left out of a request the API accepted is what makes "
                        + "this API fail")
                .contains("answered 500")
                .contains("changed one thing in a request the API had accepted");
        assertThat(withoutThem)
                .describedAs("every other request carries a customer, awkward or not, and a "
                        + "customer is all this API needs")
                .doesNotContain("answered 500")
                .doesNotContain("changed one thing");
    }

    private static String run(Path out, Path document, String... extra) {
        List<String> arguments = new ArrayList<>(List.of(
                "run", document.toString(),
                "--url", api.baseUrl(),
                "--budget", "2s",
                "--seed", "20260927",
                "--out", out.toString()));
        arguments.addAll(List.of(extra));
        StringWriter screen = new StringWriter();
        try (PrintWriter writer = new PrintWriter(screen, true)) {
            Restest.run(arguments.toArray(new String[0]), writer, writer);
        }
        return screen.toString();
    }
}

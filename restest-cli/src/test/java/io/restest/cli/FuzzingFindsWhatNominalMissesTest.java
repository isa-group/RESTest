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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An API that falls over on an empty search term, and a run that finds out.
 *
 * <p>This is the whole argument for sending values nobody sensible would send, in one test. The API
 * here answers politely to any ordinary word and breaks on an empty one - which is an everyday kind
 * of bug, the sort that comes from a query built by pasting a string into it. Values invented from
 * the document never look like that, because the document says a search term is text and a word is
 * text; only a list of deliberately awkward values goes there.
 *
 * <p>The comparison is the point. The same command, against the same API, with the same number to
 * start from, finds nothing when it has no such list and finds the server error when it has one.
 */
class FuzzingFindsWhatNominalMissesTest {

    private static final String SPECIFICATION = """
            openapi: 3.0.3
            info: {title: Fragile search, version: "1.0"}
            paths:
              /search:
                get:
                  operationId: search
                  parameters:
                    - name: q
                      in: query
                      required: true
                      schema: {type: string}
                  responses:
                    "200": {description: results}
            """;

    private static WireMockServer api;

    @BeforeAll
    static void start() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
        // Anything with at least one character is fine.
        api.stubFor(get(urlPathEqualTo("/search")).withQueryParam("q", matching(".+"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody("[]")));
        // An empty one is not. A real bug of exactly this shape is a query assembled by hand.
        api.stubFor(get(urlPathEqualTo("/search")).withQueryParam("q", matching("^$"))
                .willReturn(aResponse().withStatus(500)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("java.lang.StringIndexOutOfBoundsException")));
    }

    @AfterAll
    static void stop() {
        if (api != null) {
            api.stop();
        }
    }

    @Test
    @DisplayName("a server error only an awkward value reaches is found, and is missed without one")
    void the_awkward_values_are_what_find_it(@TempDir Path directory) throws IOException {
        Path document = Files.writeString(directory.resolve("openapi.yaml"), SPECIFICATION);

        String withAwkwardValues = run(directory.resolve("with"), document, "--fuzzing", "25");
        String withoutThem = run(directory.resolve("without"), document, "--fuzzing", "0");

        assertThat(withAwkwardValues)
                .describedAs("an empty search term is in RESTest's own list of awkward values, and "
                        + "this API breaks on it")
                .contains("answered 500");
        assertThat(withAwkwardValues)
                .describedAs("the summary says how many requests were meant to be refused, so the "
                        + "refusals do not read as the API turning away ordinary traffic")
                .contains("carried values meant to be refused");
        assertThat(withoutThem)
                .describedAs("every value invented from this document is a word, and a word works")
                .doesNotContain("answered 500");
    }

    private static String run(Path out, Path document, String... extra) {
        java.util.List<String> arguments = new java.util.ArrayList<>(java.util.List.of(
                "run", document.toString(),
                "--url", api.baseUrl(),
                "--budget", "3s",
                "--out", out.toString()));
        arguments.addAll(java.util.List.of(extra));
        StringWriter screen = new StringWriter();
        try (PrintWriter writer = new PrintWriter(screen, true)) {
            Restest.run(arguments.toArray(new String[0]), writer, writer);
        }
        return screen.toString();
    }
}

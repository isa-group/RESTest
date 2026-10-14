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
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restest.cli.ARun.SIXTY_REQUESTS;
import static io.restest.cli.ARun.STOP_AFTER_REQUESTS;
import static io.restest.cli.ARun.codesIn;
import static io.restest.cli.ARun.report;
import static io.restest.cli.ARun.settingsIn;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restest.core.json.JsonValue;
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
 * The switches that turn off one check on what an API sends back, seen from the command line.
 *
 * <p>The API is a stand-in with one operation, and every answer it gives is wrong in two ways at
 * once: a 500, whose body is not the shape the document gives a 500. With every check on, a run
 * reports both. What is checked here is that each switch reaches the run, that the other check
 * carries on without it, that what the API did is still counted whichever checks are made, and that
 * the run records which were off.
 */
class CheckSwitchesCommandTest {

    private static final String DOCUMENT = """
            openapi: 3.0.3
            info:
              title: Pets
              version: "1.0"
            paths:
              /pets:
                get:
                  operationId: listPets
                  responses:
                    "500":
                      description: it fell over, and says why
                      content:
                        application/json:
                          schema:
                            type: object
                            required: [message]
                            properties:
                              message:
                                type: string
            """;

    private static WireMockServer api;

    @BeforeAll
    static void startTheApi() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
        api.stubFor(get(urlEqualTo("/pets")).willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "application/json").withBody("{\"code\": 1}")));
    }

    @AfterAll
    static void stopTheApi() {
        api.stop();
    }

    @Test
    @DisplayName("with every check on, a 500 whose body is off its schema is two faults")
    void every_check_on(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        int answer = run(directory, screen);

        JsonValue.JsonObject report = report(directory.resolve("out"));
        assertThat(answer).describedAs("the run said:%n%s", screen).isEqualTo(1);
        assertThat(codesIn(report)).containsExactlyInAnyOrder(100, 200);
    }

    @Test
    @DisplayName("with the check of the reply's shape off, the 500 is still reported, and the run "
            + "records the switch")
    void one_check_off(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        int answer = run(directory, screen, "--set", "checks.replyOffSchema=false");

        JsonValue.JsonObject report = report(directory.resolve("out"));
        assertThat(answer).describedAs("the run said:%n%s", screen).isEqualTo(1);
        assertThat(codesIn(report)).containsExactly(100);
        assertThat(settingsIn(report)).contains(
                "checks.internalServerError true default",
                "checks.replyOffSchema false command line");
    }

    @Test
    @DisplayName("with both off, nothing is reported and the run ends 0, while the 500s are still "
            + "counted as something the API did")
    void every_check_off(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        int answer = run(directory, screen, "--set", "checks.replyOffSchema=false",
                "--set", "checks.internalServerError=false");

        JsonValue.JsonObject report = report(directory.resolve("out"));
        assertThat(answer)
                .describedAs("a run ends 1 for a fault reported, and with every check off none "
                        + "is; the run said:%n%s", screen)
                .isZero();
        assertThat(codesIn(report)).isEmpty();
        JsonValue.JsonObject serverErrors =
                (JsonValue.JsonObject) report.member("serverErrors").orElseThrow();
        assertThat(serverErrors.member("operationsAnswering500"))
                .describedAs("what the API answered is counted whichever checks are made")
                .contains(JsonValue.of(1));
        assertThat(settingsIn(report)).contains(
                "checks.internalServerError false command line",
                "checks.replyOffSchema false command line");
    }

    /** A short run against the stand-in, with these settings, and the code it ended with. */
    private static int run(Path directory, StringWriter screen, String... settings)
            throws IOException {
        Path document = Files.writeString(directory.resolve("openapi.yaml"), DOCUMENT);
        List<String> arguments = new ArrayList<>(List.of("run", document.toString(),
                "--url", api.baseUrl(), "--budget", "30s",
                "--set", STOP_AFTER_REQUESTS, "--set", SIXTY_REQUESTS, "--seed", "1",
                "--out", directory.resolve("out").toString()));
        arguments.addAll(List.of(settings));
        PrintWriter out = new PrintWriter(screen);
        try {
            return Restest.run(arguments.toArray(String[]::new), out, out);
        } finally {
            out.flush();
        }
    }
}

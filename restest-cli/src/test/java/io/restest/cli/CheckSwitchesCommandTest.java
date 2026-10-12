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
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restest.core.json.JsonText;
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
        int answer = run(directory);

        JsonValue.JsonObject report = report(directory);
        assertThat(answer).isEqualTo(1);
        assertThat(codesFound(report)).containsExactlyInAnyOrder(100, 200);
    }

    @Test
    @DisplayName("with the check of the reply's shape off, the 500 is still reported, and the run "
            + "records the switch")
    void one_check_off(@TempDir Path directory) throws IOException {
        int answer = run(directory, "--set", "checks.replyOffSchema=false");

        JsonValue.JsonObject report = report(directory);
        assertThat(answer).isEqualTo(1);
        assertThat(codesFound(report)).containsExactly(100);
        assertThat(setting(report, "checks.replyOffSchema"))
                .containsExactly("false", "command line");
        assertThat(setting(report, "checks.internalServerError"))
                .containsExactly("true", "default");
    }

    @Test
    @DisplayName("with both off, nothing is reported and the run ends 0, while the 500s are still "
            + "counted as something the API did")
    void every_check_off(@TempDir Path directory) throws IOException {
        int answer = run(directory, "--set", "checks.replyOffSchema=false",
                "--set", "checks.internalServerError=false");

        JsonValue.JsonObject report = report(directory);
        assertThat(answer)
                .describedAs("a run ends 1 for a fault reported, and with every check off none is")
                .isZero();
        assertThat(codesFound(report)).isEmpty();
        JsonValue.JsonObject serverErrors =
                (JsonValue.JsonObject) report.member("serverErrors").orElseThrow();
        assertThat(serverErrors.member("operationsAnswering500"))
                .describedAs("what the API answered is counted whichever checks are made")
                .contains(JsonValue.of(1));
        assertThat(setting(report, "checks.internalServerError"))
                .containsExactly("false", "command line");
    }

    /** A short run against the stand-in, with these settings, and the code it ended with. */
    private static int run(Path directory, String... settings) throws IOException {
        Path document = Files.writeString(directory.resolve("openapi.yaml"), DOCUMENT);
        List<String> arguments = new ArrayList<>(List.of("run", document.toString(),
                "--url", api.baseUrl(), "--budget", "2s", "--seed", "1",
                "--out", directory.resolve("out").toString()));
        arguments.addAll(List.of(settings));
        StringWriter screen = new StringWriter();
        PrintWriter out = new PrintWriter(screen);
        try {
            return Restest.run(arguments.toArray(String[]::new), out, out);
        } finally {
            out.flush();
        }
    }

    private static JsonValue.JsonObject report(Path directory) throws IOException {
        return (JsonValue.JsonObject) JsonText.read(
                Files.readString(directory.resolve("out").resolve("report.json")));
    }

    /** The catalogue numbers of every kind of fault the run reported. */
    private static List<Integer> codesFound(JsonValue.JsonObject report) {
        JsonValue.JsonArray kinds =
                (JsonValue.JsonArray) report.member("faultsByCategory").orElseThrow();
        return kinds.elements().stream()
                .map(kind -> ((JsonValue.JsonObject) kind).member("code").orElseThrow())
                .map(code -> ((JsonValue.JsonNumber) code).value().intValueExact())
                .toList();
    }

    /** One setting as the report records it: its value, and where that value came from. */
    private static List<String> setting(JsonValue.JsonObject report, String key) {
        JsonValue.JsonArray rows = (JsonValue.JsonArray) report.member("settings").orElseThrow();
        JsonValue.JsonObject row = rows.elements().stream()
                .map(JsonValue.JsonObject.class::cast)
                .filter(each -> each.member("key").orElseThrow().equals(JsonValue.of(key)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(key + " is not among the run's settings"));
        return List.of(text(row, "value"), text(row, "source"));
    }

    private static String text(JsonValue.JsonObject row, String member) {
        return ((JsonValue.JsonString) row.member(member).orElseThrow()).value();
    }
}

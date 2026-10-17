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
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restest.cli.ARun.SIXTY_REQUESTS;
import static io.restest.cli.ARun.STOP_AFTER_REQUESTS;
import static io.restest.cli.ARun.codesIn;
import static io.restest.cli.ARun.json;
import static io.restest.cli.ARun.report;
import static io.restest.cli.ARun.settingsIn;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restest.core.json.JsonValue;
import io.restest.core.settings.SettingKey;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The switches that turn off one check on what an API sends back, seen from the command line.
 *
 * <p>The API is a stand-in, read through one of two documents. The first names one operation, and
 * every answer it gives is wrong in two ways at once: a 500, whose body is not the shape the document
 * gives a 500. With every check on, a run reports both. The second names three more, and is wrong in
 * every way a check looks for: one answers 503; one takes a required number and answers 200
 * whatever it is sent, so that a request broken on purpose - the number left out, or a word in its
 * place - is accepted; and one answers only after four seconds, long after the second these runs
 * wait for a reply. What is checked here is that each switch reaches the run, that the other checks
 * carry on without it, that what the API did is still counted whichever checks are made, and that
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

    /** The first document's operation, and two more that are wrong in the other ways. */
    private static final String WRONG_IN_EVERY_WAY = DOCUMENT + """
              /shelters:
                get:
                  operationId: listShelters
                  responses:
                    "200":
                      description: every shelter
                    "503":
                      description: closed for the night
              /owners:
                get:
                  operationId: findOwners
                  parameters:
                    - name: limit
                      in: query
                      required: true
                      schema:
                        type: integer
                        minimum: 1
                        maximum: 10
                  responses:
                    "200":
                      description: the owners
                    "400":
                      description: the limit is missing or not a number from 1 to 10
              /kennels:
                get:
                  operationId: listKennels
                  responses:
                    "200":
                      description: every kennel, eventually
            """;

    /** How long these runs wait for a reply, and how long the slow operation takes to give one. */
    private static final String ONE_SECOND_FOR_A_REPLY = "engine.readTimeout=1s";
    private static final int SLOWER_THAN_THAT_MILLIS = 4000;

    private static WireMockServer api;

    @BeforeAll
    static void startTheApi() {
        // Threads enough for the slow operation's replies, each held for seconds after the run
        // stopped waiting for it, not to keep the others from being answered.
        api = new WireMockServer(options().dynamicPort().containerThreads(64));
        api.start();
        api.stubFor(get(urlEqualTo("/pets")).willReturn(json(500, "{\"code\": 1}")));
        // Only the second document names these two.
        api.stubFor(get(urlEqualTo("/shelters")).willReturn(aResponse().withStatus(503)));
        // Accepts whatever it is sent, so a request with the limit broken on purpose is accepted.
        api.stubFor(get(urlPathEqualTo("/owners")).willReturn(aResponse().withStatus(200)));
        api.stubFor(get(urlEqualTo("/kennels")).willReturn(aResponse().withStatus(200)
                .withFixedDelay(SLOWER_THAN_THAT_MILLIS)));
    }

    @AfterAll
    static void stopTheApi() {
        api.stop();
    }

    @Test
    @DisplayName("with every check on, a 500 whose body is off its schema is two faults")
    void every_check_on(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        int answer = run(DOCUMENT, directory, screen);

        JsonValue.JsonObject report = report(directory.resolve("out"));
        assertThat(answer).describedAs("the run said:%n%s", screen).isEqualTo(1);
        assertThat(codesIn(report)).containsExactlyInAnyOrder(100, 200);
    }

    @Test
    @DisplayName("with the check of the reply's shape off, the 500 is still reported, and the run "
            + "records the switch")
    void one_check_off(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        int answer = run(DOCUMENT, directory, screen, "--set", "checks.replyOffSchema=false");

        JsonValue.JsonObject report = report(directory.resolve("out"));
        assertThat(answer).describedAs("the run said:%n%s", screen).isEqualTo(1);
        assertThat(codesIn(report)).containsExactly(100);
        assertThat(settingsIn(report)).contains(
                "checks.internalServerError true default",
                "checks.replyOffSchema false command line");
    }

    @Test
    @DisplayName("with every check off, nothing is reported and the run ends 0, while the 500s are "
            + "still counted as something the API did")
    void every_check_off(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();
        List<String> everyCheck = SettingKey.all().stream()
                .filter(key -> key.group().equals("checks"))
                .map(key -> key.group() + "." + key.name())
                .toList();

        int answer = run(DOCUMENT, directory, screen, everyCheck.stream()
                .flatMap(check -> Stream.of("--set", check + "=false"))
                .toArray(String[]::new));

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
        assertThat(settingsIn(report)).containsAll(everyCheck.stream()
                .map(check -> check + " false command line")
                .toList());
    }

    @Test
    @DisplayName("an API wrong in every way a check looks for is reported in five kinds of fault, "
            + "and only the slow operation as one that never answered")
    void every_kind_of_fault(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        int answer = run(WRONG_IN_EVERY_WAY, directory, screen);

        JsonValue.JsonObject report = report(directory.resolve("out"));
        assertThat(answer).describedAs("the run said:%n%s", screen).isEqualTo(1);
        assertThat(codesIn(report)).containsExactlyInAnyOrder(100, 121, 200, 206, 312);
        assertThat(operationsWith(312, report)).containsExactly("listKennels");
    }

    @Test
    @DisplayName("with the check for a request that got no reply off, the others still report, the "
            + "requests are still counted as unanswered, and the run records the switch")
    void the_check_for_no_reply_off(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        int answer = run(WRONG_IN_EVERY_WAY, directory, screen,
                "--set", "checks.noReplyInTime=false");

        JsonValue.JsonObject report = report(directory.resolve("out"));
        assertThat(answer).describedAs("the run said:%n%s", screen).isEqualTo(1);
        assertThat(codesIn(report)).containsExactlyInAnyOrder(100, 121, 200, 206);
        JsonValue.JsonObject replies = (JsonValue.JsonObject) report.member("replies").orElseThrow();
        JsonValue.JsonObject byClass = (JsonValue.JsonObject) replies.member("byClass").orElseThrow();
        assertThat(byClass.member("noReply"))
                .describedAs("a request that got no reply is counted whichever checks are made")
                .hasValueSatisfying(count -> assertThat(
                        ((JsonValue.JsonNumber) count).value().intValueExact()).isPositive());
        assertThat(settingsIn(report)).contains("checks.noReplyInTime false command line");
    }

    @Test
    @DisplayName("with the checks for another server error and for a broken request accepted off, "
            + "the others still report, the 503 is still counted, and the run records both")
    void the_two_newer_checks_off(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        int answer = run(WRONG_IN_EVERY_WAY, directory, screen,
                "--set", "checks.otherServerError=false",
                "--set", "checks.invalidDataAccepted=false");

        JsonValue.JsonObject report = report(directory.resolve("out"));
        assertThat(answer).describedAs("the run said:%n%s", screen).isEqualTo(1);
        assertThat(codesIn(report)).containsExactlyInAnyOrder(100, 200, 312);
        JsonValue.JsonObject serverErrors =
                (JsonValue.JsonObject) report.member("serverErrors").orElseThrow();
        assertThat(serverErrors.member("operationsAnsweringAny5xx"))
                .describedAs("the 503 is counted as something the API did, whichever checks are "
                        + "made")
                .contains(JsonValue.of(2));
        assertThat(settingsIn(report)).contains(
                "checks.internalServerError true default",
                "checks.otherServerError false command line",
                "checks.replyOffSchema true default",
                "checks.invalidDataAccepted false command line",
                "checks.noReplyInTime true default");
    }

    /** The operations a report counts faults of this kind on. */
    private static List<String> operationsWith(int code, JsonValue.JsonObject report) {
        JsonValue.JsonArray rows =
                (JsonValue.JsonArray) report.member("faultsByOperation").orElseThrow();
        return rows.elements().stream()
                .map(JsonValue.JsonObject.class::cast)
                .filter(row -> ((JsonValue.JsonNumber) row.member("code").orElseThrow()).value()
                        .intValueExact() == code)
                .map(row -> ((JsonValue.JsonString) row.member("operation").orElseThrow()).value())
                .distinct()
                .toList();
    }

    /** A short run against the stand-in read through this document, and the code it ended with. */
    private static int run(String described, Path directory, StringWriter screen,
            String... settings) throws IOException {
        Path document = Files.writeString(directory.resolve("openapi.yaml"), described);
        List<String> arguments = new ArrayList<>(List.of("run", document.toString(),
                "--url", api.baseUrl(), "--budget", "30s",
                "--set", STOP_AFTER_REQUESTS, "--set", SIXTY_REQUESTS, "--seed", "1",
                "--set", ONE_SECOND_FOR_A_REPLY, "--out", directory.resolve("out").toString()));
        arguments.addAll(List.of(settings));
        PrintWriter out = new PrintWriter(screen);
        try {
            return Restest.run(arguments.toArray(String[]::new), out, out);
        } finally {
            out.flush();
        }
    }
}

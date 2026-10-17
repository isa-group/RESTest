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
import static io.restest.cli.ARun.codesIn;
import static io.restest.cli.ARun.json;
import static io.restest.cli.ARun.operationsWith;
import static io.restest.cli.ARun.repliesOfClass;
import static io.restest.cli.ARun.report;
import static io.restest.cli.ARun.settingsIn;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restest.core.json.JsonValue;
import io.restest.core.settings.SettingKey;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The switches that turn off one check on what an API sends back, seen from the command line.
 *
 * <p>The API is a stand-in, read through one of two documents. The first names one operation, and
 * every answer it gives is wrong in two ways at once: a 500, whose body is not the shape the document
 * gives a 500. With every check on, a run reports both. The second names six more, and is wrong in
 * every way a check looks for: one answers 503; one takes a required number and answers 200
 * whatever it is sent, so that a request broken on purpose - the number left out, or a word in its
 * place - is accepted; one answers only after four seconds, long after the second the runs of
 * that document wait for a reply; one answers 201 where the document declares only 200; one sends
 * its body as {@code application/problem+json} where the document declares {@code
 * application/json}; and one answers 501, not implemented. What is checked here is that each switch
 * reaches the run, that the other checks
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
              /vets:
                get:
                  operationId: listVets
                  responses:
                    "200":
                      description: every vet
              /visits:
                get:
                  operationId: listVisits
                  responses:
                    "200":
                      description: every visit
                      content:
                        application/json:
                          schema:
                            type: object
              /clinics:
                get:
                  operationId: listClinics
                  responses:
                    "200":
                      description: every clinic
            """;

    /**
     * How long the runs of the second document wait for a reply, and how long its slow operation
     * takes to give one. Only those runs are given the shorter wait, so that a stand-in slow to
     * answer on a busy machine cannot turn into a fault in the runs that are not about one.
     */
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
        api.stubFor(get(urlEqualTo("/vets")).willReturn(aResponse().withStatus(201)));
        api.stubFor(get(urlEqualTo("/visits")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"title\": \"no visits\"}")));
        api.stubFor(get(urlEqualTo("/clinics")).willReturn(aResponse().withStatus(501)));
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
    @DisplayName("an API wrong in every way a check looks for is reported in eight kinds of fault, "
            + "each on the operation wrong in that way")
    void every_kind_of_fault(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        int answer = run(WRONG_IN_EVERY_WAY, directory, screen, "--set", ONE_SECOND_FOR_A_REPLY);

        JsonValue.JsonObject report = report(directory.resolve("out"));
        assertThat(answer).describedAs("the run said:%n%s", screen).isEqualTo(1);
        assertThat(codesIn(report))
                .containsExactlyInAnyOrder(100, 121, 200, 205, 206, 312, 920, 921);
        assertThat(operationsWith(312, report)).containsExactly("listKennels");
        assertThat(operationsWith(205, report)).containsExactly("listClinics");
        assertThat(operationsWith(920, report)).containsExactly("listVets");
        assertThat(operationsWith(921, report)).containsExactly("listVisits");
        assertThat(screen.toString()).contains(
                " x F205  Schema Violation: no-501-if-implemented",
                " x F920  A Status Code The Document Does Not Declare",
                " x F921  A Content Type The Document Does Not Declare");
    }

    @ParameterizedTest
    @CsvSource({
            "checks.notImplemented, 205",
            "checks.undeclaredStatusCode, 920",
            "checks.undeclaredContentType, 921"})
    @DisplayName("each check on what the document declares, switched off alone, takes its own kind "
            + "off the screen and out of the report, and no other")
    void each_check_of_what_the_document_declares_off_alone(String check, int code,
            @TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        int answer = run(WRONG_IN_EVERY_WAY, directory, screen, "--set", ONE_SECOND_FOR_A_REPLY,
                "--set", check + "=false");

        JsonValue.JsonObject report = report(directory.resolve("out"));
        List<Integer> others = new ArrayList<>(List.of(205, 920, 921));
        others.remove(Integer.valueOf(code));
        assertThat(answer).describedAs("the run said:%n%s", screen).isEqualTo(1);
        assertThat(codesIn(report)).doesNotContain(code).containsAll(others);
        assertThat(screen.toString()).doesNotContain(" x F" + code + " ");
        others.forEach(other -> assertThat(screen.toString()).contains(" x F" + other + " "));
        assertThat(settingsIn(report)).contains(check + " false command line");
    }

    @Test
    @DisplayName("with the check for a request that got no reply off, the others still report, the "
            + "requests are still counted as unanswered, and the run records the switch")
    void the_check_for_no_reply_off(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        int answer = run(WRONG_IN_EVERY_WAY, directory, screen, "--set", ONE_SECOND_FOR_A_REPLY,
                "--set", "checks.noReplyInTime=false");

        JsonValue.JsonObject report = report(directory.resolve("out"));
        assertThat(answer).describedAs("the run said:%n%s", screen).isEqualTo(1);
        assertThat(codesIn(report)).containsExactlyInAnyOrder(100, 121, 200, 205, 206, 920, 921);
        assertThat(repliesOfClass(report, "noReply"))
                .describedAs("a request that got no reply is counted whichever checks are made")
                .isPositive();
        assertThat(settingsIn(report)).contains("checks.noReplyInTime false command line");
    }

    @Test
    @DisplayName("with the checks for another server error and for a broken request accepted off, "
            + "the others still report, the 503 is still counted, and the run records both")
    void the_two_newer_checks_off(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        int answer = run(WRONG_IN_EVERY_WAY, directory, screen, "--set", ONE_SECOND_FOR_A_REPLY,
                "--set", "checks.otherServerError=false",
                "--set", "checks.invalidDataAccepted=false");

        JsonValue.JsonObject report = report(directory.resolve("out"));
        assertThat(answer).describedAs("the run said:%n%s", screen).isEqualTo(1);
        assertThat(codesIn(report)).containsExactlyInAnyOrder(100, 200, 205, 312, 920, 921);
        JsonValue.JsonObject serverErrors =
                (JsonValue.JsonObject) report.member("serverErrors").orElseThrow();
        assertThat(serverErrors.member("operationsAnsweringAny5xx"))
                .describedAs("the 503 is counted as something the API did, whichever checks are "
                        + "made")
                .contains(JsonValue.of(3));
        assertThat(settingsIn(report)).contains(
                "checks.internalServerError true default",
                "checks.otherServerError false command line",
                "checks.replyOffSchema true default",
                "checks.invalidDataAccepted false command line",
                "checks.noReplyInTime true default");
    }

    @Test
    @DisplayName("with the checks for a 501, an undeclared status code and an undeclared content "
            + "type off, the others still report, the 501 is still counted, and the run records "
            + "all three")
    void the_three_checks_of_what_the_document_declares_off(@TempDir Path directory)
            throws IOException {
        StringWriter screen = new StringWriter();

        int answer = run(WRONG_IN_EVERY_WAY, directory, screen, "--set", ONE_SECOND_FOR_A_REPLY,
                "--set", "checks.notImplemented=false",
                "--set", "checks.undeclaredStatusCode=false",
                "--set", "checks.undeclaredContentType=false");

        JsonValue.JsonObject report = report(directory.resolve("out"));
        assertThat(answer).describedAs("the run said:%n%s", screen).isEqualTo(1);
        assertThat(codesIn(report)).containsExactlyInAnyOrder(100, 121, 200, 206, 312);
        JsonValue.JsonObject serverErrors =
                (JsonValue.JsonObject) report.member("serverErrors").orElseThrow();
        assertThat(serverErrors.member("operationsAnsweringAny5xx"))
                .describedAs("the 501 is counted as something the API did, whichever checks are "
                        + "made")
                .contains(JsonValue.of(3));
        assertThat(settingsIn(report)).contains(
                "checks.notImplemented false command line",
                "checks.undeclaredStatusCode false command line",
                "checks.undeclaredContentType false command line",
                "checks.replyOffSchema true default");
    }

    /** A short run against the stand-in read through this document, and the code it ended with. */
    private static int run(String described, Path directory, StringWriter screen,
            String... settings) throws IOException {
        return ARun.run(described, api.baseUrl(), directory, screen, settings);
    }
}

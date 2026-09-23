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
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.restest.core.execution.Interaction;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.store.InteractionQuery;
import io.restest.core.store.InteractionStore;
import io.restest.store.SqliteInteractionStore;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How a run begins, seen from the command line: the first round, what it says about itself, and
 * the switch that turns it off.
 *
 * <p>The API is a stand-in that answers every operation well, so that what is checked is the order
 * requests went out in and what the run said about it, not what it found.
 */
class OpeningLapCommandTest {

    private static WireMockServer api;

    @BeforeAll
    static void startTheApi() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
        api.stubFor(get(urlMatching("/pets")).willReturn(json(200, "[]")));
        api.stubFor(post(urlMatching("/pets")).willReturn(json(201,
                "{\"id\": 7, \"name\": \"Rex\"}")));
        api.stubFor(get(urlMatching("/pets/[0-9]+")).willReturn(json(200,
                "{\"id\": 7, \"name\": \"Rex\"}")));
        api.stubFor(get(urlMatching("/shelters")).willReturn(json(200, "{\"total\": 3}")));
        api.stubFor(get(urlEqualTo("/ping")).willReturn(aResponse().withStatus(204)));
    }

    @AfterAll
    static void stopTheApi() {
        api.stop();
    }

    @Test
    @DisplayName("a run begins with every operation once - the lists, then what creates, then what "
            + "reads one thing - and says what that round bought")
    void a_run_begins_with_every_operation_once(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        // Five seconds, not two: the budget starts with the command, before the document is read,
        // and a cold machine must still see the whole round through before the time is up.
        run(screen, "run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "5s",
                "--seed", "20260923", "--out", directory.toString(), "--store");

        // In the order they went out. Within a step the requests are sent together, so the two
        // lists may go either way round; between steps the order is the point.
        List<String> sent = firstOperationsSent(directory, 4);
        assertThat(sent.subList(0, 2)).containsExactlyInAnyOrder("listPets", "listShelters");
        assertThat(sent.subList(2, 4)).containsExactly("addPet", "getPet");
        // Line by line, since a line ends differently on Windows.
        assertThat(screen.toString().lines())
                .describedAs("the summary says what the round took and what it covered")
                .anyMatch(line -> line.matches("  opening lap: 4 requests in [0-9.]+m?s, 4 of 4 "
                        + "operations answered 2xx"));
        JsonValue.JsonObject lap = (JsonValue.JsonObject) phases(directory).elements().get(0);
        assertThat(lap.member("name")).contains(JsonValue.of("opening lap"));
        assertThat(lap.member("requests")).contains(JsonValue.of(4));
        assertThat(lap.member("operationsAnswering2xx")).contains(JsonValue.of(4));
        assertThat(lap.member("cutShort")).contains(JsonValue.of(false));
    }

    @Test
    @DisplayName("switched off, a run goes round in the order the document declares from the start")
    void switched_off_there_is_no_first_round(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        run(screen, "run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "2s",
                "--seed", "20260923", "--out", directory.toString(), "--store",
                "--set", "schedule.openingLap=false");

        // The order it goes round in is pinned where it is decided, request by request; what the
        // command line owes is that the switch reaches the run and the run says nothing of a round.
        assertThat(screen.toString()).doesNotContain("opening lap");
        assertThat(phases(directory).elements()).isEmpty();
        assertThat(firstOperationsSent(directory, 1)).isNotEmpty();
    }

    @Test
    @DisplayName("an operation that says nothing about what it returns is asked for anything")
    void nothing_declared_means_anything_accepted(@TempDir Path directory) throws IOException {
        Path document = Files.writeString(directory.resolve("openapi.yaml"), """
                openapi: 3.0.3
                info:
                  title: Ping
                  version: "1.0"
                paths:
                  /ping:
                    get:
                      operationId: ping
                      responses:
                        "204":
                          description: nothing to say
                """);

        run(new StringWriter(), "run", document.toString(), "--url", api.baseUrl(),
                "--budget", "2s", "--seed", "1", "--out", directory.resolve("out").toString());

        List<LoggedRequest> pinged = api.findAll(getRequestedFor(urlEqualTo("/ping")));
        assertThat(pinged).isNotEmpty();
        assertThat(pinged).allSatisfy(request ->
                assertThat(request.getHeader("Accept")).isEqualTo("*/*"));
    }

    /** The operations of the first requests the run sent, in the order the engine sent them. */
    private static List<String> firstOperationsSent(Path directory, int howMany) {
        try (InteractionStore store = SqliteInteractionStore.at(directory.resolve("run.sqlite"))) {
            return store.find(InteractionQuery.all()).stream()
                    .limit(howMany)
                    .map(Interaction::testCase)
                    .map(testCase -> testCase.operation().value())
                    .toList();
        }
    }

    private static JsonValue.JsonArray phases(Path directory) throws IOException {
        JsonValue.JsonObject report = (JsonValue.JsonObject)
                JsonText.read(Files.readString(directory.resolve("report.json")));
        return (JsonValue.JsonArray) report.member("phases").orElseThrow();
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(
            int status, String body) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/json")
                .withBody(body);
    }

    private static int run(StringWriter screen, String... arguments) {
        PrintWriter out = new PrintWriter(screen);
        PrintWriter err = new PrintWriter(screen);
        try {
            return Restest.run(arguments, out, err);
        } finally {
            out.flush();
            err.flush();
        }
    }
}

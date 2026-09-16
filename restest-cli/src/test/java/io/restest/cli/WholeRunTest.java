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
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.store.InteractionQuery;
import io.restest.core.store.InteractionStore;
import io.restest.store.SqliteInteractionStore;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole tool, from the command line, against an API that misbehaves on purpose.
 *
 * <p>Every other test in this project checks one part on its own. This one runs the command a person
 * would run and checks what they would see: a description is read, requests are invented from it,
 * they are sent to a real HTTP server, what comes back is judged, and what was judged wrong is
 * printed, stored, and turned into a command anybody can paste into a terminal.
 *
 * <p>The API is a stand-in that answers exactly as this test tells it to, so there is no network, no
 * waiting, and no chance of the test failing because somebody's real server had a bad day. It is
 * wrong in two different ways on purpose - one operation falls over, one lies about the shape of
 * what it returns - and a third operation is perfectly well behaved, so that "found two kinds of
 * fault" is not the same sentence as "complained about everything".
 */
class WholeRunTest {

    private static WireMockServer api;

    @BeforeAll
    static void startTheApi() {
        api = new WireMockServer(options().dynamicPort());
        api.start();

        // Falls over. Nobody can defend this one.
        api.stubFor(get(urlMatching("/pets")).willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\": \"could not reach the database\"}")));

        // Answers, but not with what it said it would: the document promises a whole number.
        api.stubFor(get(urlMatching("/pets/[0-9]+")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\": \"seven\", \"name\": \"Rex\"}")));

        // Behaves perfectly.
        api.stubFor(get(urlMatching("/shelters")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"total\": 3}")));
    }

    @AfterAll
    static void stopTheApi() {
        api.stop();
    }

    @Test
    @DisplayName("one command reads a document, tests an API, and reports what is wrong")
    void one_command_finds_and_reports_what_is_wrong(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        // --store because this is the test that proves the stored run and the report agree, and a
        // run is not asked to keep anything unless it is told to.
        int answer = run(screen, "run", "pet-shelter.yaml", "--url", api.baseUrl(),
                "--budget", "1s", "--seed", "20260913", "--out", directory.toString(), "--store");

        assertThat(answer).describedAs("faults were found, so the answer is 1").isEqualTo(1);

        String printed = screen.toString();
        assertThat(printed)
                .describedAs("the API that fell over is reported by its catalogue number")
                .contains("F100")
                .contains("HTTP Status 500")
                .contains("listPets - GET " + api.baseUrl() + "/pets");
        assertThat(printed)
                .describedAs("the API that lied about its own shape is reported too")
                .contains("F200")
                .contains("getPet - GET " + api.baseUrl() + "/pets/")
                .contains("/id: string found, integer expected");
        assertThat(printed)
                .describedAs("the operation that behaved is not complained about")
                .doesNotContain("listShelters");

        JsonValue.JsonObject report = (JsonValue.JsonObject)
                JsonText.read(Files.readString(directory.resolve("report.json")));
        assertThat(codesIn(report))
                .describedAs("two kinds of fault, and only the two that were planted")
                .containsExactlyInAnyOrder(100, 200);
        assertThat(totalIn(report, "operations"))
                .describedAs("all three operations were exercised, including the healthy one")
                .isEqualTo(3);
        assertThat(stored(directory.resolve("run.sqlite")))
                .describedAs("every attempt is kept, faulty or not")
                .isEqualTo(totalIn(report, "requests"));
    }

    @Test
    @DisplayName("the curl command printed beside a fault really does reproduce it")
    void the_printed_command_reproduces_the_fault(@TempDir Path directory) throws Exception {
        Assumptions.assumeTrue(curlIsInstalled(), "curl is not installed on this machine");
        StringWriter screen = new StringWriter();

        // Two seconds rather than a fraction of one: reading the document and warming the parser
        // up costs about half a second, and that comes out of the budget, so a budget of a few
        // hundred milliseconds can run out before a single request has gone anywhere.
        run(screen, "run", "pet-shelter.yaml", "--url", api.baseUrl(),
                "--budget", "2s", "--seed", "1", "--out", directory.toString());

        String command = screen.toString().lines()
                .map(String::trim)
                .filter(line -> line.startsWith("curl ")
                        && line.contains("'" + api.baseUrl() + "/pets'"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no command was printed beside the fault"));

        Path script = directory.resolve("reproduce.sh");
        Files.writeString(script, command + System.lineSeparator());
        Process shell = new ProcessBuilder("sh", script.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(shell.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(shell.waitFor(30, TimeUnit.SECONDS)).describedAs("the command finished").isTrue();

        assertThat(output)
                .describedAs("running what was printed produces the same failure: %s", command)
                .contains("500");
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

    private static List<Integer> codesIn(JsonValue.JsonObject report) {
        JsonValue.JsonArray categories =
                (JsonValue.JsonArray) report.member("faultsByCategory").orElseThrow();
        return categories.elements().stream()
                .map(JsonValue.JsonObject.class::cast)
                .map(category -> (JsonValue.JsonNumber) category.member("code").orElseThrow())
                .map(code -> code.value().intValueExact())
                .distinct()
                .toList();
    }

    private static long totalIn(JsonValue.JsonObject report, String what) {
        JsonValue.JsonObject totals = (JsonValue.JsonObject) report.member("totals").orElseThrow();
        return ((JsonValue.JsonNumber) totals.member(what).orElseThrow()).value().longValueExact();
    }

    private static long stored(Path runFile) {
        try (InteractionStore store = SqliteInteractionStore.at(runFile)) {
            return store.count(InteractionQuery.all());
        }
    }

    private static boolean curlIsInstalled() {
        try {
            return new ProcessBuilder("curl", "--version")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(30, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException notThere) {
            return false;
        }
    }
}

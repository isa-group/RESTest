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
 * What {@code restest} answers, and what it says, for each way a command can go.
 *
 * <p>The number the command leaves behind is what a build server and a script act on, usually
 * without reading anything else, so each of them is pinned here: nothing wrong, something wrong,
 * nonsense on the command line, nothing to test. The API is a stand-in that answers exactly as this
 * test tells it to, so no network is involved and nothing depends on somebody else's server.
 */
class RestestTest {

    private static WireMockServer api;

    private final StringWriter screen = new StringWriter();
    private final StringWriter problems = new StringWriter();

    @BeforeAll
    static void startTheApi() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
        api.stubFor(get(urlMatching("/pets")).willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\": \"could not reach the database\"}")));
        api.stubFor(get(urlMatching("/pets/[0-9]+")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\": 7, \"name\": \"Rex\"}")));
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
    @DisplayName("a run that finds something wrong says so, and answers 1")
    void faults_found_answer_one(@TempDir Path directory) throws Exception {
        int answer = run("run", "pet-shelter.yaml", "--url", api.baseUrl(),
                "--budget", "1s", "--seed", "20260914", "--out", directory.toString());

        assertThat(answer).isEqualTo(1);
        assertThat(screen.toString())
                .contains("RESTest testing Pet Shelter at " + api.baseUrl())
                .contains("3 of 3 operations can be tested, seed 20260914, budget 1s")
                .contains("F100")
                .contains("listPets - GET " + api.baseUrl() + "/pets")
                .containsPattern("\\d+ requests to \\d+ operations in .+, \\d+% of it idle");
        assertThat(directory.resolve("report.json")).exists();
        assertThat(directory.resolve("run.sqlite")).exists();
        assertThat(screen.toString())
                .contains("report written to " + directory.resolve("report.json"))
                .contains("run stored in " + directory.resolve("run.sqlite"));
    }

    @Test
    @DisplayName("a run that finds nothing wrong says so, and answers 0")
    void a_clean_run_answers_zero(@TempDir Path directory) throws Exception {
        int answer = run("run", "pet-shelter.yaml", "--url", api.baseUrl() + "/shelters-only",
                "--budget", "1s", "--seed", "1", "--out", directory.toString());

        // Nothing is stubbed under that prefix, so every reply is a 404 - which no oracle in this
        // version complains about, because a document that does not describe 404 has not been
        // contradicted by one.
        assertThat(answer).isZero();
        assertThat(screen.toString()).contains("no faults found");
    }

    @Test
    @DisplayName("running with nothing to run shows what the choices are, and answers 2")
    void no_command_answers_two() {
        assertThat(run()).isEqualTo(2);
        assertThat(problems.toString()).contains("Usage: restest").contains("run");
    }

    @Test
    @DisplayName("a mistyped option answers 2 rather than pretending to have run")
    void a_bad_option_answers_two() {
        assertThat(run("run", "pet-shelter.yaml", "--nonsense")).isEqualTo(2);
        assertThat(problems.toString()).contains("--nonsense");
    }

    @Test
    @DisplayName("a budget that is not a length of time answers 2, and says how to write one")
    void a_bad_budget_answers_two() {
        assertThat(run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "soon"))
                .isEqualTo(2);
        assertThat(problems.toString()).contains("30s");
    }

    @Test
    @DisplayName("a document nothing can be read from answers 3, not 0")
    void an_unreadable_document_answers_three(@TempDir Path directory) throws Exception {
        Path nothingThere = directory.resolve("not-a-document.yaml");
        Files.writeString(nothingThere, "this file is not an API description\n");

        assertThat(run("run", nothingThere.toString(), "--url", api.baseUrl(), "--budget", "1s"))
                .isEqualTo(3);
        assertThat(problems.toString()).contains("no operation that could be tested");
    }

    @Test
    @DisplayName("a document with nowhere to send requests asks for --url, and answers 3")
    void nowhere_to_send_answers_three() {
        assertThat(run("run", "pet-shelter.yaml", "--budget", "1s")).isEqualTo(3);
        assertThat(problems.toString())
                .contains("there is nowhere to send the requests")
                .contains("--url");
    }

    @Test
    @DisplayName("an address nothing is listening at answers 3 rather than 'no faults found'")
    void nothing_listening_answers_three(@TempDir Path directory) throws Exception {
        int answer = run("run", "pet-shelter.yaml", "--url", "http://127.0.0.1:1",
                "--budget", "20s", "--seed", "3", "--out", directory.toString());

        assertThat(answer)
                .describedAs("an empty report that exits successfully would read as 'this API is "
                        + "fine', which is the one thing this run has no evidence for")
                .isEqualTo(3);
        assertThat(problems.toString()).contains("answered any of the").contains("Check the address");
    }

    @Test
    @DisplayName("a budget too short to send anything says so, rather than 'no faults found'")
    void a_budget_that_buys_nothing_answers_three(@TempDir Path directory) throws Exception {
        int answer = run("run", "pet-shelter.yaml", "--url", api.baseUrl(),
                "--budget", "1ms", "--out", directory.toString());

        assertThat(answer).isEqualTo(3);
        assertThat(problems.toString())
                .describedAs("and explains where the time went, because it is not obvious")
                .contains("ran out before a single request could be sent")
                .contains("paid out of the budget");
    }

    @Test
    @DisplayName("the same seed asks the API the same questions")
    void the_same_seed_repeats_the_run(@TempDir Path directory) throws Exception {
        StringWriter first = new StringWriter();
        StringWriter second = new StringWriter();
        String[] arguments = {"run", "pet-shelter.yaml", "--url", api.baseUrl(),
            "--budget", "500ms", "--seed", "424242", "--out", directory.toString()};

        Restest.run(arguments, new PrintWriter(first), new PrintWriter(problems));
        Restest.run(arguments, new PrintWriter(second), new PrintWriter(problems));

        assertThat(addressesIn(second.toString()))
                .describedAs("the addresses asked for are decided by the seed, not by the clock")
                .containsAll(addressesIn(first.toString()).stream().limit(3).toList());
    }

    @Test
    @DisplayName("a second run into the same place replaces the first rather than adding to it")
    void a_run_replaces_whatever_was_there(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("report.json"), "left over from something else");

        run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "500ms",
                "--seed", "7", "--out", directory.toString());

        assertThat(Files.readString(directory.resolve("report.json")))
                .doesNotContain("left over")
                .contains("\"tool\"");
    }

    @Test
    @DisplayName("the tool can say which version of itself it is")
    void it_names_its_own_version() {
        assertThat(run("--version")).isZero();
        assertThat(screen.toString()).contains("RESTest ");
    }

    @Test
    @DisplayName("the help names the three things a run needs")
    void the_help_names_what_a_run_needs() {
        assertThat(run("run", "--help")).isZero();
        assertThat(screen.toString())
                .contains("<specification>")
                .contains("--url")
                .contains("--budget")
                .contains("60s");
    }

    private int run(String... arguments) {
        PrintWriter out = new PrintWriter(screen);
        PrintWriter err = new PrintWriter(problems);
        try {
            return Restest.run(arguments, out, err);
        } finally {
            out.flush();
            err.flush();
        }
    }

    /** The addresses the printed {@code curl} commands were aimed at, in the order they appeared. */
    private static java.util.List<String> addressesIn(String printed) {
        return printed.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("curl "))
                .toList();
    }
}

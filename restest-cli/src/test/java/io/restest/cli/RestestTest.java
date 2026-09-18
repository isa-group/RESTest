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
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
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
                .contains("4 of 4 operations can be tested, seed 20260914, budget 1s")
                .contains("F100")
                .contains("listPets - GET " + api.baseUrl() + "/pets")
                .containsPattern("\\d+ requests to \\d+ operations in .+, \\d+% of it idle");
        assertThat(directory.resolve("report.json")).exists();
        assertThat(screen.toString())
                .contains("report written to " + directory.resolve("report.json"));
    }

    @Test
    @DisplayName("an API served from under a directory is tested there, not at the top of its server")
    void the_directory_the_document_declares_is_where_the_requests_go(@TempDir Path directory) {
        // Only under the directory. Anything asked for anywhere else on this server is answered the
        // way a real server answers a path it has never heard of, so a run that lost the directory
        // produces exactly what the benchmark produced before this was fixed: everything refused.
        api.stubFor(get(urlMatching("/shelter/api/pets")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[{\"id\": 7, \"name\": \"Rex\"}]")));

        int answer = run("run", "pet-shelter-under-a-directory.yaml", "--url", api.baseUrl(),
                "--budget", "1s", "--seed", "20260918", "--out", directory.toString());

        assertThat(answer)
                .describedAs("nothing here is broken - though this says only that the run finished, "
                        + "not that it arrived anywhere; the two assertions below say that")
                .isZero();
        assertThat(screen.toString())
                .describedAs("the address it settled on is the first thing a run says, so losing "
                        + "the directory is visible before any request goes out")
                .contains("RESTest testing Pet Shelter at " + api.baseUrl() + "/shelter/api");
        api.verify(moreThanOrExactly(1), getRequestedFor(urlMatching("/shelter/api/pets")));
    }

    @Test
    @DisplayName("a run keeps nothing unless asked to, and says so rather than leaving you to look")
    void a_run_keeps_nothing_unless_asked(@TempDir Path directory) {
        run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                "--seed", "20260914", "--out", directory.toString());

        assertThat(directory.resolve("run.sqlite"))
                .describedAs("a minute against a fast API keeps hundreds of megabytes, and almost "
                        + "nothing ever reads them back, so it is not the default")
                .doesNotExist();
        assertThat(screen.toString())
                .describedAs("whoever wanted the evidence should find out in the run that did not "
                        + "keep it, not the next day")
                .contains("the run itself was not kept")
                .contains("--store");
    }

    @Test
    @DisplayName("a run asked to keep itself does, and says where and how big")
    void a_run_asked_to_keep_itself_does(@TempDir Path directory) {
        run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                "--seed", "20260914", "--out", directory.toString(), "--store");

        assertThat(directory.resolve("run.sqlite")).exists();
        assertThat(screen.toString())
                .contains("run stored in " + directory.resolve("run.sqlite"))
                .describedAs("the size is beside the name: a tool that writes hundreds of megabytes "
                        + "owes that number to whoever ran it, when it writes them")
                .containsPattern("run stored in .+ \\(\\d+(\\.\\d+)? (bytes|KiB|MiB|GiB)\\)");
    }

    @Test
    @DisplayName("a second run replaces a kept run rather than leaving two runs in one directory")
    void a_second_run_replaces_a_kept_run(@TempDir Path directory) {
        run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                "--seed", "1", "--out", directory.toString(), "--store");
        assertThat(directory.resolve("run.sqlite")).exists();

        StringWriter second = new StringWriter();
        PrintWriter out = new PrintWriter(second);
        Restest.run(new String[] {"run", "pet-shelter.yaml", "--url", api.baseUrl(),
            "--budget", "1s", "--seed", "2", "--out", directory.toString()},
                out, new PrintWriter(problems));
        out.flush();

        assertThat(directory.resolve("run.sqlite"))
                .describedAs("a directory holds one run. Keeping one means giving it a directory "
                        + "of its own, and the run that removes it says so")
                .doesNotExist();
        assertThat(second.toString()).contains("was replaced");
    }

    @Test
    @DisplayName("a run clears up after itself rather than after whoever owns the directory")
    void a_run_leaves_other_files_alone(@TempDir Path directory) throws Exception {
        Path somebodysWork = directory.resolve("notes.txt");
        Files.writeString(somebodysWork, "not ours");

        run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                "--seed", "20260914", "--out", directory.toString());

        assertThat(somebodysWork)
                .describedAs("--out is whatever somebody typed, and may be a directory full of "
                        + "their own work: only the files this tool writes are ever removed")
                .exists();
        assertThat(Files.readString(somebodysWork)).isEqualTo("not ours");
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
    @DisplayName("what the tool prints is the same text on every machine, colour codes included")
    void nothing_printed_is_dressed_up_for_a_terminal() {
        // The command-line framework decides for itself whether the terminal can take colour, and
        // it decides differently on different operating systems - which is how this came up: on
        // Windows the word "restest" in the usage text arrived wrapped in invisible characters, so
        // the same run printed something else there than here. Asking for colour as loudly as
        // possible and getting none back is what stops that coming back.
        String asked = System.getProperty("picocli.ansi");
        System.setProperty("picocli.ansi", "true");
        try {
            run();
            run("run", "--help");
        } finally {
            if (asked == null) {
                System.clearProperty("picocli.ansi");
            } else {
                System.setProperty("picocli.ansi", asked);
            }
        }

        assertThat(screen.toString() + problems.toString())
                .describedAs("a transcript pasted into a bug report, or compared with last week's, "
                        + "must not depend on which machine produced it")
                .doesNotContain("\u001B[");
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
    @DisplayName("a directory nothing can be written to answers 3, not 4, and says which directory")
    void an_unwritable_output_directory_answers_three(@TempDir Path parent) throws Exception {
        Path directory = Files.createDirectory(parent.resolve("read-only"));
        // Asked for, not insisted upon. Whether this can be done at all depends on the machine:
        // Windows keeps a read-only flag that means nothing for a directory and refuses the request
        // outright, and a build running as root may write anywhere whatever the permissions say.
        // What matters is the state that follows, not whether the request was granted, so the
        // answer is thrown away and the state is asked about instead. Insisting here was this
        // test's own bug: it failed on Windows before reaching the line that would have skipped it.
        directory.toFile().setWritable(false, false);
        Assumptions.assumeFalse(Files.isWritable(directory),
                "this machine lets the current user write into a directory marked unwritable");

        try {
            int answer = run("run", "pet-shelter.yaml", "--url", api.baseUrl(),
                    "--budget", "1s", "--out", directory.toString());

            assertThat(answer)
                    .describedAs("nowhere to write the results is one of the ways a run cannot "
                            + "start, not RESTest breaking; answering 4 would send whoever reads it "
                            + "looking for a bug in the tool")
                    .isEqualTo(3);
            assertThat(problems.toString())
                    .contains(directory.toString())
                    .contains("--out");
        } finally {
            // Best effort, for the same reason, so that the temporary directory can be removed.
            directory.toFile().setWritable(true, true);
        }
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
    @DisplayName("the seed a run used is the one it was given, and is printed either way")
    void the_seed_is_passed_on_and_reported(@TempDir Path directory) throws Exception {
        // What the command is responsible for is handing the seed to the part that invents values
        // and saying which one it used; that the same seed then produces the same values is that
        // part's own promise, and its own test. Note what is deliberately NOT claimed here: with
        // requests overlapping, the order answers come back in is not fixed by the seed, so two
        // runs print the same questions but not necessarily in the same order.
        run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "2s",
                "--seed", "424242", "--out", directory.toString());

        assertThat(screen.toString()).contains("seed 424242");

        StringWriter unseeded = new StringWriter();
        PrintWriter out = new PrintWriter(unseeded);
        Restest.run(new String[] {"run", "pet-shelter.yaml", "--url", api.baseUrl(),
            "--budget", "2s", "--out", directory.toString()}, out, new PrintWriter(problems));
        out.flush();

        assertThat(unseeded.toString())
                .describedAs("a run nobody gave a seed still says which one it chose, or the run "
                        + "cannot be repeated")
                .containsPattern("seed -?\\d+,")
                .doesNotContain("seed 424242");
    }

    @Test
    @DisplayName("a second run into the same place replaces the first rather than adding to it")
    void a_run_replaces_whatever_was_there(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("report.json"), "left over from something else");

        run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "2s",
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

    @Test
    @DisplayName("the help says plainly that a run writes to the API it is pointed at")
    void the_help_says_that_a_run_writes() {
        assertThat(run("run", "--help")).isZero();
        assertThat(screen.toString())
                .describedAs("an operation that creates something is tested by creating something, "
                        + "and whoever runs this should know before they run it")
                .contains("willing to have written to");
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
}

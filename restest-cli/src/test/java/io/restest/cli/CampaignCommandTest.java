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
import static com.github.tomakehurst.wiremock.client.WireMock.any;
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
 * Telling a run where its values should come from, from the command line.
 *
 * <p>Two options and one refusal. {@code --print-campaign} writes out the plan RESTest follows when
 * it is given none, so that somebody can save it and change a line; {@code --campaign} hands one
 * back. The refusal matters most: a plan that cannot be read ends the run rather than quietly
 * running a different one, because one of the things a plan is for is keeping a run away from
 * everything that writes.
 */
class CampaignCommandTest {

    private static WireMockServer api;

    private final StringWriter screen = new StringWriter();
    private final StringWriter problems = new StringWriter();

    @BeforeAll
    static void startTheApi() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
        api.stubFor(any(urlMatching(".*")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")));
    }

    @AfterAll
    static void stopTheApi() {
        api.stop();
    }

    @Test
    @DisplayName("the plan RESTest follows can be printed, and printing it needs no document, "
            + "because it is a question about the tool rather than about an API")
    void the_carried_plan_can_be_printed() {
        assertThat(run("run", "--print-campaign")).isZero();

        assertThat(screen.toString())
                .contains("strategies:")
                .contains("name: nominal")
                .contains("name: fuzzing")
                .describedAs("printed with the comments that say why, since it is meant to be "
                        + "copied and changed rather than only parsed")
                .contains("# What a run does when nobody has said otherwise.");
    }

    @Test
    @DisplayName("what is printed is a plan the tool will read back")
    void what_is_printed_can_be_handed_back(@TempDir Path directory) throws Exception {
        run("run", "--print-campaign");
        Path saved = directory.resolve("mine.yaml");
        Files.writeString(saved, screen.toString());

        assertThat(run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                "--campaign", saved.toString(), "--out", directory.resolve("out").toString()))
                .isBetween(0, 1);
        assertThat(problems.toString()).doesNotContain("could not be read");
    }

    @Test
    @DisplayName("a plan keeps the run to the operations it names, and the run says how many it "
            + "left alone rather than blaming the document for them")
    void a_plan_narrows_which_operations_are_touched(@TempDir Path directory) throws Exception {
        Path plan = planIn(directory, """
                operations:
                  methods: [GET, HEAD, OPTIONS, TRACE]
                """);

        run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                "--campaign", plan.toString(), "--out", directory.resolve("out").toString());

        assertThat(screen.toString())
                .describedAs("the document has four operations and one of them is a POST")
                .contains("3 of 4 operations can be tested")
                .contains("1 left alone by the plan");
    }

    @Test
    @DisplayName("a plan that matches no operation says it was the plan, because a run that "
            + "blamed the document would send somebody to look in the wrong place")
    void a_plan_that_matches_nothing_says_so(@TempDir Path directory) throws Exception {
        Path plan = planIn(directory, """
                operations:
                  only: [thereIsNoSuchOperation]
                """);

        assertThat(run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                "--campaign", plan.toString(), "--out", directory.resolve("out").toString()))
                .isEqualTo(3);
        assertThat(problems.toString())
                .contains("the plan keeps this run to no operation at all")
                .contains("describes 4 that could have been tested")
                .describedAs("and the name nothing answered to is quoted back, since a plan "
                        + "written against an older document is the usual reason")
                .contains("thereIsNoSuchOperation");
    }

    @Test
    @DisplayName("a plan that cannot be read ends the run: following a different one would "
            + "answer 'only read' by writing to the API")
    void a_plan_that_cannot_be_read_ends_the_run(@TempDir Path directory) throws Exception {
        Path plan = directory.resolve("plan.yaml");
        Files.writeString(plan, """
                version: 1
                strategies:
                  - name: nominal
                    share: 100
                    sourses:
                      - source: random
                operations:
                  methods: [GET]
                """);

        assertThat(run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                "--campaign", plan.toString(), "--out", directory.resolve("out").toString()))
                .isEqualTo(2);
        assertThat(problems.toString()).contains("does not recognise (sourses)");
        assertThat(screen.toString())
                .describedAs("nothing was sent, so nothing was written to")
                .doesNotContain("operations can be tested");
    }

    @Test
    @DisplayName("nor does a plan named that is not there, or one with a mistake inside a group")
    void the_other_two_ways_a_plan_is_refused(@TempDir Path directory) throws Exception {
        assertThat(run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                "--campaign", directory.resolve("nope.yaml").toString(),
                "--out", directory.resolve("out").toString()))
                .isEqualTo(2);
        assertThat(problems.toString()).contains("there is no such file");

        Path zero = planIn(directory, "");
        Files.writeString(zero, """
                version: 1
                strategies:
                  - name: nominal
                    share: 100
                    sources:
                      - weighted:
                          - source: random
                            weight: 100
                          - source: example
                            weight: 0
                """);
        assertThat(run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                "--campaign", zero.toString(), "--out", directory.resolve("out2").toString()))
                .describedAs("a person's typo answers 2, not the 4 that means RESTest broke")
                .isEqualTo(2);
        assertThat(problems.toString()).contains("would never be chosen");
    }

    @Test
    @DisplayName("naming both a plan and a share of pushing is refused, since the plan already "
            + "sets the share of every strategy it has")
    void a_plan_and_a_share_of_pushing_cannot_both_be_named(@TempDir Path directory)
            throws Exception {
        Path plan = planIn(directory, "");

        assertThat(run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                "--campaign", plan.toString(), "--fuzzing", "10",
                "--out", directory.resolve("out").toString()))
                .isEqualTo(2);
        assertThat(problems.toString())
                .contains("--fuzzing sets how much of a run pushes at the API")
                .contains("Name one or the other");
    }

    @Test
    @DisplayName("a plan's own shares decide the run: leaving --fuzzing alone does not quietly "
            + "put them back to what this command defaults to")
    void a_plan_keeps_its_own_shares(@TempDir Path directory) throws Exception {
        // A plan every strategy of which pushes at the API. It is the one shape that cannot
        // survive having a share of pushing imposed on it - there would be nothing to give the
        // rest of the run to - so if this runs, the file's shares were left alone. Asserted this
        // way rather than by counting requests, because how many of a run's requests can be
        // counted as pushing depends on how many of a document's operations take a parameter the
        // awkward list can fill, which for this document is two of the four.
        Path everythingPushes = directory.resolve("plan.yaml");
        Files.writeString(everythingPushes, """
                version: 1
                strategies:
                  - name: fuzzing
                    share: 100
                    sources:
                      - dictionary: fuzzing
                      - source: random
                """);

        assertThat(run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                "--campaign", everythingPushes.toString(),
                "--out", directory.resolve("out").toString()))
                .describedAs("applying this command's default share whatever the file said would "
                        + "leave three quarters of the run with no strategy to build it")
                .isBetween(0, 1);
        assertThat(problems.toString()).doesNotContain("nothing to give");
        assertThat(screen.toString()).contains("were pushing at the API");
    }

    /** A plan that does the ordinary thing, plus whatever else is written here. */
    private static Path planIn(Path directory, String extra) throws Exception {
        Path plan = directory.resolve("plan.yaml");
        Files.writeString(plan, """
                version: 1
                strategies:
                  - name: nominal
                    share: 100
                    sources:
                      - source: enum
                      - source: example
                      - source: random
                """ + extra);
        return plan;
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

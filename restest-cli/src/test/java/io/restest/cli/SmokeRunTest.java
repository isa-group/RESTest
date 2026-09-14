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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.store.InteractionQuery;
import io.restest.core.store.InteractionStore;
import io.restest.store.SqliteInteractionStore;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * The whole tool, from the command line, against two real open-source APIs running in containers.
 *
 * <p>Every other test puts RESTest in front of an API somebody wrote for the occasion, which answers
 * exactly what the test asked it to. This one puts it in front of two applications that were written
 * by other people for other reasons, and that nobody here can adjust: a pet shop and a catalogue of
 * activities for scouts. What is being checked is not any one fault but that the whole thing still
 * works when the API is not on our side - the document is read over the network, requests are
 * invented and sent, replies are judged, and a report comes out with numbers in it.
 *
 * <p>Both APIs are asked for their own description as they are running, rather than being tested
 * against a copy kept here. The container is pinned to an exact published image, so what it serves
 * cannot change underneath this test, and taking the description from the API itself means the two
 * can never drift apart.
 *
 * <p>What is asserted is deliberately shape and floor rather than exact counts. How many requests
 * fit into ten seconds depends on the machine this runs on, so pinning a number would make the test
 * fail on a busy day for a reason that has nothing to do with RESTest. The real numbers belong in
 * the pull request, where a person compares them with last time.
 *
 * <p>It needs a container runtime, so it does not run in an ordinary build.
 */
@Tag("smoke")
class SmokeRunTest {

    /**
     * Asked for by name, which is how this gate refuses to disappear quietly.
     *
     * <p>A gate that reports success because it could not run is worse than no gate, because it
     * looks like one. Whoever asked for the smoke run asked for the containers, so a machine without
     * them fails rather than skips; a machine that simply happens not to have Docker, running an
     * ordinary build, never gets here at all.
     */
    private static final String ASKED_FOR = "restest.smoke.required";

    /**
     * The pet shop, pinned to an exact published image.
     *
     * <p>Pinned by content rather than by name: the other names this image is published under are
     * built for one kind of processor only, and would refuse to start on half the machines that run
     * this. Pinning what it contains also means the API under test is the same one next month.
     */
    private static final DockerImageName PETSTORE = DockerImageName.parse(
            "webfuzzing/wfd-swagger-petstore@sha256:"
                    + "26951cb671d013c44ea978aa659afe89d438d0fb431411c127ead2633dc876a4");

    /** The scouting activity catalogue, pinned the same way. */
    private static final DockerImageName SCOUT = DockerImageName.parse(
            "webfuzzing/wfd-scout-api@sha256:"
                    + "664e2809a869bfe735e33df7cfd7ce9ec9c393861ed4788531c718f10c9131e8");

    /** Both applications listen here inside their container; the port outside is chosen for us. */
    private static final int INSIDE = 8080;

    /** How long a run is given. Short, because this has to answer on every pull request. */
    private static final String BUDGET = "10s";

    private static GenericContainer<?> petstore;
    private static GenericContainer<?> scout;

    @BeforeAll
    static void startTheApis() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            if (Boolean.getBoolean(ASKED_FOR)) {
                fail("the smoke run was asked for, but there is no container runtime on this "
                        + "machine, so the two APIs it tests cannot be started");
            }
            Assumptions.abort("no container runtime on this machine");
        }
        // Waiting for a line in the log would be wrong for both of these, and quietly so. Each
        // prints its one distinctive line before it starts listening, so a run would begin against
        // a port nothing had opened yet. Asking for the description over HTTP waits for the thing
        // that actually matters, and proves the application is serving while it is at it.
        petstore = started(PETSTORE, "/api/v3/openapi.json");
        scout = started(SCOUT, "/api/swagger.json");
    }

    @AfterAll
    static void stopTheApis() {
        if (scout != null) {
            scout.stop();
        }
        if (petstore != null) {
            petstore.stop();
        }
    }

    @Test
    @DisplayName("the command tests a containerised pet shop and reports what it found")
    void the_pet_shop_is_tested_from_the_command_line(@TempDir Path directory) throws Exception {
        String base = addressOf(petstore) + "/api/v3";

        smoke(petstore, "Swagger Petstore", base + "/openapi.json", base, directory);
    }

    @Test
    @DisplayName("the command tests a containerised activity catalogue and reports what it found")
    void the_activity_catalogue_is_tested_from_the_command_line(@TempDir Path directory)
            throws Exception {
        String base = addressOf(scout) + "/api";

        // This one answers 401 on the operations it protects, because nothing here signs in. That is
        // a perfectly ordinary answer for an API to give and the run must survive it rather than
        // treat it as a reason to stop.
        smoke(scout, "Aktivitetsbanken", base + "/swagger.json", base, directory);
    }

    /** One whole run of the command, and everything a run must be able to say afterwards. */
    private static void smoke(GenericContainer<?> api, String expectedTitle, String document,
            String base, Path directory) throws Exception {
        StringWriter screen = new StringWriter();
        StringWriter problems = new StringWriter();
        PrintWriter out = new PrintWriter(screen);
        PrintWriter err = new PrintWriter(problems);

        int answer;
        try {
            answer = Restest.run(new String[] {"run", document, "--url", base,
                "--budget", BUDGET, "--seed", "20260914", "--out", directory.toString()}, out, err);
        } finally {
            out.flush();
            err.flush();
        }

        assertThat(answer)
                .describedAs("the command ran to completion. A fault is a legitimate answer; "
                        + "2, 3 and 4 are not, and 4 in particular would mean a report broke. "
                        + "What it said was:%n%s%n%s", screen, problems)
                .isBetween(0, 1);
        assertThat(screen.toString())
                .describedAs("the run names the API it read, which proves the document was fetched "
                        + "from the running container and understood")
                .contains(expectedTitle)
                .contains(base);
        assertThat(screen.toString())
                .describedAs("and reports what it did with its time")
                .containsPattern("\\d+ requests to \\d+ operations in .+, \\d+% of it idle");

        JsonValue.JsonObject report = (JsonValue.JsonObject)
                JsonText.read(Files.readString(directory.resolve("report.json")));
        assertThat(totalIn(report, "requests"))
                .describedAs("ten seconds against an API on the same machine is worth more than a "
                        + "handful of requests")
                .isGreaterThan(20);
        assertThat(totalIn(report, "operations"))
                .describedAs("and it got round more than one corner of the API")
                .isGreaterThan(3);
        assertThat(totalIn(report, "faults"))
                .describedAs("both of these APIs disagree with their own description in ways this "
                        + "tool can see; finding none of them would mean the oracles stopped working")
                .isGreaterThan(0);
        assertThat(idleFractionIn(report))
                .describedAs("most of the run was spent waiting for the API rather than thinking. "
                        + "Loose enough for a busy machine, tight enough to notice generation "
                        + "starting to dominate - which is the failure this whole project is a "
                        + "reaction to")
                .isLessThan(0.6);
        assertThat(stored(directory.resolve("run.sqlite")))
                .describedAs("every attempt is kept, and the stored run agrees with the report")
                .isEqualTo(totalIn(report, "requests"));

        assertThat(api.isRunning())
                .describedAs("the API is still up: a test tool that takes its target down with it "
                        + "is not testing, it is attacking")
                .isTrue();
    }

    private static GenericContainer<?> started(DockerImageName image, String documentPath) {
        GenericContainer<?> container = new GenericContainer<>(image)
                .withExposedPorts(INSIDE)
                .waitingFor(Wait.forHttp(documentPath).forPort(INSIDE).forStatusCode(200))
                // Generous on purpose: a first run has to pull about 160 MB before anything starts,
                // and a continuous-integration machine is not a quiet one.
                .withStartupTimeout(Duration.ofMinutes(3));
        container.start();
        return container;
    }

    private static String addressOf(GenericContainer<?> container) {
        return "http://" + container.getHost() + ":" + container.getMappedPort(INSIDE);
    }

    private static long totalIn(JsonValue.JsonObject report, String what) {
        JsonValue.JsonObject totals = (JsonValue.JsonObject) report.member("totals").orElseThrow();
        return ((JsonValue.JsonNumber) totals.member(what).orElseThrow()).value().longValueExact();
    }

    private static double idleFractionIn(JsonValue.JsonObject report) {
        JsonValue.JsonObject engine = (JsonValue.JsonObject) report.member("engine").orElseThrow();
        return ((JsonValue.JsonNumber) engine.member("idleFraction").orElseThrow())
                .value().doubleValue();
    }

    private static long stored(Path runFile) {
        try (InteractionStore store = SqliteInteractionStore.at(runFile)) {
            return store.count(InteractionQuery.all());
        }
    }
}

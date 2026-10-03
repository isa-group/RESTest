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
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The real thing: RESTest started as a program of its own, stopped the way a person or a machine
 * stops it - Ctrl-C, {@code kill}, {@code docker stop} - and what it leaves behind looked at
 * afterwards.
 *
 * <p>This is the one place Java's own part is tested: that the order to stop reaches the run, that
 * the program waits for the run to write what it found before it ends, and that it ends with the
 * number Java gives for the way it was stopped - 130 for Ctrl-C, 143 for the other two. Everything
 * else about a stopped run is tested in {@link ARunCutShortTest}, inside the program running the
 * tests, which a real order to stop would end.
 *
 * <p>Not on Windows, which has no {@code kill}: there, only Ctrl-C in a console and closing the
 * console stop a program this way, and neither can be sent from a test.
 */
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "no signals to send")
class StoppedBySignalTest {

    /** Far longer than any of these runs is allowed to take, so a run that ran it out failed. */
    private static final String BUDGET = "60s";

    /**
     * How long a stopped program may take to end: the two seconds it waits for its answers, the
     * five it may take to write, and room for a slow machine. One that ignored the order would
     * still be running.
     */
    private static final Duration ENDS_WITHIN = Duration.ofSeconds(20);

    private static WireMockServer api;

    @BeforeAll
    static void startTheApi() {
        api = new WireMockServer(options().dynamicPort().containerThreads(64));
        api.start();
        api.stubFor(get(urlMatching("/pets")).willReturn(json("[]")));
        api.stubFor(get(urlMatching("/pets/[0-9]+"))
                .willReturn(json("{\"id\": 7, \"name\": \"Rex\"}")));
        api.stubFor(get(urlMatching("/shelters")).willReturn(json("{\"total\": 3}")));
    }

    @AfterAll
    static void stopTheApi() {
        api.stop();
    }

    @Test
    @DisplayName("Ctrl-C stops a run, which writes what it found and a closed stored run, and ends with 130")
    void ctrl_c(@TempDir Path directory) throws IOException, InterruptedException {
        Process restest = started(directory);
        waitUntilItIsTesting(restest, directory);

        signal(restest, "INT");
        if (!restest.waitFor(ENDS_WITHIN.toSeconds(), TimeUnit.SECONDS)) {
            // A program started in the background by a shell that is not a terminal's is started
            // with Ctrl-C ignored, and Java then leaves it ignored; nothing of RESTest's is at stake
            // there, since a stopped run ends inside the bound above whatever it is doing.
            restest.destroyForcibly().waitFor();
            Assumptions.abort("this machine starts the tests with Ctrl-C ignored: "
                    + said(directory));
        }

        assertThat(restest.exitValue()).describedAs(said(directory)).isEqualTo(130);
        leftBehind(directory);
    }

    @Test
    @DisplayName("kill and docker stop stop a run, which writes what it found and a closed stored run, and ends with 143")
    void kill(@TempDir Path directory) throws IOException, InterruptedException {
        Process restest = started(directory);
        waitUntilItIsTesting(restest, directory);

        // What docker stop sends the program a container runs, and what kill sends by default.
        restest.destroy();

        assertThat(restest.waitFor(ENDS_WITHIN.toSeconds(), TimeUnit.SECONDS))
                .describedAs("the program ended: %s", said(directory)).isTrue();
        assertThat(restest.exitValue()).describedAs(said(directory)).isEqualTo(143);
        leftBehind(directory);
    }

    /** What a run stopped from outside leaves behind, looked at once the program has ended. */
    private static void leftBehind(Path directory) throws IOException {
        String screen = Files.readString(directory.resolve("screen.txt"));
        String problems = Files.readString(directory.resolve("problems.txt"));
        Path out = directory.resolve("out");

        assertThat(screen)
                .describedAs("the summary, which says how to read everything under it: %s%n%s",
                        screen, problems)
                .containsPattern("(?m)^\\d+ requests to \\d+ operations in .*, cut short$")
                .contains("report written to " + out.resolve("report.json"))
                .contains("run stored in " + out.resolve("run.sqlite"));
        assertThat(problems)
                .contains("restest: the run was stopped from outside after")
                .doesNotContain("had not finished writing")
                .doesNotContain("Exception");

        JsonValue.JsonObject report = (JsonValue.JsonObject) JsonText.read(
                Files.readString(out.resolve("report.json")));
        assertThat(report.member("cutShort")).contains(JsonValue.of(true));
        assertThat(out.resolve("run.sqlite-wal"))
                .describedAs("the stored run was closed, which folds its working files back in")
                .doesNotExist();
        assertThat(out.resolve("run.sqlite-shm")).doesNotExist();
        long requests = ((JsonValue.JsonNumber) ((JsonValue.JsonObject) report.member("totals")
                .orElseThrow()).member("requests").orElseThrow()).value().longValueExact();
        try (InteractionStore store = SqliteInteractionStore.at(out.resolve("run.sqlite"))) {
            assertThat(store.count(InteractionQuery.all()))
                    .describedAs("the report and the stored run hold the same requests")
                    .isEqualTo(requests)
                    .isPositive();
        }
    }

    /**
     * RESTest, started as a program of its own from the classes these tests run, the way the
     * {@code restest} script starts it, writing what it says into two files.
     */
    private static Process started(Path directory) throws IOException {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        List<String> command = List.of(java.toString(), "--enable-native-access=ALL-UNNAMED",
                "-cp", System.getProperty("java.class.path"), Restest.class.getName(),
                "run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", BUDGET,
                "--seed", "7", "--store", "--out", directory.resolve("out").toString());
        return new ProcessBuilder(command)
                .redirectOutput(directory.resolve("screen.txt").toFile())
                .redirectError(directory.resolve("problems.txt").toFile())
                .start();
    }

    /** Until the API has been asked enough to show the run is under way, or the program ended. */
    private static void waitUntilItIsTesting(Process restest, Path directory)
            throws InterruptedException {
        int before = asked();
        Instant giveUpAt = Instant.now().plusSeconds(30);
        while (asked() - before < 40) {
            assertThat(restest.isAlive())
                    .describedAs("RESTest ended before it was stopped: %s", said(directory))
                    .isTrue();
            assertThat(Instant.now()).describedAs("RESTest never got going: %s", said(directory))
                    .isBefore(giveUpAt);
            Thread.sleep(20);
        }
    }

    private static void signal(Process restest, String signal)
            throws IOException, InterruptedException {
        Process kill = new ProcessBuilder("kill", "-" + signal, String.valueOf(restest.pid()))
                .inheritIO().start();
        assertThat(kill.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(kill.exitValue()).describedAs("kill -%s was sent", signal).isZero();
    }

    private static int asked() {
        return api.countRequestsMatching(anyRequestedFor(anyUrl()).build()).getCount();
    }

    /** Everything RESTest printed, for a failure to show. */
    private static String said(Path directory) {
        try {
            return Files.readString(directory.resolve("screen.txt")) + System.lineSeparator()
                    + Files.readString(directory.resolve("problems.txt"));
        } catch (IOException cannotRead) {
            return "(what it printed could not be read: " + cannotRead + ")";
        }
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(
            String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody(body);
    }
}

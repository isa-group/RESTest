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
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restest.core.exec.EngineSettings;
import io.restest.core.exec.EngineStatistics;
import io.restest.core.exec.HttpEngine;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.TestCase;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.store.InteractionQuery;
import io.restest.core.store.InteractionStore;
import io.restest.exec.OkHttpEngine;
import io.restest.store.SqliteInteractionStore;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A run stopped from outside - Ctrl-C, {@code kill}, {@code docker stop} - makes no new requests,
 * waits a little for the answers it is owed, and leaves behind what it found: the summary, a report that
 * says the run was cut short, and a stored run that was closed. Or it says, before the program
 * ends, what it could not leave behind.
 *
 * <p>The order to stop comes from the test, through the same door Java's comes through, at a moment
 * the test chooses: once the stand-in API has been asked enough to show the run was under way. What
 * the door does with it is run on a thread of its own, as Java runs it, and the test waits for it as
 * Java would before ending the program. {@link StoppedBySignalTest} sends the real order to a
 * program of its own.
 */
class ARunCutShortTest {

    /** Far longer than any of these runs is allowed to take, so a run that ran it out failed. */
    private static final String BUDGET = "60s";

    private WireMockServer api;

    private final StringWriter screen = new StringWriter();
    private final StringWriter problems = new StringWriter();

    @AfterEach
    void stopTheApi() {
        if (api != null) {
            api.stop();
        }
    }

    @Test
    @DisplayName("a run stopped from outside answers 130, and leaves the summary, a report that says it was cut short, and a closed stored run")
    void a_run_stopped_leaves_what_it_found_behind(@TempDir Path directory) throws IOException {
        startTheApi(Duration.ZERO);
        StopWhen stop = new StopWhen(() -> asked() >= 40);

        Instant began = Instant.now();
        int answer = run(OkHttpEngine::new, stop, "run", "pet-shelter.yaml", "--url",
                api.baseUrl(), "--budget", BUDGET, "--seed", "7", "--store", "--out",
                directory.toString());
        Duration took = Duration.between(began, Instant.now());
        stop.awaitDone();

        assertThat(answer).describedAs("%s%n%s", screen, problems).isEqualTo(130);
        assertThat(took).describedAs("a minute was given, and the run was stopped")
                .isLessThan(Duration.ofSeconds(20));
        assertThat(Duration.between(stop.setOffAt, stop.doneAt))
                .describedAs("the program was let end once the run was done, well inside the "
                        + "seven seconds it would have waited")
                .isLessThan(Duration.ofSeconds(5));
        assertThat(stop.wasUndone).describedAs("and the run stopped listening for a stop").isTrue();
        assertThat(screen.toString())
                .describedAs("the summary, which says how to read everything under it")
                .containsPattern("(?m)^\\d+ requests to \\d+ operations in .*, cut short$")
                .contains("report written to " + directory.resolve("report.json"))
                .contains("run stored in " + directory.resolve("run.sqlite"));
        assertThat(problems.toString())
                .containsPattern("restest: the run was stopped from outside after \\d+\\.\\ds of "
                        + "its 60s budget; what is reported above, and in report.json, is what it "
                        + "found until then")
                .doesNotContain("had not finished writing");

        JsonValue.JsonObject report = report(directory);
        assertThat(report.member("cutShort")).contains(JsonValue.of(true));
        assertThat(directory.resolve("report.json.partial")).doesNotExist();
        assertThat(directory.resolve("run.sqlite-wal"))
                .describedAs("the stored run was closed, which folds its working files back in")
                .doesNotExist();
        assertThat(directory.resolve("run.sqlite-shm")).doesNotExist();
        long requests = ((JsonValue.JsonNumber) ((JsonValue.JsonObject) report.member("totals")
                .orElseThrow()).member("requests").orElseThrow()).value().longValueExact();
        assertThat(requests).isGreaterThanOrEqualTo(40);
        assertThat(stored(directory))
                .describedAs("the report and the stored run hold the same requests")
                .isEqualTo(requests);
    }

    @Test
    @DisplayName("a run stopped with answers still owed waits its grace for them, says how many never came, and ends")
    void a_run_stopped_waits_its_grace_and_no_longer(@TempDir Path directory) {
        // One operation answers in longer than a stopped run waits, so that when the run is stopped
        // some of its requests are always on their way.
        startTheApi(Duration.ofSeconds(4));
        StopWhen stop = new StopWhen(() -> asked() >= 20);

        int answer = run(OkHttpEngine::new, stop, "run", "pet-shelter.yaml", "--url",
                api.baseUrl(), "--budget", BUDGET, "--seed", "7", "--out", directory.toString(),
                "--set", "schedule.interruptGrace=500ms");
        Duration afterTheStop = Duration.between(stop.setOffAt, Instant.now());
        stop.awaitDone();

        assertThat(answer).describedAs("%s%n%s", screen, problems).isEqualTo(130);
        assertThat(afterTheStop)
                .describedAs("it waited half a second for what it was owed, not the four seconds "
                        + "the slow operation takes, nor the forty a run at its deadline waits")
                .isGreaterThanOrEqualTo(Duration.ofMillis(450))
                .isLessThan(Duration.ofMillis(3500));
        assertThat(problems.toString())
                .containsPattern("restest: \\d+ request\\(s\\) were still unanswered when the run "
                        + "stopped waiting for them, at most 500ms after it was stopped "
                        + "\\(schedule.interruptGrace\\), so they are missing from what is reported "
                        + "above");
        assertThat(report(directory).member("cutShort")).contains(JsonValue.of(true));
    }

    @Test
    @DisplayName("a run that cannot finish writing in time says what it did not leave behind, before the program ends")
    void a_run_that_cannot_finish_writing_says_so(@TempDir Path directory) {
        startTheApi(Duration.ZERO);
        StopWhen stop = new StopWhen(() -> asked() >= 20);

        // Stuck where the run says it is over, so nothing after that - the report, the stored run
        // being closed - is done while the program waits for it.
        int answer = run(settings -> new StuckAtTheEnd(new OkHttpEngine(settings),
                Duration.ofSeconds(7)), stop, "run", "pet-shelter.yaml", "--url", api.baseUrl(),
                "--budget", BUDGET, "--seed", "7", "--store", "--out", directory.toString(),
                "--set", "schedule.interruptGrace=0s");
        stop.awaitDone();

        assertThat(Duration.between(stop.setOffAt, stop.doneAt))
                .describedAs("the grace, none here, and the five seconds writing is given")
                .isGreaterThanOrEqualTo(Duration.ofMillis(4900))
                .isLessThan(Duration.ofSeconds(7));
        assertThat(problems.toString())
                .contains("restest: the run was stopped from outside and had not finished writing "
                        + "what it found 5s later, so it ends here: report.json was not written; "
                        + "run.sqlite was not closed, so the last interactions it had not yet saved "
                        + "are lost, and some of what it did save is in run.sqlite-wal beside it: "
                        + "keep the three files together");
        // A real program would have ended there. This one is a test, so the run finishes after all.
        assertThat(answer).isEqualTo(130);
    }

    @Test
    @DisplayName("a run stopped before it began testing sends nothing, touches nothing an earlier run left, and says so")
    void a_run_stopped_before_it_began_leaves_the_directory_as_it_was(@TempDir Path directory)
            throws IOException {
        startTheApi(Duration.ZERO);
        Path earlier = directory.resolve("report.json");
        Files.writeString(earlier, "{\"an\": \"earlier run\"}");
        // Stopped at once: while the document is still being read.
        StopWhen stop = new StopWhen(() -> true);

        int answer = run(OkHttpEngine::new, stop, "run", "pet-shelter.yaml", "--url",
                api.baseUrl(), "--budget", BUDGET, "--seed", "7", "--out", directory.toString());
        stop.awaitDone();

        assertThat(answer).describedAs("%s%n%s", screen, problems).isEqualTo(130);
        assertThat(problems.toString())
                .contains("restest: the run was stopped before it began testing, so it sent "
                        + "nothing and wrote nothing, and " + directory + " is as it was, with "
                        + "whatever an earlier run left there");
        assertThat(asked()).describedAs("not one request went out").isZero();
        assertThat(Files.readString(earlier))
                .describedAs("an earlier run's report is left for whoever stopped this one to "
                        + "judge, rather than cleared away by a run that never began")
                .isEqualTo("{\"an\": \"earlier run\"}");
        assertThat(Duration.between(stop.setOffAt, stop.doneAt))
                .describedAs("there was nothing to wait for")
                .isLessThan(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("a run stopped before it began testing answers 130 even when it finds something else to answer on its way out")
    void a_run_stopped_before_it_began_answers_as_a_stopped_program(@TempDir Path directory)
            throws IOException {
        startTheApi(Duration.ZERO);
        Path nothingToTest = directory.resolve("empty.yaml");
        Files.writeString(nothingToTest, """
                openapi: 3.0.3
                info: {title: Nothing, version: '1'}
                paths: {}
                """);
        StopWhen stop = new StopWhen(() -> true);

        int answer = run(OkHttpEngine::new, stop, "run", nothingToTest.toString(), "--url",
                api.baseUrl(), "--budget", BUDGET, "--out", directory.resolve("out").toString());

        assertThat(problems.toString())
                .describedAs("the premise: the document has nothing to test, which answers 3")
                .contains("the document describes no operation that could be tested")
                .contains("the run was stopped before it began testing");
        assertThat(answer)
                .describedAs("a program stopped from outside ends with 130 or 143, so a test must "
                        + "read the same")
                .isEqualTo(130);
    }

    private void startTheApi(Duration sheltersTake) {
        api = new WireMockServer(options().dynamicPort().containerThreads(80));
        api.start();
        api.stubFor(get(urlMatching("/pets")).willReturn(json("[]")));
        api.stubFor(post(urlMatching("/pets")).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\": 7, \"name\": \"Rex\"}")));
        api.stubFor(get(urlMatching("/pets/[0-9]+"))
                .willReturn(json("{\"id\": 7, \"name\": \"Rex\"}")));
        api.stubFor(get(urlMatching("/shelters")).willReturn(json("{\"total\": 3}")
                .withFixedDelay((int) sheltersTake.toMillis())));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(
            String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody(body);
    }

    /** How many requests the stand-in API has been asked, answered or not. */
    private int asked() {
        return api.countRequestsMatching(anyRequestedFor(anyUrl()).build()).getCount();
    }

    private int run(Function<EngineSettings, HttpEngine> engines, StopsFromOutside stops,
            String... arguments) {
        PrintWriter out = new PrintWriter(screen);
        PrintWriter err = new PrintWriter(problems);
        try {
            return Restest.run(arguments, out, err, Map.of(), engines, stops);
        } finally {
            out.flush();
            err.flush();
        }
    }

    private static JsonValue.JsonObject report(Path directory) {
        try {
            return (JsonValue.JsonObject) JsonText.read(
                    Files.readString(directory.resolve("report.json")));
        } catch (IOException cannotRead) {
            throw new AssertionError("the report could not be read", cannotRead);
        }
    }

    private static long stored(Path directory) {
        try (InteractionStore store = SqliteInteractionStore.at(directory.resolve("run.sqlite"))) {
            return store.count(InteractionQuery.all());
        }
    }

    /**
     * An order to stop, given once something is true, and carried out on a thread of its own the
     * way Java carries out its own: what the run arranged is run, and waited for.
     */
    private static final class StopWhen implements StopsFromOutside {

        private final BooleanSupplier when;
        private volatile Thread carriedOut;
        private volatile boolean wasUndone;
        private volatile Instant setOffAt;
        private volatile Instant doneAt;

        StopWhen(BooleanSupplier when) {
            this.when = when;
        }

        @Override
        public Arrangement whenStopped(Runnable stop) {
            if (when.getAsBoolean()) {
                // Due already, so carried out before the run takes another step: the order came
                // while the command was still starting. A thread of its own would leave when it
                // lands to chance.
                setOffAt = Instant.now();
                stop.run();
                doneAt = Instant.now();
                return () -> wasUndone = true;
            }
            carriedOut = Thread.ofPlatform().daemon().name("stop-when").start(() -> {
                while (!when.getAsBoolean()) {
                    if (wasUndone) {
                        return;
                    }
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException notToday) {
                        return;
                    }
                }
                setOffAt = Instant.now();
                // What Java would do: carry out what the run arranged, and end the program when it
                // returns. The run says it is done by the time this returns, or it ran out of time.
                stop.run();
                doneAt = Instant.now();
            });
            return () -> wasUndone = true;
        }

        void awaitDone() {
            try {
                if (carriedOut != null) {
                    carriedOut.join(Duration.ofSeconds(30));
                }
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            }
            assertThat(setOffAt).describedAs("the order to stop was given").isNotNull();
        }
    }

    /** The real engine, except that saying what it saw takes as long as it is told to. */
    private static final class StuckAtTheEnd implements HttpEngine {

        private final HttpEngine real;
        private final Duration stuckFor;

        StuckAtTheEnd(HttpEngine real, Duration stuckFor) {
            this.real = real;
            this.stuckFor = stuckFor;
        }

        @Override
        public CompletableFuture<Interaction> sendAsync(TestCase testCase,
                HttpRequestRecord request) {
            return real.sendAsync(testCase, request);
        }

        @Override
        public EngineStatistics statistics() {
            try {
                Thread.sleep(stuckFor);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            }
            return real.statistics();
        }

        @Override
        public void close() {
            real.close();
        }
    }
}

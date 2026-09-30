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
import io.restest.core.exec.EngineStatistics;
import io.restest.core.exec.HttpEngine;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ParameterLocation;
import io.restest.exec.OkHttpEngine;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A run in which RESTest itself lost part of its own work answers 4, the number that says the tool
 * went wrong, and never the 0, 1 or 3 that say something about the API.
 *
 * <p>Two things can go wrong on RESTest's side while the API answers perfectly well. The part of
 * the tool that sends a request can fail on the thread the request is sent on - running out of
 * memory while it reads an enormous reply, say - and then nothing comes back for that request at
 * all. And the part that hides a key the run was handed can fail to pick it out of an exchange, and
 * keep that exchange with nearly everything in it blanked. Either way every report is missing
 * something, so finding nothing wrong - or finding faults, or finding nobody at the address - is
 * not an answer the run is in a position to give.
 *
 * <p>The API here is a stand-in that answers every request; what breaks is a stand-in for the
 * sending part of the tool, wrapped around the real one, that fails the way the real one does when
 * it breaks. The failure is a stack that ran out rather than memory that ran out, which is the same
 * kind of failure and one the test framework does not treat as the end of every test.
 */
class ABrokenRunAnswersFourTest {

    /**
     * Long enough for a slow machine to read the document, start and still send: a run that sends
     * nothing answers 3, which is not what these runs are about. One second was not, on one of the
     * machines the build runs on.
     */
    private static final String BUDGET = "3s";

    private static WireMockServer api;

    private final StringWriter screen = new StringWriter();
    private final StringWriter problems = new StringWriter();

    @BeforeAll
    static void startTheApi() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
        api.stubFor(get(urlMatching("/pets")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("[]")));
        api.stubFor(get(urlMatching("/pets/[0-9]+")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\": 7, \"name\": \"Rex\"}")));
        api.stubFor(get(urlMatching("/shelters")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"total\": 3}")));
    }

    @AfterAll
    static void stopTheApi() {
        api.stop();
    }

    @Test
    @DisplayName("a run in which RESTest lost the requests to one operation answers 4, says so with the first failure, and tests the rest")
    void losing_some_requests_answers_four(@TempDir Path directory) {
        int answer = run(settings -> new Losing(new OkHttpEngine(settings),
                testCase -> testCase.operation().value().equals("listPets")),
                "run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", BUDGET, "--seed",
                "7", "--out", directory.toString());

        assertThat(answer).describedAs("%s%n%s", screen, problems).isEqualTo(4);
        assertThat(problems.toString())
                .containsPattern("restest: \\d+ request\\(s\\) were lost by RESTest itself, on "
                        + "their way to the API or back")
                .contains("this is a fault of RESTest rather than of the API")
                .contains("The first went wrong like this:")
                .contains("java.lang.StackOverflowError: lost on the way to listPets")
                .contains("\tat ")
                .describedAs("the other operations were answered, so this is no run that stopped")
                .doesNotContain("Not one request was answered");
        assertThat(screen.toString())
                .describedAs("what was found is still reported, and where it was written")
                .contains("report written to " + directory.resolve("report.json"));
        assertThat(directory.resolve("report.json")).exists();
    }

    @Test
    @DisplayName("a run in which RESTest lost every request answers 4 and stops early, and does not blame the address")
    void losing_every_request_answers_four_and_stops_early(@TempDir Path directory) {
        Instant before = Instant.now();

        int answer = run(settings -> new Losing(new OkHttpEngine(settings), testCase -> true),
                "run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "30s", "--seed",
                "7", "--out", directory.toString());

        assertThat(answer).describedAs("%s%n%s", screen, problems).isEqualTo(4);
        assertThat(Duration.between(before, Instant.now()))
                .describedAs("it does not go on losing requests for the half minute it was given")
                .isLessThan(Duration.ofSeconds(15));
        assertThat(problems.toString())
                .contains("were lost by RESTest itself")
                .contains("Not one request was answered, and a run stops sending, at the end of "
                        + "the round it is in, once as many as can be in flight have gone "
                        + "unanswered")
                .describedAs("the address is not what is wrong, and saying it is would send "
                        + "whoever reads it looking in the wrong place")
                .doesNotContain("Check the address");
    }

    @Test
    @DisplayName("a run in which an exchange had to be kept without its details answers 4")
    void an_exchange_kept_without_its_details_answers_four(@TempDir Path directory) {
        // An exchange whose test case carries a value nested far deeper than hiding a key can walk:
        // picking the key out of it runs out of stack, so the exchange is kept with everything that
        // could hold a key blanked. The run is handed a key, or there is nothing to hide at all.
        int answer = run(settings -> new NestingTooDeep(new OkHttpEngine(settings)),
                "run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", BUDGET, "--seed",
                "7", "--out", directory.toString(), "--auth",
                "header:X-Shelter-Key=Zk9-leakprobe-4f");

        assertThat(problems.toString())
                .describedAs("the premise: hiding the key did fail on these exchanges. %s", screen)
                .containsPattern("restest: \\d+ exchange\\(s\\) were kept without their details, "
                        + "because RESTest could not pick the key out of them");
        assertThat(answer).describedAs("%s%n%s", screen, problems).isEqualTo(4);
        assertThat(problems.toString())
                .describedAs("and for that reason alone, not because something else broke")
                .doesNotContain("listener(s) failed")
                .doesNotContain("rule(s) failed")
                .doesNotContain("were lost by RESTest");
        assertThat(screen.toString()).contains("report written to");
        assertThat(directory.resolve("report.json")).exists();
    }

    @Test
    @DisplayName("a request the engine hands back as nothing is lost, with nothing to show, a key or no key")
    void a_request_handed_back_as_nothing_is_lost(@TempDir Path directory) {
        Function<io.restest.core.exec.EngineSettings, HttpEngine> nothingForListPets = settings ->
                new Losing(new OkHttpEngine(settings),
                        testCase -> testCase.operation().value().equals("listPets"), true);

        int plain = run(nothingForListPets, "run", "pet-shelter.yaml", "--url", api.baseUrl(),
                "--budget", BUDGET, "--seed", "7", "--out", directory.resolve("plain").toString());
        String saidWithoutAKey = problems.toString();
        problems.getBuffer().setLength(0);
        int keyed = run(nothingForListPets, "run", "pet-shelter.yaml", "--url", api.baseUrl(),
                "--budget", BUDGET, "--seed", "7", "--out", directory.resolve("keyed").toString(),
                "--auth", "header:X-Shelter-Key=Zk9-leakprobe-4f");
        String saidWithAKey = problems.toString();

        assertThat(List.of(plain, keyed)).describedAs("%s%n%s%n%s", screen, saidWithoutAKey,
                saidWithAKey).containsOnly(4);
        assertThat(List.of(saidWithoutAKey, saidWithAKey)).allSatisfy(said -> assertThat(said)
                .contains("were lost by RESTest itself")
                .describedAs("nothing went wrong that could be shown, and hiding a key in the "
                        + "nothing must not stand in for it")
                .doesNotContain("The first went wrong like this:")
                .doesNotContain("NullPointerException"));
    }

    @Test
    @DisplayName("a run that lost requests where the address answered nothing either says both")
    void lost_requests_and_an_address_that_answers_nothing(@TempDir Path directory) {
        int answer = run(settings -> new Losing(new OkHttpEngine(settings),
                testCase -> testCase.operation().value().equals("listPets")),
                "run", "pet-shelter.yaml", "--url", "http://127.0.0.1:1", "--budget", "30s",
                "--seed", "7", "--out", directory.toString());

        assertThat(answer).describedAs("%s%n%s", screen, problems).isEqualTo(4);
        assertThat(problems.toString())
                .contains("were lost by RESTest itself")
                .containsPattern("the other \\d+ ended without an answer - refused, reset, timed "
                        + "out, or never sent at all - so check http://127.0.0.1:1 as well");
    }

    @Test
    @DisplayName("the failure a lost request is shown with has every key the run was handed hidden in it")
    void the_failure_shown_has_the_key_hidden(@TempDir Path directory) {
        String key = "Zk9-leakprobe-4f";
        int answer = run(settings -> new Losing(new OkHttpEngine(settings),
                testCase -> testCase.operation().value().equals("listPets"), false,
                "sent with " + key), "run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget",
                BUDGET, "--seed", "7", "--out", directory.toString(), "--auth",
                "header:X-Shelter-Key=" + key);

        assertThat(answer).describedAs("%s%n%s", screen, problems).isEqualTo(4);
        assertThat(problems.toString())
                .contains("java.lang.StackOverflowError: sent with "
                        + "REDACTED-AUTH.header.X-Shelter-Key")
                .doesNotContain("leakprobe");
    }

    private int run(Function<io.restest.core.exec.EngineSettings, HttpEngine> engines,
            String... arguments) {
        PrintWriter out = new PrintWriter(screen);
        PrintWriter err = new PrintWriter(problems);
        try {
            return Restest.run(arguments, out, err, Map.of(), engines);
        } finally {
            out.flush();
            err.flush();
        }
    }

    /**
     * The real engine, except that it loses the requests it is told to: each fails on the thread it
     * is sent on, as a request does when the sending part of the tool breaks.
     */
    private static final class Losing implements HttpEngine {

        private final HttpEngine real;
        private final Predicate<TestCase> loses;
        private final boolean withNothingToShow;
        private final String said;

        Losing(HttpEngine real, Predicate<TestCase> loses) {
            this(real, loses, false);
        }

        Losing(HttpEngine real, Predicate<TestCase> loses, boolean withNothingToShow) {
            this(real, loses, withNothingToShow, null);
        }

        /**
         * @param withNothingToShow whether a lost request comes back as nothing at all, which the
         *     engine's contract forbids, rather than as a failure
         * @param said what the failure says, or {@code null} for the operation it was lost on the
         *     way to
         */
        Losing(HttpEngine real, Predicate<TestCase> loses, boolean withNothingToShow,
                String said) {
            this.real = real;
            this.loses = loses;
            this.withNothingToShow = withNothingToShow;
            this.said = said;
        }

        @Override
        public CompletableFuture<Interaction> sendAsync(TestCase testCase,
                HttpRequestRecord request) {
            if (loses.test(testCase)) {
                if (withNothingToShow) {
                    return CompletableFuture.completedFuture(null);
                }
                return CompletableFuture.supplyAsync(() -> {
                    throw new StackOverflowError(said != null ? said
                            : "lost on the way to " + testCase.operation().value());
                });
            }
            return real.sendAsync(testCase, request);
        }

        @Override
        public EngineStatistics statistics() {
            return real.statistics();
        }

        @Override
        public void close() {
            real.close();
        }
    }

    /**
     * The real engine, except that every exchange it hands back carries a value nested two hundred
     * thousand levels deep: far past what walking it, one level inside another, can reach.
     */
    private static final class NestingTooDeep implements HttpEngine {

        private final HttpEngine real;

        NestingTooDeep(HttpEngine real) {
            this.real = real;
        }

        @Override
        public CompletableFuture<Interaction> sendAsync(TestCase testCase,
                HttpRequestRecord request) {
            return real.sendAsync(testCase, request).thenApply(answered -> {
                JsonValue deep = JsonValue.of("Zk9-leakprobe-4f");
                for (int level = 0; level < 200_000; level++) {
                    deep = new JsonValue.JsonArray(List.of(deep));
                }
                // In place of one of the same name: a request built from an exchange that went
                // this way - a change to an accepted one, a step of a series - carries it already.
                List<ParameterValue> values = new java.util.ArrayList<>(
                        testCase.parameterValues());
                values.removeIf(value -> value.name().equals("deep"));
                values.add(ParameterValue.of("deep", ParameterLocation.QUERY, deep,
                        new ValueOrigin.Generated("a test")));
                // The same test case in every other way, so that everything listening hears of the
                // one it was told was planned.
                TestCase carrying = new TestCase(testCase.id(), testCase.operation(), values,
                        testCase.body(), testCase.intent(), testCase.mutation(),
                        testCase.sequence());
                return Interaction.answered(carrying, answered.request(), new HttpResponseRecord(
                        StatusLine.of(200), List.of(), Optional.empty()),
                        answered.sentAt(), answered.elapsed());
            });
        }

        @Override
        public EngineStatistics statistics() {
            return real.statistics();
        }

        @Override
        public void close() {
            real.close();
        }
    }
}

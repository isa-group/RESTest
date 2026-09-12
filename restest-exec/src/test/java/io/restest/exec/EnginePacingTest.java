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
package io.restest.exec;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restest.core.exec.EngineSettings;
import io.restest.core.exec.EngineStatistics;
import io.restest.core.execution.Interaction;
import io.restest.core.model.HttpMethod;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How the engine spends time: several questions in the air at once when the API can take them, never
 * more than it was allowed, and an honest account of the moments it had nothing in flight.
 *
 * <p>The assertions here are deliberately loose - "more than one at a time", "not most of the run" -
 * because they run against a real socket on whatever machine happens to be free. The exact arithmetic
 * of both moving parts is pinned down in {@link EngineActivityTest} and {@link ConcurrencyLimiterTest},
 * which drive them with a clock of their own.
 */
class EnginePacingTest {

    private static final int REQUESTS = 40;
    private static final int SLOW_REPLY_MILLIS = 50;

    private static WireMockServer api;

    @BeforeAll
    static void startTheStubApi() {
        api = new WireMockServer(options().dynamicPort().containerThreads(24));
        api.start();
    }

    @AfterAll
    static void stopTheStubApi() {
        api.stop();
    }

    @BeforeEach
    void aSlowEndpoint() {
        api.resetAll();
        api.stubFor(get(urlEqualTo("/slow")).willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(SLOW_REPLY_MILLIS)
                .withBody("{}")));
    }

    @Test
    @DisplayName("a slow API is met with several requests at once, and never more than allowed")
    void requests_overlap_but_stay_within_the_ceiling() {
        EngineSettings settings = EngineSettings.defaults().withConcurrency(1, 2, 6);

        try (OkHttpEngine engine = new OkHttpEngine(settings)) {
            List<Interaction> interactions = sendAll(engine, REQUESTS);

            assertThat(interactions).hasSize(REQUESTS)
                    .allSatisfy(interaction -> assertThat(interaction.isAnswered()).isTrue());
            EngineStatistics statistics = engine.statistics();
            assertThat(statistics.requestsSent()).isEqualTo(REQUESTS);
            assertThat(statistics.peakConcurrency())
                    .describedAs("several requests should have been in flight at once")
                    .isGreaterThan(1)
                    .describedAs("the ceiling the settings named is a promise, not a suggestion")
                    .isLessThanOrEqualTo(6);
            assertThat(statistics.concurrencyLimit()).isBetween(1, 6);
            assertThat(statistics.totalResponseTime())
                    .describedAs("the time spent waiting on the API adds up to more than the run "
                            + "itself lasted, which cannot happen unless requests overlapped - and "
                            + "unlike a stopwatch bound, it cannot be failed by a slow machine")
                    .isGreaterThan(statistics.wallClock());
        }
    }

    @Test
    @DisplayName("the ceiling holds: one request at a time takes as long as one at a time takes")
    void a_ceiling_of_one_sends_them_strictly_one_after_another() {
        int few = 6;

        try (OkHttpEngine engine =
                new OkHttpEngine(EngineSettings.defaults().withoutConcurrency())) {
            List<Interaction> interactions = sendAll(engine, few);

            assertThat(interactions).hasSize(few);
            EngineStatistics statistics = engine.statistics();
            assertThat(statistics.peakConcurrency()).isEqualTo(1);
            assertThat(statistics.wallClock())
                    .describedAs("six replies of %dms each, one at a time", SLOW_REPLY_MILLIS)
                    .isGreaterThanOrEqualTo(
                            Duration.ofMillis((long) few * SLOW_REPLY_MILLIS));
        }
    }

    @Test
    @DisplayName("waiting on the API is not idle time; having nothing to do is")
    void idle_time_counts_the_gaps_and_not_the_waiting() throws InterruptedException {
        try (OkHttpEngine busy = new OkHttpEngine(EngineSettings.defaults().withConcurrency(1, 4, 8));
                OkHttpEngine idle = new OkHttpEngine()) {

            sendAll(busy, REQUESTS);
            assertThat(busy.statistics().idleFraction())
                    .describedAs("a run that was always waiting on the API is not idle")
                    .isLessThan(0.5);

            idle.send(Requests.testCase(HttpMethod.GET, "/slow"), Requests.get(api.baseUrl()
                    + "/slow"));
            Thread.sleep(800);
            assertThat(idle.statistics().idle())
                    .describedAs("a run that stopped sending anything is idle, and says so")
                    .isGreaterThan(Duration.ofMillis(500));
        }
    }

    private static List<Interaction> sendAll(OkHttpEngine engine, int count) {
        List<CompletableFuture<Interaction>> sent = IntStream.range(0, count)
                .mapToObj(i -> engine.sendAsync(Requests.testCase(HttpMethod.GET, "/slow"),
                        Requests.get(api.baseUrl() + "/slow")))
                .toList();
        return sent.stream().map(CompletableFuture::join).toList();
    }
}

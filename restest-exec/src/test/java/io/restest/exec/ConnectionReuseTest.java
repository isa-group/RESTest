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
import io.restest.core.execution.Interaction;
import io.restest.core.model.HttpMethod;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Whether the engine keeps the connections it opened, so that a run against a fast API goes on
 * using the same ones instead of opening a new one for almost every request.
 *
 * <p>Every connection a client closes leaves one of the machine's local ports unusable for a while
 * afterwards. A client that closes most of its connections runs out of those ports within seconds
 * against an API that answers quickly, and the requests after that fail as if nothing had answered.
 */
class ConnectionReuseTest {

    private static final int IN_FLIGHT = 12;

    private static WireMockServer api;

    @BeforeAll
    static void startTheStubApi() {
        api = new WireMockServer(options().dynamicPort().containerThreads(IN_FLIGHT * 2));
        api.start();
        // Slow enough that every request sent at once is still in flight when the last one goes
        // out, so each of them needs a connection of its own.
        api.stubFor(get(urlEqualTo("/widgets"))
                .willReturn(aResponse().withStatus(204).withFixedDelay(100)));
    }

    @AfterAll
    static void stopTheStubApi() {
        api.stop();
    }

    @Test
    @DisplayName("as many connections are kept for the next requests as there can be requests in "
            + "flight, not the handful the HTTP client keeps by default")
    void connections_are_kept_up_to_the_concurrency_limit() {
        EngineSettings settings = EngineSettings.defaults()
                .withConcurrency(IN_FLIGHT, IN_FLIGHT, IN_FLIGHT);

        try (OkHttpEngine engine = new OkHttpEngine(settings)) {
            List<CompletableFuture<Interaction>> answers = IntStream.range(0, IN_FLIGHT)
                    .mapToObj(i -> engine.sendAsync(Requests.testCase(HttpMethod.GET, "/widgets"),
                            Requests.get(api.baseUrl() + "/widgets")))
                    .toList();
            assertThat(answers.stream().map(CompletableFuture::join))
                    .allSatisfy(interaction -> assertThat(interaction.isAnswered()).isTrue());

            assertThat(engine.connectionsKept())
                    .describedAs("every connection those requests opened is kept for the next ones")
                    .isEqualTo(IN_FLIGHT);
        }
    }
}

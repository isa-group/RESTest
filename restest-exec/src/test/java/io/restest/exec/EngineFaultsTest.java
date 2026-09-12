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
import com.github.tomakehurst.wiremock.http.Fault;
import io.restest.core.exec.EngineSettings;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionOutcome;
import io.restest.core.model.HttpMethod;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What happens when the API misbehaves: refuses the connection, never answers, hangs up in the
 * middle of a reply.
 *
 * <p>Every one of these has to come back as an interaction that says what went wrong, and the engine
 * has to keep working afterwards. An API broken enough to hang up on us is a finding, and a tool that
 * stopped at the first one would lose both the finding and the rest of the run.
 */
class EngineFaultsTest {

    private static WireMockServer api;

    @BeforeAll
    static void startTheStubApi() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
    }

    @AfterAll
    static void stopTheStubApi() {
        api.stop();
    }

    @BeforeEach
    void resetTheStubApi() {
        api.resetAll();
    }

    @Test
    @DisplayName("a refused connection is reported, and the engine sends the next request")
    void a_refused_connection_is_reported() throws IOException {
        int nothingListening;
        try (ServerSocket closed = new ServerSocket(0)) {
            nothingListening = closed.getLocalPort();
        }
        api.stubFor(get(urlEqualTo("/after")).willReturn(aResponse().withStatus(200)));

        try (OkHttpEngine engine = new OkHttpEngine()) {
            Interaction refused = engine.send(Requests.testCase(HttpMethod.GET, "/gone"),
                    Requests.get("http://localhost:" + nothingListening + "/gone"));

            assertThat(refused.outcome()).isInstanceOf(InteractionOutcome.TransportFailure.class);
            assertThat(((InteractionOutcome.TransportFailure) refused.outcome()).reason())
                    .isNotBlank();
            assertThat(engine.send(Requests.testCase(HttpMethod.GET, "/after"),
                    Requests.get(api.baseUrl() + "/after")).isAnswered()).isTrue();
        }
    }

    @Test
    @DisplayName("an API that never answers costs us the timeout, not the run")
    void a_read_timeout_is_reported() {
        api.stubFor(get(urlEqualTo("/slow")).willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(2000)));

        try (OkHttpEngine engine = new OkHttpEngine(
                EngineSettings.defaults().withReadTimeout(Duration.ofMillis(200)))) {
            Interaction interaction = engine.send(Requests.testCase(HttpMethod.GET, "/slow"),
                    Requests.get(api.baseUrl() + "/slow"));

            assertThat(interaction.isAnswered()).isFalse();
            assertThat(interaction.outcome().toString()).containsIgnoringCase("timeout");
        }
    }

    @Test
    @DisplayName("an API that hangs up without answering at all is reported")
    void an_empty_reply_is_reported() {
        api.stubFor(get(urlEqualTo("/nothing"))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        try (OkHttpEngine engine = new OkHttpEngine()) {
            Interaction interaction = engine.send(Requests.testCase(HttpMethod.GET, "/nothing"),
                    Requests.get(api.baseUrl() + "/nothing"));

            assertThat(interaction.outcome())
                    .isInstanceOf(InteractionOutcome.TransportFailure.class);
        }
    }

    @Test
    @DisplayName("an API that sends nonsense instead of a reply is reported")
    void garbage_instead_of_a_reply_is_reported() {
        api.stubFor(get(urlEqualTo("/garbage"))
                .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE)));

        try (OkHttpEngine engine = new OkHttpEngine()) {
            Interaction interaction = engine.send(Requests.testCase(HttpMethod.GET, "/garbage"),
                    Requests.get(api.baseUrl() + "/garbage"));

            assertThat(interaction.isAnswered()).isFalse();
        }
    }

    /**
     * The one case the stub server cannot produce: a reply that starts correctly, promises a length
     * and then stops. It has to be a hand-written server, because the point is that the status line
     * and the headers arrived and are worth keeping - which is what tells this apart from a
     * connection that failed outright.
     */
    @Test
    @DisplayName("a reply that stops halfway keeps its status, its headers and the part that arrived")
    void a_reply_that_stops_halfway_is_malformed_rather_than_lost() throws Exception {
        try (ServerSocket cutsOff = new ServerSocket(0)) {
            Thread server = Thread.ofVirtual().start(() -> serveHalfAReply(cutsOff));
            try (OkHttpEngine engine = new OkHttpEngine()) {
                Interaction interaction = engine.send(Requests.testCase(HttpMethod.GET, "/half"),
                        Requests.get("http://localhost:" + cutsOff.getLocalPort() + "/half"));

                assertThat(interaction.outcome())
                        .isInstanceOf(InteractionOutcome.MalformedResponse.class);
                InteractionOutcome.MalformedResponse malformed =
                        (InteractionOutcome.MalformedResponse) interaction.outcome();
                assertThat(malformed.statusLine().orElseThrow().statusCode()).isEqualTo(200);
                assertThat(malformed.headers()).isNotEmpty();
                assertThat(new String(malformed.partial().orElseThrow().content(),
                        StandardCharsets.UTF_8)).isEqualTo("half");
                assertThat(malformed.reason()).contains("stopped after 4 bytes");
            }
            server.join(Duration.ofSeconds(5));
        }
    }

    /** Answers one request with a reply that promises a hundred bytes and sends four. */
    private static void serveHalfAReply(ServerSocket socket) {
        try (Socket connection = socket.accept()) {
            connection.getInputStream().read(new byte[1024]);
            OutputStream out = connection.getOutputStream();
            out.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: 100\r\n"
                    + "\r\n"
                    + "half").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            // The client hanging up first is one of the ways this test can end; nothing to do.
        }
    }
}

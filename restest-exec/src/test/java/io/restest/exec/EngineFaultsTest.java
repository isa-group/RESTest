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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
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
            assertThat(waitOf(refused)).describedAs("a refusal is no time limit running out")
                    .isEmpty();
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
            assertThat(waitOf(interaction))
                    .describedAs("the API had the request on an open connection and said nothing")
                    .contains(InteractionOutcome.TransportFailure.Wait.REPLY);
        }
    }

    /**
     * A status line that arrives a byte at a time. Each byte comes long before the read timeout, so
     * only the limit on the whole request ends it - before a status line was ever whole, which is
     * still no reply.
     */
    @Test
    @DisplayName("a reply whose first line never finishes arriving is no reply once the whole "
            + "request has taken as long as it may")
    void a_status_line_that_never_finishes_is_no_reply() throws Exception {
        try (ServerSocket trickles = new ServerSocket(0)) {
            Thread server = Thread.ofVirtual().start(() -> trickle(trickles, "", "H"));
            Duration wait = Duration.ofMillis(500);
            EngineSettings settings = EngineSettings.defaults().withConnectTimeout(wait)
                    .withReadTimeout(wait).withWriteTimeout(wait)
                    .withCallTimeout(Duration.ofSeconds(1));
            try (OkHttpEngine engine = new OkHttpEngine(settings)) {
                Interaction interaction = engine.send(Requests.testCase(HttpMethod.GET, "/slow"),
                        Requests.get("http://localhost:" + trickles.getLocalPort() + "/slow"));

                assertThat(interaction.outcome())
                        .isInstanceOf(InteractionOutcome.TransportFailure.class);
                assertThat(waitOf(interaction))
                        .contains(InteractionOutcome.TransportFailure.Wait.REPLY);
            }
            server.join(Duration.ofSeconds(35));
        }
    }

    /**
     * An address where something takes connections and never says a word: a socket listening and
     * never read, whose connections the operating system completes and leaves waiting. Asked for
     * encryption, the engine waits for the other side's part of agreeing on it, which never comes -
     * so time runs out before there is a connection to the API to send anything on.
     */
    @Test
    @DisplayName("a connection that never finishes being made is a wait for a connection, not for "
            + "a reply")
    void a_connection_that_never_finishes_is_not_a_wait_for_a_reply() throws Exception {
        try (ServerSocket silent = new ServerSocket(0)) {
            try (OkHttpEngine engine = new OkHttpEngine(
                    EngineSettings.defaults().withReadTimeout(Duration.ofMillis(300)))) {
                Interaction interaction = engine.send(Requests.testCase(HttpMethod.GET, "/pets"),
                        Requests.get("https://127.0.0.1:" + silent.getLocalPort() + "/pets"));

                assertThat(interaction.outcome())
                        .isInstanceOf(InteractionOutcome.TransportFailure.class);
                assertThat(waitOf(interaction))
                        .contains(InteractionOutcome.TransportFailure.Wait.CONNECTION);
            }
        }
    }

    /**
     * The API answers, and sends the engine on to another address, where no connection can be made
     * in time. What failed is the second address, not the API, which did reply.
     */
    @Test
    @DisplayName("a redirection to an address that cannot be reached in time is a wait for a "
            + "connection, though the first address answered")
    void a_redirection_nowhere_is_a_wait_for_a_connection() throws Exception {
        try (ServerSocket silent = new ServerSocket(0)) {
            api.stubFor(get(urlEqualTo("/moved")).willReturn(aResponse().withStatus(302)
                    .withHeader("Location",
                            "https://127.0.0.1:" + silent.getLocalPort() + "/there")));
            try (OkHttpEngine engine = new OkHttpEngine(EngineSettings.defaults()
                    .withReadTimeout(Duration.ofMillis(300)).withFollowRedirects(true))) {
                Interaction interaction = engine.send(Requests.testCase(HttpMethod.GET, "/moved"),
                        Requests.get(api.baseUrl() + "/moved"));

                assertThat(waitOf(interaction))
                        .contains(InteractionOutcome.TransportFailure.Wait.CONNECTION);
            }
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
            assertThat(waitOf(interaction)).describedAs("hanging up is no time limit running out")
                    .isEmpty();
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
            assertThat(waitOf(interaction)).isEmpty();
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

    /**
     * A reply whose status line and headers arrive, and whose body then stops with no word of
     * goodbye, as an endpoint that streams does between two things it has to say. The read timeout
     * runs out in the body, after the API has answered.
     */
    @Test
    @DisplayName("a reply whose body stops arriving keeps its status, and is no wait for a reply")
    void a_reply_whose_body_stalls_is_malformed_and_no_wait() throws Exception {
        try (ServerSocket stalls = new ServerSocket(0)) {
            Thread server = Thread.ofVirtual().start(() -> sayThenWait(stalls, "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: 100\r\n"
                    + "\r\n"
                    + "{\"par"));
            try (OkHttpEngine engine = new OkHttpEngine(
                    EngineSettings.defaults().withReadTimeout(Duration.ofMillis(300)))) {
                Interaction interaction = engine.send(Requests.testCase(HttpMethod.GET, "/records"),
                        Requests.get("http://localhost:" + stalls.getLocalPort() + "/records"));

                assertThat(interaction.outcome())
                        .isInstanceOf(InteractionOutcome.MalformedResponse.class);
                assertThat(interaction.statusCode()).contains(200);
                assertThat(interaction.timedOutWaitingForAReply()).isFalse();
            }
            server.join(Duration.ofSeconds(5));
        }
    }

    /**
     * A reply that never ends: an endpoint that streams, or keeps a connection alive by sending a
     * byte every so often. Each byte arrives long before the read timeout runs out, so only a limit
     * on the whole request ends it - and without one, the request would hold its place among those
     * in flight for as long as the run lasts.
     */
    @Test
    @DisplayName("a reply that never stops arriving is cut off once the whole request has taken as "
            + "long as it may, keeping what arrived")
    void a_reply_that_never_ends_is_cut_off() throws Exception {
        try (ServerSocket streams = new ServerSocket(0)) {
            Thread server = Thread.ofVirtual().start(() -> trickle(streams, "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/event-stream\r\n"
                    + "Transfer-Encoding: chunked\r\n"
                    + "\r\n", "1\r\nx\r\n"));
            Duration wait = Duration.ofMillis(500);
            EngineSettings settings = EngineSettings.defaults().withConnectTimeout(wait)
                    .withReadTimeout(wait).withWriteTimeout(wait)
                    .withCallTimeout(Duration.ofSeconds(1));
            try (OkHttpEngine engine = new OkHttpEngine(settings)) {
                long startedAt = System.nanoTime();
                Interaction interaction = engine.send(Requests.testCase(HttpMethod.GET, "/events"),
                        Requests.get("http://localhost:" + streams.getLocalPort() + "/events"));
                Duration took = Duration.ofNanos(System.nanoTime() - startedAt);

                assertThat(took)
                        .describedAs("the server would go on sending for half a minute")
                        .isLessThan(Duration.ofSeconds(15));
                assertThat(interaction.outcome())
                        .isInstanceOf(InteractionOutcome.MalformedResponse.class);
                InteractionOutcome.MalformedResponse cut =
                        (InteractionOutcome.MalformedResponse) interaction.outcome();
                assertThat(cut.statusLine().orElseThrow().statusCode()).isEqualTo(200);
                assertThat(new String(cut.partial().orElseThrow().content(),
                        StandardCharsets.UTF_8)).startsWith("x");
                assertThat(cut.reason()).contains("stopped after");
            }
            server.join(Duration.ofSeconds(35));
        }
    }

    /**
     * Answers one request with what it is given to begin with, and then the same few bytes every
     * tenth of a second, for half a minute or until the client hangs up.
     */
    private static void trickle(ServerSocket socket, String beginning, String every) {
        try (Socket connection = socket.accept()) {
            connection.getInputStream().read(new byte[1024]);
            OutputStream out = connection.getOutputStream();
            out.write(beginning.getBytes(StandardCharsets.UTF_8));
            long until = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < until) {
                out.write(every.getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(100);
            }
        } catch (IOException | InterruptedException e) {
            // The client hanging up is how this test is meant to end; nothing to do.
        }
    }

    /**
     * Answers one request with what it is given, and then says nothing more until the client hangs
     * up.
     */
    private static void sayThenWait(ServerSocket socket, String said) {
        try (Socket connection = socket.accept()) {
            InputStream in = connection.getInputStream();
            in.read(new byte[1024]);
            OutputStream out = connection.getOutputStream();
            out.write(said.getBytes(StandardCharsets.UTF_8));
            out.flush();
            while (in.read() != -1) {
                // Nothing the client sends now is answered.
            }
        } catch (IOException e) {
            // The client hanging up is how this test is meant to end; nothing to do.
        }
    }

    /** What the engine was waiting for when time ran out, or nothing if it did not. */
    private static Optional<InteractionOutcome.TransportFailure.Wait> waitOf(
            Interaction interaction) {
        return interaction.outcome() instanceof InteractionOutcome.TransportFailure failure
                ? failure.timedOutWaitingFor() : Optional.empty();
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

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

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.exec.EngineSettings;
import io.restest.core.execution.Interaction;
import io.restest.core.model.HttpMethod;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Whether the engine stops using a connection once the API has said it will close it after the
 * reply.
 *
 * <p>An API that answers in HTTP/1.0, as Python's own {@code http.server} does unless told
 * otherwise, closes the connection after every reply, and says so by answering in HTTP/1.0 without
 * asking to keep it. Many small APIs under development run on a server like that. A request sent on
 * such a connection after the reply reaches nobody, and comes back with no reply: half of a run's
 * requests, every other one, would be lost that way, and the report would blame the API.
 */
class ConnectionsTheApiClosesTest {

    private static final int REQUESTS = 20;

    @Test
    @DisplayName("every request to an API that answers in HTTP/1.0 and hangs up after each reply "
            + "gets its reply")
    void every_request_to_an_api_that_hangs_up_after_each_reply_is_answered() throws Exception {
        try (OldApi api = OldApi.closingAfterEachReply();
                OkHttpEngine engine = new OkHttpEngine()) {
            List<Interaction> interactions = IntStream.range(0, REQUESTS)
                    .mapToObj(i -> engine.send(Requests.testCase(HttpMethod.GET, "/widgets"),
                            Requests.get(api.url("/widgets"))))
                    .toList();

            assertThat(interactions)
                    .allSatisfy(interaction -> assertThat(interaction.isAnswered())
                            .describedAs(interaction.outcome().toString())
                            .isTrue());
            assertThat(api.requests()).isEqualTo(REQUESTS);
        }
    }

    @Test
    @DisplayName("a request that creates something is sent once, and answered, when the API hangs "
            + "up after each reply")
    void a_post_is_sent_once_and_answered() throws Exception {
        try (OldApi api = OldApi.closingAfterEachReply();
                OkHttpEngine engine = new OkHttpEngine()) {
            List<Interaction> interactions = IntStream.range(0, REQUESTS)
                    .mapToObj(i -> engine.send(Requests.testCase(HttpMethod.POST, "/widgets"),
                            Requests.json(HttpMethod.POST, api.url("/widgets"),
                                    "{\"name\":\"widget " + i + "\"}")))
                    .toList();

            assertThat(interactions)
                    .allSatisfy(interaction -> assertThat(interaction.isAnswered())
                            .describedAs(interaction.outcome().toString())
                            .isTrue());
            assertThat(api.requests())
                    .describedAs("each request reached the API once, none of them twice")
                    .isEqualTo(REQUESTS);
        }
    }

    @Test
    @DisplayName("requests sent several at once to an API that hangs up after each reply are all "
            + "answered")
    void requests_in_flight_together_are_all_answered() throws Exception {
        EngineSettings settings = EngineSettings.defaults().withConcurrency(8, 8, 8);
        try (OldApi api = OldApi.closingAfterEachReply();
                OkHttpEngine engine = new OkHttpEngine(settings)) {
            List<CompletableFuture<Interaction>> answers = IntStream.range(0, REQUESTS * 4)
                    .mapToObj(i -> engine.sendAsync(Requests.testCase(HttpMethod.GET, "/widgets"),
                            Requests.get(api.url("/widgets"))))
                    .toList();

            assertThat(answers.stream().map(CompletableFuture::join))
                    .allSatisfy(interaction -> assertThat(interaction.isAnswered())
                            .describedAs(interaction.outcome().toString())
                            .isTrue());
            assertThat(api.requests()).isEqualTo(REQUESTS * 4);
        }
    }

    @Test
    @DisplayName("an API that answers in HTTP/1.0 and asks to keep the connection keeps it")
    void a_connection_kept_alive_in_http_1_0_is_used_again() throws Exception {
        try (OldApi api = OldApi.keepingConnections(); OkHttpEngine engine = new OkHttpEngine()) {
            List<Interaction> interactions = IntStream.range(0, REQUESTS)
                    .mapToObj(i -> engine.send(Requests.testCase(HttpMethod.GET, "/widgets"),
                            Requests.get(api.url("/widgets"))))
                    .toList();

            assertThat(interactions)
                    .allSatisfy(interaction -> assertThat(interaction.isAnswered()).isTrue());
            assertThat(api.connections())
                    .describedAs("every request went on the one connection the API kept open")
                    .isEqualTo(1);
        }
    }

    /**
     * The API is slower to close the connection than RESTest is to let it go, as Python's is
     * against a run that sends the next request at once: here it never closes it, and notes how
     * RESTest leaves.
     */
    @Test
    @DisplayName("a connection the API said it will close is ended outright once its reply is in, "
            + "so that RESTest holds none of its own addresses after it")
    void a_connection_the_api_closes_is_ended_outright() throws Exception {
        try (OldApi api = OldApi.watchingHowTheClientLeaves()) {
            try (OkHttpEngine engine = new OkHttpEngine()) {
                for (int i = 0; i < 2; i++) {
                    Interaction interaction = engine.send(
                            Requests.testCase(HttpMethod.GET, "/widgets"),
                            Requests.get(api.url("/widgets")));
                    assertThat(interaction.isAnswered()).isTrue();
                }
            }

            assertThat(api.howTheClientLeft(2))
                    .describedAs("a connection closed politely leaves whichever side closed it "
                            + "first holding the address it used for half a minute or more, and "
                            + "a run opening a connection for every request would soon have none "
                            + "left")
                    .containsExactly(OldApi.RESET, OldApi.RESET);
        }
    }

    @ParameterizedTest(name = "{0} with Connection {1}: {2}")
    @MethodSource("replies")
    @DisplayName("a reply in HTTP/1.0 closes the connection unless it asks to keep it; one in "
            + "HTTP/1.1 is left to the HTTP client, which hears Connection: close by itself")
    void which_replies_close_the_connection(Protocol protocol, List<String> connection,
            boolean closes) {
        Response.Builder reply = new Response.Builder()
                .request(new Request.Builder().url("http://localhost/widgets").build())
                .protocol(protocol)
                .code(200)
                .message("OK");
        connection.forEach(value -> reply.addHeader("Connection", value));

        assertThat(ConnectionsTheApiCloses.closesAfter(reply.build())).isEqualTo(closes);
    }

    static Stream<Arguments> replies() {
        return Stream.of(
                Arguments.of(Protocol.HTTP_1_0, List.of(), true),
                Arguments.of(Protocol.HTTP_1_0, List.of("close"), true),
                Arguments.of(Protocol.HTTP_1_0, List.of("keep-alive"), false),
                Arguments.of(Protocol.HTTP_1_0, List.of("Upgrade, Keep-Alive"), false),
                Arguments.of(Protocol.HTTP_1_0, List.of("Upgrade", "keep-alive"), false),
                Arguments.of(Protocol.HTTP_1_0, List.of("keep-alive-ish"), true),
                Arguments.of(Protocol.HTTP_1_1, List.of(), false),
                Arguments.of(Protocol.HTTP_1_1, List.of("close"), false));
    }

    /**
     * An API that answers every request in HTTP/1.0 with a short JSON body, as a handler written
     * for Python's {@code http.server} does.
     *
     * <p>Unless it keeps connections alive, it closes each one as soon as the reply is written,
     * which is what HTTP/1.0 means when the reply does not ask to keep the connection - or, when it
     * watches how the client leaves, waits for the client to end it and notes how it did. When it
     * does keep connections, it says so with {@code Connection: Keep-Alive} and reads the next
     * request on the same one.
     */
    private static final class OldApi implements AutoCloseable {

        /** The client ended the connection outright. */
        static final String RESET = "reset";
        /** The client closed the connection politely. */
        static final String HUNG_UP = "hung up";
        /** The client did not end the connection in the time given. */
        static final String STAYED = "stayed";

        private final ServerSocket socket;
        private final boolean keepsAlive;
        private final boolean watchesHowTheClientLeaves;
        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicInteger connections = new AtomicInteger();
        private final BlockingQueue<String> leavings = new LinkedBlockingQueue<>();

        private OldApi(boolean keepsAlive, boolean watchesHowTheClientLeaves) throws IOException {
            this.socket = new ServerSocket(0);
            this.keepsAlive = keepsAlive;
            this.watchesHowTheClientLeaves = watchesHowTheClientLeaves;
            Thread.ofVirtual().start(this::accept);
        }

        /** Python's {@code http.server} as it comes: one reply per connection. */
        static OldApi closingAfterEachReply() throws IOException {
            return new OldApi(false, false);
        }

        /** The same, except that it leaves each connection for the client to end. */
        static OldApi watchingHowTheClientLeaves() throws IOException {
            return new OldApi(false, true);
        }

        /** An API that answers in HTTP/1.0 and keeps each connection for the next request. */
        static OldApi keepingConnections() throws IOException {
            return new OldApi(true, false);
        }

        String url(String path) {
            return "http://localhost:" + socket.getLocalPort() + path;
        }

        /** How many requests the API read, whole, and answered. */
        int requests() {
            return requests.get();
        }

        /** How many connections the API accepted. */
        int connections() {
            return connections.get();
        }

        /** How the client ended each of the first connections, in the order it ended them. */
        List<String> howTheClientLeft(int connections) throws InterruptedException {
            List<String> left = new ArrayList<>();
            for (int i = 0; i < connections; i++) {
                left.add(Objects.requireNonNullElse(leavings.poll(10, TimeUnit.SECONDS), "?"));
            }
            return left;
        }

        private void accept() {
            try {
                while (true) {
                    Socket connection = socket.accept();
                    connections.incrementAndGet();
                    Thread.ofVirtual().start(() -> serve(connection));
                }
            } catch (IOException e) {
                // The server socket closed: the test is over.
            }
        }

        private void serve(Socket connection) {
            try (connection) {
                InputStream in = new BufferedInputStream(connection.getInputStream());
                OutputStream out = connection.getOutputStream();
                do {
                    if (!readRequest(in)) {
                        return;
                    }
                    requests.incrementAndGet();
                    String body = "{\"id\":" + requests.get() + "}";
                    out.write(("HTTP/1.0 200 OK\r\n"
                            + "Server: SimpleHTTP/0.6 Python/3.9.6\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + body.length() + "\r\n"
                            + (keepsAlive ? "Connection: Keep-Alive\r\n" : "")
                            + "\r\n"
                            + body).getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                } while (keepsAlive);
                if (watchesHowTheClientLeaves) {
                    leavings.add(howTheClientLeaves(connection, in));
                }
            } catch (IOException e) {
                // The client went away; nothing to answer.
            }
        }

        /** Waits for the client to end the connection, and says how it did. */
        private static String howTheClientLeaves(Socket connection, InputStream in)
                throws IOException {
            connection.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
            try {
                return in.read() == -1 ? HUNG_UP : "sent more";
            } catch (SocketTimeoutException e) {
                return STAYED;
            } catch (SocketException e) {
                return RESET;
            }
        }

        /** Reads one request, its body included; false if the connection ended before one came. */
        private static boolean readRequest(InputStream in) throws IOException {
            int contentLength = 0;
            String line = readLine(in);
            if (line == null) {
                return false;
            }
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(lower.substring(15).trim());
                }
            }
            return in.readNBytes(contentLength).length == contentLength;
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n') {
                    String read = line.toString(StandardCharsets.US_ASCII);
                    return read.endsWith("\r") ? read.substring(0, read.length() - 1) : read;
                }
                line.write(b);
            }
            return null;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}

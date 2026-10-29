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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An engine given a rate to keep to, against an API that stops answering for a while and then
 * answers everything at once: the moment requests pile up behind the ones in flight, which is when a
 * rate kept only by whoever hands requests over would let them all go together.
 *
 * <p>The times are taken where the requests arrive, as the API's owner would see them. The API is a
 * few lines over a plain socket, because none of the stand-ins at hand can hold its answers until it
 * is told to let them all go.
 */
class KeepingToARateTest {

    private static final int IN_FLIGHT = 4;
    private static final Duration BETWEEN = Duration.ofMillis(100);

    private final List<Long> arrivals = new CopyOnWriteArrayList<>();
    private final CountDownLatch answerAtLast = new CountDownLatch(1);
    private final CountDownLatch everyFirstArrived = new CountDownLatch(IN_FLIGHT);
    private final AtomicInteger seen = new AtomicInteger();
    private ServerSocket api;

    @BeforeEach
    void anApiThatStallsAndThenAnswersEverything() throws IOException {
        api = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread.ofVirtual().start(() -> {
            while (!api.isClosed()) {
                try {
                    Socket connection = api.accept();
                    Thread.ofVirtual().start(() -> answer(connection));
                } catch (IOException closed) {
                    return;
                }
            }
        });
    }

    /** Answers every request on one connection, the first few only once it is told to. */
    private void answer(Socket connection) {
        try (connection;
                BufferedReader in = new BufferedReader(new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.ISO_8859_1));
                OutputStream out = connection.getOutputStream()) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.isEmpty()) {
                    continue;
                }
                // The blank line after the headers: a whole request, since a GET carries no body.
                arrivals.add(System.nanoTime());
                if (seen.incrementAndGet() <= IN_FLIGHT) {
                    everyFirstArrived.countDown();
                    answerAtLast.await(10, TimeUnit.SECONDS);
                }
                out.write("HTTP/1.1 204 No Content\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                out.flush();
            }
        } catch (IOException | InterruptedException gone) {
            // The engine hung up, or the test is over.
        }
    }

    @AfterEach
    void stopTheApi() throws IOException {
        answerAtLast.countDown();
        api.close();
    }

    @Test
    @DisplayName("requests that waited behind a stall go out one turn apart when the answers come "
            + "back together, never in a burst")
    void no_burst_after_a_stall() throws InterruptedException {
        EngineSettings settings = EngineSettings.defaults()
                .withConcurrency(1, IN_FLIGHT, IN_FLIGHT)
                .withMostRequestsPerMinute((int) (Duration.ofMinutes(1).toMillis()
                        / BETWEEN.toMillis()));
        String url = "http://127.0.0.1:" + api.getLocalPort() + "/";

        try (OkHttpEngine engine = new OkHttpEngine(settings)) {
            List<CompletableFuture<Interaction>> sent = IntStream.range(0, 2 * IN_FLIGHT)
                    .mapToObj(i -> engine.sendAsync(Requests.testCase(HttpMethod.GET, "/"),
                            Requests.get(url)))
                    .toList();
            assertThat(everyFirstArrived.await(10, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(300);
            answerAtLast.countDown();
            sent.forEach(CompletableFuture::join);
        }

        assertThat(arrivals).hasSize(2 * IN_FLIGHT);
        // Those that waited behind the stall, from the first to go once the answers came back. The
        // ones before it are left out: the first request of a run also opens a connection, and
        // arrives late by however long that takes.
        List<Duration> gaps = new ArrayList<>();
        for (int i = IN_FLIGHT + 1; i < arrivals.size(); i++) {
            gaps.add(Duration.ofNanos(arrivals.get(i) - arrivals.get(i - 1)));
        }
        assertThat(gaps)
                .describedAs("a tenth of a second between any two, less what a busy machine adds "
                        + "or takes on the way; a burst would be a few microseconds apart")
                .hasSize(IN_FLIGHT - 1)
                .allSatisfy(gap -> assertThat(gap).isGreaterThan(BETWEEN.dividedBy(2)));
    }
}

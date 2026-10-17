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

import io.restest.core.execution.InteractionOutcome.TransportFailure.Wait;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Which failures the engine records as one of its time limits running out, and what it was waiting
 * for then.
 *
 * <p>The engine's own tests make the real failures happen against real sockets. These name each
 * kind of failure directly, so that every case is pinned whether or not a socket can be made to
 * produce it on the machine the build runs on.
 */
class TimedOutWaitingForTest {

    @Test
    @DisplayName("a wait on the connection that ran out, with a connection open, is a wait for the "
            + "reply")
    void a_socket_timeout_on_an_open_connection_is_a_wait_for_the_reply() {
        assertThat(OkHttpEngine.timedOutWaitingFor(new SocketTimeoutException("timeout"), true))
                .contains(Wait.REPLY);
    }

    @Test
    @DisplayName("the same with no connection open yet is a wait for a connection")
    void a_socket_timeout_with_no_connection_is_a_wait_for_a_connection() {
        assertThat(OkHttpEngine.timedOutWaitingFor(
                new SocketTimeoutException("Connect timed out"), false))
                .contains(Wait.CONNECTION);
    }

    @Test
    @DisplayName("the limit on the whole request running out is a timeout, either way")
    void the_whole_request_running_out_is_a_timeout() {
        InterruptedIOException wholeRequest = new InterruptedIOException("timeout");

        assertThat(OkHttpEngine.timedOutWaitingFor(wholeRequest, true)).contains(Wait.REPLY);
        assertThat(OkHttpEngine.timedOutWaitingFor(wholeRequest, false))
                .contains(Wait.CONNECTION);
    }

    @Test
    @DisplayName("the run stopping is not a timeout, though it reaches the engine the same way")
    void an_interrupted_thread_is_not_a_timeout() {
        Thread.currentThread().interrupt();
        try {
            assertThat(OkHttpEngine.timedOutWaitingFor(
                    new InterruptedIOException("interrupted"), true)).isEmpty();
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failuresThatAreNoTimeout")
    @DisplayName("a connection refused, cut or reset, an address not found, or RESTest's own "
            + "mistake is no timeout")
    void other_failures_are_no_timeout(Exception failure) {
        assertThat(OkHttpEngine.timedOutWaitingFor(failure, true)).isEmpty();
        assertThat(OkHttpEngine.timedOutWaitingFor(failure, false)).isEmpty();
    }

    static Stream<Exception> failuresThatAreNoTimeout() {
        return Stream.of(
                new ConnectException("Failed to connect to localhost/127.0.0.1:1"),
                new UnknownHostException("nowhere.invalid"),
                new SocketException("Connection reset"),
                new IOException("unexpected end of stream on http://localhost:9966/..."),
                new IllegalStateException("something of RESTest's own went wrong"));
    }
}

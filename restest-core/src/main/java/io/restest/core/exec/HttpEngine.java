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
package io.restest.core.exec;

import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.TestCase;
import java.util.concurrent.CompletableFuture;

/**
 * Sends requests to the API under test and reports, for each one, exactly what was sent and exactly
 * what came back.
 *
 * <p>This is the only door out of RESTest onto the network. Everything on this side of it - the
 * generator that invents the requests, the oracles that judge the answers, the reports - deals in
 * {@link Interaction} records and never opens a socket, so which HTTP client is used, and how it is
 * tuned, is a fact about the one class implementing this interface rather than about the rest of the
 * tool.
 *
 * <p><strong>Nothing the network does is an error here.</strong> A refused connection, a request that
 * times out, a reply that stops halfway: every one of them comes back as an {@link Interaction} whose
 * outcome says what happened, and the run carries on. An API that is broken enough to hang up on us
 * is precisely what we are looking for, so treating it as a failure of the tool would throw away the
 * finding and the rest of the run with it.
 *
 * <p>An engine is built once and used until the run ends. It is safe to use from many threads at the
 * same time, it decides for itself how many requests to keep in flight - raising that number against
 * a fast API and lowering it against a struggling one - and it remembers how much of the run was
 * spent with nothing in flight at all, which is what {@link #statistics()} is for. Two engines can
 * run side by side in one program without interfering.
 */
public interface HttpEngine extends AutoCloseable {

    /**
     * Sends one request and promises the interaction it produced.
     *
     * <p>The call returns immediately; the request is sent on a thread of the engine's own, and may
     * wait a moment first if the engine already has as many requests in flight as it is currently
     * willing to. The returned promise is completed with the interaction - answered, malformed or
     * failed - and is not completed with an exception for anything the network did.
     *
     * @param testCase what was being tried, carried through so that the interaction can say which
     *     test it belongs to
     * @param request what to send
     * @return a promise of the interaction
     */
    CompletableFuture<Interaction> sendAsync(TestCase testCase, HttpRequestRecord request);

    /**
     * Sends one request and waits for the interaction.
     *
     * <p>The convenience for a caller with one thing to do and no reason to be asynchronous about
     * it - a test, or a single step of a sequence that cannot continue without the answer.
     *
     * @param testCase what was being tried
     * @param request what to send
     * @return the interaction, however it turned out
     */
    default Interaction send(TestCase testCase, HttpRequestRecord request) {
        return sendAsync(testCase, request).join();
    }

    /** What the engine has done so far, and how much of the time it spent doing nothing. */
    EngineStatistics statistics();

    /**
     * Releases the connections and threads the engine is holding.
     *
     * <p>Closing does not throw, and closing twice is harmless. Requests still in flight are given
     * no special treatment: a caller that wants their answers waits for them first.
     */
    @Override
    void close();
}

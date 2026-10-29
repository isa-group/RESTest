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
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Sends requests to the API under test and reports, for each one, exactly what was sent and exactly
 * what came back - save for a key the person running the tool handed over, which the door that adds
 * it to requests hides in everything it reports (see {@link io.restest.core.auth.CredentialedEngine}).
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
     * Sends nothing more for this long, because the API asked to be left alone.
     *
     * <p>Requests already handed over and not yet sent wait until then; those already in flight come
     * back as usual. Afterwards the engine starts again from the fewest requests in flight it allows
     * and climbs as it always does, since the API that asked has just shown it cannot take what it
     * was being sent. A hold that ends sooner than one already in force changes nothing.
     *
     * @param howLong how long to send nothing, from now
     */
    void holdFor(Duration howLong);

    /**
     * Never sends what it is holding back because the API asked to be left alone, or because the
     * rate it keeps to has not given it a turn yet: each such request comes back at once as one that
     * never went out.
     *
     * <p>For when a run stops sending - its time is up, or it was stopped - so that nothing held back
     * reaches the API after that. A request waiting only for room among those in flight is untouched,
     * and so is one whose turn has come, and a hold that has already ended.
     */
    void dropWhatIsHeld();

    /**
     * How long a request handed over now would wait before the rate the engine keeps to lets it go,
     * counting the turns of those handed over before it and not yet sent.
     *
     * <p>A caller that builds requests only when they can go keeps them from piling up inside the
     * engine, built long before they are sent. Room among the requests in flight is not counted: that
     * depends on answers nobody can foresee.
     *
     * @return that wait; nothing for an engine that keeps to no rate, which is what this answers
     *     unless an engine says otherwise
     */
    default Duration untilItsTurn() {
        return Duration.ZERO;
    }

    /**
     * Releases the connections and threads the engine is holding.
     *
     * <p>Closing does not throw, and closing twice is harmless. Requests still in flight are given
     * no special treatment: a caller that wants their answers waits for them first. A request handed
     * over and still waiting to be sent is not sent: it comes back as one that never went out.
     */
    @Override
    void close();
}

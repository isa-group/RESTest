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

import io.restest.core.exec.EngineStatistics;
import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Keeps track of what the engine is doing with its time: how many requests it has sent, how long
 * they took, how many were ever in flight at once, and - the point of the whole class - how much of
 * the run passed with nothing in flight at all.
 *
 * <p>Idle time is what tells a slow tool from a slow API. Against an API that takes three seconds to
 * answer, a well-behaved run sends few requests per minute and is almost never idle, because it is
 * always waiting on the API. A run that spends its time computing instead of testing is idle no
 * matter how fast the API is. That is the number this class exists to produce, and it is why the
 * project measures idle time rather than requests per second.
 *
 * <p>The clock starts when the engine is built, which is the start of the run, and it keeps running
 * to the moment somebody asks. That is deliberate and it is the whole point: the failure this
 * measurement exists to catch is a tool that spends the first minutes of its budget computing before
 * it sends anything, and a clock that only started at the first request would report that run as
 * perfectly efficient. Time before the first request and time after the last answer are both time
 * the engine had nothing in flight and could have.
 *
 * <p>Where the time comes from is a parameter, so that the tests can hand it a clock they control
 * and assert exact numbers instead of sleeping and hoping.
 */
final class EngineActivity {

    private final LongSupplier nanoTime;
    private final long startedAt;

    private int inFlight;
    private int peakConcurrency;
    private long idleNanos;
    private long idleSince;
    private long requestsSent;
    private long totalResponseNanos;

    EngineActivity(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
        this.startedAt = nanoTime.getAsLong();
        this.idleSince = startedAt;
    }

    /** A request is about to go out; the engine stops being idle. */
    synchronized void requestStarted() {
        long now = nanoTime.getAsLong();
        if (inFlight == 0) {
            idleNanos += now - idleSince;
        }
        inFlight++;
        peakConcurrency = Math.max(peakConcurrency, inFlight);
    }

    /**
     * A request is done, however it turned out.
     *
     * @param elapsed how long that one request took, from going out to being finished with
     */
    synchronized void requestFinished(Duration elapsed) {
        requestsSent++;
        totalResponseNanos += Math.max(0, elapsed.toNanos());
        inFlight--;
        if (inFlight == 0) {
            idleSince = nanoTime.getAsLong();
        }
    }

    /**
     * An attempt that never reached the network - an address nothing could be sent to, a run
     * interrupted before the request went out - which still counts as a request the engine failed to
     * get an answer to.
     *
     * <p>Counting these matters: a run whose every address is unusable produces nothing but failures,
     * and one that reported "no requests sent, no time spent" would read as a flawless run rather
     * than as the broken one it is.
     */
    synchronized void requestNeverSent() {
        requestsSent++;
    }

    /**
     * Everything above, as of this moment.
     *
     * @param concurrencyLimit how many requests the engine is currently willing to have in flight,
     *     which this class does not decide and only reports
     * @return a snapshot that will not change afterwards
     */
    synchronized EngineStatistics snapshot(int concurrencyLimit) {
        long now = nanoTime.getAsLong();
        long wallNanos = now - startedAt;
        long idle = idleNanos + (inFlight == 0 ? now - idleSince : 0);
        return new EngineStatistics(requestsSent, Duration.ofNanos(wallNanos),
                Duration.ofNanos(idle), Duration.ofNanos(totalResponseNanos), peakConcurrency,
                concurrencyLimit);
    }
}

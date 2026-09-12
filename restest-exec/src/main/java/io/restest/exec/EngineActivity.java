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
 * <p>The clock starts at the first request, not when the engine is built: time spent before anybody
 * asked for anything is nobody's waste. Time after the last answer does count, because from then on
 * the engine had nothing in flight and could have.
 *
 * <p>Where the time comes from is a parameter, so that the tests can hand it a clock they control
 * and assert exact numbers instead of sleeping and hoping.
 */
final class EngineActivity {

    private final LongSupplier nanoTime;

    private boolean started;
    private long startedAt;
    private int inFlight;
    private int peakConcurrency;
    private long idleNanos;
    private long idleSince;
    private long requestsSent;
    private long totalResponseNanos;

    EngineActivity(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    /** A request is about to go out; the engine stops being idle. */
    synchronized void requestStarted() {
        long now = nanoTime.getAsLong();
        if (!started) {
            started = true;
            startedAt = now;
        } else if (inFlight == 0) {
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
     * Everything above, as of this moment.
     *
     * @param concurrencyLimit how many requests the engine is currently willing to have in flight,
     *     which this class does not decide and only reports
     * @return a snapshot that will not change afterwards
     */
    synchronized EngineStatistics snapshot(int concurrencyLimit) {
        long now = nanoTime.getAsLong();
        long wallNanos = started ? now - startedAt : 0;
        long idle = idleNanos + (started && inFlight == 0 ? now - idleSince : 0);
        return new EngineStatistics(requestsSent, Duration.ofNanos(wallNanos),
                Duration.ofNanos(idle), Duration.ofNanos(totalResponseNanos), peakConcurrency,
                concurrencyLimit);
    }
}

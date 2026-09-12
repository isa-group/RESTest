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

import java.time.Duration;
import java.util.Objects;

/**
 * What an engine did with the time it was given: how many requests it sent, how long it ran, and -
 * the number this project cares most about - how much of that time nothing at all was in flight.
 *
 * <p>Idle time is the honest measure of whether the tool itself is slow. Requests per second is not:
 * an API that takes three seconds to answer will produce a low rate no matter how good the tool is,
 * so a slow API and a slow tool would look identical. Idle time separates them. It counts only the
 * time during which the engine was waiting on nothing - not on the API, on itself - so against a
 * slow API a good run shows low throughput and almost no idle time, while a run that spends its
 * budget thinking instead of testing shows idle time however fast the API is.
 *
 * <p>This is a snapshot, not a live view. Asking an engine for its statistics twice gives two values
 * describing two moments; neither one changes afterwards.
 *
 * @param requestsSent how many requests were sent and answered, or failed trying
 * @param wallClock how long the engine has been running, from the first request to this snapshot
 * @param idle how much of that time no request was in flight
 * @param totalResponseTime the sum of the time every request took. It can exceed the wall clock,
 *     because requests overlap; divided by {@link #requestsSent()} it gives the average
 * @param peakConcurrency the most requests that were ever in flight at the same moment
 * @param concurrencyLimit how many the engine was willing to have in flight at this moment, which
 *     moves up and down as the API turns out to be fast or slow
 */
public record EngineStatistics(
        long requestsSent,
        Duration wallClock,
        Duration idle,
        Duration totalResponseTime,
        int peakConcurrency,
        int concurrencyLimit) {

    public EngineStatistics {
        nonNegative(wallClock, "wallClock");
        nonNegative(idle, "idle");
        nonNegative(totalResponseTime, "totalResponseTime");
        if (requestsSent < 0) {
            throw new IllegalArgumentException("requestsSent cannot be negative: " + requestsSent);
        }
        if (peakConcurrency < 0) {
            throw new IllegalArgumentException(
                    "peakConcurrency cannot be negative: " + peakConcurrency);
        }
        if (concurrencyLimit < 0) {
            throw new IllegalArgumentException(
                    "concurrencyLimit cannot be negative: " + concurrencyLimit);
        }
        if (idle.compareTo(wallClock) > 0) {
            throw new IllegalArgumentException("idle time (" + idle + ") cannot exceed the wall "
                    + "clock (" + wallClock + "): it is part of it, not additional to it");
        }
    }

    /** An engine that has not sent anything yet. */
    public static EngineStatistics none() {
        return new EngineStatistics(0, Duration.ZERO, Duration.ZERO, Duration.ZERO, 0, 0);
    }

    /** The part of the run during which at least one request was in flight. */
    public Duration busy() {
        return wallClock.minus(idle);
    }

    /**
     * The share of the run during which nothing was in flight, between 0 and 1.
     *
     * @return 0 for a run that has not started, so that an empty run reads as "nothing wasted"
     *     rather than as "everything wasted"
     */
    public double idleFraction() {
        long total = wallClock.toNanos();
        return total == 0 ? 0.0 : (double) idle.toNanos() / total;
    }

    /** How long the average request took, or zero if none were sent. */
    public Duration meanResponseTime() {
        return requestsSent == 0 ? Duration.ZERO : totalResponseTime.dividedBy(requestsSent);
    }

    @Override
    public String toString() {
        return "EngineStatistics[" + requestsSent + " requests in " + wallClock + ", idle "
                + Math.round(idleFraction() * 100) + "%, mean response " + meanResponseTime()
                + ", peak concurrency " + peakConcurrency + ", limit " + concurrencyLimit + "]";
    }

    private static void nonNegative(Duration value, String what) {
        Objects.requireNonNull(value, what);
        if (value.isNegative()) {
            throw new IllegalArgumentException(what + " cannot be negative: " + value);
        }
    }
}

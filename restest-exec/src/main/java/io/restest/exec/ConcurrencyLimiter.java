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

import io.restest.core.exec.EngineSettings;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Decides how many requests the engine may have in flight at the same time, and changes its mind as
 * the API turns out to be fast or slow.
 *
 * <p>A fixed number would be wrong either way. Too low, and a slow API is met by waiting, which is
 * exactly the waste the project measures itself on. Too high, and a test tool becomes a load
 * generator against somebody's staging environment. So the number moves between a floor and a
 * ceiling the user sets, and the API's own answers move it:
 *
 * <ul>
 *   <li>Answers keep arriving at about the speed recent answers have been arriving at, and every
 *       slot is in use: allow one more. This is how the engine finds out that the API could have
 *       been answering four questions at once all along.
 *   <li>An answer takes much longer than recent ones have been taking: requests are queueing up
 *       somewhere, so take one slot away.
 *   <li>A request failed outright - refused, timed out, hung up on: halve the number at once. An
 *       API that is falling over should be asked less, quickly, not gradually.
 * </ul>
 *
 * <p>"Recent answers" is deliberately not "the quickest answer ever seen". An API under test answers
 * some requests in milliseconds - a 404 for an identifier that does not exist, a 400 rejecting input
 * the tool deliberately made invalid - and a limiter that took the quickest of those as its idea of
 * normal would decide, for the rest of the run, that every ordinary answer was a sign of trouble. So
 * the comparison is against a running average that follows the API rather than against a record that
 * only ever falls.
 *
 * <p>The number only grows when the engine actually ran out of slots. Sixteen quick answers sent one
 * after another are no evidence that the API would have coped with sixteen at once, and a limiter
 * that grew on that evidence would arrive at a busy API already at full speed. Whenever the number
 * changes, that evidence is spent: it has to be earned again at the new size.
 */
final class ConcurrencyLimiter {

    /**
     * How much slower than the best answer so far counts as "the API is struggling". Two is
     * deliberately forgiving: normal APIs vary by more than a few percent, and reacting to that
     * would leave the limit oscillating rather than settling.
     */
    private static final double SLOWDOWN_FACTOR = 2.0;

    /**
     * How much of the running average one answer replaces. A fifth: slow enough that a single odd
     * answer barely moves it, quick enough to follow an API that genuinely changes pace within a
     * dozen requests.
     */
    private static final double WEIGHT_OF_ONE_ANSWER = 0.2;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition slotFreed = lock.newCondition();
    private final int minimum;
    private final int maximum;

    private int limit;
    private int inFlight;
    private boolean ranOutOfSlots;
    private double usualResponseNanos;

    ConcurrencyLimiter(EngineSettings settings) {
        this.minimum = settings.minConcurrency();
        this.maximum = settings.maxConcurrency();
        this.limit = settings.initialConcurrency();
    }

    /**
     * Waits until there is a slot, and takes it.
     *
     * @throws InterruptedException if the caller is interrupted while waiting, which the engine
     *     turns into a failed interaction rather than losing
     */
    void acquire() throws InterruptedException {
        lock.lock();
        try {
            while (inFlight >= limit) {
                ranOutOfSlots = true;
                slotFreed.await();
            }
            inFlight++;
            if (inFlight == limit) {
                ranOutOfSlots = true;
            }
        } finally {
            lock.unlock();
        }
    }

    /** Gives the slot back, whatever happened to the request. */
    void release() {
        lock.lock();
        try {
            inFlight--;
            slotFreed.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Tells the limiter how that request went, which is what moves the number.
     *
     * @param responseNanos how long the request took
     * @param failed whether it failed outright rather than being answered
     */
    void observe(long responseNanos, boolean failed) {
        lock.lock();
        try {
            int before = limit;
            if (failed) {
                limit = Math.max(minimum, limit / 2);
            } else {
                boolean struggling = usualResponseNanos > 0
                        && responseNanos > usualResponseNanos * SLOWDOWN_FACTOR;
                usualResponseNanos = usualResponseNanos == 0
                        ? responseNanos
                        : usualResponseNanos * (1 - WEIGHT_OF_ONE_ANSWER)
                                + responseNanos * WEIGHT_OF_ONE_ANSWER;
                if (struggling) {
                    limit = Math.max(minimum, limit - 1);
                } else if (ranOutOfSlots && limit < maximum) {
                    limit = limit + 1;
                }
            }
            if (limit != before) {
                // Whatever the old size proved about the API, it proved about the old size. The
                // evidence for changing again has to be gathered at the new one.
                ranOutOfSlots = false;
            }
            if (limit > before) {
                slotFreed.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /** How many requests the engine is willing to have in flight right now. */
    int limit() {
        lock.lock();
        try {
            return limit;
        } finally {
            lock.unlock();
        }
    }
}

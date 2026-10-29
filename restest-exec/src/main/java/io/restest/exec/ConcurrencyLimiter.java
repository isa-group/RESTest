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
import java.time.Duration;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

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
 *
 * <p>An API can also say so in words: "too many requests, leave me alone for a while". Then the
 * engine is held: no slot is given to anybody until the time is up, whatever the number says, and
 * the number falls to the floor, so that what follows the wait starts from one request at a time and
 * earns its way back up. Requests waiting out a hold are not evidence that the engine ran out of
 * room - the API asked for quiet - so they earn nothing.
 *
 * <p>The person running the tool can ask for a rate as well: at most so many requests a minute,
 * which somebody testing another person's server can promise its owner. Then no two requests take a
 * slot closer together than a minute divided by that number, however many slots are free - evenly,
 * never a burst to catch up after a quiet spell. A request waiting for its turn holds no slot and,
 * like one waiting out a hold, earns nothing. The limiter also counts the requests handed to the
 * engine and not yet given a slot, so that it can say how long one more would wait for its turn;
 * whoever builds requests can then build each when it can go, rather than long before.
 */
final class ConcurrencyLimiter {

    /** The longest hold that is counted: some seventy years, far beyond any run. */
    private static final Duration LONGEST_HOLD = Duration.ofNanos(Long.MAX_VALUE / 4);

    /**
     * How much of the running average one answer replaces. A fifth: slow enough that a single odd
     * answer barely moves it, quick enough to follow an API that genuinely changes pace within a
     * dozen requests.
     */
    private static final double WEIGHT_OF_ONE_ANSWER = 0.2;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition slotFreed = lock.newCondition();

    /**
     * What a request waiting for its turn waits on: a condition of its own, so that a slot given back
     * wakes a request that can take it rather than one that would only go back to waiting.
     */
    private final Condition turnComes = lock.newCondition();
    private final LongSupplier nanoTime;
    private final int minimum;
    private final int maximum;

    /** How much slower than the best answer so far counts as "the API is struggling". */
    private final double slowdownFactor;

    /** The least time between two requests taking a slot, on the clock below; none when zero. */
    private final long betweenRequests;

    private int limit;
    private int inFlight;
    private boolean ranOutOfSlots;
    private double usualResponseNanos;

    /**
     * Whether a hold may be in force, and until when no slot is given, on the clock below. The clock
     * is read only while one may be, so that a run never held pays nothing for holds.
     */
    private boolean held;
    private long heldUntil;
    private boolean closed;

    /**
     * Whether a request waiting out a hold, or waiting for its turn, goes away without a slot rather
     * than wait.
     */
    private boolean dropTheHeld;

    /**
     * When the next request may take a slot, while the rate is kept to. It starts at the moment the
     * limiter is built, so that the first request goes at once wherever the clock happens to begin.
     * Written under the lock, and read without it by whoever counts the engine's idle time.
     */
    private volatile long nextTurn;

    /** How many requests have been handed to the engine and not yet given a slot, or gone. */
    private int waitingToGo;

    ConcurrencyLimiter(EngineSettings settings) {
        this(settings, System::nanoTime);
    }

    /** The same, telling the time of a hold by the given clock. */
    ConcurrencyLimiter(EngineSettings settings, LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
        this.minimum = settings.minConcurrency();
        this.maximum = settings.maxConcurrency();
        this.slowdownFactor = settings.slowdownFactor();
        this.limit = settings.initialConcurrency();
        this.betweenRequests = settings.timeBetweenRequests().map(Duration::toNanos).orElse(0L);
        this.nextTurn = nanoTime.getAsLong();
    }

    /**
     * One more request has been handed to the engine and will ask for a slot. Said on the thread
     * that handed it over, before the request leaves it, so that the next question about turns
     * already counts it. Not counted when no rate is kept to, so that a run without one pays
     * nothing for it.
     */
    void handedOver() {
        if (betweenRequests == 0) {
            return;
        }
        lock.lock();
        try {
            waitingToGo++;
        } finally {
            lock.unlock();
        }
    }

    /** A request handed over will never ask for a slot: it could not be built, say. */
    void notGoing() {
        if (betweenRequests == 0) {
            return;
        }
        lock.lock();
        try {
            waitingToGo--;
        } finally {
            lock.unlock();
        }
    }

    /**
     * When the next request may take a slot, on the clock this limiter was given: the moment it was
     * built, for as long as no rate is kept to.
     */
    long nextTurn() {
        return nextTurn;
    }

    /**
     * How long a request handed over now would wait for its turn: until the next turn, and one turn
     * more for each request handed over before it and not yet given a slot. Room among the requests
     * in flight is not counted, since that depends on answers nobody can foresee.
     *
     * @return that wait, or none when no rate is kept to
     */
    Duration untilItsTurn() {
        if (betweenRequests == 0) {
            return Duration.ZERO;
        }
        lock.lock();
        try {
            long untilTheNext = Math.max(0, nextTurn - nanoTime.getAsLong());
            long longest = LONGEST_HOLD.toNanos();
            long theOthers = Math.min(waitingToGo, longest / betweenRequests) * betweenRequests;
            return Duration.ofNanos(Math.min(longest, untilTheNext + theOthers));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Waits until there is a slot, and takes it - unless the engine is closed first.
     *
     * @return whether a slot was taken; not when the engine was closed while this waited, or what
     *     is held was dropped while this waited out a hold or for its turn, and the request is then
     *     never sent
     * @throws InterruptedException if the caller is interrupted while waiting, which the engine
     *     turns into a failed interaction rather than losing
     */
    boolean acquire() throws InterruptedException {
        lock.lock();
        try {
            while (!closed) {
                // Read only while a hold or a rate may make the time matter, so that a run with
                // neither pays nothing for them.
                long now = held || betweenRequests > 0 ? nanoTime.getAsLong() : 0;
                // Counted in the clock's own units, which go round rather than overflow.
                long left = held ? heldUntil - now : 0;
                if (left > 0) {
                    if (dropTheHeld) {
                        return false;
                    }
                    slotFreed.awaitNanos(left);
                    continue;
                }
                held = false;
                long untilTheTurn = betweenRequests > 0 ? nextTurn - now : 0;
                if (untilTheTurn > 0) {
                    if (dropTheHeld) {
                        return false;
                    }
                    // Several may wake at once and the lock decides which takes the turn. With a
                    // caller that builds each request when it can go there is hardly ever more than
                    // one, so their order is not kept.
                    turnComes.awaitNanos(untilTheTurn);
                    continue;
                }
                if (inFlight >= limit) {
                    ranOutOfSlots = true;
                    slotFreed.await();
                } else {
                    inFlight++;
                    if (inFlight == limit) {
                        ranOutOfSlots = true;
                    }
                    if (betweenRequests > 0) {
                        // From now, not from the turn that was due: a quiet spell is never made up
                        // for with a burst.
                        nextTurn = now + betweenRequests;
                    }
                    return true;
                }
            }
            return false;
        } finally {
            // Counted only while a rate is kept to, as handing it over was.
            if (betweenRequests > 0) {
                waitingToGo--;
            }
            lock.unlock();
        }
    }

    /**
     * Gives no slot to anybody for this long, and starts again from the floor afterwards. A hold
     * that would end sooner than one already in force leaves that one as it is.
     *
     * @param howLong how long to give nothing, from now
     */
    void holdFor(Duration howLong) {
        long nanos = howLong.isNegative() ? 0
                : (howLong.compareTo(LONGEST_HOLD) > 0 ? LONGEST_HOLD : howLong).toNanos();
        lock.lock();
        try {
            long until = nanoTime.getAsLong() + nanos;
            if (!held || until - heldUntil > 0) {
                heldUntil = until;
            }
            held = true;
            limit = minimum;
            ranOutOfSlots = false;
            // Whoever is waiting for a slot now waits for the hold instead.
            slotFreed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whoever is waiting out a hold, or waiting for a turn that has not come, now or later, goes away
     * without a slot rather than wait. Whoever is waiting only for a slot, with no hold in force and
     * the turn already come, is untouched.
     */
    void dropWhatIsHeld() {
        lock.lock();
        try {
            dropTheHeld = true;
            slotFreed.signalAll();
            turnComes.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** Gives no slot to anybody ever again: whoever is waiting for one goes away without it. */
    void close() {
        lock.lock();
        try {
            closed = true;
            slotFreed.signalAll();
            turnComes.signalAll();
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
                        && responseNanos > usualResponseNanos * slowdownFactor;
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

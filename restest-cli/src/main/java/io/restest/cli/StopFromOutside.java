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
package io.restest.cli;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Whether a run has been told to stop from outside, and when; and the wait that follows, until the
 * run has left behind what it found.
 *
 * <p>A run can be stopped from outside at any moment: somebody presses Ctrl-C, a script sends
 * {@code kill}, a container is stopped. Java then begins to end the program, and first gives each
 * part of it that asked a chance to finish - on a thread of its own, while the rest of the program
 * carries on. This is what that thread and the run share. The thread asks the run to stop, and
 * waits. The run, which looks here between one thing and the next, makes no new requests, waits a
 * little for the answers it is still owed, writes what it found, and says it is done. When it is,
 * or when the thread has waited as long as it may, the thread lets Java end the program.
 *
 * <p>A run can also be stopped before it has begun testing, while it is still reading the
 * document. There is nothing to wait for then, and the run must not begin: it has not yet cleared
 * away what an earlier run left in its directory, and whoever stopped it is told that it is still
 * there.
 *
 * <p>{@link StopsFromOutside} is what arranges for that thread; {@link RunLoop} is what looks here.
 * There is one of these per run, so two runs in one program are each stopped on their own.
 */
final class StopFromOutside {

    /** How the wait for a stopped run ended. */
    enum Waited {

        /** The run had not begun testing, and now never will: there was nothing to wait for. */
        NOTHING_BEGUN,

        /** The run left behind everything it was going to, in time. */
        DONE,

        /** The time was up first. */
        NOT_DONE
    }

    private final AtomicReference<Instant> askedAt = new AtomicReference<>();
    private final CountDownLatch wrappedUp = new CountDownLatch(1);

    /**
     * Whether the run has begun testing. Settled together with the order to stop, so that the two
     * never both think they came first.
     */
    private boolean testing;

    /**
     * Asks the run to stop. Asking again changes nothing: the run was stopped when it was first
     * asked.
     */
    void ask() {
        askedAt.compareAndSet(null, Instant.now());
    }

    /**
     * Says the run is about to begin testing, unless it has already been asked to stop - in which
     * case it must not begin, and must leave its directory as it is.
     *
     * @return whether the run may begin
     */
    synchronized boolean beginTesting() {
        if (asked()) {
            return false;
        }
        testing = true;
        return true;
    }

    /**
     * Whether the run was asked to stop before it began testing, so that it never did: what it
     * answers is then the number a program stopped that way ends with, whatever else it found to
     * say on its way out.
     */
    synchronized boolean stoppedBeforeTesting() {
        return asked() && !testing;
    }

    /** Whether the run has been asked to stop. */
    boolean asked() {
        return askedAt.get() != null;
    }

    /** When the run was first asked to stop, if it has been. */
    Optional<Instant> askedAt() {
        return Optional.ofNullable(askedAt.get());
    }

    /**
     * Says that the run has left behind everything it was going to - what it prints, its report,
     * its stored run - whether or not it was ever asked to stop.
     */
    void wrappedUp() {
        wrappedUp.countDown();
    }

    /**
     * Asks the run to stop, and waits until it has left behind what it found, but no longer than
     * this. A run that had not begun testing is not waited for, since it will not begin.
     *
     * @param atMost how long to wait at most. However long, it is waited without complaint: a wait
     *     longer than anybody could sit through is simply waited until the run is done
     * @return how the wait ended
     */
    Waited askAndWait(Duration atMost) {
        Objects.requireNonNull(atMost, "atMost");
        synchronized (this) {
            ask();
            if (!testing) {
                return Waited.NOTHING_BEGUN;
            }
        }
        try {
            return wrappedUp.await(nanosIn(atMost), TimeUnit.NANOSECONDS)
                    ? Waited.DONE : Waited.NOT_DONE;
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return Waited.NOT_DONE;
        }
    }

    /**
     * A length of time in the unit a wait is given in, never less than none nor more than any. Java's
     * own conversion fails for a length too long to count that finely; the longest wait there is
     * stands in for it.
     */
    static long nanosIn(Duration length) {
        if (length.isNegative()) {
            return 0;
        }
        try {
            return length.toNanos();
        } catch (ArithmeticException longerThanAnyWait) {
            return Long.MAX_VALUE;
        }
    }
}

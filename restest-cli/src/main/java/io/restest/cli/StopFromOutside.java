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
 * waits. The run, which looks here between one thing and the next, stops sending, waits a little
 * for the answers it is still owed, writes what it found, and says it is done. When it is, or when
 * the thread has waited as long as it may, the thread lets Java end the program.
 *
 * <p>{@link StopsFromOutside} is what arranges for that thread; {@link RunLoop} is what looks here.
 * There is one of these per run, so two runs in one program are each stopped on their own.
 */
final class StopFromOutside {

    private final AtomicReference<Instant> askedAt = new AtomicReference<>();
    private final CountDownLatch wrappedUp = new CountDownLatch(1);

    /**
     * Asks the run to stop. Asking again changes nothing: the run was stopped when it was first
     * asked.
     */
    void ask() {
        askedAt.compareAndSet(null, Instant.now());
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
     * this.
     *
     * @param atMost how long to wait at most
     * @return whether the run was done in time
     */
    boolean askAndWait(Duration atMost) {
        Objects.requireNonNull(atMost, "atMost");
        ask();
        try {
            return wrappedUp.await(atMost.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}

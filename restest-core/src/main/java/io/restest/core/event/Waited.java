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
package io.restest.core.event;

import io.restest.core.model.OperationId;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * How long a run left the API alone because the API asked it to.
 *
 * <p>An API that is being asked too much answers 429, "too many requests", or 503 with a
 * {@code Retry-After} header saying when to ask again. A run that hears it leaves the operation
 * that was answered alone for that long - or, when the API does not say, for a wait it doubles each
 * time it is told again - while every other operation goes on. When a second operation is told to
 * wait while another still waits, the limit is the whole API's, and nothing at all is sent until
 * its wait is over. The {@link RunEvent.RunFinished} that says a run is over carries this, for the
 * reports to say who was told to wait, how often and for how long, and how much of the time the run
 * spent sending nothing was the API's doing rather than the tool's.
 *
 * @param idle how much of the run's idle time - the time it had no request in flight - came while
 *     it waited as the API asked: the whole API waiting, or every operation it could send waiting
 * @param wholeApi how often, and for how long, nothing at all was sent
 * @param operations each operation that was told to wait, in the order they first were
 */
public record Waited(Duration idle, WholeApi wholeApi, List<ForOperation> operations) {

    public Waited {
        nonNegative(idle, "idle");
        Objects.requireNonNull(wholeApi, "wholeApi");
        operations = List.copyOf(operations);
    }

    /** A run that was never told to wait. */
    public static Waited never() {
        return new Waited(Duration.ZERO, new WholeApi(0, Duration.ZERO), List.of());
    }

    /**
     * The waits of the whole API: the times nothing at all was sent.
     *
     * @param times how many such waits began while the run was sending
     * @param waited how long they lasted, in all, up to the moment the run stopped sending
     */
    public record WholeApi(long times, Duration waited) {

        public WholeApi {
            if (times < 0) {
                throw new IllegalArgumentException("times cannot be negative: " + times);
            }
            nonNegative(waited, "waited");
        }
    }

    /**
     * The waits of one operation.
     *
     * @param operation which operation
     * @param times how many waits it was given while the run was sending
     * @param stated how many of them lasted as long as the API said, rather than a wait the run
     *     guessed because the reply did not say
     * @param waited how long they lasted, in all, up to the moment the run stopped sending
     * @param turnsSkipped how many times its turn came while it waited, and nothing was sent
     */
    public record ForOperation(OperationId operation, long times, long stated, Duration waited,
            long turnsSkipped) {

        public ForOperation {
            Objects.requireNonNull(operation, "operation");
            if (times < 1) {
                throw new IllegalArgumentException("an operation listed was told to wait at least "
                        + "once: " + times);
            }
            if (stated < 0 || stated > times) {
                throw new IllegalArgumentException("stated is between 0 and times (" + times
                        + "): " + stated);
            }
            nonNegative(waited, "waited");
            if (turnsSkipped < 0) {
                throw new IllegalArgumentException("turnsSkipped cannot be negative: "
                        + turnsSkipped);
            }
        }
    }

    private static void nonNegative(Duration value, String what) {
        Objects.requireNonNull(value, what);
        if (value.isNegative()) {
            throw new IllegalArgumentException(what + " cannot be negative: " + value);
        }
    }
}

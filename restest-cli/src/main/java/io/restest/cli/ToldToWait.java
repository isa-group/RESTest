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

import io.restest.core.event.Waited;
import io.restest.core.execution.Interaction;
import io.restest.core.model.OperationId;
import io.restest.core.settings.ScheduleSettings;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Keeps track of what the API has asked the run to leave alone, and for how long, so that the run
 * obeys an API that asks it to slow down.
 *
 * <p>An API that is being asked too much answers 429, "too many requests", or 503, "not now", with
 * a {@code Retry-After} header saying when to ask again. Which part of the API the limit covers it
 * does not say: one operation can have a quota of its own - so many creations an hour - while the
 * rest answer as usual, or the whole API can be limited at once. So the operation whose request drew
 * the answer is left alone: for as long as the header says, read by {@link RetryAfter}, or, when it
 * says nothing that can be read, for a wait that starts short and doubles each time the same
 * operation is told again, up to a ceiling, and goes back to the start as soon as the operation
 * gives any answer that does not ask it to wait. Every other operation goes on. When a second
 * operation is told to wait while another still waits, the limit is plainly the whole API's:
 * nothing at all is sent until the whole API's own wait is over, which doubles in the same way.
 *
 * <p>Requests are sent several at a time and come back in any order, so an answer is weighed by
 * when its request went out. A request that went before the wait it belongs to was over had not
 * heard of that wait yet: its refusal begins no wait, and only moves the end of one still going
 * later when it says so. Any other answer of it does not shorten the next wait either.
 *
 * <p>{@link RunLoop} tells this the moment each request settles, from the thread its answer arrived
 * on, and asks it from the thread that decides what to send: whether an operation may be sent,
 * whether the whole API waits, and when the soonest wait ends. When the whole API's wait begins or
 * moves later, the answer to that telling is how long the HTTP engine must hold back what it already
 * has - for good, when the wait lasts to the end of the run, so that the loop can have the engine
 * drop what it holds rather than send it once the run's time is up. Everything is kept behind one
 * lock; a run never told to wait pays one read per answer and per request, and nothing an API
 * writes in its answers can make this fail. At the end it says, as {@link Waited}, who was told to
 * wait, how often and for how long.
 */
final class ToldToWait {

    /** How long the engine is held when the whole API waits to the end of the run: for good. */
    static final Duration FOR_GOOD = Duration.ofSeconds(Long.MAX_VALUE);

    private final boolean asked;
    private final Duration firstGuess;
    private final Duration longestGuess;
    private final Instant deadline;
    private final InstantSource clock;
    private final Object lock = new Object();

    /** Whether anything has been told to wait yet, read without the lock. */
    private volatile boolean everTold;

    /**
     * When the latest wait of all ends, read without the lock: once it is past, nothing waits, and
     * the questions asked before every request are answered without taking the lock.
     */
    private volatile Instant latestEnd = Instant.MIN;

    // Everything below is read and written holding the lock.
    private final Map<OperationId, Waiting> operations = new LinkedHashMap<>();
    private final Waiting wholeApi;
    private Instant stoppedSendingAt;
    private Duration idle = Duration.ZERO;

    private ToldToWait(ScheduleSettings settings, Instant deadline, InstantSource clock) {
        Objects.requireNonNull(settings, "settings");
        this.asked = settings.waitWhenToldTo();
        this.firstGuess = settings.firstGuessedWait();
        this.longestGuess = settings.longestGuessedWait();
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.wholeApi = new Waiting(firstGuess);
    }

    /**
     * Keeps track of a run's waits.
     *
     * @param settings whether the run waits when told to, and how long it guesses when the API
     *     does not say
     * @param deadline when the run's time is up: no wait lasts beyond it
     */
    static ToldToWait watching(ScheduleSettings settings, Instant deadline) {
        return watching(settings, deadline, InstantSource.system());
    }

    /** The same, telling the time by the given clock. */
    static ToldToWait watching(ScheduleSettings settings, Instant deadline, InstantSource clock) {
        return new ToldToWait(settings, deadline, clock);
    }

    /**
     * A request settled: what came back to it, if anything did.
     *
     * @param operation which operation the request was for
     * @param reply what came back
     * @return how long the HTTP engine must hold back every request it already has, when this began
     *     a wait of the whole API or moved its end later; nothing otherwise
     */
    Optional<Duration> settled(OperationId operation, Interaction reply) {
        if (!asked) {
            return Optional.empty();
        }
        Optional<Integer> status = reply.statusCode();
        // A request that got no reply says nothing about waiting.
        if (status.isEmpty()) {
            return Optional.empty();
        }
        int code = status.get();
        Instant now = null;
        Optional<Instant> stated = Optional.empty();
        if (code == 429 || code == 503) {
            now = clock.instant();
            stated = RetryAfter.until(reply, now, deadline);
        }
        if (code != 429 && stated.isEmpty()) {
            // An answer that does not ask to wait brings a guess back to the start, once something
            // has waited: until then it costs one read.
            if (everTold) {
                synchronized (lock) {
                    Waiting waiting = operations.get(operation);
                    if (waiting != null) {
                        waiting.answeredWithoutAWait(reply.sentAt());
                    }
                    wholeApi.answeredWithoutAWait(reply.sentAt());
                }
            }
            return Optional.empty();
        }
        Instant sentAt = reply.sentAt();
        synchronized (lock) {
            if (stoppedSendingAt != null) {
                return Optional.empty();
            }
            everTold = true;
            Waiting waiting = operations.computeIfAbsent(operation, ignored ->
                    new Waiting(firstGuess));
            boolean began = told(waiting, sentAt, now, stated);
            Instant wholeApiUntil = wholeApi.until;
            if (began && anotherWaits(operation, now) || wholeApi.heardOfItsWaitAfter(sentAt)) {
                told(wholeApi, sentAt, now, stated);
            }
            latestEnd = later(later(latestEnd, waiting.until),
                    wholeApi.until == null ? Instant.MIN : wholeApi.until);
            if (Objects.equals(wholeApi.until, wholeApiUntil) || !wholeApi.waitsAt(now)) {
                return Optional.empty();
            }
            // A wait that lasts to the end of the run holds the engine beyond it, so that what it
            // holds is never sent rather than sent once the run's time is up.
            return Optional.of(wholeApi.until.equals(deadline) ? FOR_GOOD
                    : Duration.between(now, wholeApi.until));
        }
    }

    /**
     * Whether a request for this operation may be sent now. When it may not, its turn is counted as
     * skipped.
     */
    boolean mayBeSent(OperationId operation) {
        Instant now = clock.instant();
        if (!now.isBefore(latestEnd)) {
            return true;
        }
        synchronized (lock) {
            Waiting waiting = operations.get(operation);
            if (waiting == null || !waiting.waitsAt(now)) {
                return true;
            }
            waiting.turnsSkipped++;
            return false;
        }
    }

    /** Until when nothing at all may be sent, when the whole API is waiting now. */
    Optional<Instant> wholeApiWaitsUntil() {
        Instant now = clock.instant();
        if (!now.isBefore(latestEnd)) {
            return Optional.empty();
        }
        synchronized (lock) {
            return wholeApi.waitsAt(now) ? Optional.of(wholeApi.until) : Optional.empty();
        }
    }

    /** When the soonest wait still going ends, if any is. */
    Optional<Instant> soonestEnd() {
        Instant now = clock.instant();
        if (!now.isBefore(latestEnd)) {
            return Optional.empty();
        }
        synchronized (lock) {
            Instant soonest = wholeApi.waitsAt(now) ? wholeApi.until : null;
            for (Waiting waiting : operations.values()) {
                if (waiting.waitsAt(now) && (soonest == null || waiting.until.isBefore(soonest))) {
                    soonest = waiting.until;
                }
            }
            return Optional.ofNullable(soonest);
        }
    }

    /** Some of the run's idle time came while it waited as the API asked. */
    void idleWhileWaiting(Duration idleTime) {
        synchronized (lock) {
            idle = idle.plus(idleTime.isNegative() ? Duration.ZERO : idleTime);
        }
    }

    /**
     * The run has stopped sending. Nothing heard from here on begins or moves a wait, and every wait
     * is counted up to this moment. Only the first call counts.
     */
    void stoppedSending() {
        synchronized (lock) {
            if (stoppedSendingAt == null) {
                stoppedSendingAt = clock.instant();
            }
        }
    }

    /**
     * Who was told to wait, how often and for how long, up to the moment the run stopped sending.
     *
     * @return nothing when the run does not wait when told to
     */
    Optional<Waited> summary() {
        if (!asked) {
            return Optional.empty();
        }
        synchronized (lock) {
            Instant end = stoppedSendingAt == null ? clock.instant() : stoppedSendingAt;
            List<Waited.ForOperation> each = new ArrayList<>();
            operations.forEach((operation, waiting) -> each.add(new Waited.ForOperation(operation,
                    waiting.times, waiting.stated, waiting.waited(end), waiting.turnsSkipped)));
            return Optional.of(new Waited(idle,
                    new Waited.WholeApi(wholeApi.times, wholeApi.waited(end)), each));
        }
    }

    /**
     * One more refusal for an operation or for the whole API, holding the lock.
     *
     * @return whether a wait began, rather than an earlier one being heard of again
     */
    private boolean told(Waiting waiting, Instant sentAt, Instant now, Optional<Instant> stated) {
        if (waiting.heardOfItsWaitAfter(sentAt)) {
            // Sent before the wait it belongs to was over: it begins nothing. A moment it states
            // only moves the end of a wait still going, and only later.
            if (stated.isPresent() && waiting.waitsAt(now)) {
                waiting.until = later(waiting.until, stated.get());
            }
            return false;
        }
        if (stated.isPresent()) {
            waiting.begin(now, stated.get(), true);
        } else {
            waiting.begin(now, RetryAfter.after(now, waiting.guess, deadline), false);
            waiting.guess = waiting.guess.compareTo(longestGuess.dividedBy(2)) >= 0
                    ? longestGuess : waiting.guess.multipliedBy(2);
        }
        return true;
    }

    private boolean anotherWaits(OperationId operation, Instant now) {
        for (Map.Entry<OperationId, Waiting> each : operations.entrySet()) {
            if (!each.getKey().equals(operation) && each.getValue().waitsAt(now)) {
                return true;
            }
        }
        return false;
    }

    private static Instant later(Instant one, Instant other) {
        return other.isAfter(one) ? other : one;
    }

    /** The waits of one operation, or of the whole API. */
    private static final class Waiting {

        /** How long the first wait the API does not give a length for lasts. */
        private final Duration firstGuess;

        /** How long the next wait the API does not give a length for lasts. */
        private Duration guess;

        /** When the latest wait began and ends; both {@code null} until the first. */
        private Instant began;
        private Instant until;

        private long times;
        private long stated;
        private long turnsSkipped;

        /** How long the waits before the latest one lasted, in all. */
        private Duration waitedBefore = Duration.ZERO;

        private Waiting(Duration firstGuess) {
            this.firstGuess = firstGuess;
            this.guess = firstGuess;
        }

        private boolean waitsAt(Instant now) {
            return until != null && now.isBefore(until);
        }

        /** Whether a request sent then went before the latest wait was over. */
        private boolean heardOfItsWaitAfter(Instant sentAt) {
            return until != null && sentAt.isBefore(until);
        }

        private void begin(Instant now, Instant end, boolean statedByTheApi) {
            if (began != null) {
                // A wait begins only once the one before is over, so that one is counted whole.
                waitedBefore = waitedBefore.plus(Duration.between(began, until));
            }
            began = now;
            until = end;
            times++;
            if (statedByTheApi) {
                stated++;
            }
        }

        /**
         * An answer that does not ask to wait, which brings the guess back to the start if its
         * request was sent once the last wait was over.
         */
        private void answeredWithoutAWait(Instant sentAt) {
            if (!heardOfItsWaitAfter(sentAt)) {
                guess = firstGuess;
            }
        }

        private Duration waited(Instant end) {
            if (began == null) {
                return Duration.ZERO;
            }
            Instant last = until.isBefore(end) ? until : end;
            return last.isAfter(began) ? waitedBefore.plus(Duration.between(began, last))
                    : waitedBefore;
        }
    }
}

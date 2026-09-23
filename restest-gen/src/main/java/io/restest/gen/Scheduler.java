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
package io.restest.gen;

import io.restest.core.event.RunEvent;
import io.restest.core.execution.TestCase;
import io.restest.core.model.Operation;
import io.restest.core.settings.ScheduleSettings;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Decides what a run sends next, and when the run is over.
 *
 * <p>A run is given a length of time, and this is the part of the tool that knows it: it holds the
 * deadline, says when the time is up, and decides, one request at a time, which of the API's
 * operations comes next. It sends nothing itself. The loop that talks to the API asks it what to do,
 * does it, and asks again; what goes into each request is filled in by {@link
 * RandomTestCaseGenerator}.
 *
 * <p>A run begins with a first round, unless it is told not to: every operation once, each with the
 * request it is most likely to accept. The round goes in steps - first the lists of what is there,
 * then the creations, then the reads of one thing in particular, then the changes, and the deletions
 * last - and between one step and the next it waits for the answers to come back, so that an
 * identifier one step has just been handed is there for the next to use. That round is what gets an
 * API's operations answered in the first second of a run rather than whenever chance gets round to
 * them. It is paid for out of the same budget as everything else, and announced as a stretch of the
 * run of its own, so the reports can say how long it took and what it achieved. A wait is bounded,
 * so an API that never answers one request cannot hold the start of a run up.
 *
 * <p>After it, the run goes round the operations over and over, in the order the document declares
 * them, each request filled in the ordinary way, until the time is up. At the end of every round it
 * says so, so that whoever sends can stop a run that has shown it can send nothing at all, or that
 * nobody is answering.
 *
 * <p>Nothing here is decided by chance: which operation comes next depends only on the document, the
 * settings and the clock. A run with the first round switched off therefore builds exactly the test
 * cases it built before there was a first round, in the same order, from the same starting number.
 *
 * <p>One scheduler belongs to one run, and is asked from one thread.
 */
public final class Scheduler {

    /** What the first round of a run is called when it is announced and reported. */
    public static final String OPENING_LAP = "opening lap";

    private final RandomTestCaseGenerator generator;
    private final List<Operation> operations;
    private final List<List<Operation>> lap;
    private final Duration patience;
    private final Instant deadline;
    private final InstantSource clock;
    private final Consumer<? super RunEvent> announce;

    /** Which step of the first round is being sent, and how far through it. */
    private int step;
    private int inStep;
    private boolean lapBegun;
    private boolean lapOver;

    /** Which operation comes next once the first round is over. */
    private int next;
    private boolean endOfAPassDue;
    private boolean stopped;

    /**
     * A scheduler for one run.
     *
     * @param generator what fills in each request, and knows which operations can be attempted
     * @param settings whether the run begins with a first round, and how long a step of it waits
     * @param deadline when the run's time is up
     * @param clock what tells the time; the system clock, except in a test
     * @param announce where to say that the first round has begun and ended
     * @throws IllegalArgumentException if there is no operation that can be attempted, since a run
     *     with nothing to send has nothing to do
     */
    public Scheduler(RandomTestCaseGenerator generator, ScheduleSettings settings, Instant deadline,
            InstantSource clock, Consumer<? super RunEvent> announce) {
        this.generator = Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(settings, "settings");
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.announce = Objects.requireNonNull(announce, "announce");
        this.operations = generator.testableOperations();
        if (operations.isEmpty()) {
            throw new IllegalArgumentException("a run with no operations to test has nothing to do");
        }
        // Without a way of building a request meant to work there is no request most likely to
        // be accepted, and a round of requests built to push at the API is not what a first round
        // is for.
        this.lap = settings.openingLap() && generator.canBuildTheLikeliestRequest()
                ? OpeningLap.steps(operations) : List.of();
        this.patience = settings.openingLapPatience();
    }

    /** When the run's time is up. */
    public Instant deadline() {
        return deadline;
    }

    /**
     * What to do next.
     *
     * <p>Asked again after every step has been carried out. Once it has answered that the time is
     * up, it goes on answering that.
     */
    public Step next() {
        if (endOfAPassDue) {
            // Said before anything else, the time included, so that the round just finished is
            // counted and judged exactly as it always was, however close to the deadline it ended.
            endOfAPassDue = false;
            return new Step.EndOfAPass(false);
        }
        if (stopped || !clock.instant().isBefore(deadline)) {
            stop();
            return new Step.TimeIsUp();
        }
        while (step < lap.size()) {
            List<Operation> current = lap.get(step);
            if (inStep < current.size()) {
                if (!lapBegun) {
                    // Announced with the round's first step rather than beforehand, so a run whose
                    // time is up before the round began has no first round to report.
                    lapBegun = true;
                    announce.accept(new RunEvent.PhaseStarted(clock.instant(), OPENING_LAP));
                }
                return new Step.Send(current.get(inStep++), true);
            }
            step++;
            inStep = 0;
            if (!patience.isZero()) {
                Duration left = Duration.between(clock.instant(), deadline);
                return new Step.WaitForTheAnswers(patience.compareTo(left) < 0 ? patience : left);
            }
        }
        if (lapBegun && !lapOver) {
            lapOver = true;
            announce.accept(new RunEvent.PhaseFinished(clock.instant(), OPENING_LAP, false));
            return new Step.EndOfAPass(true);
        }
        Operation operation = operations.get(next++);
        if (next == operations.size()) {
            next = 0;
            endOfAPassDue = true;
        }
        return new Step.Send(operation, false);
    }

    /**
     * The request to send for this step, built now.
     *
     * <p>Asked only once there is room to send it, never ahead of time, so that a request is filled
     * in from everything the API has said up to the moment it goes out.
     *
     * @param send the step this is for
     * @return the request, or empty if one could not be built for that operation this time
     */
    public Optional<TestCase> testCaseFor(Step.Send send) {
        Objects.requireNonNull(send, "send");
        return send.inTheOpeningLap()
                ? generator.likeliestRequest(send.operation())
                : generator.generate(send.operation());
    }

    /**
     * Ends the run's scheduling. A first round still going is announced as cut short.
     *
     * <p>Called however the run ends, including when something went wrong, so the reports never
     * hear of a first round that began and did not end. Calling it twice does nothing the second
     * time.
     */
    public void stop() {
        stopped = true;
        if (lapBegun && !lapOver) {
            lapOver = true;
            announce.accept(new RunEvent.PhaseFinished(clock.instant(), OPENING_LAP, true));
        }
    }

    /**
     * One thing for the loop that talks to the API to do.
     *
     * <p>The list is closed, which is what lets that loop handle every kind and be told by the
     * compiler if another is ever added.
     */
    public sealed interface Step {

        /**
         * Send one request for this operation.
         *
         * @param operation which operation
         * @param inTheOpeningLap whether it belongs to the first round, which waits for its answers
         *     before the round goes on
         */
        record Send(Operation operation, boolean inTheOpeningLap) implements Step {
            public Send {
                Objects.requireNonNull(operation, "operation");
            }
        }

        /**
         * Wait until every request the first round has sent since the last wait has been answered,
         * and until what came back has been heard by everybody listening, but no longer than this.
         *
         * @param atMost the longest to wait, never beyond the deadline
         */
        record WaitForTheAnswers(Duration atMost) implements Step {
            public WaitForTheAnswers {
                Objects.requireNonNull(atMost, "atMost");
            }
        }

        /**
         * A round of every operation is complete.
         *
         * @param ofTheOpeningLap whether it was the first round, which is not one of the ordinary
         *     rounds a run counts
         */
        record EndOfAPass(boolean ofTheOpeningLap) implements Step {
        }

        /** The time is up: send nothing more. */
        record TimeIsUp() implements Step {
        }
    }
}

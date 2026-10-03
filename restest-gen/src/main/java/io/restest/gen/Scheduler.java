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
import io.restest.core.execution.Interaction;
import io.restest.core.execution.TestCase;
import io.restest.core.model.Operation;
import io.restest.core.settings.ScheduleSettings;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
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
 * API's operations answered in the first seconds of a run rather than whenever chance gets round to
 * them. It is paid for out of the same budget as everything else, and announced as a stretch of the
 * run of its own, so the reports can say how long it took and what it achieved. A wait is bounded,
 * so an API that never answers one request cannot hold the start of a run up.
 *
 * <p>After it, the run goes round the operations over and over, in the order the document declares
 * them, each request filled in the ordinary way, until the time is up. At the end of every round it
 * says so, so that whoever sends can stop a run that has shown it can send nothing at all, or that
 * nobody is answering.
 *
 * <p>Some of those requests are the first step of a short series about a thing the run has just
 * created - read it, delete it, read it again. Whoever sends hands the answer to every step back
 * here, and the next step of that series is sent before the next ordinary request, built with what
 * the answer said. One step of a series is waiting for its answer at any time; any number of series
 * and ordinary requests go out alongside it.
 *
 * <p>Nothing here is decided by chance: which operation comes next depends only on the document, the
 * settings, the clock and, where a series is under way, what the API answered it. A run with the
 * first round switched off and no series therefore builds exactly the test cases it built before
 * there was a first round, in the same order, from the same starting number.
 *
 * <p>One scheduler belongs to one run, and is asked from one thread - except to be told what came
 * back to a step of a series, which it hears from whichever thread the answer arrived on.
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

    /** The series a creation may start, or {@code null} when the run sends none. */
    private final Sequences series;

    /**
     * Series ready for their next step: a step answered, or one that could not be built. Heard from
     * whichever thread an answer arrives on and taken from the one that decides, so it never
     * blocks and never refuses: nothing that tells it something may be kept waiting.
     */
    private final Queue<Ready> ready = new ConcurrentLinkedQueue<>();

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
        this.series = generator.sequences().orElse(null);
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
        // A series waiting for its next step goes before the ordinary turn, so that each of its
        // steps is sent as soon as there is room once the answer to the one before is in. One
        // that has nothing more to send is simply left behind.
        for (Ready waiting = ready.poll(); waiting != null; waiting = ready.poll()) {
            Optional<Sequences.Next> following = switch (waiting) {
                case Ready.Heard heard -> series.heard(heard.step(), heard.answer());
                case Ready.NotBuilt unbuilt -> series.notBuilt(unbuilt.next());
            };
            if (following.isPresent()) {
                return new Step.Send(following.get().operation(), false,
                        Optional.of(new Continuation(following.get())));
            }
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
        if (send.continuation().isPresent()) {
            Sequences.Next next = send.continuation().get().next;
            Optional<TestCase> built = series.build(next);
            if (built.isEmpty()) {
                // Said here rather than by whoever sends, who has no test case to say it with: the
                // series goes on without the step, or ends if it cannot.
                ready.add(new Ready.NotBuilt(next));
            }
            return built;
        }
        return send.inTheOpeningLap()
                ? generator.likeliestRequest(send.operation())
                : generator.generate(send.operation());
    }

    /**
     * Tells the scheduler what came back to a request, so that a series it was a step of can go on.
     *
     * <p>Called from whichever thread the answer arrived on, for every request built - and, with
     * nothing, for one that could not be sent at all. A request that is not a step of a series is
     * ignored. This only takes the news; the next step is decided and built by the thread that
     * decides, the next time it asks what to do.
     *
     * @param sent the request
     * @param answer what came back, or nothing when nothing did
     */
    public void heard(TestCase sent, Optional<Interaction> answer) {
        Objects.requireNonNull(sent, "sent");
        Objects.requireNonNull(answer, "answer");
        if (series != null && sent.sequence().isPresent()) {
            ready.add(new Ready.Heard(sent, answer));
        }
    }

    /** A series ready for whatever comes after a step. */
    private sealed interface Ready {

        /** The step was answered, or could not be sent. */
        record Heard(TestCase step, Optional<Interaction> answer) implements Ready {
        }

        /** The step could not be built, so it was never sent. */
        record NotBuilt(Sequences.Next next) implements Ready {
        }
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
         * @param continuation the series this request is the next step of, when it is one
         */
        record Send(Operation operation, boolean inTheOpeningLap,
                Optional<Continuation> continuation) implements Step {
            public Send {
                Objects.requireNonNull(operation, "operation");
                Objects.requireNonNull(continuation, "continuation");
            }

            /**
             * A request of its own, not the next step of a series.
             *
             * @param operation which operation
             * @param inTheOpeningLap whether it belongs to the first round
             */
            public Send(Operation operation, boolean inTheOpeningLap) {
                this(operation, inTheOpeningLap, Optional.empty());
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

    /**
     * The next step of a series, which {@link #testCaseFor} builds once there is room to send it.
     *
     * <p>What is inside is for the scheduler and what builds requests; whoever sends only carries
     * it from the one to the other.
     */
    public static final class Continuation {

        private final Sequences.Next next;

        private Continuation(Sequences.Next next) {
            this.next = next;
        }

        @Override
        public String toString() {
            return next.toString();
        }
    }
}

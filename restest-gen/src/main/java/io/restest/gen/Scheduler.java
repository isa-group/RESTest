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
import io.restest.core.gen.GeneratedValue;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.settings.ScheduleSettings;
import io.restest.core.settings.SequenceSettings;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
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
 * <p>In those rounds, an operation that needs the identifier of a thing the run knows of none of -
 * {@code GET /pets/{petId}} when no pet the API has returned is still there - is sent as a pair: first
 * the request that creates such a thing, {@code POST /pets}, and, as soon as its answer is back, the
 * operation itself, with the identifier that answer carried. The two are one piece of work: the
 * second takes its identifier from the first one's reply, not from whatever the run happens to
 * remember, so it asks for the very thing that was made for it. {@link Producers} says which
 * operation creates what. Only one creation of each kind is awaited at a time, and one that makes
 * nothing usable is not sent as one again until the next round, so an API that refuses every
 * creation costs at most one extra request per round for it. Pairs are only made when the plan remembers what the API returned - without that nobody knows
 * what is missing - and can be switched off.
 *
 * <p>Nothing here is decided by chance: which operation comes next depends only on the document, the
 * settings, the clock and, when the plan has a memory, what the API has answered. A run with the
 * first round switched off and no memory therefore builds exactly the test cases it built before
 * there was a first round, in the same order, from the same starting number.
 *
 * <p>One scheduler belongs to one run, and is asked from one thread - except {@link #heard}, which is
 * told about an answer from whichever thread it arrived on.
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
    private final boolean pairs;

    /** Answers to creations, waiting for the request each was made for to be sent. */
    private final Queue<Heard> heard = new ConcurrentLinkedQueue<>();

    /** The creations that made nothing usable in this round, not sent as one again until the next. */
    private final Set<OperationId> madeNothingThisRound = new HashSet<>();

    /**
     * The creations sent for another request whose answer has not been dealt with yet. One at a
     * time for each: a run sends a whole round before the first answer is back, and without this
     * every request of the round needing the same thing would send its own creation - all of them
     * refused, on an API that refuses that creation.
     */
    private final Set<OperationId> creationsAwaited = new HashSet<>();

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
     * A scheduler for one run, sending requests in sequences as RESTest does unless told otherwise.
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
        this(generator, settings, SequenceSettings.defaults(), deadline, clock, announce);
    }

    /**
     * A scheduler for one run, told whether to send requests in sequences.
     *
     * @param generator what fills in each request, and knows which operations can be attempted
     * @param settings whether the run begins with a first round, and how long a step of it waits
     * @param sequences whether a request that needs something nobody has is sent straight after a
     *     request that creates it
     * @param deadline when the run's time is up
     * @param clock what tells the time; the system clock, except in a test
     * @param announce where to say that the first round has begun and ended
     * @throws IllegalArgumentException if there is no operation that can be attempted, since a run
     *     with nothing to send has nothing to do
     */
    public Scheduler(RandomTestCaseGenerator generator, ScheduleSettings settings,
            SequenceSettings sequences, Instant deadline, InstantSource clock,
            Consumer<? super RunEvent> announce) {
        Objects.requireNonNull(sequences, "sequences");
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
        this.pairs = sequences.pairs();
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
            madeNothingThisRound.clear();
            return new Step.EndOfAPass(false);
        }
        if (stopped || !clock.instant().isBefore(deadline)) {
            stop();
            return new Step.TimeIsUp();
        }
        Heard answered = heard.poll();
        if (answered != null) {
            return theRequestItWasMadeFor(answered);
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
        if (pairs) {
            Optional<Producers.Need> missing = generator.whatIsMissing(operation)
                    .filter(need -> !madeNothingThisRound.contains(need.producer().id())
                            && !creationsAwaited.contains(need.producer().id()));
            if (missing.isPresent()) {
                creationsAwaited.add(missing.get().producer().id());
                return new Step.Send(missing.get().producer(), false,
                        Optional.of(Pair.creation(operation, missing.get())));
            }
        }
        return new Step.Send(operation, false);
    }

    /** The second half of a pair, filled in with what its creation handed back. */
    private Step.Send theRequestItWasMadeFor(Heard answered) {
        Pair creation = answered.send().pair().orElseThrow();
        creationsAwaited.remove(creation.need().producer().id());
        Map<String, GeneratedValue> gave = generator.whatTheCreationGave(creation.consumer(),
                creation.need(), answered.creation(), answered.answer());
        if (!gave.containsKey(creation.need().gap())) {
            madeNothingThisRound.add(creation.need().producer().id());
        }
        return new Step.Send(creation.consumer(), false,
                Optional.of(Pair.followUp(creation.consumer(), creation.need(), gave)));
    }

    /**
     * Tells the scheduler what came back for a request that creates something another request
     * needs.
     *
     * <p>That other request is sent next, ahead of anything else, and takes its identifier from
     * what came back here. Called from whichever thread the answer arrived on. Also called, with
     * nothing, when the creation could not be built or sent at all, so that the request it was
     * made for is still sent - the ordinary way.
     *
     * @param send the step that sent the creation; one whose {@link Step.Send#startsAPair()} is
     *     true
     * @param creation the request that went out, or {@code null} if none did
     * @param answer what came back, or {@code null} if nothing did
     */
    public void heard(Step.Send send, TestCase creation, Interaction answer) {
        Objects.requireNonNull(send, "send");
        if (!send.startsAPair()) {
            throw new IllegalArgumentException("only a creation sent for another request is "
                    + "waited on: " + send);
        }
        heard.add(new Heard(send, creation, answer));
    }

    private record Heard(Step.Send send, TestCase creation, Interaction answer) {
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
        if (send.pair().isPresent()) {
            Pair pair = send.pair().get();
            // A creation is sent the way it is likeliest to be accepted, since making the thing
            // is its whole job; the request it was made for is an ordinary one, to be tested.
            return pair.isTheCreation()
                    ? generator.likeliestRequest(send.operation())
                    : generator.followUp(send.operation(), pair.gave());
        }
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
        record Send(Operation operation, boolean inTheOpeningLap, Optional<Pair> pair)
                implements Step {
            public Send {
                Objects.requireNonNull(operation, "operation");
                Objects.requireNonNull(pair, "pair");
            }

            /**
             * A request on its own, not one of a pair.
             *
             * @param operation which operation
             * @param inTheOpeningLap whether it belongs to the first round
             */
            public Send(Operation operation, boolean inTheOpeningLap) {
                this(operation, inTheOpeningLap, Optional.empty());
            }

            /**
             * Whether this request creates something the next request needs, so that what comes
             * back has to be handed to {@link Scheduler#heard}.
             */
            public boolean startsAPair() {
                return pair.filter(Pair::isTheCreation).isPresent();
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
     * One half of a pair of requests: a creation, or the request it was made for.
     *
     * <p>What is inside is for the scheduler and the generator; whoever sends only needs to know
     * whether a request is a creation whose answer it must hand back.
     */
    public static final class Pair {

        private final Operation consumer;
        private final Producers.Need need;
        private final Map<String, GeneratedValue> gave;

        private Pair(Operation consumer, Producers.Need need, Map<String, GeneratedValue> gave) {
            this.consumer = consumer;
            this.need = need;
            this.gave = gave;
        }

        static Pair creation(Operation consumer, Producers.Need need) {
            return new Pair(consumer, need, null);
        }

        static Pair followUp(Operation consumer, Producers.Need need,
                Map<String, GeneratedValue> gave) {
            return new Pair(consumer, need, Map.copyOf(gave));
        }

        /** Whether this is the creation, rather than the request it was made for. */
        public boolean isTheCreation() {
            return gave == null;
        }

        /** The request the creation is made for. */
        Operation consumer() {
            return consumer;
        }

        Producers.Need need() {
            return need;
        }

        /** What the creation handed back, by the gap each value goes in; empty for the creation. */
        Map<String, GeneratedValue> gave() {
            return gave == null ? Map.of() : gave;
        }

        @Override
        public String toString() {
            return isTheCreation()
                    ? "creating for " + consumer.id() + " the " + need.gap() + " it needs"
                    : "sent after its creation, with " + gave.keySet();
        }
    }
}

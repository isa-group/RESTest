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

import io.restest.core.event.EventStream;
import io.restest.core.event.RunEvent;
import io.restest.core.exec.HttpEngine;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.TestCase;
import io.restest.core.json.JsonException;
import io.restest.core.model.Operation;
import io.restest.gen.RandomTestCaseGenerator;
import io.restest.gen.RequestBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spends the time a run was given, asking the API one question after another until it runs out.
 *
 * <p>It goes round the operations it can test, over and over, until the deadline: an operation is
 * not tried once and then forgotten, because every attempt invents fresh values and is therefore a
 * different test. A run given thirty seconds uses thirty seconds. That matters for more than
 * thoroughness - the headline number a run reports is how much of its time nothing was happening,
 * and a run that stopped after a fifth of a second would make that number meaningless.
 *
 * <p>Requests are sent without waiting for the previous answer, and - this is the part that is easy
 * to get subtly wrong - without waiting for them <em>in the order they were sent</em>. Almost every
 * API has one operation slower than the rest, and a loop that insisted on the oldest answer before
 * asking anything else would spend the run waiting on that one operation while every other request
 * had long since come back. It would also look efficient while doing it, because "nothing in
 * flight" is how wasted time is measured and there would always be that one request in flight. So
 * each answer is dealt with the moment it arrives, whichever it is, and the loop waits only when
 * every slot it is allowed is genuinely occupied.
 *
 * <p>Two things it deliberately is not. It is not a scheduler: it has no notion of which operations
 * deserve more of the time, or of dividing the time into phases. And it is not clever about what to
 * send - deciding that belongs to the part of the tool that invents values, which is asked for the
 * next test case and never told what time it is.
 */
final class RunLoop {

    /**
     * How many requests may be waiting for an answer at once, as a multiple of what the engine will
     * ever have in flight.
     *
     * <p>The engine decides the real number for itself, moving it up and down as the API turns out
     * to be fast or slow. This is only a ceiling on how far ahead the loop may run, and it sits
     * above the engine's own so the engine is never left with a free slot while the next request is
     * still being invented.
     */
    static final int WORK_AHEAD_FACTOR = 2;

    /**
     * How many announcements may be waiting to reach the reports before the loop pauses.
     *
     * <p>Judging a reply against the document, writing it to the run's file and printing a fault all
     * take time, and an API on the same machine can answer faster than all three together. Without a
     * limit the backlog grows for as long as the run lasts, and a long run against a fast API ends
     * by running out of memory rather than by running out of time.
     *
     * <p>Pausing is the honest response: the tool was not testing, and it was the tool's own work
     * that stopped it, so the pause is counted as time the tool wasted and shows up in the summary.
     */
    static final int ANNOUNCEMENTS_ALLOWED_TO_PILE_UP = 1_000;

    /** How long to wait before looking again at whether the reports have caught up. */
    private static final Duration CATCH_UP_PAUSE = Duration.ofMillis(5);

    private RunLoop() {
    }

    /**
     * What the loop did with the time.
     *
     * @param sent how many requests were sent
     * @param answered how many of them came back with a reply, rather than failing on the way
     * @param notGenerated how many times no test case could be invented for an operation
     * @param notAssembled how many test cases could not be turned into a request that could be sent
     * @param passes how many times the loop went round the whole list of operations
     * @param stillOwed how many answers had still not arrived when the run stopped waiting for them.
     *     Normally zero, because the engine gives up on a request of its own accord long before this
     *     does
     */
    record Outcome(long sent, long answered, long notGenerated, long notAssembled, long passes,
            long stillOwed) {

        /** Whether requests went out and not one of them was ever answered. */
        boolean nothingAnswered() {
            return sent > 0 && answered == 0;
        }

        /** Whether nothing was sent because nothing in the document could be turned into a request. */
        boolean nothingCouldBeBuilt() {
            return sent == 0 && notGenerated + notAssembled > 0;
        }
    }

    /**
     * Tests the given operations until the deadline, then waits for the answers still owed.
     *
     * @param operations the operations to go round, in order. Never empty
     * @param generator asked for each test case. Used from this thread only, because one generator
     *     is one sequence of decisions
     * @param baseUrl where the API is
     * @param deadline when to stop inventing new requests
     * @param workAhead how many requests may be waiting for an answer at once
     * @param howLongToWaitForStragglers how long to go on waiting, past the deadline, for answers to
     *     requests that had already gone out
     * @param engine what sends them
     * @param events where everything that happens is announced
     * @return what the loop did with the time
     */
    static Outcome run(List<Operation> operations, RandomTestCaseGenerator generator,
            String baseUrl, Instant deadline, int workAhead, Duration howLongToWaitForStragglers,
            HttpEngine engine, EventStream events) {
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(deadline, "deadline");
        Objects.requireNonNull(howLongToWaitForStragglers, "howLongToWaitForStragglers");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(events, "events");
        if (operations.isEmpty()) {
            throw new IllegalArgumentException("a run with no operations to test has nothing to do");
        }

        // One permit per request that may be outstanding. Taken before a request is invented and
        // given back by whichever answer arrives, so a slow operation costs one slot rather than
        // holding up the queue behind it.
        Semaphore slots = new Semaphore(workAhead);
        Answers answers = new Answers();
        long sent = 0;
        long notGenerated = 0;
        long notAssembled = 0;
        long passes = 0;
        int next = 0;

        while (Instant.now().isBefore(deadline)) {
            if (!waitForASlot(slots, deadline)) {
                break;
            }
            pauseWhileTheReportsCatchUp(events, deadline);

            Operation operation = operations.get(next);
            Optional<TestCase> testCase = generator.generate(operation);
            HttpRequestRecord request = testCase
                    .map(planned -> assemble(operation, planned, baseUrl))
                    .orElse(null);
            if (request == null) {
                slots.release();
                if (testCase.isEmpty()) {
                    notGenerated++;
                } else {
                    notAssembled++;
                }
            } else {
                events.publish(new RunEvent.TestCasePlanned(Instant.now(), testCase.get()));
                send(engine, testCase.get(), request, slots, answers, events);
                sent++;
            }

            next++;
            if (next == operations.size()) {
                next = 0;
                passes++;
                // Nothing in this document could be sent even once. Going round again would produce
                // the same nothing until the deadline, so stop and let the run say why.
                if (sent == 0) {
                    break;
                }
                // Enough requests have come back to know that nothing is listening at this address.
                // Carrying on would spend the whole budget collecting the same refusal - tens of
                // thousands of times, because an address with nothing behind it refuses instantly -
                // and bury the one useful sentence, which is that the address is wrong.
                if (answers.nothingIsThere(workAhead)) {
                    break;
                }
            }
        }

        // The deadline stops us asking new questions, not listening to the answers we are owed.
        // Those requests were paid for and what came back is evidence; throwing it away would also
        // leave the run's stored file disagreeing with the number of requests it says it sent.
        long stillOwed = waitForTheAnswersStillOwed(slots, workAhead, howLongToWaitForStragglers);
        return new Outcome(sent, answers.answered(), notGenerated, notAssembled, passes, stillOwed);
    }

    /**
     * Sends one request, and arranges for its answer to be announced the moment it arrives.
     *
     * <p>Dealing with each answer where it lands, rather than collecting them in the order they went
     * out, is what stops one slow operation from holding up every other request in the run.
     */
    private static void send(HttpEngine engine, TestCase testCase, HttpRequestRecord request,
            Semaphore slots, Answers answers, EventStream events) {
        try {
            engine.sendAsync(testCase, request).whenComplete((interaction, wentWrong) -> {
                try {
                    announce(interaction, answers, events);
                } finally {
                    slots.release();
                }
            });
        } catch (RuntimeException couldNotEvenBeStarted) {
            slots.release();
            throw couldNotEvenBeStarted;
        }
    }

    /**
     * Announces one answer.
     *
     * <p>A request that produced nothing at all is counted and not announced. The engine does not
     * normally allow that - it turns even a refused connection into an answer of its own - but if it
     * ever happens, making one up would mean inventing the moment the request was sent, and one
     * request's misfortune must not throw away everything else that is still outstanding.
     */
    private static void announce(Interaction interaction, Answers answers, EventStream events) {
        if (interaction == null) {
            answers.recordNothingCameBack();
            return;
        }
        answers.record(interaction);
        events.publish(new RunEvent.InteractionCompleted(Instant.now(), interaction));
    }

    /**
     * Turns a test case into a request, or says it could not be done.
     *
     * <p>This catch is doing real work rather than being defensive. Assembling a request fails on
     * values that look perfectly reasonable until they are put in a web address - an empty string
     * chosen for something that fills a gap in the path, most often - and there is no way to ask in
     * advance whether a particular value will do. Without the catch, one unlucky value out of
     * thousands ends the whole run, which is exactly what this tool promises not to do to a document
     * it does not entirely understand.
     *
     * <p>A value that cannot be <em>written down</em> is caught here for the same reason and was
     * not, until a value read out of an API's own reply reached this - a number can be eight
     * characters on the wire and beyond anything JSON can write out in full. It is not only that
     * source: a list of values somebody wrote by hand can hold one too, and one of those ended a
     * run rather than costing it a request.
     *
     * @return the request, or {@code null} if this test case cannot be sent
     */
    private static HttpRequestRecord assemble(Operation operation, TestCase testCase,
            String baseUrl) {
        try {
            return RequestBuilder.build(operation, testCase, baseUrl);
        } catch (IllegalArgumentException | JsonException cannotBeSent) {
            return null;
        }
    }

    /** Waits for room to send another request, giving up at the deadline rather than after it. */
    private static boolean waitForASlot(Semaphore slots, Instant deadline) {
        Duration left = Duration.between(Instant.now(), deadline);
        if (left.isNegative() || left.isZero()) {
            return false;
        }
        try {
            return slots.tryAcquire(left.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Waits for every request already sent to be answered.
     *
     * @return how many were still unanswered when it stopped waiting
     */
    private static long waitForTheAnswersStillOwed(Semaphore slots, int workAhead,
            Duration howLong) {
        try {
            if (slots.tryAcquire(workAhead, howLong.toNanos(), TimeUnit.NANOSECONDS)) {
                return 0;
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
        return workAhead - slots.availablePermits();
    }

    private static void pauseWhileTheReportsCatchUp(EventStream events, Instant deadline) {
        while (events.undelivered() > ANNOUNCEMENTS_ALLOWED_TO_PILE_UP
                && Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(CATCH_UP_PAUSE);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Keeps track of how many requests actually came back with something.
     *
     * <p>A reply saying the API is broken is a result. A request that never reached anything at all
     * is not: it says something about the address it was sent to, and nothing whatsoever about the
     * API. Telling the two apart is what lets a run notice it is shouting into an empty room.
     *
     * <p>Counted from whichever thread the answer arrives on, so the counts are atomic.
     */
    private static final class Answers {

        private final AtomicLong answered = new AtomicLong();
        private final AtomicLong unanswered = new AtomicLong();

        void record(Interaction interaction) {
            if (interaction.isAnswered()) {
                answered.incrementAndGet();
            } else {
                unanswered.incrementAndGet();
            }
        }

        void recordNothingCameBack() {
            unanswered.incrementAndGet();
        }

        long answered() {
            return answered.get();
        }

        /**
         * Whether enough has come back, with nothing answered, to conclude the address is wrong.
         *
         * @param evidenceNeeded how many unanswered requests count as enough. As many as may be in
         *     flight at once, so the judgement is never made on one unlucky moment
         */
        boolean nothingIsThere(int evidenceNeeded) {
            return answered.get() == 0 && unanswered.get() >= evidenceNeeded;
        }
    }
}

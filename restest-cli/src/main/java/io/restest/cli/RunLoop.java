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
import io.restest.gen.RequestBuilder;
import io.restest.gen.Scheduler;
import java.time.Duration;
import java.time.Instant;
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
 * each answer is dealt with the moment it arrives, whichever it is, and outside the first round of
 * a run the loop waits only when every slot it is allowed is genuinely occupied.
 *
 * <p>What to send next, and when the time is up, it does not decide. It asks the {@link Scheduler},
 * which holds the deadline and the order the operations go in, and does what it is told: send a
 * request for this operation, wait for the answers still owed, or stop. What it does decide for
 * itself is to stop early on its own evidence - a round in which nothing could be sent, or an
 * address where nothing answers. The one waiting it does on the scheduler's behalf is at the start
 * of a run, where the first round goes in steps and each step waits for the answers to the one
 * before - and even that wait has a limit, so one request the API never answers costs the run that
 * limit once rather than the whole budget.
 */
final class RunLoop {

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
     * @param passes how many times the loop went round the whole list of operations, not counting
     *     the first round a run may begin with
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
     * Sends what the scheduler decides until it says the time is up, then waits for the answers
     * still owed.
     *
     * @param scheduler what decides what to send next, and when to stop. Asked from this thread
     *     only, because one scheduler, like the generator behind it, is one sequence of decisions
     * @param baseUrl where the API is
     * @param workAhead how many requests may be waiting for an answer at once
     * @param announcementsAllowedToPileUp how many announcements may be waiting to reach the
     *     reports before the loop pauses to let them catch up. Judging a reply, writing it to the
     *     run's file and printing a fault all take time, and an API on the same machine can answer
     *     faster than all three together; without a limit the backlog grows for as long as the run
     *     lasts, and a long run against a fast API ends by running out of memory rather than out of
     *     time. Pausing is the honest response, and is counted as time the tool wasted
     * @param howLongToWaitForStragglers how long to go on waiting, past the deadline, for answers to
     *     requests that had already gone out
     * @param engine what sends them
     * @param events where everything that happens is announced
     * @return what the loop did with the time
     */
    static Outcome run(Scheduler scheduler, String baseUrl, int workAhead,
            int announcementsAllowedToPileUp, Duration howLongToWaitForStragglers,
            HttpEngine engine, EventStream events) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(howLongToWaitForStragglers, "howLongToWaitForStragglers");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(events, "events");
        Instant deadline = scheduler.deadline();

        // One permit per request that may be outstanding. Taken before a request is invented and
        // given back by whichever answer arrives, so a slow operation costs one slot rather than
        // holding up the queue behind it.
        Semaphore slots = new Semaphore(workAhead);
        Answers answers = new Answers();
        long sent = 0;
        long sentInTheOrdinaryRounds = 0;
        long notGenerated = 0;
        long notAssembled = 0;
        long passes = 0;
        // One permit per answer to a request of the current step of the first round. A fresh one
        // for every step, so an answer that arrives too late for its own step is not mistaken for
        // an answer to the next.
        Semaphore stepAnswered = new Semaphore(0);
        int sentInThisStep = 0;

        try {
            deciding:
            while (true) {
                Scheduler.Step step = scheduler.next();
                switch (step) {
                    case Scheduler.Step.TimeIsUp ignored -> {
                        break deciding;
                    }
                    case Scheduler.Step.WaitForTheAnswers waiting -> {
                        waitForTheStep(stepAnswered, sentInThisStep, waiting.atMost(), events);
                        stepAnswered = new Semaphore(0);
                        sentInThisStep = 0;
                    }
                    case Scheduler.Step.EndOfAPass end -> {
                        if (!end.ofTheOpeningLap()) {
                            passes++;
                            // Nothing in this document could be sent even once in an ordinary
                            // round. Going round again would produce the same nothing until the
                            // deadline, so stop and let the run say why.
                            if (sentInTheOrdinaryRounds == 0) {
                                break deciding;
                            }
                        }
                        // Enough requests have come back to know that nothing is listening at this
                        // address. Carrying on would spend the whole budget collecting the same
                        // refusal - tens of thousands of times, because an address with nothing
                        // behind it refuses instantly - and bury the one useful sentence, which is
                        // that the address is wrong.
                        if (answers.nothingIsThere(workAhead)) {
                            break deciding;
                        }
                    }
                    case Scheduler.Step.Send send -> {
                        if (!waitForASlot(slots, deadline)) {
                            break deciding;
                        }
                        pauseWhileTheReportsCatchUp(events, deadline, announcementsAllowedToPileUp);

                        Operation operation = send.operation();
                        Optional<TestCase> testCase = scheduler.testCaseFor(send);
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
                            events.publish(new RunEvent.TestCasePlanned(Instant.now(),
                                    testCase.get()));
                            send(engine, testCase.get(), request, slots,
                                    send.inTheOpeningLap() ? stepAnswered : null, answers, events);
                            sent++;
                            if (send.inTheOpeningLap()) {
                                sentInThisStep++;
                            } else {
                                sentInTheOrdinaryRounds++;
                            }
                        }
                    }
                }
            }
        } finally {
            // However the deciding ended, a first round still going is announced as cut short, so
            // the reports never hear of one that began and did not end.
            scheduler.stop();
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
            Semaphore slots, Semaphore alsoTell, Answers answers, EventStream events) {
        try {
            engine.sendAsync(testCase, request).whenComplete((interaction, wentWrong) -> {
                try {
                    announce(interaction, answers, events);
                } finally {
                    slots.release();
                    // After the announcement, never before: whoever is waiting on this is waiting
                    // to know that what came back is on its way to everybody listening.
                    if (alsoTell != null) {
                        alsoTell.release();
                    }
                }
            });
        } catch (RuntimeException couldNotEvenBeStarted) {
            slots.release();
            if (alsoTell != null) {
                alsoTell.release();
            }
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

    /**
     * Waits until every request of one step of the first round has been answered, and until what
     * came back has been heard by everybody listening, but no longer than the scheduler allows.
     *
     * <p>Both halves matter. An answer that has arrived but not yet been heard by whoever keeps
     * what the API returns is, for the next request, no answer at all: the identifier in it cannot
     * be sent until it has been taken in. Waiting for the announcements is waiting on the tool's own
     * work rather than on the API, and it is counted as time the tool wasted, like every other
     * pause.
     *
     * @param answered one permit per answer to a request of this step
     * @param sent how many requests this step sent
     * @param atMost the longest the two waits may take together
     */
    private static void waitForTheStep(Semaphore answered, int sent, Duration atMost,
            EventStream events) {
        long giveUpAt = System.nanoTime() + atMost.toNanos();
        try {
            if (!answered.tryAcquire(sent, atMost.toNanos(), TimeUnit.NANOSECONDS)) {
                return;
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return;
        }
        long left = giveUpAt - System.nanoTime();
        if (left > 0) {
            events.awaitDelivery(Duration.ofNanos(left));
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

    private static void pauseWhileTheReportsCatchUp(EventStream events, Instant deadline,
            int allowedToPileUp) {
        while (events.undelivered() > allowedToPileUp
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

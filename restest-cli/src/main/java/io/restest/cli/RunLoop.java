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
import io.restest.core.model.Operation;
import io.restest.gen.RandomTestCaseGenerator;
import io.restest.gen.RequestBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Spends the time a run was given, asking the API one question after another until it runs out.
 *
 * <p>It goes round the operations it can test, over and over, until the deadline: an operation is
 * not tried once and then forgotten, because every attempt invents fresh values and is therefore a
 * different test. A run given thirty seconds uses thirty seconds. That matters for more than
 * thoroughness - the headline number a run reports is how much of its time nothing was happening,
 * and a run that stopped after a fifth of a second would make that number meaningless.
 *
 * <p>Requests are sent without waiting for the previous answer, so that thinking about the next
 * request happens while the API is still answering the last one. That is the whole reason this is a
 * queue rather than a straight line: on a straight line, every millisecond spent inventing a request
 * is a millisecond in which the API is being asked nothing, and the run's own measurement could not
 * tell that apart from an API that is simply slow.
 *
 * <p>Two things it deliberately is not. It is not a scheduler: it has no notion of which operations
 * deserve more of the time or of dividing the time into phases. And it is not clever about what to
 * send - deciding that belongs to the part of the tool that invents values, which is asked for the
 * next test case and never told what time it is.
 */
final class RunLoop {

    /**
     * How many requests may be waiting for an answer at the same time.
     *
     * <p>Twice what the engine will ever have in flight. The engine decides the real number for
     * itself, moving it up and down as the API turns out to be fast or slow; this is only a ceiling
     * on how far ahead the loop is allowed to run, and it sits above the engine's own so that the
     * engine is never left with a free slot while the next request is still being invented.
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
     * that stopped it, so it is counted as time the tool wasted and shows up in the run's summary.
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
     */
    record Outcome(long sent, long answered, long notGenerated, long notAssembled, long passes) {

        /** Whether requests went out and not one of them was ever answered. */
        boolean nothingAnswered() {
            return sent > 0 && answered == 0;
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
     * @param engine what sends them
     * @param events where everything that happens is announced
     * @return what the loop did with the time
     */
    static Outcome run(List<Operation> operations, RandomTestCaseGenerator generator,
            String baseUrl, Instant deadline, int workAhead, HttpEngine engine, EventStream events) {
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(deadline, "deadline");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(events, "events");
        if (operations.isEmpty()) {
            throw new IllegalArgumentException("a run with no operations to test has nothing to do");
        }

        Deque<CompletableFuture<Interaction>> waitingForAnAnswer = new ArrayDeque<>();
        Answers answers = new Answers();
        long sent = 0;
        long notGenerated = 0;
        long notAssembled = 0;
        long passes = 0;
        int next = 0;
        boolean anythingSentThisPass = false;

        while (Instant.now().isBefore(deadline)) {
            while (waitingForAnAnswer.size() >= workAhead) {
                announce(waitingForAnAnswer.poll(), events, answers);
            }
            pauseWhileTheReportsCatchUp(events, deadline);

            Operation operation = operations.get(next);
            Optional<TestCase> testCase = generator.generate(operation);
            if (testCase.isEmpty()) {
                notGenerated++;
            } else {
                HttpRequestRecord request = assemble(operation, testCase.get(), baseUrl);
                if (request == null) {
                    notAssembled++;
                } else {
                    events.publish(new RunEvent.TestCasePlanned(Instant.now(), testCase.get()));
                    waitingForAnAnswer.add(engine.sendAsync(testCase.get(), request));
                    sent++;
                    anythingSentThisPass = true;
                }
            }

            next++;
            if (next == operations.size()) {
                next = 0;
                passes++;
                // Nothing in this document could be sent even once. Going round again would produce
                // the same nothing until the deadline, so stop and let the run say so. Only a first
                // pass that sent nothing at all counts: once anything has been sent, a later pass
                // where every value happens to be unusable is bad luck, not a dead end.
                if (!anythingSentThisPass && sent == 0) {
                    break;
                }
                // Enough requests have come back to know that nothing is listening at this address.
                // Carrying on would spend the whole budget collecting the same refusal - tens of
                // thousands of times against an address that answers instantly because nothing is
                // there - and bury the one useful sentence, which is that the address is wrong.
                if (answers.nothingIsThere(workAhead)) {
                    break;
                }
                anythingSentThisPass = false;
            }
        }

        // The deadline stops us asking new questions, not listening to the answers we are owed.
        // Those requests were paid for and what came back is evidence; throwing it away would also
        // leave the run's stored file disagreeing with the number of requests it says it sent.
        while (!waitingForAnAnswer.isEmpty()) {
            announce(waitingForAnAnswer.poll(), events, answers);
        }
        return new Outcome(sent, answers.answered(), notGenerated, notAssembled, passes);
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
     * @return the request, or {@code null} if this test case cannot be sent
     */
    private static HttpRequestRecord assemble(Operation operation, TestCase testCase,
            String baseUrl) {
        try {
            return RequestBuilder.build(operation, testCase, baseUrl);
        } catch (IllegalArgumentException cannotBeSent) {
            return null;
        }
    }

    private static void announce(CompletableFuture<Interaction> owed, EventStream events,
            Answers answers) {
        Interaction interaction = owed.join();
        answers.record(interaction);
        events.publish(new RunEvent.InteractionCompleted(Instant.now(), interaction));
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
     * <p>A reply that says the API is broken is a result. A request that never reached anything at
     * all is not: it says something about the address it was sent to, and nothing whatsoever about
     * the API. Telling the two apart is what lets a run notice it is shouting into an empty room.
     */
    private static final class Answers {

        private long answered;
        private long unanswered;

        void record(Interaction interaction) {
            if (interaction.isAnswered()) {
                answered++;
            } else {
                unanswered++;
            }
        }

        long answered() {
            return answered;
        }

        /**
         * Whether enough has come back, with nothing answered, to conclude the address is wrong.
         *
         * @param evidenceNeeded how many unanswered requests count as enough. As many as may be in
         *     flight at once, so the judgement is never made on a single unlucky moment
         */
        boolean nothingIsThere(int evidenceNeeded) {
            return answered == 0 && unanswered >= evidenceNeeded;
        }
    }
}

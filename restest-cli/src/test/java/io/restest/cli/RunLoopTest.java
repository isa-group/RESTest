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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.restest.core.event.EventStream;
import io.restest.core.event.RunEvent;
import io.restest.core.event.RunListener;
import io.restest.core.exec.EngineStatistics;
import io.restest.core.exec.HttpEngine;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.Payload;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.model.ApiModel;
import io.restest.gen.RandomTestCaseGenerator;
import io.restest.spec.SwaggerSpecificationParser;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The loop on its own, against an API this test decides the speed of.
 *
 * <p>Nothing here touches the network. What is being checked is how the loop spends the time it is
 * given: that it keeps going round rather than stopping after one pass, that one slow operation does
 * not bring the rest of the run to a halt, that it never has more requests outstanding than it was
 * allowed, that it slows down rather than piling up work when the reports cannot keep up, and that
 * it stops when there is demonstrably nothing to be gained by carrying on.
 */
class RunLoopTest {

    private static final int WORK_AHEAD = 8;

    /** Long enough for several passes even when one operation is slow, short enough to wait for. */
    private static final Duration BUDGET = Duration.ofMillis(1500);

    /** Long enough that a healthy run always drains, short enough that a stuck one does not hang. */
    private static final Duration PATIENT = Duration.ofSeconds(5);

    private final ApiModel model = new SwaggerSpecificationParser().parse("pet-shelter.yaml");
    private final ApiEngine engine = new ApiEngine();

    @AfterEach
    void stopTheEngine() {
        engine.close();
    }

    @Test
    @DisplayName("the operations are tested over and over until the time runs out")
    void the_whole_budget_is_spent() {
        RunLoop.Outcome outcome = run(BUDGET, listening -> { });

        assertThat(outcome.passes())
                .describedAs("an operation is not tried once and then forgotten")
                .isGreaterThan(1);
        assertThat(outcome.sent())
                .describedAs("more requests went out than there are operations")
                .isGreaterThan(model.operations().size());
        assertThat(outcome.stillOwed())
                .describedAs("every answer arrived before the run gave up waiting: %s", outcome)
                .isZero();
        assertThat(outcome.answered())
                .describedAs("and every one of them was an answer: %s", outcome)
                .isEqualTo(outcome.sent());
    }

    @Test
    @DisplayName("one slow operation does not stop the rest of the API being tested")
    void a_slow_operation_does_not_hold_up_the_run() {
        // One request takes far longer than the budget; everything after it is quick. A loop that
        // insisted on answers in the order it sent them would fill its slots, reach the head of the
        // queue, and sit there for the rest of the run - having sent exactly as many requests as it
        // had slots. Worse, it would report almost no wasted time while doing it, because there
        // would always be that one request in flight.
        engine.takes(request -> engine.asked() <= 1 ? Duration.ofSeconds(30) : Duration.ofMillis(1));

        RunLoop.Outcome outcome = run(BUDGET, listening -> { });

        assertThat(outcome.sent())
                .describedAs("one stuck request costs one slot, not the run: %s", outcome)
                .isGreaterThan(WORK_AHEAD * 10L);
        assertThat(outcome.stillOwed())
                .describedAs("and the one that never came back is reported rather than waited out")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("the engine is never asked for more at once than the loop was allowed")
    void the_work_ahead_is_bounded() {
        engine.takes(request -> Duration.ofMillis(5));

        run(BUDGET, listening -> { });

        assertThat(engine.mostAtOnce.get())
                .describedAs("with every request taking real time, the bound is what stops the "
                        + "loop running away")
                .isBetween(2, WORK_AHEAD);
    }

    @Test
    @DisplayName("the loop slows down when the reports cannot keep up, rather than piling work up")
    void announcements_do_not_pile_up_without_limit() {
        engine.takes(request -> Duration.ofMillis(1));

        // A listener slow enough that, unchecked, the loop would leave it tens of thousands of
        // events behind within the budget.
        Undelivered watched = new Undelivered();
        run(BUDGET, events -> {
            events.subscribe(slowListener());
            watched.stream = events;
        });

        assertThat(watched.highestSeen)
                .describedAs("the backlog is held near its limit rather than growing all run")
                .isLessThan(RunLoop.ANNOUNCEMENTS_ALLOWED_TO_PILE_UP * 2L);
    }

    @Test
    @DisplayName("every request that was sent is announced, and nothing else is")
    void everything_sent_is_announced() {
        Counting heard = new Counting();

        RunLoop.Outcome outcome = run(BUDGET, events -> events.subscribe(heard));

        assertThat(heard.completed.get()).isEqualTo((int) outcome.sent());
        assertThat(heard.planned.get()).isEqualTo((int) outcome.sent());
    }

    @Test
    @DisplayName("a run against an address where nothing is listening gives up rather than spinning")
    void an_address_with_nothing_behind_it_stops_the_run_early() {
        engine.answers = false;

        Instant before = Instant.now();
        RunLoop.Outcome outcome = run(Duration.ofSeconds(30), listening -> { });

        assertThat(Duration.between(before, Instant.now()))
                .describedAs("it does not sit there for the whole half minute it was given")
                .isLessThan(Duration.ofSeconds(10));
        assertThat(outcome.answered()).isZero();
        assertThat(outcome.nothingAnswered()).isTrue();
        assertThat(outcome.sent())
                .describedAs("but it does send enough to be sure, rather than giving up on one")
                .isGreaterThanOrEqualTo(WORK_AHEAD);
    }

    @Test
    @DisplayName("a document nothing can be sent for stops after one pass instead of spinning")
    void a_document_that_yields_no_request_stops_after_one_pass() {
        // A base address carrying a query string is one no request can be built on, so every
        // operation fails to assemble - the shape of a run that could never send anything at all.
        RunLoop.Outcome outcome;
        try (EventStream events = new EventStream()) {
            outcome = RunLoop.run(model.operations(), generator(), "https://api.example?key=abc",
                    Instant.now().plusSeconds(30), WORK_AHEAD, PATIENT, engine, events);
        }

        assertThat(outcome.sent()).isZero();
        assertThat(outcome.notAssembled()).isEqualTo(model.operations().size());
        assertThat(outcome.passes()).isEqualTo(1);
        assertThat(outcome.nothingCouldBeBuilt())
                .describedAs("and the run can say which of the several ways of testing nothing "
                        + "this was")
                .isTrue();
    }

    @Test
    @DisplayName("a run with nothing to test is refused rather than looping over an empty list")
    void no_operations_is_not_a_run() {
        try (EventStream events = new EventStream()) {
            assertThatThrownBy(() -> RunLoop.run(List.of(), generator(), "https://api.example",
                    Instant.now().plusSeconds(1), WORK_AHEAD, PATIENT, engine, events))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nothing to do");
        }
    }

    private RunLoop.Outcome run(Duration budget, java.util.function.Consumer<EventStream> setUp) {
        try (EventStream events = new EventStream()) {
            setUp.accept(events);
            return RunLoop.run(model.operations(), generator(), "https://api.example",
                    Instant.now().plus(budget), WORK_AHEAD, PATIENT, engine, events);
        }
    }

    private RandomTestCaseGenerator generator() {
        return new RandomTestCaseGenerator(model, 20260914L);
    }

    /** A listener slow enough that the loop has to wait for it. */
    private RunListener slowListener() {
        return event -> {
            try {
                Thread.sleep(2);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            }
        };
    }

    /** Watches how far the announcements ever got ahead of the listeners. */
    private static final class Undelivered implements RunListener {

        private volatile EventStream stream;
        private volatile long highestSeen;

        @Override
        public void on(RunEvent event) {
            EventStream watching = stream;
            if (watching != null) {
                highestSeen = Math.max(highestSeen, watching.undelivered());
            }
        }
    }

    /** Counts what reached the listeners, which is how "announced" is checked without a report. */
    private static final class Counting implements RunListener {

        private final AtomicInteger planned = new AtomicInteger();
        private final AtomicInteger completed = new AtomicInteger();

        @Override
        public void on(RunEvent event) {
            if (event instanceof RunEvent.TestCasePlanned) {
                planned.incrementAndGet();
            } else if (event instanceof RunEvent.InteractionCompleted) {
                completed.incrementAndGet();
            }
        }
    }

    /**
     * An API whose speed this test decides, one operation at a time.
     *
     * <p>Answers arrive later than they were asked for, on another thread, which is the only way to
     * put a loop that is supposed to overlap its requests under any real pressure. An engine that
     * answered the instant it was called would leave every test here passing whatever the loop did.
     */
    private static final class ApiEngine implements HttpEngine {

        private final ScheduledExecutorService later = Executors.newScheduledThreadPool(16);
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger mostAtOnce = new AtomicInteger();
        private final AtomicInteger sent = new AtomicInteger();

        private volatile Function<HttpRequestRecord, Duration> howLong =
                request -> Duration.ofMillis(1);
        private volatile boolean answers = true;

        void takes(Function<HttpRequestRecord, Duration> perRequest) {
            this.howLong = perRequest;
        }

        /** How many requests have been asked for so far. */
        int asked() {
            return sent.get();
        }

        @Override
        public CompletableFuture<Interaction> sendAsync(TestCase testCase,
                HttpRequestRecord request) {
            mostAtOnce.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            sent.incrementAndGet();
            CompletableFuture<Interaction> answer = new CompletableFuture<>();
            later.schedule(() -> {
                inFlight.decrementAndGet();
                answer.complete(reply(testCase, request));
            }, howLong.apply(request).toMillis(), TimeUnit.MILLISECONDS);
            return answer;
        }

        private Interaction reply(TestCase testCase, HttpRequestRecord request) {
            if (!answers) {
                return Interaction.transportFailure(testCase, request,
                        "nothing was listening", Instant.EPOCH, Duration.ZERO);
            }
            return Interaction.answered(testCase, request,
                    new HttpResponseRecord(StatusLine.of(200),
                            List.of(Header.of("Content-Type", "application/json")),
                            Optional.of(Payload.of("[]".getBytes(StandardCharsets.UTF_8),
                                    "application/json"))),
                    Instant.EPOCH, Duration.ofMillis(1));
        }

        @Override
        public EngineStatistics statistics() {
            return new EngineStatistics(sent.get(), Duration.ofSeconds(1), Duration.ZERO,
                    Duration.ofMillis(sent.get()), mostAtOnce.get(), WORK_AHEAD);
        }

        @Override
        public void close() {
            later.shutdownNow();
        }
    }
}

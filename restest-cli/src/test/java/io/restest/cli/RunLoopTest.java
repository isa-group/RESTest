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
import io.restest.core.model.Operation;
import io.restest.gen.RandomTestCaseGenerator;
import io.restest.spec.SwaggerSpecificationParser;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The loop on its own, with an API that answers instantly and a clock this test controls.
 *
 * <p>Nothing here touches the network. What is being checked is how the loop spends the time it is
 * given: that it keeps going round rather than stopping after one pass, that it stops when there is
 * demonstrably nothing to be gained by carrying on, and that everything it sent is announced.
 */
class RunLoopTest {

    private static final int WORK_AHEAD = 8;

    private final ApiModel model = new SwaggerSpecificationParser().parse("pet-shelter.yaml");

    @Test
    @DisplayName("the operations are tested over and over until the time runs out")
    void the_whole_budget_is_spent() {
        AnsweringEngine engine = new AnsweringEngine(true);

        RunLoop.Outcome outcome = runFor(Duration.ofMillis(300), engine);

        assertThat(outcome.passes())
                .describedAs("an operation is not tried once and then forgotten")
                .isGreaterThan(1);
        assertThat(outcome.sent())
                .describedAs("more requests went out than there are operations")
                .isGreaterThan(model.operations().size());
        assertThat(outcome.answered()).isEqualTo(outcome.sent());
    }

    @Test
    @DisplayName("every request that was sent is announced, and nothing else is")
    void everything_sent_is_announced() {
        AnsweringEngine engine = new AnsweringEngine(true);
        CountingListener heard = new CountingListener();

        RunLoop.Outcome outcome;
        try (EventStream events = new EventStream()) {
            events.subscribe(heard);
            outcome = RunLoop.run(model.operations(), generator(), "https://api.example",
                    Instant.now().plus(Duration.ofMillis(200)), WORK_AHEAD, engine, events);
        }

        assertThat(heard.completed.get()).isEqualTo((int) outcome.sent());
        assertThat(heard.planned.get()).isEqualTo((int) outcome.sent());
    }

    @Test
    @DisplayName("the engine is never asked for more at once than it was told to keep in flight")
    void the_work_ahead_is_bounded() {
        AnsweringEngine engine = new AnsweringEngine(true);

        runFor(Duration.ofMillis(200), engine);

        assertThat(engine.mostAtOnce.get()).isLessThanOrEqualTo(WORK_AHEAD);
    }

    @Test
    @DisplayName("a run against an address where nothing is listening gives up rather than spinning")
    void an_address_with_nothing_behind_it_stops_the_run_early() {
        AnsweringEngine refusing = new AnsweringEngine(false);

        Instant before = Instant.now();
        RunLoop.Outcome outcome = runFor(Duration.ofSeconds(30), refusing);

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
        AnsweringEngine engine = new AnsweringEngine(true);

        // A base address carrying a query string is one no request can be built on, so every
        // operation in the document fails to assemble - which is the shape of a run that could
        // never send anything at all.
        RunLoop.Outcome outcome;
        try (EventStream events = new EventStream()) {
            outcome = RunLoop.run(model.operations(), generator(), "https://api.example?key=abc",
                    Instant.now().plus(Duration.ofSeconds(30)), WORK_AHEAD, engine, events);
        }

        assertThat(outcome.sent()).isZero();
        assertThat(outcome.notAssembled()).isEqualTo(model.operations().size());
        assertThat(outcome.passes()).isEqualTo(1);
    }

    @Test
    @DisplayName("a run with nothing to test is refused rather than looping over an empty list")
    void no_operations_is_not_a_run() {
        try (EventStream events = new EventStream()) {
            assertThatThrownBy(() -> RunLoop.run(List.of(), generator(), "https://api.example",
                    Instant.now().plusSeconds(1), WORK_AHEAD, new AnsweringEngine(true), events))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nothing to do");
        }
    }

    private RunLoop.Outcome runFor(Duration budget, HttpEngine engine) {
        try (EventStream events = new EventStream()) {
            return RunLoop.run(model.operations(), generator(), "https://api.example",
                    Instant.now().plus(budget), WORK_AHEAD, engine, events);
        }
    }

    private RandomTestCaseGenerator generator() {
        return new RandomTestCaseGenerator(model, 20260914L);
    }

    /** Counts what reached the listeners, which is how "announced" is checked without a report. */
    private static final class CountingListener implements io.restest.core.event.RunListener {

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

    /** An API that answers instantly, or refuses to answer at all, and counts what it was asked. */
    private static final class AnsweringEngine implements HttpEngine {

        private final boolean answers;
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger mostAtOnce = new AtomicInteger();
        private final AtomicInteger sent = new AtomicInteger();

        AnsweringEngine(boolean answers) {
            this.answers = answers;
        }

        @Override
        public CompletableFuture<Interaction> sendAsync(TestCase testCase,
                HttpRequestRecord request) {
            mostAtOnce.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            sent.incrementAndGet();
            CompletableFuture<Interaction> answer =
                    CompletableFuture.completedFuture(reply(testCase, request));
            inFlight.decrementAndGet();
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
            // Nothing to shut: this engine holds no threads and no connections.
        }
    }
}

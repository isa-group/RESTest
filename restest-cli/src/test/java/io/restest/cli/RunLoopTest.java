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

import io.restest.core.event.EventStream;
import io.restest.core.event.RunEvent;
import io.restest.core.event.RunListener;
import io.restest.core.event.Waited;
import io.restest.core.exec.EngineStatistics;
import io.restest.core.exec.HttpEngine;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionId;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.Payload;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.ParameterLocation;
import io.restest.core.settings.ScheduleSettings;
import io.restest.core.settings.SequenceSettings;
import io.restest.core.settings.Settings;
import io.restest.gen.Campaign;
import io.restest.gen.Campaigns;
import io.restest.gen.Dictionaries;
import io.restest.gen.RandomTestCaseGenerator;
import io.restest.gen.Scheduler;
import io.restest.gen.WhichOperations;
import io.restest.spec.SwaggerSpecificationParser;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * it stops when there is demonstrably nothing to be gained by carrying on. And, at the start of a
 * run, that the first round goes in steps, each waiting for the answers to the one before, and that
 * a request the API never answers holds that round up for one wait and no longer.
 */
class RunLoopTest {

    private static final int WORK_AHEAD = 8;

    /** Long enough for several passes even when one operation is slow, short enough to wait for. */
    private static final Duration BUDGET = Duration.ofMillis(1500);

    /** Long enough that a healthy run always drains, short enough that a stuck one does not hang. */
    private static final Duration PATIENT = Duration.ofSeconds(5);

    /**
     * How long a run stopped from outside waits for its answers here: far less than the wait for
     * stragglers above, so that a run that waited the one rather than the other is told apart.
     */
    private static final Duration GRACE = Duration.ofMillis(300);

    /** How many announcements the loop under test lets pile up before it pauses for the reports. */
    private static final int ANNOUNCEMENTS_ALLOWED =
            ScheduleSettings.defaults().announcementsAllowedToPileUp();

    /** How a run is scheduled when nobody says otherwise: it begins with a first round. */
    private static final ScheduleSettings WITH_A_FIRST_ROUND = ScheduleSettings.defaults();

    /** The same, with no first round, for what is only about the ordinary rounds after it. */
    private static final ScheduleSettings WITHOUT_A_FIRST_ROUND = new ScheduleSettings(
            WITH_A_FIRST_ROUND.workAheadFactor(), ANNOUNCEMENTS_ALLOWED,
            WITH_A_FIRST_ROUND.stragglerGrace(), WITH_A_FIRST_ROUND.interruptGrace(), false,
            WITH_A_FIRST_ROUND.openingLapPatience(), false, 1_000,
            WITH_A_FIRST_ROUND.interruptWritingGrace(), WITH_A_FIRST_ROUND.interruptLookingGrace(),
            true, true, Duration.ofSeconds(1), Duration.ofSeconds(60));

    /** No limit on how many requests a run here may send: its budget alone ends it. */
    private static final long NO_CAP = Long.MAX_VALUE;

    /** The API under test: pet-shelter's four operations, unless a test says otherwise. */
    private ApiModel model = new SwaggerSpecificationParser().parse("pet-shelter.yaml");
    private final ApiEngine engine = new ApiEngine();

    /** Whether the runs here stop when the API answers 401 to the whole opening lap, as by default. */
    private boolean watchTheLap = true;

    /** Whether the runs here leave alone what the API asks to be left alone, as by default. */
    private boolean waitWhenToldTo = true;

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
    @DisplayName("told its size, a run stops once it has sent that many requests, having waited "
            + "for their answers")
    void a_run_of_a_known_size_stops_after_that_many_requests() {
        engine.takes(request -> Duration.ofMillis(1));
        long began = System.nanoTime();

        RunLoop.Outcome outcome = run(Duration.ofSeconds(30), WORK_AHEAD, ANNOUNCEMENTS_ALLOWED,
                12, WITH_A_FIRST_ROUND, new StopFromOutside(), listening -> { });

        assertThat(outcome.sent())
                .describedAs("the first round's requests count towards the size: %s", outcome)
                .isEqualTo(12);
        assertThat(outcome.answered()).isEqualTo(12);
        assertThat(outcome.stillOwed())
                .describedAs("the answers to the last requests were waited for: %s", outcome)
                .isZero();
        assertThat(outcome.cutShort())
                .describedAs("a run that ends at its size ended as it was meant to")
                .isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - began))
                .describedAs("and it ended long before its budget of thirty seconds")
                .isLessThan(Duration.ofSeconds(10));
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

        // Without the first round, whose steps wait for their answers on purpose and have a limit
        // of their own, checked further down. This is about every round after it.
        RunLoop.Outcome outcome = run(BUDGET, WITHOUT_A_FIRST_ROUND, listening -> { });

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
            events.subscribe(watched);
        });

        assertThat(watched.highestSeen)
                .describedAs("the backlog is held near its limit rather than growing all run")
                .isLessThan(ANNOUNCEMENTS_ALLOWED * 2L);
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
    @DisplayName("how far ahead of the API the loop may work is a number it is told, so a run set "
            + "to one request at a time has one request at a time")
    void how_far_ahead_to_work_is_a_setting() {
        engine.takes(request -> Duration.ofMillis(2));

        run(BUDGET, 1, ANNOUNCEMENTS_ALLOWED, events -> { });

        assertThat(engine.mostAtOnce.get())
                .describedAs("at the usual setting this run has several in flight at once")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("and how many announcements may wait for the reports is another, so a run told to "
            + "let fewer pile up lets fewer pile up")
    void how_many_announcements_may_wait_is_a_setting() {
        engine.takes(request -> Duration.ofMillis(1));

        Undelivered watched = new Undelivered();
        run(BUDGET, WORK_AHEAD, 4, events -> {
            events.subscribe(slowListener());
            watched.stream = events;
            events.subscribe(watched);
        });

        assertThat(watched.highestSeen)
                .describedAs("held near the four it was given rather than near the thousand it "
                        + "would otherwise have been")
                .isLessThan(4 + 2L * WORK_AHEAD);
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
    @DisplayName("a request RESTest loses is counted apart with what went wrong, and the run goes on")
    void a_lost_request_is_counted_apart_and_the_run_goes_on() {
        engine.loses = testCase -> testCase.operation().value().equals("listPets");

        RunLoop.Outcome outcome = run(BUDGET, listening -> { });

        assertThat(outcome.lost()).describedAs("%s", outcome).isPositive();
        assertThat(outcome.firstLoss()).get(org.assertj.core.api.InstanceOfAssertFactories.THROWABLE)
                .describedAs("the failure itself, not the wrapping it arrived in")
                .isInstanceOf(StackOverflowError.class)
                .hasMessage("lost on the way to listPets");
        assertThat(outcome.answered() + outcome.lost())
                .describedAs("every request was either answered or lost: %s", outcome)
                .isEqualTo(outcome.sent());
        assertThat(outcome.passes())
                .describedAs("one operation lost does not end the run for the others")
                .isGreaterThan(1);
        assertThat(outcome.nothingAnswered()).isFalse();
    }

    @Test
    @DisplayName("a request the engine hands nothing back for is lost too, with nothing to show")
    void a_request_handed_back_as_nothing_is_lost() {
        engine.loses = testCase -> testCase.operation().value().equals("listPets");
        engine.losesWithoutSaying = true;

        RunLoop.Outcome outcome = run(BUDGET, listening -> { });

        assertThat(outcome.lost()).describedAs("%s", outcome).isPositive();
        assertThat(outcome.firstLoss()).isEmpty();
    }

    @Test
    @DisplayName("a run that loses every request stops as early as one whose address answers nothing")
    void losing_every_request_stops_the_run_early() {
        engine.loses = testCase -> true;

        Instant before = Instant.now();
        RunLoop.Outcome outcome = run(Duration.ofSeconds(30), listening -> { });

        assertThat(Duration.between(before, Instant.now()))
                .describedAs("it does not go on losing requests for the half minute it was given")
                .isLessThan(Duration.ofSeconds(10));
        assertThat(outcome.answered()).isZero();
        assertThat(outcome.lost()).isEqualTo(outcome.sent());
        assertThat(outcome.sent())
                .describedAs("but it does lose enough to be sure, rather than stopping at the first")
                .isGreaterThanOrEqualTo(WORK_AHEAD);
    }

    @Test
    @DisplayName("a step of a series RESTest loses is heard as a step nothing came back to, so its series goes on")
    void a_lost_step_of_a_series_is_heard_and_its_series_goes_on() {
        // Reading around a pet: create it, read it, read the list, read it again. Reading the list
        // only adds an observation, so a series whose list read came back with nothing goes on to
        // the read after it - which it can only do if the lost step was heard. Unheard, the series
        // would wait for an answer that never comes, and its last read would never go out.
        engine.repliesWith(testCase -> "{\"id\": 7}");
        engine.loses = testCase -> testCase.sequence().isPresent()
                && testCase.operation().value().equals("listPets");
        Campaign onlySeries = new Campaign(List.of(new Campaign.PlannedStrategy("sequences", 100,
                List.of(new Campaign.Entry.Single(new Campaign.Source.Builtin(
                        Campaign.Builtin.RANDOM))), false, true)), WhichOperations.everything());
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model, 20260914L,
                List.of(), onlySeries, Settings.defaults().withSequences(
                        new SequenceSettings(false, false, false, false, true, false)));

        RunLoop.Outcome outcome;
        try (EventStream events = new EventStream()) {
            Scheduler scheduler = new Scheduler(generator, WITHOUT_A_FIRST_ROUND,
                    Instant.now().plus(BUDGET), InstantSource.system(), events::publish);
            outcome = RunLoop.run(scheduler, "https://api.example", WORK_AHEAD,
                    ANNOUNCEMENTS_ALLOWED, NO_CAP, PATIENT, GRACE, new StopFromOutside(),
                    RefusedOpeningLap.watching(true), obeying(scheduler), engine,
                    events);
        }

        assertThat(outcome.lost()).describedAs("list reads were sent and lost: %s", outcome)
                .isPositive();
        Map<InteractionId, List<TestCase>> bySeries = new HashMap<>();
        engine.stepsSentAt.keySet().forEach(step -> bySeries.computeIfAbsent(
                step.sequence().orElseThrow().step() == 1
                        ? engine.exchangeOf.get(step)
                        : step.sequence().orElseThrow().follows().get(0),
                ignored -> new ArrayList<>()).add(step));
        assertThat(bySeries.values())
                .describedAs("a series whose list read was lost still sent the read after it")
                .anySatisfy(series -> {
                    int lostAt = series.stream()
                            .filter(step -> step.operation().value().equals("listPets"))
                            .mapToInt(step -> step.sequence().orElseThrow().step())
                            .min().orElse(Integer.MAX_VALUE);
                    assertThat(series).anySatisfy(step -> assertThat(
                            step.sequence().orElseThrow().step()).isGreaterThan(lostAt));
                });
    }

    @Test
    @DisplayName("a document nothing can be sent for stops after one pass instead of spinning")
    void a_document_that_yields_no_request_stops_after_one_pass() {
        // A base address carrying a query string is one no request can be built on, so every
        // operation fails to assemble - the shape of a run that could never send anything at all.
        RunLoop.Outcome outcome = runAgainst("https://api.example?key=abc", WITHOUT_A_FIRST_ROUND);

        assertThat(outcome.sent()).isZero();
        assertThat(outcome.notAssembled()).isEqualTo(model.operations().size());
        assertThat(outcome.passes()).isEqualTo(1);
        assertThat(outcome.nothingCouldBeBuilt())
                .describedAs("and the run can say which of the several ways of testing nothing "
                        + "this was")
                .isTrue();
    }

    @Test
    @DisplayName("and one that begins with a first round stops after that round and one more")
    void a_document_that_yields_no_request_stops_after_one_ordinary_pass_too() {
        RunLoop.Outcome outcome = runAgainst("https://api.example?key=abc", WITH_A_FIRST_ROUND);

        assertThat(outcome.sent()).isZero();
        assertThat(outcome.notAssembled())
                .describedAs("every operation tried once in the first round and once more after it")
                .isEqualTo(2L * model.operations().size());
        assertThat(outcome.passes())
                .describedAs("the first round is not one of the ordinary rounds a run counts")
                .isEqualTo(1);
        assertThat(outcome.nothingCouldBeBuilt()).isTrue();
    }

    @Test
    @DisplayName("a run begins by sending every operation once: the lists, then what creates, then "
            + "what reads one thing")
    void the_first_round_sends_every_operation_once_in_steps() {
        List<String> heard = new CopyOnWriteArrayList<>();

        run(BUDGET, events -> events.subscribe(event -> {
            switch (event) {
                case RunEvent.PhaseStarted started -> heard.add("[" + started.phase());
                case RunEvent.PhaseFinished finished -> heard.add(finished.phase() + "]");
                case RunEvent.TestCasePlanned planned ->
                        heard.add(planned.testCase().operation().value());
                default -> { }
            }
        }));

        assertThat(heard.subList(0, 6)).containsExactly("[opening lap", "listPets",
                "listShelters", "addPet", "getPet", "opening lap]");
        assertThat(heard.subList(6, heard.size()))
                .describedAs("and then goes round in the order the document declares them")
                .startsWith("listPets", "addPet", "getPet", "listShelters");
    }

    @Test
    @DisplayName("a step of the first round is only sent once the step before it has been answered")
    void each_step_of_the_first_round_waits_for_the_one_before() {
        engine.takes(request -> request.method() == io.restest.core.model.HttpMethod.POST
                ? Duration.ofMillis(300) : Duration.ofMillis(1));

        run(BUDGET, listening -> { });

        assertThat(engine.firstSent.get("getPet"))
                .describedAs("reading one pet waits for the pet being created to be answered, "
                        + "which took three hundred milliseconds")
                .isGreaterThanOrEqualTo(engine.firstAnswered.get("addPet"));
        assertThat(engine.firstSent.get("addPet"))
                .describedAs("and creating one waits for the lists")
                .isGreaterThanOrEqualTo(engine.firstAnswered.get("listPets"))
                .isGreaterThanOrEqualTo(engine.firstAnswered.get("listShelters"));
    }

    @Test
    @DisplayName("a step of the first round is built only once what the step before brought back "
            + "has been heard by the memory of what the API returned")
    void each_step_is_built_from_what_the_step_before_brought_back() {
        // The list of pets names pet seven, and reading one pet is two steps later. A listener that
        // is slow to hear the first list is subscribed ahead of the memory, so that answer is in
        // long before the memory has heard it: waiting for the answers alone would build the read
        // of one pet from nothing, and only waiting for them to be heard gets pet seven into it.
        engine.repliesWith(testCase -> testCase.operation().value().equals("listPets")
                ? "[{\"id\": 7, \"name\": \"Rex\", \"petId\": 7}]" : "[]");
        // With a creation's body never filled afresh. The name in it is the sign that the list was
        // heard, and a body filled afresh would invent the name instead of taking the one the list
        // brought back, whatever the memory had heard by then.
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model, 20260914L,
                Dictionaries.fuzzing().map(List::of).orElse(List.of()), Campaigns.carried(),
                Settings.from(Map.of("generation.freshWhereMade", "false")));
        List<TestCase> planned = new CopyOnWriteArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean heldUpOnce =
                new java.util.concurrent.atomic.AtomicBoolean();

        try (EventStream events = new EventStream()) {
            events.subscribe(event -> {
                if (event instanceof RunEvent.InteractionCompleted completed
                        && completed.interaction().testCase().operation().value()
                                .equals("listPets")
                        && heldUpOnce.compareAndSet(false, true)) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException stopped) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            generator.whatListensToTheRun().forEach(events::subscribe);
            events.subscribe(event -> {
                if (event instanceof RunEvent.TestCasePlanned sent) {
                    planned.add(sent.testCase());
                }
            });
            // Three seconds: the whole first round has to fit, on the slowest machine CI has.
            Scheduler scheduler = new Scheduler(generator, WITH_A_FIRST_ROUND,
                    Instant.now().plusSeconds(3), InstantSource.system(), events::publish);
            RunLoop.run(scheduler, "https://api.example", WORK_AHEAD, ANNOUNCEMENTS_ALLOWED, NO_CAP,
                    PATIENT, GRACE, new StopFromOutside(),
                    RefusedOpeningLap.watching(true), obeying(scheduler), engine, events);
        }

        // The step straight after the list: the creation's body fills its name from the name
        // the list brought back, which is kafka's shape - the list in one step, the creations in
        // the next - and holds the wait after the very first step to account.
        JsonValue createdWith = planned.stream()
                .filter(testCase -> testCase.operation().value().equals("addPet"))
                .findFirst()
                .orElseThrow()
                .body()
                .orElseThrow()
                .value();
        assertThat(((JsonValue.JsonObject) createdWith).member("name"))
                .describedAs("the first round's creation names the pet the list named")
                .contains(JsonValue.of("Rex"));
        ParameterValue petId = planned.stream()
                .filter(testCase -> testCase.operation().value().equals("getPet"))
                .findFirst()
                .orElseThrow()
                .parameterValue("petId", ParameterLocation.PATH)
                .orElseThrow();
        assertThat(petId.value())
                .describedAs("the first round's read of one pet names the pet the list named")
                .isEqualTo(JsonValue.of(7));
        assertThat(petId.origin()).isInstanceOf(ValueOrigin.Derived.class);
    }

    @Test
    @DisplayName("a step of a series goes out only once the answer to the step before it is in, "
            + "and soon after it")
    void a_series_waits_for_each_answer() {
        engine.takes(request -> Duration.ofMillis(30));
        engine.repliesWith(testCase -> "{\"id\": 7}");
        Campaign onlySeries = new Campaign(List.of(new Campaign.PlannedStrategy("sequences", 100,
                List.of(new Campaign.Entry.Single(new Campaign.Source.Builtin(
                        Campaign.Builtin.RANDOM))), false, true)), WhichOperations.everything());
        // Reading around a pet: create it, read it, read the list, read it again - four steps,
        // each of which has to wait for the one before.
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model, 20260914L,
                List.of(), onlySeries, Settings.defaults().withSequences(
                        new SequenceSettings(false, false, false, false, true, false)));

        long deadline;
        try (EventStream events = new EventStream()) {
            deadline = System.nanoTime() + BUDGET.toNanos();
            Scheduler scheduler = new Scheduler(generator, WITHOUT_A_FIRST_ROUND,
                    Instant.now().plus(BUDGET), InstantSource.system(), events::publish);
            RunLoop.run(scheduler, "https://api.example", WORK_AHEAD, ANNOUNCEMENTS_ALLOWED, NO_CAP,
                    PATIENT, GRACE, new StopFromOutside(),
                    RefusedOpeningLap.watching(true), obeying(scheduler), engine, events);
        }

        // Each series by its creation's exchange, which every later step names first.
        Map<InteractionId, List<TestCase>> bySeries = new HashMap<>();
        engine.stepsSentAt.keySet().forEach(step -> bySeries.computeIfAbsent(
                step.sequence().orElseThrow().step() == 1
                        ? engine.exchangeOf.get(step)
                        : step.sequence().orElseThrow().follows().get(0),
                ignored -> new ArrayList<>()).add(step));
        assertThat(bySeries.values())
                .describedAs("series went further than their creation")
                .anySatisfy(series -> assertThat(series).hasSizeGreaterThan(2));
        for (List<TestCase> series : bySeries.values()) {
            series.sort(Comparator.comparingInt(step -> step.sequence().orElseThrow().step()));
            for (int at = 1; at < series.size(); at++) {
                TestCase before = series.get(at - 1);
                TestCase after = series.get(at);
                assertThat(engine.answeredAt.get(before))
                        .describedAs("step %d was answered before step %d went out",
                                before.sequence().orElseThrow().step(),
                                after.sequence().orElseThrow().step())
                        .isNotNull()
                        .isLessThanOrEqualTo(engine.stepsSentAt.get(after));
            }
            TestCase creation = series.get(0);
            Long created = engine.answeredAt.get(creation);
            if (created != null && created < deadline - Duration.ofMillis(300).toNanos()) {
                assertThat(series)
                        .describedAs("a creation answered well before the deadline was followed "
                                + "by the next step of its series")
                        .hasSizeGreaterThan(1);
            }
        }
    }

    @Test
    @DisplayName("a request the API never answers holds the first round up for one wait, no longer")
    void a_request_never_answered_holds_the_first_round_up_for_one_wait_at_most() {
        // The first request of the round - the list of pets - never comes back within the run.
        engine.takes(request -> engine.asked() <= 1 ? Duration.ofSeconds(30) : Duration.ofMillis(1));
        ScheduleSettings patientForAFifth = withPatience(Duration.ofMillis(200));

        long began = System.nanoTime();
        RunLoop.Outcome outcome = run(Duration.ofMillis(2500), patientForAFifth, listening -> { });

        Duration heldUp = Duration.ofNanos(engine.firstSent.get("addPet") - began);
        assertThat(heldUp)
                .describedAs("the round waited its fifth of a second for the list, then went on")
                .isGreaterThanOrEqualTo(Duration.ofMillis(200))
                .isLessThan(Duration.ofMillis(1500));
        assertThat(outcome.sent())
                .describedAs("and the rest of the run went ahead as usual: %s", outcome)
                .isGreaterThan(WORK_AHEAD * 10L);
        assertThat(outcome.stillOwed())
                .describedAs("the one that never came back is reported rather than waited out")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a run stopped from outside makes no new requests, and says it was cut short")
    void a_stop_from_outside_ends_the_run_at_once() {
        StopFromOutside stop = stoppedAfter(Duration.ofMillis(400));

        long began = System.nanoTime();
        RunLoop.Outcome outcome = run(Duration.ofSeconds(30), WORK_AHEAD, ANNOUNCEMENTS_ALLOWED,
                WITHOUT_A_FIRST_ROUND, stop, listening -> { });
        Duration took = Duration.ofNanos(System.nanoTime() - began);

        assertThat(outcome.cutShort()).describedAs("%s", outcome).isTrue();
        assertThat(took)
                .describedAs("thirty seconds were given, and the run stopped when it was told to")
                .isGreaterThanOrEqualTo(Duration.ofMillis(400))
                .isLessThan(Duration.ofSeconds(3));
        assertThat(outcome.sent()).describedAs("it had been sending until then").isPositive();
        assertThat(engine.asked())
                .describedAs("every request the engine was handed is one the run counted")
                .isEqualTo(outcome.sent());
        assertThat(outcome.answered())
                .describedAs("the API answered at once, so nothing was left owed: %s", outcome)
                .isEqualTo(outcome.sent());
        assertThat(outcome.stillOwed()).isZero();
    }

    @Test
    @DisplayName("a run stopped from outside waits its grace for the answers still owed, and no longer")
    void a_stop_waits_for_the_answers_owed_for_its_grace_only() {
        // After the first few, every request takes longer than the whole run: the slots fill with
        // them and stay full, which is what a run stopped with requests in flight looks like.
        engine.takes(request -> engine.asked() <= 20 ? Duration.ofMillis(1)
                : Duration.ofSeconds(30));
        StopFromOutside stop = stoppedAfter(Duration.ofMillis(600));

        RunLoop.Outcome outcome = run(Duration.ofSeconds(30), WORK_AHEAD, ANNOUNCEMENTS_ALLOWED,
                WITHOUT_A_FIRST_ROUND, stop, listening -> { });
        Duration afterTheStop = Duration.between(stop.askedAt().orElseThrow(), Instant.now());

        assertThat(outcome.cutShort()).isTrue();
        assertThat(afterTheStop)
                .describedAs("it waited the grace a stopped run is given, not the %s a run at its "
                        + "deadline waits", PATIENT)
                .isGreaterThanOrEqualTo(GRACE.minusMillis(20))
                .isLessThan(Duration.ofSeconds(2));
        assertThat(outcome.stillOwed())
                .describedAs("every slot was held by a request that never came back: %s", outcome)
                .isEqualTo(WORK_AHEAD);
        assertThat(outcome.answered() + outcome.stillOwed()).isEqualTo(outcome.sent());
    }

    @Test
    @DisplayName("a stop that comes while the run waits for its answers after the deadline shortens that wait")
    void a_stop_after_the_deadline_shortens_the_wait_for_stragglers() {
        engine.takes(request -> Duration.ofSeconds(30));
        StopFromOutside stop = stoppedAfter(Duration.ofMillis(1200));

        long began = System.nanoTime();
        RunLoop.Outcome outcome = run(Duration.ofMillis(500), WORK_AHEAD, ANNOUNCEMENTS_ALLOWED,
                WITHOUT_A_FIRST_ROUND, stop, listening -> { });
        Duration took = Duration.ofNanos(System.nanoTime() - began);

        assertThat(took)
                .describedAs("the run would have waited %s past its deadline; it was stopped "
                        + "during that wait and waited its grace from then", PATIENT)
                .isGreaterThanOrEqualTo(Duration.ofMillis(1200))
                .isLessThan(Duration.ofMillis(500).plus(PATIENT).minusSeconds(2));
        assertThat(outcome.cutShort())
                .describedAs("what was cut short was the wait for evidence already paid for")
                .isTrue();
        assertThat(outcome.stillOwed()).isEqualTo(outcome.sent()).isPositive();
    }

    @Test
    @DisplayName("a run stopped during its first round ends that round, cut short, without waiting it out")
    void a_stop_during_the_first_round_cuts_it_short() {
        // Every answer is slower than the round is willing to wait, so the round sits in a wait.
        engine.takes(request -> Duration.ofSeconds(20));
        ScheduleSettings patient = withPatience(Duration.ofSeconds(10));
        List<RunEvent.PhaseFinished> ended = new CopyOnWriteArrayList<>();
        StopFromOutside stop = stoppedAfter(Duration.ofMillis(400));

        long began = System.nanoTime();
        RunLoop.Outcome outcome = run(Duration.ofSeconds(30), WORK_AHEAD, ANNOUNCEMENTS_ALLOWED,
                patient, stop, listening -> listening.subscribe(event -> {
                    if (event instanceof RunEvent.PhaseFinished finished) {
                        ended.add(finished);
                    }
                }));
        Duration took = Duration.ofNanos(System.nanoTime() - began);

        assertThat(took).describedAs("the step would have waited ten seconds for its answers")
                .isLessThan(Duration.ofSeconds(3));
        assertThat(outcome.cutShort()).isTrue();
        assertThat(ended).singleElement()
                .satisfies(round -> assertThat(round.cutShort()).isTrue());
    }

    @Test
    @DisplayName("an answer that arrives after the run stopped waiting for it is never announced")
    void an_answer_after_the_wait_is_not_announced() throws InterruptedException {
        // Slower than the grace and quicker than the test: the answers are still on their way when
        // the loop stops waiting, and arrive while everybody is still listening.
        engine.takes(request -> Duration.ofMillis(900));
        StopFromOutside stop = stoppedAfter(Duration.ofMillis(200));
        Counting heard = new Counting();

        RunLoop.Outcome outcome;
        try (EventStream events = new EventStream()) {
            events.subscribe(heard);
            Scheduler scheduler = new Scheduler(generator(), WITHOUT_A_FIRST_ROUND,
                    Instant.now().plusSeconds(30), InstantSource.system(), events::publish);
            outcome = RunLoop.run(scheduler, "https://api.example", WORK_AHEAD,
                    ANNOUNCEMENTS_ALLOWED, NO_CAP, PATIENT, GRACE, stop,
                    RefusedOpeningLap.watching(true), obeying(scheduler), engine, events);
            Thread.sleep(1500);
            events.awaitDelivery(PATIENT);
        }

        assertThat(outcome.stillOwed()).describedAs("%s", outcome).isPositive();
        assertThat(heard.completed.get())
                .describedAs("what the reports and the stored run hear is what the run counted as "
                        + "answered, and the rest counts as never answered: %s", outcome)
                .isEqualTo(outcome.answered());
    }

    @Test
    @DisplayName("a loop that fails stops listening too, so nothing is announced after the run says it is over")
    void a_loop_that_fails_announces_nothing_afterwards() throws InterruptedException {
        // Four requests on their way, then an engine that will take no more: the loop fails while
        // their answers are still coming.
        engine.takes(request -> Duration.ofMillis(600));
        engine.refusesFrom = 5;
        Counting heard = new Counting();

        try (EventStream events = new EventStream()) {
            events.subscribe(heard);
            Scheduler scheduler = new Scheduler(generator(), WITHOUT_A_FIRST_ROUND,
                    Instant.now().plusSeconds(30), InstantSource.system(), events::publish);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> RunLoop.run(scheduler,
                    "https://api.example", WORK_AHEAD, ANNOUNCEMENTS_ALLOWED, NO_CAP, PATIENT,
                    GRACE,
                    new StopFromOutside(),
                    RefusedOpeningLap.watching(true), obeying(scheduler), engine, events))
                    .isInstanceOf(IllegalStateException.class);
            Thread.sleep(1200);
            events.awaitDelivery(PATIENT);
        }

        assertThat(engine.asked()).describedAs("the premise: four went out").isEqualTo(4);
        assertThat(heard.completed.get())
                .describedAs("their answers came after the loop had failed, and the run had gone "
                        + "on to say it was over")
                .isZero();
    }

    @Test
    @DisplayName("a wait for stragglers longer than the clock can count is waited like any other")
    void a_wait_too_long_to_count_is_still_a_wait() {
        RunLoop.Outcome outcome;
        try (EventStream events = new EventStream()) {
            Scheduler scheduler = new Scheduler(generator(), WITHOUT_A_FIRST_ROUND,
                    Instant.now().plusMillis(300), InstantSource.system(), events::publish);
            outcome = RunLoop.run(scheduler, "https://api.example", WORK_AHEAD,
                    ANNOUNCEMENTS_ALLOWED, NO_CAP, Duration.ofMillis(Long.MAX_VALUE),
                    Duration.ofMillis(Long.MAX_VALUE), new StopFromOutside(),
                    RefusedOpeningLap.watching(true), obeying(scheduler), engine, events);
        }

        assertThat(outcome.sent()).isPositive();
        assertThat(outcome.stillOwed())
                .describedAs("every answer came back, so the endless wait ended when they had")
                .isZero();
    }

    @Test
    @DisplayName("a run whose every request of the opening lap is answered 401 stops sending once "
            + "the lap is over, long before its time is up")
    void a_run_refused_its_whole_opening_lap_stops_there() {
        engine.answersWith(testCase -> Optional.of(401));

        long began = System.nanoTime();
        RunLoop.Outcome outcome = run(Duration.ofSeconds(30), listening -> { });

        assertThat(outcome.refusedLap()).describedAs("%s", outcome).isPresent();
        long lap = outcome.refusedLap().orElseThrow().lapRequests();
        assertThat(lap).describedAs("one request for each operation")
                .isEqualTo(model.operations().size());
        assertThat(outcome.sent())
                .describedAs("nothing after the lap but what the engine had already been handed")
                .isLessThanOrEqualTo(lap + WORK_AHEAD);
        assertThat(outcome.stillOwed())
                .describedAs("the answers still owed were waited for, as at the end of a run")
                .isZero();
        assertThat(outcome.cutShort()).describedAs("nobody stopped it from outside").isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - began))
                .describedAs("thirty seconds were given")
                .isLessThan(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("one operation of the opening lap answered with anything but 401 keeps the run "
            + "going until its time is up")
    void one_operation_answered_otherwise_keeps_the_run_going() {
        engine.answersWith(testCase -> Optional.of(
                testCase.operation().value().equals("listShelters") ? 200 : 401));

        RunLoop.Outcome outcome = run(BUDGET, listening -> { });

        assertThat(outcome.refusedLap()).describedAs("%s", outcome).isEmpty();
        assertThat(outcome.passes()).describedAs("it went round again and again").isGreaterThan(1);
    }

    @Test
    @DisplayName("an operation of the opening lap that answers only after the lap has stopped "
            + "waiting for it is still heard before the run decides, whatever the lap's patience")
    void a_slow_answer_to_the_lap_is_heard_before_the_run_decides() {
        // Everything is refused at once except the list of shelters, which anybody may read and
        // which takes longer than the lap waits for it. Decided when the lap ended, the run would
        // have stopped without hearing the one operation that would have let it in.
        engine.answersWith(testCase -> Optional.of(
                testCase.operation().value().equals("listShelters") ? 200 : 401));
        engine.takes(request -> request.url().endsWith("/shelters")
                ? Duration.ofMillis(800) : Duration.ofMillis(1));

        for (Duration patience : List.of(Duration.ofMillis(200), Duration.ZERO)) {
            ScheduleSettings impatient = withPatience(patience);

            RunLoop.Outcome outcome = run(Duration.ofMillis(2000), impatient, listening -> { });

            assertThat(outcome.refusedLap())
                    .describedAs("waiting %s for each step: %s", patience, outcome).isEmpty();
            assertThat(outcome.sent())
                    .describedAs("and the run went on sending, waiting %s for each step", patience)
                    .isGreaterThan(model.operations().size() + WORK_AHEAD);
        }
    }

    @Test
    @DisplayName("a request of the opening lap that gets no reply at all keeps the run going")
    void a_lap_request_with_no_reply_keeps_the_run_going() {
        engine.answersWith(testCase -> testCase.operation().value().equals("listShelters")
                ? Optional.empty() : Optional.of(401));

        RunLoop.Outcome outcome = run(BUDGET, listening -> { });

        assertThat(outcome.refusedLap()).describedAs("%s", outcome).isEmpty();
        assertThat(outcome.passes()).isGreaterThan(1);
    }

    @Test
    @DisplayName("a run is never stopped this way without an opening lap, before its lap is over, "
            + "or with the switch off")
    void a_run_with_no_lap_over_is_never_stopped_this_way() {
        engine.answersWith(testCase -> Optional.of(401));

        RunLoop.Outcome noLap = run(BUDGET, WITHOUT_A_FIRST_ROUND, listening -> { });
        assertThat(noLap.refusedLap()).describedAs("without a lap: %s", noLap).isEmpty();
        assertThat(noLap.passes()).isGreaterThan(1);

        RunLoop.Outcome smallerThanTheLap = run(Duration.ofSeconds(30), WORK_AHEAD,
                ANNOUNCEMENTS_ALLOWED, 2, WITH_A_FIRST_ROUND, new StopFromOutside(),
                listening -> { });
        assertThat(smallerThanTheLap.refusedLap())
                .describedAs("a run of two requests never ends its lap: %s", smallerThanTheLap)
                .isEmpty();
        assertThat(smallerThanTheLap.sent()).isEqualTo(2);

        watchTheLap = false;
        RunLoop.Outcome switchedOff = run(BUDGET, listening -> { });
        assertThat(switchedOff.refusedLap()).describedAs("switched off: %s", switchedOff).isEmpty();
        assertThat(switchedOff.passes()).isGreaterThan(1);
    }

    @Test
    @DisplayName("an opening lap that nothing answered is a run nothing answered, not one the API "
            + "refused")
    void a_lap_nothing_answered_is_not_refused() {
        engine.answersWith(testCase -> Optional.empty());

        RunLoop.Outcome outcome = run(BUDGET, listening -> { });

        assertThat(outcome.refusedLap()).describedAs("%s", outcome).isEmpty();
        assertThat(outcome.nothingAnswered()).isTrue();
    }

    @Test
    @DisplayName("a run whose time runs out while the last request of its opening lap is on its way "
            + "is not said to have been refused, even when that request comes back 401")
    void a_lap_that_settles_after_the_time_is_up_did_not_stop_the_run() {
        // Every answer is 401, but the list of shelters takes longer than the whole run: the lap is
        // over, still waiting for it, when the time runs out, and it settles only while the run
        // waits for its answers. The run spent its budget; the API did not stop it.
        engine.answersWith(testCase -> Optional.of(401));
        engine.takes(request -> request.url().endsWith("/shelters")
                ? Duration.ofMillis(1500) : Duration.ofMillis(1));
        ScheduleSettings impatient = withPatience(Duration.ofMillis(100));

        RunLoop.Outcome outcome = run(Duration.ofMillis(800), impatient, listening -> { });

        assertThat(outcome.stillOwed())
                .describedAs("the premise: the slow requests were waited for, and came back: %s",
                        outcome)
                .isZero();
        assertThat(outcome.refusedLap()).describedAs("%s", outcome).isEmpty();
    }

    @Test
    @DisplayName("an opening lap whose slowest request comes back 401 after the lap went on still "
            + "stops the run, once that answer is in, and the run gives back the room it held")
    void a_slow_401_to_the_lap_stops_the_run_when_it_arrives() {
        // The list of shelters takes longer than the lap waits for it, so the lap is over before it
        // is answered and the run goes on sending; when the 401 comes, the next request is where
        // the run stops. A slot taken for that request and not given back would leave the run
        // waiting for an answer nobody owes it until the wait for stragglers ran out.
        engine.answersWith(testCase -> Optional.of(401));
        engine.takes(request -> request.url().endsWith("/shelters")
                ? Duration.ofMillis(800) : Duration.ofMillis(1));

        for (Duration patience : List.of(Duration.ofMillis(200), Duration.ZERO)) {
            long began = System.nanoTime();
            RunLoop.Outcome outcome = run(Duration.ofSeconds(30), withPatience(patience),
                    listening -> { });
            Duration took = Duration.ofNanos(System.nanoTime() - began);

            assertThat(outcome.refusedLap())
                    .describedAs("waiting %s for each step: %s", patience, outcome).isPresent();
            assertThat(outcome.sent())
                    .describedAs("the premise: it went on sending while it waited for the answer")
                    .isGreaterThan(model.operations().size());
            assertThat(took)
                    .describedAs("it stopped once the answer was in, and waited for nothing it "
                            + "was not owed")
                    .isLessThan(PATIENT.minusSeconds(1));
        }
    }

    @Test
    @DisplayName("a run the API stopped, then stopped from outside while it waited for its last "
            + "answers, says both")
    void a_refused_run_stopped_from_outside_while_it_waits_says_both() {
        // The lap is over before the list of shelters answers; everything sent after it takes
        // seconds, so the run is waiting for those when the 401 arrives and it stops, and it is
        // still waiting for them when it is stopped from outside.
        engine.answersWith(testCase -> Optional.of(401));
        engine.takes(request -> request.url().endsWith("/shelters") && engine.asked() <= 4
                ? Duration.ofMillis(800)
                : engine.asked() <= 4 ? Duration.ofMillis(1) : Duration.ofSeconds(4));

        RunLoop.Outcome outcome = run(Duration.ofSeconds(30), WORK_AHEAD, ANNOUNCEMENTS_ALLOWED,
                withPatience(Duration.ofMillis(200)), stoppedAfter(Duration.ofMillis(1600)),
                listening -> { });

        assertThat(outcome.refusedLap()).describedAs("%s", outcome).isPresent();
        assertThat(outcome.cutShort()).describedAs("%s", outcome).isTrue();
        assertThat(outcome.stillOwed())
                .describedAs("the requests sent after the lap were still on their way")
                .isPositive();
    }

    @Test
    @DisplayName("an operation the API asks to be left alone is skipped while the others go on")
    void an_operation_told_to_wait_is_skipped() {
        askForQuiet("3600", "getPet");

        RunLoop.Outcome outcome = run(BUDGET, WITHOUT_A_FIRST_ROUND, listening -> { });

        assertThat(engine.askedFor("getPet"))
                .describedAs("asked until the first answer said to wait, and never again")
                .isLessThanOrEqualTo(WORK_AHEAD);
        assertThat(engine.askedFor("listPets"))
                .describedAs("while the others went on: %s", outcome)
                .isGreaterThan(WORK_AHEAD * 10L);
        Waited waited = outcome.waited().orElseThrow();
        assertThat(waited.operations()).singleElement().satisfies(getPet -> {
            assertThat(getPet.operation().value()).isEqualTo("getPet");
            assertThat(getPet.times())
                    .describedAs("the refusals of the requests already on their way begin nothing")
                    .isEqualTo(1);
            assertThat(getPet.stated()).isEqualTo(1);
            assertThat(getPet.turnsSkipped()).isPositive();
        });
        assertThat(waited.wholeApi().times()).isZero();
        assertThat(engine.holds).isEmpty();
    }

    @Test
    @DisplayName("a second operation told to wait while another waits makes the whole API wait: "
            + "nothing at all is sent until it is over, and the engine is told to hold")
    void a_second_operation_makes_the_whole_api_wait() {
        askForQuiet("1", "getPet", "listShelters");

        RunLoop.Outcome outcome = run(Duration.ofMillis(2500), WITHOUT_A_FIRST_ROUND,
                listening -> { });

        assertThat(engine.holds).describedAs("the engine holds what it already has").isNotEmpty();
        assertThat(engine.longestQuiet())
                .describedAs("nothing was sent while the whole API waited: %s", outcome)
                .isGreaterThanOrEqualTo(Duration.ofMillis(900));
        Waited.WholeApi whole = outcome.waited().orElseThrow().wholeApi();
        assertThat(whole.times()).isPositive();
        assertThat(whole.waited()).isGreaterThanOrEqualTo(Duration.ofMillis(900));
    }

    @Test
    @DisplayName("a wait of the whole API ends with the run's time, and is counted no further")
    void the_whole_apis_wait_ends_with_the_run() {
        askForQuiet("3600", "listPets", "addPet", "getPet", "listShelters");
        long began = System.nanoTime();

        RunLoop.Outcome outcome = run(BUDGET, WITHOUT_A_FIRST_ROUND, listening -> { });

        assertThat(Duration.ofNanos(System.nanoTime() - began))
                .describedAs("an hour asked for, and the run still ends with its time")
                .isLessThan(BUDGET.plusSeconds(3));
        Waited.WholeApi whole = outcome.waited().orElseThrow().wholeApi();
        assertThat(whole.times()).isEqualTo(1);
        assertThat(whole.waited()).isLessThanOrEqualTo(BUDGET);
        assertThat(outcome.sent()).describedAs("%s", outcome).isLessThan(50);
        assertThat(engine.holds)
                .describedAs("a wait that lasts to the end holds the engine for good")
                .contains(ToldToWait.FOR_GOOD);
        assertThat(engine.dropped.get())
                .describedAs("and what it holds is dropped once the run stops sending, never sent")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a stop from outside ends a wait of the whole API at once")
    void a_stop_ends_the_whole_apis_wait() {
        askForQuiet("3600", "listPets", "addPet", "getPet", "listShelters");
        long began = System.nanoTime();

        RunLoop.Outcome outcome = run(Duration.ofSeconds(30), WORK_AHEAD, ANNOUNCEMENTS_ALLOWED,
                WITHOUT_A_FIRST_ROUND, stoppedAfter(Duration.ofMillis(500)), listening -> { });

        assertThat(outcome.cutShort()).isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - began))
                .describedAs("thirty seconds of quiet asked for, and the stop heard at once")
                .isLessThan(Duration.ofSeconds(5));
        assertThat(outcome.waited().orElseThrow().wholeApi().times()).isEqualTo(1);
    }

    @Test
    @DisplayName("a round in which every operation waits is sat out until the soonest wait is over, "
            + "rather than gone round as fast as the loop can turn, and the run goes on")
    void a_round_with_nothing_to_send_is_sat_out() {
        model = new SwaggerSpecificationParser().parse("a-single-operation.yaml");
        engine.answersWith(testCase -> Optional.of(429));
        long began = System.nanoTime();

        RunLoop.Outcome outcome = run(Duration.ofMillis(2500), listening -> { });

        assertThat(outcome.passes()).describedAs("%s", outcome).isLessThan(20);
        assertThat(outcome.sent()).describedAs("sent again once its wait was over: %s", outcome)
                .isGreaterThan(1);
        assertThat(Duration.ofNanos(System.nanoTime() - began))
                .describedAs("not ended as a run that could send nothing")
                .isGreaterThanOrEqualTo(Duration.ofMillis(2000));
        assertThat(outcome.waited().orElseThrow().operations()).singleElement()
                .satisfies(search -> assertThat(search.turnsSkipped()).isLessThan(20));
    }

    @Test
    @DisplayName("a wait of the whole API during the opening lap delays its next step, and the lap "
            + "is still judged")
    void a_whole_api_wait_delays_the_opening_lap() {
        askForQuiet("1", "listPets", "listShelters");

        RunLoop.Outcome outcome = run(Duration.ofSeconds(3), listening -> { });

        assertThat(Duration.ofNanos(engine.firstSent.get("addPet")
                - engine.firstSent.get("listPets")))
                .describedAs("the creations of the lap waited for the whole API's quiet")
                .isGreaterThanOrEqualTo(Duration.ofMillis(900));
        assertThat(outcome.refusedLap()).describedAs("a 429 is no refusal of a key").isEmpty();
    }

    @Test
    @DisplayName("an engine that breaks when asked to hold still gets every slot back, every "
            + "answer is announced, and the run goes on to its end")
    void a_broken_hold_loses_nothing() {
        engine.holdingBreaks = true;
        askForQuiet("1", "getPet", "listShelters");
        long began = System.nanoTime();

        RunLoop.Outcome outcome = run(Duration.ofMillis(2500), WITHOUT_A_FIRST_ROUND,
                listening -> { });

        assertThat(engine.holds).isNotEmpty();
        assertThat(outcome.answered()).describedAs("%s", outcome).isEqualTo(outcome.sent());
        assertThat(outcome.stillOwed()).isZero();
        assertThat(Duration.ofNanos(System.nanoTime() - began))
                .describedAs("a slot never given back would hold the end up for the whole wait "
                        + "for stragglers")
                .isLessThan(Duration.ofMillis(2500).plus(PATIENT));
    }

    @Test
    @DisplayName("switched off, nothing the API says makes anything wait")
    void switched_off_nothing_waits() {
        waitWhenToldTo = false;
        askForQuiet("3600", "getPet", "listShelters");

        RunLoop.Outcome outcome = run(BUDGET, WITHOUT_A_FIRST_ROUND, listening -> { });

        assertThat(outcome.waited()).isEmpty();
        assertThat(engine.holds).isEmpty();
        assertThat(engine.askedFor("getPet")).describedAs("%s", outcome)
                .isGreaterThan(WORK_AHEAD * 2L);
    }

    @Test
    @DisplayName("with a rate kept to, a request is built only once its turn has come, so the loop "
            + "asks for no more than the rate allows and nothing waits inside the engine")
    void a_request_is_built_when_its_turn_comes() {
        Duration between = Duration.ofMillis(200);
        engine.keepsTo(between);
        engine.idleGrows = true;

        RunLoop.Outcome outcome = run(BUDGET, WITHOUT_A_FIRST_ROUND, listening -> { });

        assertThat(outcome.sent()).describedAs("%s", outcome)
                .isBetween(2L, BUDGET.dividedBy(between) + 1);
        assertThat(engine.shortestGap())
                .describedAs("the loop waited for each turn rather than handing requests over to "
                        + "wait for it, less what a busy machine's timers take away")
                .isGreaterThan(between.dividedBy(2));
        assertThat(outcome.waited().orElseThrow().idle())
                .describedAs("quiet the rate made is not quiet the API asked for")
                .isZero();
    }

    @Test
    @DisplayName("a step of a series whose operation was told to wait is handed back unbuilt, and "
            + "the series goes on to its next step")
    void a_series_goes_on_past_a_step_left_alone() {
        engine.repliesWith(testCase -> "{\"id\": 7}");
        askForQuiet("3600", "listPets");
        Campaign onlySeries = new Campaign(List.of(new Campaign.PlannedStrategy("sequences", 100,
                List.of(new Campaign.Entry.Single(new Campaign.Source.Builtin(
                        Campaign.Builtin.RANDOM))), false, true)), WhichOperations.everything());
        // Create a pet, read it, read the list it belongs to, read it again. The list is told to
        // wait the first time it is asked for, and every series after that has to go on without it.
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model, 20260914L,
                List.of(), onlySeries, Settings.defaults().withSequences(
                        new SequenceSettings(false, false, false, false, true, false)));

        try (EventStream events = new EventStream()) {
            Scheduler scheduler = new Scheduler(generator, WITHOUT_A_FIRST_ROUND,
                    Instant.now().plus(BUDGET), InstantSource.system(), events::publish);
            RunLoop.run(scheduler, "https://api.example", WORK_AHEAD, ANNOUNCEMENTS_ALLOWED, NO_CAP,
                    PATIENT, GRACE, new StopFromOutside(),
                    RefusedOpeningLap.watching(true), obeying(scheduler), engine, events);
        }

        Map<InteractionId, List<Integer>> stepsBySeries = new HashMap<>();
        engine.stepsSentAt.keySet().forEach(step -> stepsBySeries.computeIfAbsent(
                step.sequence().orElseThrow().step() == 1
                        ? engine.exchangeOf.get(step)
                        : step.sequence().orElseThrow().follows().get(0),
                ignored -> new ArrayList<>()).add(step.sequence().orElseThrow().step()));
        assertThat(stepsBySeries.values())
                .describedAs("a series read its pet again with the list it belongs to left out: %s",
                        stepsBySeries.values())
                .anySatisfy(steps -> assertThat(steps).contains(4).doesNotContain(3));
    }

    /** The given operations answer 429 with this {@code Retry-After}; every other one, 200. */
    private void askForQuiet(String retryAfter, String... operations) {
        List<String> quiet = List.of(operations);
        engine.answersWith(testCase -> Optional.of(
                quiet.contains(testCase.operation().value()) ? 429 : 200));
        engine.answersWithHeaders(testCase -> quiet.contains(testCase.operation().value())
                ? List.of(Header.of("Retry-After", retryAfter)) : List.of());
    }

    /** A run that begins with a first round, each step of which waits this long for the last. */
    private static ScheduleSettings withPatience(Duration patience) {
        return new ScheduleSettings(WITH_A_FIRST_ROUND.workAheadFactor(), ANNOUNCEMENTS_ALLOWED,
                WITH_A_FIRST_ROUND.stragglerGrace(), WITH_A_FIRST_ROUND.interruptGrace(), true,
                patience, false, 1_000, WITH_A_FIRST_ROUND.interruptWritingGrace(),
                WITH_A_FIRST_ROUND.interruptLookingGrace(), true, true,
                Duration.ofSeconds(1), Duration.ofSeconds(60));
    }

    private RunLoop.Outcome run(Duration budget, java.util.function.Consumer<EventStream> setUp) {
        return run(budget, WORK_AHEAD, ANNOUNCEMENTS_ALLOWED, WITH_A_FIRST_ROUND, setUp);
    }

    /** The same, scheduled as told. */
    private RunLoop.Outcome run(Duration budget, ScheduleSettings schedule,
            java.util.function.Consumer<EventStream> setUp) {
        return run(budget, WORK_AHEAD, ANNOUNCEMENTS_ALLOWED, schedule, setUp);
    }

    /** The same, told how far ahead to work and how many announcements may wait for the reports. */
    private RunLoop.Outcome run(Duration budget, int workAhead, int announcementsAllowed,
            java.util.function.Consumer<EventStream> setUp) {
        return run(budget, workAhead, announcementsAllowed, WITH_A_FIRST_ROUND, setUp);
    }

    private RunLoop.Outcome run(Duration budget, int workAhead, int announcementsAllowed,
            ScheduleSettings schedule, java.util.function.Consumer<EventStream> setUp) {
        return run(budget, workAhead, announcementsAllowed, schedule, new StopFromOutside(), setUp);
    }

    /** The same, hearing a stop from outside wherever this test sets one off. */
    private RunLoop.Outcome run(Duration budget, int workAhead, int announcementsAllowed,
            ScheduleSettings schedule, StopFromOutside stop,
            java.util.function.Consumer<EventStream> setUp) {
        return run(budget, workAhead, announcementsAllowed, NO_CAP, schedule, stop, setUp);
    }

    /** The same, stopping once this many requests have been sent. */
    private RunLoop.Outcome run(Duration budget, int workAhead, int announcementsAllowed,
            long mostRequests, ScheduleSettings schedule, StopFromOutside stop,
            java.util.function.Consumer<EventStream> setUp) {
        try (EventStream events = new EventStream()) {
            setUp.accept(events);
            Scheduler scheduler = new Scheduler(generator(), schedule, Instant.now().plus(budget),
                    InstantSource.system(), events::publish);
            return RunLoop.run(scheduler, "https://api.example", workAhead, announcementsAllowed,
                    mostRequests, PATIENT, GRACE, stop,
                    RefusedOpeningLap.watching(watchTheLap), obeying(scheduler), engine, events);
        }
    }

    /** A stop from outside, set off this long from now on a thread of its own. */
    private static StopFromOutside stoppedAfter(Duration after) {
        StopFromOutside stop = new StopFromOutside();
        Thread.ofPlatform().daemon().start(() -> {
            try {
                Thread.sleep(after);
            } catch (InterruptedException notToday) {
                return;
            }
            stop.ask();
        });
        return stop;
    }

    /** A run against an address of this test's choosing, with thirty seconds to spare. */
    private RunLoop.Outcome runAgainst(String baseUrl, ScheduleSettings schedule) {
        try (EventStream events = new EventStream()) {
            Scheduler scheduler = new Scheduler(generator(), schedule,
                    Instant.now().plusSeconds(30), InstantSource.system(), events::publish);
            return RunLoop.run(scheduler, baseUrl, WORK_AHEAD, ANNOUNCEMENTS_ALLOWED, NO_CAP,
                    PATIENT, GRACE, new StopFromOutside(),
                    RefusedOpeningLap.watching(watchTheLap), obeying(scheduler), engine, events);
        }
    }

    private RandomTestCaseGenerator generator() {
        return new RandomTestCaseGenerator(model, 20260914L);
    }

    /** What keeps track of the waits a run here is asked for, with the guesses a run makes. */
    private ToldToWait obeying(Scheduler scheduler) {
        return ToldToWait.watching(Settings.from(Map.of("schedule.waitWhenToldTo",
                String.valueOf(waitWhenToldTo))).schedule(), scheduler.deadline());
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

        /** When each operation was first asked for, and first answered, by the monotonic clock. */
        private final Map<String, Long> firstSent = new ConcurrentHashMap<>();
        private final Map<String, Long> firstAnswered = new ConcurrentHashMap<>();

        /**
         * When each step of a series was asked for and answered, by that clock, and the exchange
         * each became.
         */
        private final Map<TestCase, Long> stepsSentAt = new ConcurrentHashMap<>();
        private final Map<TestCase, Long> answeredAt = new ConcurrentHashMap<>();
        private final Map<TestCase, InteractionId> exchangeOf = new ConcurrentHashMap<>();

        /** Every request asked for, by operation and by the monotonic clock, in order. */
        private final java.util.Queue<Map.Entry<String, Long>> everySend =
                new java.util.concurrent.ConcurrentLinkedQueue<>();

        /** Every hold the loop asked for. */
        private final List<Duration> holds = new java.util.concurrent.CopyOnWriteArrayList<>();

        /** How many times the loop had what is held dropped. */
        private final AtomicInteger dropped = new AtomicInteger();

        /** Whether being asked to hold breaks the engine, as a part of the tool that went wrong. */
        private volatile boolean holdingBreaks = false;

        /** The headers each reply carries besides its content type, by what was asked. */
        private volatile Function<TestCase, List<Header>> headers = testCase -> List.of();

        private volatile Function<HttpRequestRecord, Duration> howLong =
                request -> Duration.ofMillis(1);
        private volatile Function<TestCase, String> replyBody = testCase -> "[]";
        private volatile boolean answers = true;

        /** What each request is answered with, by what was asked: a status code, or no reply. */
        private volatile Function<TestCase, Optional<Integer>> status =
                testCase -> Optional.of(200);

        /** Which requests the engine loses, failing on the thread they are sent on. */
        private volatile java.util.function.Predicate<TestCase> loses = testCase -> false;

        /** Whether a lost request comes back as nothing at all rather than as a failure. */
        private volatile boolean losesWithoutSaying = false;

        /** From which request on the engine refuses to take any more, as a closed one does. */
        private volatile int refusesFrom = Integer.MAX_VALUE;

        /** The least time between two requests, as an engine kept to a rate has; none when zero. */
        private volatile Duration between = Duration.ZERO;

        /**
         * Whether the engine reports every moment since it was built as idle, so that a wait the
         * loop wrongly counted as quiet the API asked for would show; otherwise it reports none.
         */
        private volatile boolean idleGrows = false;
        private final long builtAt = System.nanoTime();

        /** Keeps to a rate: says how long until the next request's turn, as a real engine does. */
        void keepsTo(Duration gap) {
            this.between = gap;
        }

        /** The shortest stretch between two requests asked for one after the other. */
        Duration shortestGap() {
            return Duration.ofNanos(gaps().min().orElse(Long.MAX_VALUE));
        }

        void takes(Function<HttpRequestRecord, Duration> perRequest) {
            this.howLong = perRequest;
        }

        /** What the body of each reply says, by what was asked. Every reply is JSON. */
        void repliesWith(Function<TestCase, String> perTestCase) {
            this.replyBody = perTestCase;
        }

        /** The status code each request is answered with, by what was asked; none for no reply. */
        void answersWith(Function<TestCase, Optional<Integer>> perTestCase) {
            this.status = perTestCase;
        }

        /** How many requests have been asked for so far. */
        int asked() {
            return sent.get();
        }

        /** The headers each reply carries besides its content type, by what was asked. */
        void answersWithHeaders(Function<TestCase, List<Header>> perTestCase) {
            this.headers = perTestCase;
        }

        /** How many requests were asked for this operation. */
        long askedFor(String operation) {
            return everySend.stream().filter(send -> send.getKey().equals(operation)).count();
        }

        /** The longest stretch with no request asked for at all, between the first and the last. */
        Duration longestQuiet() {
            return Duration.ofNanos(gaps().max().orElse(0));
        }

        /** Every stretch between two requests asked for one after the other, in nanoseconds. */
        private java.util.stream.LongStream gaps() {
            List<Long> at = everySend.stream().map(Map.Entry::getValue).sorted().toList();
            return java.util.stream.IntStream.range(1, at.size())
                    .mapToLong(i -> at.get(i) - at.get(i - 1));
        }

        @Override
        public CompletableFuture<Interaction> sendAsync(TestCase testCase,
                HttpRequestRecord request) {
            if (sent.get() + 1 >= refusesFrom) {
                throw new IllegalStateException("this engine is closed and cannot send anything else");
            }
            mostAtOnce.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            sent.incrementAndGet();
            String operation = testCase.operation().value();
            Instant sentAt = Instant.now();
            everySend.add(Map.entry(operation, System.nanoTime()));
            firstSent.putIfAbsent(operation, System.nanoTime());
            if (testCase.sequence().isPresent()) {
                stepsSentAt.put(testCase, System.nanoTime());
            }
            CompletableFuture<Interaction> answer = new CompletableFuture<>();
            later.schedule(() -> {
                inFlight.decrementAndGet();
                if (loses.test(testCase)) {
                    // As a real engine's request fails when the part of the tool sending it breaks:
                    // wrapped the way the asynchronous machinery wraps what a request's thread threw.
                    if (losesWithoutSaying) {
                        answer.complete(null);
                    } else {
                        answer.completeExceptionally(new java.util.concurrent.CompletionException(
                                new StackOverflowError("lost on the way to " + operation)));
                    }
                    return;
                }
                firstAnswered.putIfAbsent(operation, System.nanoTime());
                Interaction replied = reply(testCase, request, sentAt);
                if (testCase.sequence().isPresent()) {
                    answeredAt.put(testCase, System.nanoTime());
                    exchangeOf.put(testCase, replied.id());
                }
                answer.complete(replied);
            }, howLong.apply(request).toMillis(), TimeUnit.MILLISECONDS);
            return answer;
        }

        private Interaction reply(TestCase testCase, HttpRequestRecord request, Instant sentAt) {
            Optional<Integer> code = status.apply(testCase);
            if (!answers || code.isEmpty()) {
                return Interaction.transportFailure(testCase, request,
                        "nothing was listening", sentAt, Duration.ZERO);
            }
            List<Header> carried = new ArrayList<>();
            carried.add(Header.of("Content-Type", "application/json"));
            carried.addAll(headers.apply(testCase));
            return Interaction.answered(testCase, request,
                    new HttpResponseRecord(StatusLine.of(code.get()), carried,
                            Optional.of(Payload.of(replyBody.apply(testCase)
                                    .getBytes(StandardCharsets.UTF_8), "application/json"))),
                    sentAt, Duration.ofMillis(1));
        }

        @Override
        public void holdFor(Duration howLong) {
            holds.add(howLong);
            if (holdingBreaks) {
                throw new IllegalStateException("the engine broke while asked to hold");
            }
        }

        @Override
        public void dropWhatIsHeld() {
            dropped.incrementAndGet();
        }

        @Override
        public Duration untilItsTurn() {
            if (between.isZero() || everySend.isEmpty()) {
                return Duration.ZERO;
            }
            long lastSentAt = everySend.stream().mapToLong(Map.Entry::getValue).max().orElseThrow();
            return Duration.ofNanos(Math.max(0, lastSentAt + between.toNanos() - System.nanoTime()));
        }

        @Override
        public EngineStatistics statistics() {
            if (idleGrows) {
                Duration since = Duration.ofNanos(System.nanoTime() - builtAt);
                return new EngineStatistics(sent.get(), since, since,
                        Duration.ofMillis(sent.get()), mostAtOnce.get(), WORK_AHEAD);
            }
            return new EngineStatistics(sent.get(), Duration.ofSeconds(1), Duration.ZERO,
                    Duration.ofMillis(sent.get()), mostAtOnce.get(), WORK_AHEAD);
        }

        @Override
        public void close() {
            later.shutdownNow();
        }
    }
}

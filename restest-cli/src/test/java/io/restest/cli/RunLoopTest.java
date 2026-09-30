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
            WITH_A_FIRST_ROUND.openingLapPatience());

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
                    ANNOUNCEMENTS_ALLOWED, PATIENT, GRACE, new StopFromOutside(), engine, events);
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
        RandomTestCaseGenerator generator = generator();
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
            RunLoop.run(scheduler, "https://api.example", WORK_AHEAD, ANNOUNCEMENTS_ALLOWED,
                    PATIENT, GRACE, new StopFromOutside(), engine, events);
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
            RunLoop.run(scheduler, "https://api.example", WORK_AHEAD, ANNOUNCEMENTS_ALLOWED,
                    PATIENT, GRACE, new StopFromOutside(), engine, events);
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
        ScheduleSettings patientForAFifth = new ScheduleSettings(
                WITH_A_FIRST_ROUND.workAheadFactor(), ANNOUNCEMENTS_ALLOWED,
                WITH_A_FIRST_ROUND.stragglerGrace(), WITH_A_FIRST_ROUND.interruptGrace(), true,
                Duration.ofMillis(200));

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
    @DisplayName("a run stopped from outside sends nothing more, and says it was cut short")
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
        ScheduleSettings patient = new ScheduleSettings(WITH_A_FIRST_ROUND.workAheadFactor(),
                ANNOUNCEMENTS_ALLOWED, WITH_A_FIRST_ROUND.stragglerGrace(),
                WITH_A_FIRST_ROUND.interruptGrace(), true, Duration.ofSeconds(10));
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
                    ANNOUNCEMENTS_ALLOWED, PATIENT, GRACE, stop, engine, events);
            Thread.sleep(1500);
            events.awaitDelivery(PATIENT);
        }

        assertThat(outcome.stillOwed()).describedAs("%s", outcome).isPositive();
        assertThat(heard.completed.get())
                .describedAs("what the reports and the stored run hear is what the run counted as "
                        + "answered, and the rest counts as never answered: %s", outcome)
                .isEqualTo(outcome.answered());
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
        try (EventStream events = new EventStream()) {
            setUp.accept(events);
            Scheduler scheduler = new Scheduler(generator(), schedule, Instant.now().plus(budget),
                    InstantSource.system(), events::publish);
            return RunLoop.run(scheduler, "https://api.example", workAhead, announcementsAllowed,
                    PATIENT, GRACE, stop, engine, events);
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
            return RunLoop.run(scheduler, baseUrl, WORK_AHEAD, ANNOUNCEMENTS_ALLOWED, PATIENT,
                    GRACE, new StopFromOutside(), engine, events);
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

        private volatile Function<HttpRequestRecord, Duration> howLong =
                request -> Duration.ofMillis(1);
        private volatile Function<TestCase, String> replyBody = testCase -> "[]";
        private volatile boolean answers = true;

        /** Which requests the engine loses, failing on the thread they are sent on. */
        private volatile java.util.function.Predicate<TestCase> loses = testCase -> false;

        /** Whether a lost request comes back as nothing at all rather than as a failure. */
        private volatile boolean losesWithoutSaying = false;

        void takes(Function<HttpRequestRecord, Duration> perRequest) {
            this.howLong = perRequest;
        }

        /** What the body of each reply says, by what was asked. Every reply is JSON. */
        void repliesWith(Function<TestCase, String> perTestCase) {
            this.replyBody = perTestCase;
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
            String operation = testCase.operation().value();
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
                Interaction replied = reply(testCase, request);
                if (testCase.sequence().isPresent()) {
                    answeredAt.put(testCase, System.nanoTime());
                    exchangeOf.put(testCase, replied.id());
                }
                answer.complete(replied);
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
                            Optional.of(Payload.of(replyBody.apply(testCase)
                                    .getBytes(StandardCharsets.UTF_8), "application/json"))),
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

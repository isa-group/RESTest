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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.restest.core.event.RunEvent;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.Payload;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.schema.StringSchema;
import io.restest.core.settings.ScheduleSettings;
import io.restest.core.settings.SequenceSettings;
import io.restest.core.settings.Settings;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a run sends next, one step at a time, against a clock this test moves by hand.
 */
class SchedulerTest {

    private static final Instant START = Instant.parse("2026-09-23T10:00:00Z");

    private static final Operation LIST = Operation.of(HttpMethod.GET, "/pets")
            .withId(OperationId.of("listPets"));
    private static final Operation ADD = Operation.of(HttpMethod.POST, "/pets")
            .withId(OperationId.of("addPet"));
    private static final Operation GET = Operation.of(HttpMethod.GET, "/pets/{petId}", List.of(
                    Parameter.of("petId", ParameterLocation.PATH, true, StringSchema.of())))
            .withId(OperationId.of("getPet"));
    private static final Operation SEARCH = Operation.of(HttpMethod.GET, "/search", List.of(
                    Parameter.of("q", ParameterLocation.QUERY, true, StringSchema.of()),
                    Parameter.of("page", ParameterLocation.QUERY, false, StringSchema.of())))
            .withId(OperationId.of("search"));

    /** Declared in the order the document would, which is not the order the first round goes in. */
    private static final ApiModel PETS = ApiModel.of("Pets", "1.0", List.of(GET, ADD, LIST));

    private Instant now = START;
    private final InstantSource clock = () -> now;
    private final List<String> announced = new ArrayList<>();

    @Test
    @DisplayName("a run begins with a first round in steps, waiting after each, then goes round in "
            + "the order the document declares")
    void the_first_round_comes_first_in_steps() {
        Scheduler scheduler = scheduler(PETS, ScheduleSettings.defaults(), Duration.ofMinutes(1));

        assertThat(steps(scheduler, 11)).containsExactly(
                "send listPets (first round)", "wait 2s",
                "send addPet (first round)", "wait 2s",
                "send getPet (first round)", "wait 2s",
                "end of the first round",
                "send getPet", "send addPet", "send listPets",
                "end of a round");
        assertThat(announced).containsExactly("began opening lap", "finished opening lap");
    }

    @Test
    @DisplayName("the first round is announced with its first request, not before")
    void the_first_round_is_announced_with_its_first_request() {
        Scheduler scheduler = scheduler(PETS, ScheduleSettings.defaults(), Duration.ofMinutes(1));

        assertThat(announced).isEmpty();
        scheduler.next();
        assertThat(announced).containsExactly("began opening lap");
    }

    @Test
    @DisplayName("a wait never reaches past the deadline")
    void a_wait_ends_at_the_deadline_at_the_latest() {
        Scheduler scheduler = scheduler(PETS, ScheduleSettings.defaults(), Duration.ofMillis(500));

        assertThat(steps(scheduler, 2)).containsExactly("send listPets (first round)",
                "wait 0.5s");
    }

    @Test
    @DisplayName("told not to wait at all, the first round goes straight from one step to the next")
    void no_patience_means_no_waiting() {
        ScheduleSettings impatient = new ScheduleSettings(2, 1_000, Duration.ofSeconds(10),
                Duration.ofSeconds(2), true, Duration.ZERO);
        Scheduler scheduler = scheduler(PETS, impatient, Duration.ofMinutes(1));

        assertThat(steps(scheduler, 5)).containsExactly("send listPets (first round)",
                "send addPet (first round)", "send getPet (first round)",
                "end of the first round", "send getPet");
    }

    @Test
    @DisplayName("switched off, there is no first round, and nothing is announced")
    void switched_off_there_is_no_first_round() {
        Scheduler scheduler = scheduler(PETS, withoutAFirstRound(), Duration.ofMinutes(1));

        assertThat(steps(scheduler, 5)).containsExactly("send getPet", "send addPet",
                "send listPets", "end of a round", "send getPet");
        assertThat(announced).isEmpty();
    }

    @Test
    @DisplayName("a plan that only pushes at the API has no first round, since it has no request "
            + "anybody believes in")
    void a_plan_that_only_pushes_has_no_first_round() {
        RandomTestCaseGenerator onlyPushes = new RandomTestCaseGenerator(PETS, 1L,
                Dictionaries.fuzzing().map(List::of).orElse(List.of()), 100);
        Scheduler scheduler = new Scheduler(onlyPushes, ScheduleSettings.defaults(),
                START.plusSeconds(60), clock, this::heard);

        assertThat(steps(scheduler, 1)).containsExactly("send getPet");
        assertThat(announced).isEmpty();
    }

    @Test
    @DisplayName("the time running out during the first round cuts it short, and says so once")
    void the_time_running_out_cuts_the_first_round_short() {
        Scheduler scheduler = scheduler(PETS, ScheduleSettings.defaults(), Duration.ofSeconds(1));

        assertThat(steps(scheduler, 1)).containsExactly("send listPets (first round)");
        now = START.plusSeconds(1);

        assertThat(steps(scheduler, 3)).containsExactly("time is up", "time is up", "time is up");
        assertThat(announced).containsExactly("began opening lap",
                "finished opening lap (cut short)");
    }

    @Test
    @DisplayName("stopping during the first round announces it cut short, once however often it is "
            + "stopped, and nothing is sent after")
    void stopping_announces_the_first_round_cut_short_once() {
        Scheduler scheduler = scheduler(PETS, ScheduleSettings.defaults(), Duration.ofMinutes(1));
        scheduler.next();

        scheduler.stop();
        scheduler.stop();

        assertThat(announced).containsExactly("began opening lap",
                "finished opening lap (cut short)");
        assertThat(steps(scheduler, 1)).containsExactly("time is up");
    }

    @Test
    @DisplayName("a run whose time is up before it could send anything has no first round to "
            + "report")
    void no_time_at_all_means_no_first_round() {
        Scheduler scheduler = scheduler(PETS, ScheduleSettings.defaults(), Duration.ZERO);

        assertThat(steps(scheduler, 1)).containsExactly("time is up");
        scheduler.stop();
        assertThat(announced).isEmpty();
    }

    @Test
    @DisplayName("a round that ended just as the time ran out is still counted as a round")
    void a_round_ending_at_the_deadline_is_counted() {
        Scheduler scheduler = scheduler(PETS, withoutAFirstRound(), Duration.ofSeconds(1));
        assertThat(steps(scheduler, 3)).containsExactly("send getPet", "send addPet",
                "send listPets");

        now = START.plusSeconds(5);

        assertThat(steps(scheduler, 2)).containsExactly("end of a round", "time is up");
    }

    @Test
    @DisplayName("the first round asks for each operation's likeliest request, the rest for an "
            + "ordinary one")
    void the_first_round_asks_for_the_likeliest_request() {
        ApiModel search = ApiModel.of("Search", "1.0", List.of(SEARCH));
        Scheduler scheduler = scheduler(search, ScheduleSettings.defaults(), Duration.ofMinutes(1));

        Scheduler.Step first = scheduler.next();
        assertThat(first).isEqualTo(new Scheduler.Step.Send(SEARCH, true));
        for (int draw = 0; draw < 20; draw++) {
            assertThat(scheduler.testCaseFor((Scheduler.Step.Send) first).orElseThrow()
                    .parameterValues()).extracting(ParameterValue::name).containsExactly("q");
        }
    }

    @Test
    @DisplayName("the next step of a series, once its answer is heard, goes before the ordinary turn")
    void a_series_goes_before_the_ordinary_turn() {
        Scheduler scheduler = aSeriesScheduler(Duration.ofMinutes(1));

        assertThat(steps(scheduler, 1)).containsExactly("send getPet");
        Scheduler.Step.Send creation = (Scheduler.Step.Send) scheduler.next();
        assertThat(creation.operation()).isEqualTo(ADD);
        TestCase first = scheduler.testCaseFor(creation).orElseThrow();
        assertThat(first.sequence()).isPresent();

        scheduler.heard(first, Optional.of(created(first)));

        Scheduler.Step.Send again = (Scheduler.Step.Send) scheduler.next();
        assertThat(again.continuation())
                .describedAs("the same creation again, before listPets, whose turn it was")
                .isPresent();
        assertThat(again.operation()).isEqualTo(ADD);
        TestCase second = scheduler.testCaseFor(again).orElseThrow();
        assertThat(second.sequence().orElseThrow().step()).isEqualTo(2);
        assertThat(second.body()).isEqualTo(first.body());
        assertThat(steps(scheduler, 1)).containsExactly("send listPets");
    }

    @Test
    @DisplayName("a request that is not a step of a series is heard and ignored")
    void an_ordinary_answer_changes_nothing() {
        Scheduler scheduler = aSeriesScheduler(Duration.ofMinutes(1));
        Scheduler.Step.Send read = (Scheduler.Step.Send) scheduler.next();
        TestCase ordinary = scheduler.testCaseFor(read).orElseThrow();

        scheduler.heard(ordinary, Optional.of(created(ordinary)));

        assertThat(steps(scheduler, 1)).containsExactly("send addPet");
    }

    @Test
    @DisplayName("an answer heard after the time is up starts nothing")
    void nothing_after_the_time_is_up() {
        Scheduler scheduler = aSeriesScheduler(Duration.ofSeconds(5));
        scheduler.next();
        Scheduler.Step.Send creation = (Scheduler.Step.Send) scheduler.next();
        TestCase first = scheduler.testCaseFor(creation).orElseThrow();

        now = START.plusSeconds(5);
        scheduler.heard(first, Optional.of(created(first)));

        assertThat(steps(scheduler, 1)).containsExactly("time is up");
    }

    @Test
    @DisplayName("a creation nothing came back to ends its series, and the ordinary turn goes on")
    void a_creation_without_an_answer_ends_its_series() {
        Scheduler scheduler = aSeriesScheduler(Duration.ofMinutes(1));
        scheduler.next();
        Scheduler.Step.Send creation = (Scheduler.Step.Send) scheduler.next();
        TestCase first = scheduler.testCaseFor(creation).orElseThrow();

        scheduler.heard(first, Optional.empty());

        assertThat(steps(scheduler, 1)).containsExactly("send listPets");
    }

    @Test
    @DisplayName("a step that cannot be built lets the ordinary turn go on, and its series ends "
            + "when the question needed it")
    void a_step_that_cannot_be_built() {
        // safeGet: its read of the pet needs the pet's identifier, and the reply carries none.
        Scheduler scheduler = aSeriesScheduler(Duration.ofMinutes(1),
                new SequenceSettings(false, false, false, false, true, false));
        scheduler.next();
        Scheduler.Step.Send creation = (Scheduler.Step.Send) scheduler.next();
        TestCase first = scheduler.testCaseFor(creation).orElseThrow();
        scheduler.heard(first, Optional.of(answered(first, 201, "{\"name\":\"Rex\"}")));

        Scheduler.Step.Send read = (Scheduler.Step.Send) scheduler.next();
        assertThat(read.continuation()).isPresent();
        assertThat(read.operation()).isEqualTo(GET);
        assertThat(scheduler.testCaseFor(read)).isEmpty();

        assertThat(steps(scheduler, 2)).containsExactly("send listPets", "end of a round");
    }

    @Test
    @DisplayName("a run with nothing it can attempt is refused rather than going round nothing")
    void nothing_to_attempt_is_not_a_run() {
        ApiModel empty = ApiModel.of("Nothing", "1.0", List.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> scheduler(empty, ScheduleSettings.defaults(),
                        Duration.ofMinutes(1)))
                .withMessageContaining("nothing to do");
    }

    /** A scheduler with no first round whose every creation starts a series: createTwice. */
    private Scheduler aSeriesScheduler(Duration budget) {
        return aSeriesScheduler(budget, new SequenceSettings(false, false, false, false, false,
                true));
    }

    private Scheduler aSeriesScheduler(Duration budget, SequenceSettings series) {
        Campaign onlySeries = new Campaign(List.of(new Campaign.PlannedStrategy("sequences", 100,
                List.of(new Campaign.Entry.Single(new Campaign.Source.Builtin(
                        Campaign.Builtin.RANDOM))), false, true)), WhichOperations.everything());
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(PETS, 20260923L,
                List.of(), onlySeries, Settings.defaults().withSequences(series));
        return new Scheduler(generator, withoutAFirstRound(), START.plus(budget), clock,
                this::heard);
    }

    private static Interaction created(TestCase sent) {
        return answered(sent, 201, "{\"id\":\"7\"}");
    }

    private static Interaction answered(TestCase sent, int status, String body) {
        return Interaction.answered(sent, HttpRequestRecord.of(HttpMethod.POST,
                        "https://api.example/pets"),
                new HttpResponseRecord(StatusLine.of(status),
                        List.of(Header.of("Content-Type", "application/json")),
                        Optional.of(Payload.text(body, "application/json"))),
                Instant.EPOCH, Duration.ofMillis(1));
    }

    private Scheduler scheduler(ApiModel model, ScheduleSettings settings, Duration budget) {
        return new Scheduler(new RandomTestCaseGenerator(model, 20260923L), settings,
                START.plus(budget), clock, this::heard);
    }

    private void heard(RunEvent event) {
        switch (event) {
            case RunEvent.PhaseStarted started -> announced.add("began " + started.phase());
            case RunEvent.PhaseFinished finished -> announced.add("finished " + finished.phase()
                    + (finished.cutShort() ? " (cut short)" : ""));
            default -> announced.add("something else: " + event);
        }
    }

    /** The next few steps, in words. */
    private static List<String> steps(Scheduler scheduler, int howMany) {
        List<String> said = new ArrayList<>();
        for (int step = 0; step < howMany; step++) {
            said.add(switch (scheduler.next()) {
                case Scheduler.Step.Send send -> "send " + send.operation().id().value()
                        + (send.inTheOpeningLap() ? " (first round)" : "");
                case Scheduler.Step.WaitForTheAnswers wait ->
                        "wait " + wait.atMost().toMillis() / 1000.0 + "s";
                case Scheduler.Step.EndOfAPass end ->
                        end.ofTheOpeningLap() ? "end of the first round" : "end of a round";
                case Scheduler.Step.TimeIsUp ignored -> "time is up";
            });
        }
        return said.stream().map(line -> line.replace(".0s", "s")).toList();
    }

    private static ScheduleSettings withoutAFirstRound() {
        return new ScheduleSettings(2, 1_000, Duration.ofSeconds(10),
                Duration.ofSeconds(2), false, Duration.ofSeconds(2));
    }
}

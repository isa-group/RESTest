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

import io.restest.core.execution.SequenceStep;
import io.restest.core.execution.TestCase;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.StringSchema;
import io.restest.core.settings.SequenceSettings;
import io.restest.core.settings.Settings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A way of building requests that turns a creation into the first request of a series, as a run
 * meets it: on a creation, on anything else, and with its switches off.
 */
class SequencingStrategyTest {

    private static final long SEED = 20261004L;

    /**
     * A thing whose name has a spelling rule and no sample, so that the only way to fill it is to
     * invent a word that fits the rule - which is where two strategies building from the same
     * sources would part company, were each to keep its own reading of the rule.
     */
    private static final Operation ADD_THING = Operation.of(HttpMethod.POST, "/things")
            .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of("name",
                    new StringSchema(SchemaMetadata.none(), Optional.empty(), Optional.empty(),
                            Optional.of("^[a-z]{3,8}$"), Optional.empty())), Set.of("name")), true))
            .withId(OperationId.of("addThing"));

    private static final Operation LIST_THINGS = Operation.of(HttpMethod.GET, "/things")
            .withId(OperationId.of("listThings"));

    private static final Operation GET_THING = Operation.of(HttpMethod.GET, "/things/{thingId}",
                    List.of(Parameter.of("thingId", ParameterLocation.PATH, true,
                            NumberSchema.of(NumberKind.INTEGER))))
            .withId(OperationId.of("getThing"));

    private static final Operation DELETE_THING = Operation.of(HttpMethod.DELETE,
                    "/things/{thingId}", List.of(Parameter.of("thingId", ParameterLocation.PATH,
                            true, NumberSchema.of(NumberKind.INTEGER))))
            .withId(OperationId.of("deleteThing"));

    private static final ApiModel API = ApiModel.of("Things", "1.0",
            List.of(ADD_THING, LIST_THINGS, GET_THING, DELETE_THING));

    @Test
    @DisplayName("with every series switched off, the shipped plan sends exactly what it sent "
            + "before it had a strategy that sends them")
    void switched_off_it_is_the_plan_it_was() throws IOException {
        Campaign shipped = Campaigns.shipped();
        Settings off = Settings.defaults().withSequences(SequenceSettings.noneSent());
        List<Dictionary> fuzzing = Dictionaries.fuzzing().map(List::of).orElse(List.of());

        assertThat(drawn(new RandomTestCaseGenerator(API, SEED, fuzzing, shipped, off)))
                .isEqualTo(drawn(new RandomTestCaseGenerator(API, SEED, fuzzing, before(shipped),
                        Settings.defaults())));
    }

    @Test
    @DisplayName("with them on, a creation with nothing optional in it starts its series from the "
            + "request nominal would have sent, so nothing else a run sends moves")
    void switched_on_nothing_else_moves() throws IOException {
        Campaign shipped = Campaigns.shipped();
        List<Dictionary> fuzzing = Dictionaries.fuzzing().map(List::of).orElse(List.of());
        RandomTestCaseGenerator withSeries = new RandomTestCaseGenerator(API, SEED, fuzzing,
                shipped, Settings.defaults());

        List<TestCase> sent = sent(withSeries);

        assertThat(drawn(sent)).isEqualTo(drawn(new RandomTestCaseGenerator(API, SEED, fuzzing,
                before(shipped), Settings.defaults())));
        assertThat(sent).filteredOn(request -> request.sequence().isPresent())
                .describedAs("a tenth of the turns, and only creations among them, start a series")
                .isNotEmpty()
                .allSatisfy(request -> {
                    assertThat(request.operation()).isEqualTo(ADD_THING.id());
                    assertThat(request.sequence().get().step()).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("a creation drawn for it is the first step of a series; anything else is not")
    void a_creation_starts_a_series() {
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(API, SEED, List.of(),
                onlySeries(), Settings.defaults());

        SequenceStep first = generator.generate(ADD_THING).orElseThrow().sequence().orElseThrow();
        assertThat(first.step()).isEqualTo(1);
        assertThat(first.shape())
                .describedAs("the series that can be asked of a thing that can be read and "
                        + "deleted, with nothing under it and no replacement")
                .isIn("readAfterDelete", "deleteTwice", "safeGet", "createTwice");
        for (Operation other : List.of(LIST_THINGS, GET_THING, DELETE_THING)) {
            assertThat(generator.generate(other).orElseThrow().sequence())
                    .describedAs("%s creates nothing, so it is built the ordinary way", other.id())
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("which series a creation starts is drawn among those that can be asked of it")
    void every_series_that_can_be_asked_is_drawn() {
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(API, SEED, List.of(),
                onlySeries(), Settings.defaults());
        List<String> shapes = new ArrayList<>();

        for (int draw = 0; draw < 200; draw++) {
            generator.generate(ADD_THING).flatMap(TestCase::sequence)
                    .ifPresent(step -> shapes.add(step.shape()));
        }

        assertThat(shapes).containsOnly("readAfterDelete", "deleteTwice", "safeGet",
                "createTwice").contains("readAfterDelete", "deleteTwice", "safeGet",
                "createTwice");
    }

    @Test
    @DisplayName("a plan that lists its series first still opens with the likeliest request, "
            + "built the ordinary way")
    void a_series_strategy_first_still_has_a_likeliest_request() {
        Campaign seriesFirst = new Campaign(List.of(
                new Campaign.PlannedStrategy("sequences", 50, List.of(new Campaign.Entry.Single(
                        new Campaign.Source.Builtin(Campaign.Builtin.RANDOM))), false, true),
                new Campaign.PlannedStrategy("nominal", 50, List.of(new Campaign.Entry.Single(
                        new Campaign.Source.Builtin(Campaign.Builtin.RANDOM))))),
                WhichOperations.everything());
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(API, SEED, List.of(),
                seriesFirst, Settings.defaults());

        assertThat(generator.canBuildTheLikeliestRequest()).isTrue();
        assertThat(generator.likeliestRequest(ADD_THING).orElseThrow().sequence()).isEmpty();
        assertThat(generator.dependsOnTheApisAnswers())
                .describedAs("a series builds its later steps from its earlier answers, so the "
                        + "seed alone does not repeat a run that sends one")
                .isTrue();
    }

    @Test
    @DisplayName("a plan that sends no series has none to begin, and says the seed repeats it")
    void no_series_without_the_strategy() {
        Campaign plain = new Campaign(List.of(new Campaign.PlannedStrategy("nominal", 100,
                List.of(new Campaign.Entry.Single(new Campaign.Source.Builtin(
                        Campaign.Builtin.RANDOM))))), WhichOperations.everything());
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(API, SEED, List.of(),
                plain, Settings.defaults());

        assertThat(generator.sequences()).isEmpty();
        assertThat(generator.dependsOnTheApisAnswers()).isFalse();
    }

    /**
     * The shipped plan as it was before it had a strategy that sends series: its share back with
     * nominal, and everything else as it is.
     */
    private static Campaign before(Campaign shipped) {
        List<Campaign.PlannedStrategy> ways = new ArrayList<>();
        int returned = shipped.strategies().stream()
                .filter(Campaign.PlannedStrategy::sendsSequences)
                .mapToInt(Campaign.PlannedStrategy::share).sum();
        for (Campaign.PlannedStrategy way : shipped.strategies()) {
            if (way.sendsSequences()) {
                continue;
            }
            ways.add(way.name().equals("nominal")
                    ? new Campaign.PlannedStrategy(way.name(), way.share() + returned,
                            way.sources(), way.mutatesAccepted())
                    : way);
        }
        return new Campaign(ways, shipped.operations());
    }

    /** A plan in which every request is built by the strategy that sends series. */
    private static Campaign onlySeries() {
        return new Campaign(List.of(new Campaign.PlannedStrategy("sequences", 100, List.of(
                new Campaign.Entry.Single(new Campaign.Source.Builtin(Campaign.Builtin.RANDOM))),
                false, true)), WhichOperations.everything());
    }

    /** Three hundred requests going round the operations, as a run's ordinary rounds would. */
    private static List<TestCase> sent(RandomTestCaseGenerator generator) {
        List<TestCase> sent = new ArrayList<>();
        List<Operation> operations = generator.testableOperations();
        for (int turn = 0; turn < 300; turn++) {
            generator.generate(operations.get(turn % operations.size())).ifPresent(sent::add);
        }
        return sent;
    }

    private static List<Object> drawn(RandomTestCaseGenerator generator) {
        return drawn(sent(generator));
    }

    /** What the requests carried, leaving out the labels they were filed under. */
    private static List<Object> drawn(List<TestCase> sent) {
        List<Object> carried = new ArrayList<>();
        for (TestCase request : sent) {
            carried.add(request.operation());
            carried.add(request.parameterValues());
            carried.add(request.body());
        }
        return carried;
    }
}

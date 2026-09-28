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

import io.restest.core.event.RunEvent;
import io.restest.core.event.RunListener;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Intent;
import io.restest.core.execution.Interaction;
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
import io.restest.core.schema.StringSchema;
import io.restest.core.settings.SequenceSettings;
import io.restest.core.settings.Settings;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A way of building requests that changes one thing in a request the API accepted, as a run meets
 * it: what it does before anything is accepted, once something is, and with its switches off.
 */
class MutatingStrategyTest {

    private static final long SEED = 20260927L;

    private static final Operation LIST_PETS = Operation.of(HttpMethod.GET, "/pets", List.of(
                    Parameter.of("status", ParameterLocation.QUERY, true, StringSchema.of()),
                    Parameter.of("limit", ParameterLocation.QUERY, false,
                            NumberSchema.of(NumberKind.INTEGER))))
            .withId(OperationId.of("listPets"));

    private static final Operation ADD_PET = Operation.of(HttpMethod.POST, "/pets")
            .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of("name",
                    StringSchema.of(), "tag", StringSchema.of()), Set.of("name")), true))
            .withId(OperationId.of("addPet"));

    private static final ApiModel API = ApiModel.of("Pets", "1.0", List.of(LIST_PETS, ADD_PET));

    /** A plan in which every request is built by changing an accepted one, when there is one. */
    private static final Campaign ONLY_CHANGES = new Campaign(List.of(new Campaign.PlannedStrategy(
            "mutation", 100, List.of(new Campaign.Entry.Single(
                    new Campaign.Source.Builtin(Campaign.Builtin.RANDOM))), true)),
            WhichOperations.everything());

    @Test
    @DisplayName("before the API has accepted anything, requests are built from the strategy's own "
            + "sources")
    void nothing_accepted_means_the_ordinary_way() {
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(API, SEED, List.of(),
                ONLY_CHANGES, Settings.defaults());

        for (int draw = 0; draw < 50; draw++) {
            TestCase built = generator.generate(LIST_PETS).orElseThrow();
            assertThat(built.mutation()).isEmpty();
            assertThat(built.intent()).isEqualTo(Intent.UNKNOWN);
        }
        assertThat(generator.whatListensToTheRun())
                .describedAs("the memory of accepted requests is listening")
                .hasSize(1).first().isInstanceOf(AcceptedRequests.class);
    }

    @Test
    @DisplayName("once the API accepts a request, the next ones change one thing in it, and say so")
    void an_accepted_request_is_changed() {
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(API, SEED, List.of(),
                ONLY_CHANGES, Settings.defaults());
        Interaction accepted = accepted(generator.generate(LIST_PETS).orElseThrow());
        tell(generator, accepted);

        for (int draw = 0; draw < 50; draw++) {
            TestCase changed = generator.generate(LIST_PETS).orElseThrow();
            assertThat(changed.mutation()).isPresent();
            assertThat(changed.mutation().orElseThrow().of()).isEqualTo(accepted.id());
            assertThat(changed.intent()).isIn(Intent.REFUSAL_EXPECTED, Intent.UNKNOWN);
        }
        assertThat(generator.generate(ADD_PET).orElseThrow().mutation())
                .describedAs("an operation the API has accepted nothing for is built the "
                        + "ordinary way")
                .isEmpty();
    }

    @Test
    @DisplayName("with both families off nothing listens for accepted requests, and nothing is "
            + "changed")
    void both_families_off() {
        Settings off = Settings.defaults().withMutation(
                Settings.defaults().mutation().withNothingChanged());
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(API, SEED, List.of(),
                ONLY_CHANGES, off);

        assertThat(generator.whatListensToTheRun()).isEmpty();
        for (int draw = 0; draw < 50; draw++) {
            assertThat(generator.generate(LIST_PETS).orElseThrow().mutation()).isEmpty();
        }
    }

    @Test
    @DisplayName("every kind of change switched off is the same as both families off")
    void every_kind_off_is_the_same_as_both_families_off() {
        Map<String, String> everyKindOff = new java.util.HashMap<>();
        for (Mutations.Operator operator : Mutations.Operator.values()) {
            everyKindOff.put("mutation." + operator.written(), "false");
        }
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(API, SEED, List.of(),
                ONLY_CHANGES, Settings.from(everyKindOff));

        assertThat(generator.whatListensToTheRun())
                .describedAs("nothing is listening, so nothing is drawn for a change that cannot "
                        + "be made, and the seed repeats the run")
                .isEmpty();
    }

    @Test
    @DisplayName("with its switches off, the shipped plan sends exactly what it sent before it had "
            + "a strategy that changes accepted requests")
    void switched_off_it_is_the_plan_it_was() throws IOException {
        Campaign shipped = Campaigns.shipped();
        // The plan before there was a strategy that changes accepted requests: nominal with every
        // share that is not pushing, and fuzzing. The series a creation may start are switched
        // off throughout, so that what is compared is the changes and nothing else.
        Campaign before = new Campaign(List.of(
                new Campaign.PlannedStrategy("nominal", 75, strategy(shipped, "nominal").sources()),
                strategy(shipped, "fuzzing")), WhichOperations.everything());
        Settings noSeries = Settings.defaults().withSequences(SequenceSettings.noneSent());
        Settings off = noSeries.withMutation(noSeries.mutation().withNothingChanged());
        List<Dictionary> fuzzing = Dictionaries.fuzzing().map(List::of).orElse(List.of());

        assertThat(drawn(new RandomTestCaseGenerator(API, SEED, fuzzing, shipped, off)))
                .isEqualTo(drawn(new RandomTestCaseGenerator(API, SEED, fuzzing, before,
                        noSeries)));
        assertThat(drawn(new RandomTestCaseGenerator(API, SEED, fuzzing, shipped, noSeries)))
                .describedAs("and so does it with them on, for as long as the API has accepted "
                        + "nothing, since there is nothing to change and nothing is drawn")
                .isEqualTo(drawn(new RandomTestCaseGenerator(API, SEED, fuzzing, before,
                        noSeries)));
    }

    private static Campaign.PlannedStrategy strategy(Campaign plan, String name) {
        return plan.strategies().stream().filter(way -> way.name().equals(name)).findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("a request built to push at the API says so; an ordinary one expects nothing in "
            + "particular")
    void every_request_says_what_it_expects() throws IOException {
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(API, SEED,
                Dictionaries.fuzzing().map(List::of).orElse(List.of()), Campaigns.shipped(),
                Settings.defaults());
        List<Intent> intents = new ArrayList<>();

        for (int draw = 0; draw < 200; draw++) {
            generator.generate().ifPresent(built -> intents.add(built.intent()));
        }

        assertThat(intents).containsOnly(Intent.UNKNOWN, Intent.PUSHING)
                .contains(Intent.UNKNOWN, Intent.PUSHING);
    }

    @Test
    @DisplayName("a request of the pushing strategy with nothing awkward in it does not claim to "
            + "be pushing")
    void a_pushing_request_with_nothing_in_it_is_not_pushing() throws IOException {
        Operation ping = Operation.of(HttpMethod.GET, "/ping").withId(OperationId.of("ping"));
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(
                ApiModel.of("Ping", "1.0", List.of(ping)), SEED,
                Dictionaries.fuzzing().map(List::of).orElse(List.of()), Campaigns.shipped(),
                Settings.defaults());

        for (int draw = 0; draw < 100; draw++) {
            assertThat(generator.generate(ping).orElseThrow().intent())
                    .describedAs("an operation with nothing to fill in gets the same request "
                            + "whichever way it is built")
                    .isEqualTo(Intent.UNKNOWN);
        }
    }

    @Test
    @DisplayName("a plan that lists its changing strategy first still opens with the likeliest "
            + "request, built the ordinary way")
    void a_changing_strategy_first_still_has_a_likeliest_request() {
        Campaign changesFirst = new Campaign(List.of(
                new Campaign.PlannedStrategy("mutation", 50, List.of(new Campaign.Entry.Single(
                        new Campaign.Source.Builtin(Campaign.Builtin.RANDOM))), true),
                new Campaign.PlannedStrategy("nominal", 50, List.of(new Campaign.Entry.Single(
                        new Campaign.Source.Builtin(Campaign.Builtin.RANDOM))))),
                WhichOperations.everything());
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(API, SEED, List.of(),
                changesFirst, Settings.defaults());

        assertThat(generator.canBuildTheLikeliestRequest()).isTrue();
        assertThat(generator.likeliestRequest(LIST_PETS).orElseThrow().mutation()).isEmpty();
        assertThat(generator.testableOperations()).containsExactly(LIST_PETS, ADD_PET);
    }

    /** What two hundred requests carried, leaving out the labels they were filed under. */
    private static List<Object> drawn(RandomTestCaseGenerator generator) {
        List<Object> carried = new ArrayList<>();
        for (int draw = 0; draw < 200; draw++) {
            generator.generate().ifPresent(built -> {
                carried.add(built.operation());
                carried.add(built.parameterValues());
                carried.add(built.body());
            });
        }
        return carried;
    }

    private static Interaction accepted(TestCase testCase) {
        return Interaction.answered(testCase,
                HttpRequestRecord.of(HttpMethod.GET, "https://api.example/pets"),
                HttpResponseRecord.of(200), Instant.EPOCH, Duration.ofMillis(1));
    }

    private static void tell(RandomTestCaseGenerator generator, Interaction interaction) {
        for (RunListener listener : generator.whatListensToTheRun()) {
            listener.on(new RunEvent.InteractionCompleted(Instant.EPOCH, interaction));
        }
    }
}

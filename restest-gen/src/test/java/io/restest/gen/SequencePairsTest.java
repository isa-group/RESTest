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
import io.restest.core.event.RunListener;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.Payload;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.gen.GeneratedValue;
import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.ResponseModel;
import io.restest.core.schema.AnySchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.StringSchema;
import io.restest.core.settings.MemorySettings;
import io.restest.core.settings.ScheduleSettings;
import io.restest.core.settings.SequenceSettings;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A request that needs the identifier of a thing nobody has, sent straight after a request that
 * creates one - and the memory letting go of what the API has deleted, which is how "nobody has
 * one" can become true again.
 */
class SequencePairsTest {

    private static final NumberSchema AN_INTEGER = NumberSchema.of(NumberKind.INTEGER);
    private static final Instant START = Instant.parse("2026-09-26T10:00:00Z");

    private static final Operation GET_PET = withGaps(HttpMethod.GET, "/pets/{petId}", "getPet",
            "petId");
    private static final Operation ADD_PET = withGaps(HttpMethod.POST, "/pets", "addPet");
    private static final Operation LIST_PETS = withGaps(HttpMethod.GET, "/pets", "listPets");
    private static final Operation DELETE_PET = withGaps(HttpMethod.DELETE, "/pets/{petId}",
            "deletePet", "petId");
    private static final Operation ADD_OWNERS_PET = withGaps(HttpMethod.POST,
            "/owners/{ownerId}/pets", "addOwnersPet", "ownerId");
    private static final Operation GET_OWNERS_PET = withGaps(HttpMethod.GET,
            "/owners/{ownerId}/pets/{petId}", "getOwnersPet", "ownerId", "petId");
    private static final Operation GET_TOPIC = Operation.of(HttpMethod.GET, "/topics/{topic_name}",
                    List.of(Parameter.of("topic_name", ParameterLocation.PATH, true,
                            StringSchema.of())))
            .withId(OperationId.of("getTopic"))
            .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));

    /** Declared as a document might: the read of one pet before the creation and the list. */
    private static final ApiModel PETS = ApiModel.of("Pets", "1.0",
            List.of(GET_PET, ADD_PET, LIST_PETS, DELETE_PET));

    @Nested
    @DisplayName("forgetting what was deleted")
    class Forgetting {

        @Test
        @DisplayName("a deletion the API agreed to lets go of that thing, and only that thing")
        void a_deletion_forgets_the_thing() {
            Memory memory = new Memory(PETS, MemorySettings.defaults());
            memory.replies(LIST_PETS, List.of(), 200, "[{\"id\": 1}, {\"id\": 2}]");

            memory.replies(DELETE_PET, List.of(petId(1)), 204, null);

            assertThat(memory.identifiersOfKind("pet")).containsExactly("2");
            assertThat(memory.hasSomethingFor(GET_PET, "petId")).isTrue();

            memory.replies(DELETE_PET, List.of(petId(2)), 204, null);

            assertThat(memory.identifiersOfKind("pet")).isEmpty();
            assertThat(memory.hasSomethingFor(GET_PET, "petId"))
                    .describedAs("every pet the API returned has since been deleted")
                    .isFalse();
        }

        @Test
        @DisplayName("a deletion the API refused forgets nothing")
        void a_refused_deletion_forgets_nothing() {
            Memory memory = new Memory(PETS, MemorySettings.defaults());
            memory.replies(LIST_PETS, List.of(), 200, "[{\"id\": 1}]");

            memory.replies(DELETE_PET, List.of(petId(1)), 404, null);

            assertThat(memory.identifiersOfKind("pet")).containsExactly("1");
        }

        @Test
        @DisplayName("switched off, a deleted thing is kept until newer values push it out")
        void switched_off_nothing_is_forgotten() {
            MemorySettings on = MemorySettings.defaults();
            Memory memory = new Memory(PETS, new MemorySettings(on.mostValuesUnderOneName(),
                    on.mostNames(), on.longestValueKept(), on.longestReplyRead(),
                    on.asDeepAsAReplyIsRead(), true, true, false));
            memory.replies(LIST_PETS, List.of(), 200, "[{\"id\": 1}]");

            memory.replies(DELETE_PET, List.of(petId(1)), 204, null);

            assertThat(memory.identifiersOfKind("pet")).containsExactly("1");
        }

        @Test
        @DisplayName("the value under the gap's own name goes, but not a value under a name every "
                + "kind of thing uses")
        void only_the_gaps_own_name_is_forgotten() {
            Memory memory = new Memory(PETS, MemorySettings.defaults());
            memory.replies(LIST_PETS, List.of(), 200,
                    "[{\"id\": 7, \"petId\": 7, \"ownerId\": 7}]");

            memory.replies(DELETE_PET, List.of(petId(7)), 200, "{}");

            assertThat(memory.underItsOwnName("petId")).isEmpty();
            assertThat(memory.underItsOwnName("id"))
                    .describedAs("an owner may be number 7 too")
                    .containsExactly(JsonValue.of(7));
            assertThat(memory.underItsOwnName("ownerId")).containsExactly(JsonValue.of(7));
        }

        @Test
        @DisplayName("the same identifier is recognised however the reply wrote the number")
        void the_same_identifier_however_written() {
            assertThat(ObservedValues.sameIdentifier(JsonValue.of(7), JsonText.read("7.0")))
                    .isTrue();
            assertThat(ObservedValues.sameIdentifier(JsonValue.of(7), JsonValue.of("7")))
                    .isTrue();
            assertThat(ObservedValues.sameIdentifier(JsonValue.of(7), JsonValue.of(70)))
                    .isFalse();
            assertThat(ObservedValues.sameIdentifier(JsonValue.of(true), JsonValue.of("true")))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("asking the memory without choosing")
    class Asking {

        @Test
        @DisplayName("says whether an offer would find something, and moves no number an offer "
                + "draws on")
        void asking_agrees_with_offering_and_draws_nothing() {
            Memory asked = new Memory(PETS, MemorySettings.defaults());
            Memory notAsked = new Memory(PETS, MemorySettings.defaults());
            String reply = "[{\"id\": 1}, {\"id\": 2}, {\"id\": 3}, {\"id\": 4}]";

            assertThat(asked.hasSomethingFor(GET_PET, "petId")).isFalse();
            assertThat(asked.offer(GET_PET, "petId")).isEmpty();

            asked.replies(LIST_PETS, List.of(), 200, reply);
            notAsked.replies(LIST_PETS, List.of(), 200, reply);
            List<JsonValue> afterAsking = new ArrayList<>();
            List<JsonValue> withoutAsking = new ArrayList<>();
            for (int draw = 0; draw < 10; draw++) {
                assertThat(asked.hasSomethingFor(GET_PET, "petId")).isTrue();
                afterAsking.add(asked.offer(GET_PET, "petId").orElseThrow().value());
                withoutAsking.add(notAsked.offer(GET_PET, "petId").orElseThrow().value());
            }

            assertThat(afterAsking).isEqualTo(withoutAsking);
        }
    }

    @Nested
    @DisplayName("reading what a creation made")
    class WhatWasCreated {

        @Test
        @DisplayName("the identifier of the created thing, naming the exchange it came from")
        void the_identifier_of_the_created_thing() {
            Memory memory = new Memory(PETS, MemorySettings.defaults());

            Interaction answer = memory.replies(ADD_PET, List.of(), 201,
                    "{\"name\": \"Leo\", \"id\": 42}");

            assertThat(memory.createdBy(GET_PET, "petId", "{\"name\": \"Leo\", \"id\": 42}",
                    answer)).hasValueSatisfying(created -> {
                        assertThat(created.value()).isEqualTo(JsonValue.of(42));
                        assertThat(created.origin()).isEqualTo(new ValueOrigin.Derived(
                                answer.id(), "the 'id' of what POST /pets created just before"));
                    });
        }

        @Test
        @DisplayName("a property named like the gap wins over id, and a wrapped thing is found")
        void the_gaps_own_name_first_and_wrappers_opened() {
            Memory memory = new Memory(PETS, MemorySettings.defaults());
            Interaction answer = memory.replies(ADD_PET, List.of(), 201, "{}");

            assertThat(memory.createdBy(GET_PET, "petId", "{\"id\": 1, \"petId\": 2}", answer)
                    .map(GeneratedValue::value)).contains(JsonValue.of(2));
            assertThat(memory.createdBy(GET_PET, "petId", "{\"data\": {\"id\": 3}}", answer)
                    .map(GeneratedValue::value)).contains(JsonValue.of(3));
        }

        @Test
        @DisplayName("nothing that could not go in the gap, and nothing but its own name for a gap "
                + "not named like an identifier")
        void only_what_fits() {
            Memory memory = new Memory(ApiModel.of("Topics", "1.0", List.of(GET_TOPIC, ADD_PET,
                    GET_PET)), MemorySettings.defaults());
            Interaction answer = memory.replies(ADD_PET, List.of(), 201, "{}");

            assertThat(memory.createdBy(GET_PET, "petId", "{\"id\": \"not a number\"}", answer))
                    .isEmpty();
            assertThat(memory.createdBy(GET_TOPIC, "topic_name",
                    "{\"id\": 3, \"topic_name\": \"orders\"}", answer).map(GeneratedValue::value))
                    .contains(JsonValue.of("orders"));
            assertThat(memory.createdBy(GET_TOPIC, "topic_name", "{\"id\": 3}", answer))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("the generator")
    class TheGenerator {

        @Test
        @DisplayName("says a read of one pet needs a pet created while the memory has none, and "
                + "not once it has one")
        void what_is_missing() {
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(PETS, 1L);

            assertThat(generator.whatIsMissing(GET_PET)).hasValueSatisfying(need -> {
                assertThat(need.gap()).isEqualTo("petId");
                assertThat(need.producer()).isEqualTo(ADD_PET);
            });
            assertThat(generator.whatIsMissing(LIST_PETS)).isEmpty();

            hear(generator, answered(LIST_PETS, List.of(), 200, "[{\"id\": 5}]"));

            assertThat(generator.whatIsMissing(GET_PET)).isEmpty();
        }

        @Test
        @DisplayName("never says anything is missing for a plan with no memory, since nothing "
                + "records what the API has")
        void nothing_is_missing_without_a_memory() {
            Campaign noMemory = new Campaign(List.of(new Campaign.PlannedStrategy("nominal",
                    Campaign.WHOLE, List.of(new Campaign.Entry.Single(
                            new Campaign.Source.Builtin(Campaign.Builtin.RANDOM))))),
                    WhichOperations.everything());
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(PETS, 1L, List.of(),
                    noMemory);

            assertThat(generator.whatIsMissing(GET_PET)).isEmpty();
        }

        @Test
        @DisplayName("an owner is created before a pet under it, when neither is known")
        void the_outermost_gap_first() {
            Operation addOwner = withGaps(HttpMethod.POST, "/owners", "addOwner");
            ApiModel owners = ApiModel.of("Owners", "1.0",
                    List.of(addOwner, ADD_OWNERS_PET, GET_OWNERS_PET));
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(owners, 1L);

            assertThat(generator.whatIsMissing(GET_OWNERS_PET).map(Producers.Need::producer))
                    .contains(addOwner);
        }

        @Test
        @DisplayName("the request after a creation carries the created identifier, and the "
                + "creation's own gaps where they are shared, each naming the creation")
        void the_follow_up_carries_what_the_creation_gave() {
            ApiModel owners = ApiModel.of("Owners", "1.0", List.of(ADD_OWNERS_PET,
                    GET_OWNERS_PET));
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(owners, 1L);
            Producers.Need need = generator.whatIsMissing(GET_OWNERS_PET).orElseThrow();
            assertThat(need.gap()).isEqualTo("petId");
            TestCase creation = TestCase.of(ADD_OWNERS_PET.id(), List.of(ParameterValue.of(
                    "ownerId", ParameterLocation.PATH, JsonValue.of(5),
                    new ValueOrigin.Generated("random"))));
            Interaction answer = answered(creation, 201, "{\"id\": 42, \"name\": \"Leo\"}");

            Map<String, GeneratedValue> gave = generator.whatTheCreationGave(GET_OWNERS_PET,
                    need, creation, answer);
            TestCase followUp = generator.followUp(GET_OWNERS_PET, gave).orElseThrow();

            assertThat(followUp.parameterValue("ownerId", ParameterLocation.PATH))
                    .hasValueSatisfying(value -> {
                        assertThat(value.value()).isEqualTo(JsonValue.of(5));
                        assertThat(value.origin()).isEqualTo(new ValueOrigin.Derived(answer.id(),
                                "the 'ownerId' POST /owners/{ownerId}/pets was sent with"));
                    });
            assertThat(followUp.parameterValue("petId", ParameterLocation.PATH))
                    .hasValueSatisfying(value -> {
                        assertThat(value.value()).isEqualTo(JsonValue.of(42));
                        assertThat(value.origin()).isEqualTo(new ValueOrigin.Derived(answer.id(),
                                "the 'id' of what POST /owners/{ownerId}/pets created just "
                                        + "before"));
                    });
        }

        @Test
        @DisplayName("a creation the API refused gives no identifier, and the request after it is "
                + "filled in the ordinary way")
        void a_refused_creation_gives_nothing() {
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(PETS, 1L);
            Producers.Need need = generator.whatIsMissing(GET_PET).orElseThrow();
            TestCase creation = TestCase.of(ADD_PET.id(), List.of());

            Map<String, GeneratedValue> gave = generator.whatTheCreationGave(GET_PET, need,
                    creation, answered(creation, 400, "{\"id\": 42}"));

            assertThat(gave).isEmpty();
            assertThat(generator.whatTheCreationGave(GET_PET, need, null, null)).isEmpty();
            assertThat(generator.followUp(GET_PET, gave)).hasValueSatisfying(ordinary ->
                    assertThat(ordinary.parameterValue("petId", ParameterLocation.PATH))
                            .isPresent());
        }
    }

    @Nested
    @DisplayName("the scheduler")
    class TheScheduler {

        private Instant now = START;
        private final InstantSource clock = () -> now;

        @Test
        @DisplayName("sends the creation in the read's place, and the read as soon as the "
                + "creation's answer is back, with the identifier it carried")
        void a_pair() {
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(PETS, 1L);
            Scheduler scheduler = scheduler(generator, true);

            Scheduler.Step.Send creation = (Scheduler.Step.Send) scheduler.next();
            assertThat(creation.operation()).isEqualTo(ADD_PET);
            assertThat(creation.startsAPair()).isTrue();
            TestCase sent = scheduler.testCaseFor(creation).orElseThrow();

            scheduler.heard(creation, sent, answered(sent, 201, "{\"id\": 42}"));

            Scheduler.Step.Send read = (Scheduler.Step.Send) scheduler.next();
            assertThat(read.operation()).isEqualTo(GET_PET);
            assertThat(read.startsAPair()).isFalse();
            assertThat(scheduler.testCaseFor(read).orElseThrow()
                    .parameterValue("petId", ParameterLocation.PATH).orElseThrow().value())
                    .isEqualTo(JsonValue.of(42));
            assertThat(said(scheduler, 3)).containsExactly("addPet", "listPets",
                    "addPet, creating for deletePet");
        }

        @Test
        @DisplayName("a creation that made nothing usable is not sent as one again until the next "
                + "round, and the request it was for still goes")
        void a_creation_that_made_nothing_waits_a_round() {
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(PETS, 1L);
            Scheduler scheduler = scheduler(generator, true);

            Scheduler.Step.Send creation = (Scheduler.Step.Send) scheduler.next();
            TestCase sent = scheduler.testCaseFor(creation).orElseThrow();
            scheduler.heard(creation, sent, answered(sent, 400, "{\"message\": \"no\"}"));

            assertThat(said(scheduler, 6)).containsExactly("getPet, after its creation",
                    "addPet", "listPets", "deletePet", "end of a round",
                    "addPet, creating for getPet");
        }

        @Test
        @DisplayName("while one creation is awaited, another request needing the same thing goes "
                + "the ordinary way rather than sending a creation of its own")
        void one_creation_of_a_kind_awaited_at_a_time() {
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(PETS, 1L);
            Scheduler scheduler = scheduler(generator, true);

            // A whole round goes out before the first answer is back, as it does against any API.
            assertThat(said(scheduler, 5)).containsExactly("addPet, creating for getPet",
                    "addPet", "listPets", "deletePet", "end of a round");
        }

        @Test
        @DisplayName("a creation that never went out still lets the request it was for go")
        void a_creation_never_sent() {
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(PETS, 1L);
            Scheduler scheduler = scheduler(generator, true);
            Scheduler.Step.Send creation = (Scheduler.Step.Send) scheduler.next();

            scheduler.heard(creation, null, null);

            assertThat(said(scheduler, 1)).containsExactly("getPet, after its creation");
        }

        @Test
        @DisplayName("switched off, no pairs: each operation in its place, as before")
        void switched_off() {
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(PETS, 1L);

            assertThat(said(scheduler(generator, false), 5)).containsExactly("getPet",
                    "addPet", "listPets", "deletePet", "end of a round");
        }

        @Test
        @DisplayName("the first round makes no pairs: it already sends creations before reads")
        void none_in_the_first_round() {
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(PETS, 1L);
            ScheduleSettings shipped = ScheduleSettings.defaults();
            Scheduler scheduler = new Scheduler(generator, shipped, START.plusSeconds(60), clock,
                    event -> { });

            assertThat(said(scheduler, 9)).containsExactly("listPets (first round)", "wait",
                    "addPet (first round)", "wait", "getPet (first round)", "wait",
                    "deletePet (first round)", "wait", "end of the first round");
        }

        @Test
        @DisplayName("nothing waiting is sent once the time is up")
        void nothing_after_the_time_is_up() {
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(PETS, 1L);
            Scheduler scheduler = scheduler(generator, true);
            Scheduler.Step.Send creation = (Scheduler.Step.Send) scheduler.next();
            TestCase sent = scheduler.testCaseFor(creation).orElseThrow();
            scheduler.heard(creation, sent, answered(sent, 201, "{\"id\": 42}"));

            now = START.plusSeconds(61);

            assertThat(said(scheduler, 2)).containsExactly("time is up", "time is up");
        }

        @Test
        @DisplayName("only a creation's answer is waited on")
        void only_a_creation_is_heard() {
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(PETS, 1L);
            Scheduler scheduler = scheduler(generator, false);

            assertThatIllegalArgumentException().isThrownBy(() -> scheduler.heard(
                    new Scheduler.Step.Send(GET_PET, false), null, null));
        }

        /** Without a first round, so the ordinary rounds start at once. */
        private Scheduler scheduler(RandomTestCaseGenerator generator, boolean pairs) {
            return new Scheduler(generator, new ScheduleSettings(2, 1_000, Duration.ofSeconds(10),
                    false, Duration.ofSeconds(2)), new SequenceSettings(pairs),
                    START.plusSeconds(60), clock, event -> { });
        }
    }

    /** The next few steps, in words. */
    private static List<String> said(Scheduler scheduler, int howMany) {
        List<String> said = new ArrayList<>();
        for (int step = 0; step < howMany; step++) {
            said.add(switch (scheduler.next()) {
                case Scheduler.Step.Send send -> send.operation().id().value()
                        + (send.inTheOpeningLap() ? " (first round)" : "")
                        + send.pair().map(pair -> pair.isTheCreation()
                                ? ", creating for " + pair.consumer().id().value()
                                : ", after its creation").orElse("");
                case Scheduler.Step.WaitForTheAnswers ignored -> "wait";
                case Scheduler.Step.EndOfAPass end ->
                        end.ofTheOpeningLap() ? "end of the first round" : "end of a round";
                case Scheduler.Step.TimeIsUp ignored -> "time is up";
            });
        }
        return said;
    }

    private static void hear(RandomTestCaseGenerator generator, Interaction interaction) {
        RunListener memory = generator.whatListensToTheRun().orElseThrow();
        memory.on(new RunEvent.InteractionCompleted(Instant.EPOCH, interaction));
    }

    private static ParameterValue petId(int value) {
        return ParameterValue.of("petId", ParameterLocation.PATH, JsonValue.of(value),
                new ValueOrigin.Generated("random"));
    }

    private static Interaction answered(Operation operation, List<ParameterValue> values,
            int status, String body) {
        return answered(TestCase.of(operation.id(), values), status, body);
    }

    private static Interaction answered(TestCase testCase, int status, String body) {
        return Interaction.answered(testCase,
                HttpRequestRecord.of(HttpMethod.GET, "https://api.example/"),
                new HttpResponseRecord(StatusLine.of(status),
                        List.of(Header.of("Content-Type", "application/json")),
                        body == null ? Optional.empty()
                                : Optional.of(Payload.of(body.getBytes(StandardCharsets.UTF_8),
                                        "application/json"))),
                Instant.EPOCH, Duration.ofMillis(3));
    }

    private static Operation withGaps(HttpMethod method, String path, String id,
            String... gaps) {
        List<Parameter> parameters = new ArrayList<>();
        for (String gap : gaps) {
            parameters.add(Parameter.of(gap, ParameterLocation.PATH, true, AN_INTEGER));
        }
        return Operation.of(method, path, parameters).withId(OperationId.of(id))
                .withResponses(List.of(ResponseModel.json(
                        method == HttpMethod.POST ? "201" : "200", AnySchema.of())));
    }

    /** A memory listening to a small API, and a source asking it. */
    private static final class Memory {

        private final ApiModel model;
        private final ObservedValues seen;
        private final ObservedValueProvider provider;

        Memory(ApiModel model, MemorySettings settings) {
            this.model = model;
            this.seen = new ObservedValues(model, settings);
            this.provider = new ObservedValueProvider(model, seen, new SplittableRandom(93L),
                    null);
        }

        Interaction replies(Operation operation, List<ParameterValue> values, int status,
                String body) {
            Interaction interaction = answered(operation, values, status, body);
            seen.on(new RunEvent.InteractionCompleted(Instant.EPOCH, interaction));
            return interaction;
        }

        List<String> identifiersOfKind(String kind) {
            return seen.underTheKindOfThingTheyAre().thingsOfKind(kind).stream()
                    .map(thing -> JsonText.write(((JsonValue.JsonObject) thing.value())
                            .members().get("id")))
                    .toList();
        }

        List<JsonValue> underItsOwnName(String name) {
            return seen.underTheirOwnNames().valuesFor(ValueRequest.of(OperationId.of("any"),
                    name, ParameterLocation.QUERY, AnySchema.of()));
        }

        boolean hasSomethingFor(Operation operation, String gap) {
            return provider.hasSomethingFor(gapIn(operation, gap));
        }

        Optional<GeneratedValue> offer(Operation operation, String gap) {
            return provider.offer(gapIn(operation, gap));
        }

        Optional<GeneratedValue> createdBy(Operation consumer, String gap, String reply,
                Interaction answer) {
            return provider.identifierCreatedBy(gapIn(consumer, gap), JsonText.read(reply),
                    answer.id(), "POST /pets");
        }

        private ValueRequest gapIn(Operation operation, String gap) {
            Parameter declared = model.operation(operation.id()).orElseThrow().parameters()
                    .stream().filter(parameter -> parameter.name().equals(gap)).findFirst()
                    .orElseThrow();
            return ValueRequest.of(operation.id(), gap, ParameterLocation.PATH,
                    declared.schema());
        }
    }
}

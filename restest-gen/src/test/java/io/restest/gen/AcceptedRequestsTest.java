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
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Intent;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionId;
import io.restest.core.execution.Mutation;
import io.restest.core.execution.TestCase;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import io.restest.core.settings.MutationSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which of the requests a run sent are kept to be changed, and for how long.
 */
class AcceptedRequestsTest {

    private static final OperationId LIST_PETS = OperationId.of("listPets");
    private static final OperationId DELETE_PET = OperationId.of("deletePet");
    private static final ApiModel API = ApiModel.of("Pets", "1.0", List.of(
            Operation.of(HttpMethod.GET, "/pets").withId(LIST_PETS),
            Operation.of(HttpMethod.DELETE, "/pets").withId(DELETE_PET)));

    @Test
    @DisplayName("a request the API accepted is kept under its operation, with the exchange")
    void an_accepted_request_is_kept() {
        AcceptedRequests memory = new AcceptedRequests(API, MutationSettings.defaults());
        Interaction accepted = answered(TestCase.of(LIST_PETS, List.of()), 200);

        memory.on(new RunEvent.InteractionCompleted(Instant.EPOCH, accepted));

        assertThat(memory.of(LIST_PETS)).containsExactly(
                new AcceptedRequests.Accepted(accepted.testCase(), accepted.id()));
        assertThat(memory.oneOf(LIST_PETS, new SplittableRandom(1)))
                .map(AcceptedRequests.Accepted::from).contains(accepted.id());
    }

    @Test
    @DisplayName("a refusal, a failure and a reply that never came are not kept")
    void only_a_success_is_kept() {
        AcceptedRequests memory = new AcceptedRequests(API, MutationSettings.defaults());

        for (int status : List.of(199, 300, 400, 404, 500)) {
            memory.on(new RunEvent.InteractionCompleted(Instant.EPOCH,
                    answered(TestCase.of(LIST_PETS, List.of()), status)));
        }
        memory.on(new RunEvent.InteractionCompleted(Instant.EPOCH, Interaction.transportFailure(
                TestCase.of(LIST_PETS, List.of()), request(), "refused", Instant.EPOCH,
                Duration.ZERO)));

        assertThat(memory.of(LIST_PETS)).isEmpty();
        assertThat(memory.oneOf(LIST_PETS, new SplittableRandom(1))).isEmpty();
    }

    @Test
    @DisplayName("a request that was itself a change, or that pushed at the API, is not kept")
    void neither_a_change_nor_a_push_is_kept() {
        AcceptedRequests memory = new AcceptedRequests(API, MutationSettings.defaults());
        TestCase changed = TestCase.changed(LIST_PETS, List.of(), Optional.empty(),
                Intent.REFUSAL_EXPECTED, new Mutation(InteractionId.generate(), "dropRequired",
                        ParameterLocation.QUERY, "limit", "left out 'limit'"));
        TestCase pushing = TestCase.of(LIST_PETS, List.of(), Optional.empty(), Intent.PUSHING);

        memory.on(new RunEvent.InteractionCompleted(Instant.EPOCH, answered(changed, 200)));
        memory.on(new RunEvent.InteractionCompleted(Instant.EPOCH, answered(pushing, 200)));

        assertThat(memory.of(LIST_PETS))
                .describedAs("changing a change would be two changes, and a request made of "
                        + "awkward values throughout is not one anybody believes in")
                .isEmpty();
    }

    @Test
    @DisplayName("a step of a series is not kept: what happens to its thing is the series' alone")
    void a_step_of_a_series_is_not_kept() {
        AcceptedRequests memory = new AcceptedRequests(API, MutationSettings.defaults());
        TestCase step = TestCase.stepOf(LIST_PETS, List.of(), Optional.empty(), Intent.UNKNOWN,
                io.restest.core.execution.SequenceStep.first("createTwice", "create a thing"));

        memory.on(new RunEvent.InteractionCompleted(Instant.EPOCH, answered(step, 200)));

        assertThat(memory.of(LIST_PETS)).isEmpty();
    }

    @Test
    @DisplayName("a deletion is not kept: what it deleted is gone")
    void a_deletion_is_not_kept() {
        AcceptedRequests memory = new AcceptedRequests(API, MutationSettings.defaults());

        memory.on(new RunEvent.InteractionCompleted(Instant.EPOCH,
                answered(TestCase.of(DELETE_PET, List.of()), 204)));

        assertThat(memory.of(DELETE_PET)).isEmpty();
    }

    @Test
    @DisplayName("only the most recent are kept, the oldest let go first")
    void only_the_most_recent_are_kept() {
        MutationSettings three = new MutationSettings(true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true, 3, 10, 10);
        AcceptedRequests memory = new AcceptedRequests(API, three);
        List<Interaction> sent = List.of(answered(TestCase.of(LIST_PETS, List.of()), 200),
                answered(TestCase.of(LIST_PETS, List.of()), 200),
                answered(TestCase.of(LIST_PETS, List.of()), 201),
                answered(TestCase.of(LIST_PETS, List.of()), 200));

        sent.forEach(each -> memory.on(new RunEvent.InteractionCompleted(Instant.EPOCH, each)));

        assertThat(memory.of(LIST_PETS)).extracting(AcceptedRequests.Accepted::from)
                .containsExactly(sent.get(1).id(), sent.get(2).id(), sent.get(3).id());
    }

    @Test
    @DisplayName("anything but a finished exchange is ignored")
    void other_announcements_are_ignored() {
        AcceptedRequests memory = new AcceptedRequests(API, MutationSettings.defaults());

        memory.on(new RunEvent.TestCasePlanned(Instant.EPOCH, TestCase.of(LIST_PETS, List.of())));

        assertThat(memory.of(LIST_PETS)).isEmpty();
    }

    private static Interaction answered(TestCase testCase, int status) {
        return Interaction.answered(testCase, request(), HttpResponseRecord.of(status),
                Instant.EPOCH, Duration.ofMillis(1));
    }

    private static HttpRequestRecord request() {
        return HttpRequestRecord.of(HttpMethod.GET, "https://api.example/pets");
    }
}

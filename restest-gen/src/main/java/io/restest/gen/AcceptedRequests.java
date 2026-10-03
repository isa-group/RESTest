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

import io.restest.core.event.RunEvent;
import io.restest.core.event.RunListener;
import io.restest.core.execution.Intent;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionId;
import io.restest.core.execution.TestCase;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import io.restest.core.settings.MutationSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.random.RandomGenerator;

/**
 * The requests the API has accepted so far, kept so that one of them can be sent again with a
 * single thing changed.
 *
 * <p>Changing one thing in a request that worked is how a run finds out what an API does when only
 * that one thing is wrong. For that it needs requests that worked, and this is where they are kept:
 * it listens to every reply, and each time the API answers with a success, the request that earned
 * it is put aside under its operation. {@link Mutations} then takes one of them and changes it.
 *
 * <p>Not every success is worth keeping. A request that was itself made by changing another one is
 * left out, because changing it again would be two changes, and the point is that the API's answer
 * can be put down to one. So is a request built from awkward values throughout, which is not a
 * request anybody believes in. And so is a deletion: the thing it deleted is gone, so the same
 * request sent again with something else changed would only be told there is no such thing. A step
 * of a series built around a thing the run created is left out as well, since what happens to that
 * thing is the series' business alone.
 *
 * <p>Only the most recent few are kept for each operation, and the oldest let go, for the same
 * reason the memory of values the API returned is kept small: a request that worked a while ago may
 * name something that has been deleted since.
 *
 * <p>It hears about replies through the run's stream of announcements, on the thread that carries
 * them, and is asked for a request on the thread that builds them. The list kept for an operation is
 * replaced whole rather than added to, so the one asking always sees a complete list and never
 * waits for a reply to be taken in.
 */
final class AcceptedRequests implements RunListener {

    private final ApiModel model;
    private final MutationSettings settings;
    private final ConcurrentMap<OperationId, List<Accepted>> byOperation =
            new ConcurrentHashMap<>();

    /**
     * An empty memory of accepted requests.
     *
     * @param model the API being tested, which says which operations delete things
     * @param settings how many requests are kept for each operation
     */
    AcceptedRequests(ApiModel model, MutationSettings settings) {
        this.model = Objects.requireNonNull(model, "model");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public void on(RunEvent event) {
        Objects.requireNonNull(event, "event");
        if (!(event instanceof RunEvent.InteractionCompleted completed)) {
            return;
        }
        Interaction interaction = completed.interaction();
        TestCase testCase = interaction.testCase();
        if (!worthLearningFrom(model, interaction)) {
            return;
        }
        byOperation.compute(testCase.operation(), (ignored, kept) -> {
            List<Accepted> latest = new ArrayList<>(kept == null ? List.of() : kept);
            latest.add(new Accepted(testCase, interaction.id()));
            while (latest.size() > settings.acceptedKept()) {
                latest.remove(0);
            }
            return List.copyOf(latest);
        });
    }

    /**
     * The requests kept for one operation, the most recent last.
     *
     * @param operation the operation
     * @return them, empty when the API has not accepted one yet
     */
    List<Accepted> of(OperationId operation) {
        return byOperation.getOrDefault(Objects.requireNonNull(operation, "operation"), List.of());
    }

    /**
     * One of the requests kept for an operation, chosen by chance.
     *
     * @param operation the operation
     * @param random where the choice comes from
     * @return one of them, or nothing when the API has not accepted one yet
     */
    Optional<Accepted> oneOf(OperationId operation, RandomGenerator random) {
        List<Accepted> kept = of(operation);
        return kept.isEmpty()
                ? Optional.empty()
                : Optional.of(kept.get(random.nextInt(kept.size())));
    }

    /**
     * Whether a request the API answered is one a run should learn from: accepted, not made by
     * changing another one, not a step of a series, not built from values meant to push at the API,
     * and not a deletion. The one rule for this, shared by everything that keeps accepted requests
     * or the values in them.
     *
     * @param model the API, which says which operations delete things
     * @param interaction the request and the API's answer to it
     * @return whether it is worth learning from
     */
    static boolean worthLearningFrom(ApiModel model, Interaction interaction) {
        TestCase testCase = interaction.testCase();
        boolean accepted = interaction.statusCode()
                .filter(code -> code >= 200 && code < 300)
                .isPresent();
        return accepted
                && testCase.mutation().isEmpty()
                && testCase.sequence().isEmpty()
                && testCase.intent() != Intent.PUSHING
                && model.operation(testCase.operation())
                        .filter(operation -> operation.method() != HttpMethod.DELETE)
                        .isPresent();
    }

    /**
     * One request the API accepted, and the exchange in which it did.
     *
     * @param testCase what was sent
     * @param from the exchange that accepted it, which a change made to it names
     */
    record Accepted(TestCase testCase, InteractionId from) {

        Accepted {
            Objects.requireNonNull(testCase, "testCase");
            Objects.requireNonNull(from, "from");
        }
    }
}

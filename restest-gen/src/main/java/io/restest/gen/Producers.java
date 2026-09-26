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

import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Which of an API's operations creates the thing another operation needs the identifier of.
 *
 * <p>Asking an API for one pet - {@code GET /pets/{petId}} - needs the identifier of a pet that
 * exists. When the run knows of none, the way to have one is to create one, and this is what says
 * which operation does the creating. It is read off the addresses alone, the way a person reading
 * the document would read them:
 *
 * <ul>
 *   <li>the {@code POST} to the same address with the gap and what follows it left off -
 *       {@code POST /pets} for {@code GET /pets/{petId}}, {@code POST /owners/{ownerId}/pets} for
 *       {@code GET /owners/{ownerId}/pets/{petId}}. Gaps match gaps whatever they are called, and a
 *       closing {@code /} makes no difference;</li>
 *   <li>failing that, and only for a gap named the way identifiers are - {@code {petId}},
 *       {@code {id}} - the first {@code POST} whose address ends in the same kind of thing the gap
 *       is about, written with the rules {@link ObservedValues} uses for kinds - so
 *       {@code GET /pets/{petId}} is still helped by {@code POST /owners/{ownerId}/pets} in an API
 *       with no {@code POST /pets}. A gap called {@code {owner}} or {@code {username}} is asking
 *       for something other than the identifier of what the address is about.</li>
 * </ul>
 *
 * <p>Only operations the run can attempt are considered, on both sides, so an operation a plan set
 * aside is never sent to make something. An operation never creates for itself.
 *
 * <p>This only answers the question. The {@link Scheduler} decides when to ask it, and {@link
 * RandomTestCaseGenerator} builds both requests.
 */
final class Producers {

    private final Map<OperationId, List<Need>> byConsumer;

    private Producers(Map<OperationId, List<Need>> byConsumer) {
        this.byConsumer = byConsumer;
    }

    /**
     * Who creates what, among these operations.
     *
     * @param operations the operations a run can attempt, in the order the document declares them
     * @return the answer for every one of them
     */
    static Producers among(List<Operation> operations) {
        Objects.requireNonNull(operations, "operations");
        List<Operation> creations = operations.stream()
                .filter(operation -> operation.method() == HttpMethod.POST)
                .toList();
        Map<OperationId, List<Need>> byConsumer = new LinkedHashMap<>();
        for (Operation consumer : operations) {
            List<String> parts = partsOf(consumer.path());
            List<Need> needs = new ArrayList<>();
            for (int at = 0; at < parts.size(); at++) {
                gapNamed(parts.get(at)).flatMap(gap -> producerFor(consumer,
                        parts, gap, creations)).ifPresent(needs::add);
            }
            if (!needs.isEmpty()) {
                byConsumer.put(consumer.id(), List.copyOf(needs));
            }
        }
        return new Producers(byConsumer);
    }

    /**
     * What this operation's address needs created, one entry per gap that some operation can
     * create the thing for, in the order the gaps appear.
     */
    List<Need> of(Operation consumer) {
        return byConsumer.getOrDefault(Objects.requireNonNull(consumer, "consumer").id(),
                List.of());
    }

    private static Optional<Need> producerFor(Operation consumer, List<String> parts, String gap,
            List<Operation> creations) {
        List<String> before = parts.subList(0, parts.indexOf("{" + gap + "}"));
        for (Operation creation : creations) {
            if (creation.id().equals(consumer.id())) {
                continue;
            }
            List<String> theirs = partsOf(creation.path());
            if (theirs.size() != before.size()) {
                continue;
            }
            Map<String, String> shared = new LinkedHashMap<>();
            boolean same = true;
            for (int at = 0; at < before.size() && same; at++) {
                Optional<String> ourGap = gapNamed(before.get(at));
                Optional<String> theirGap = gapNamed(theirs.get(at));
                if (ourGap.isPresent() && theirGap.isPresent()) {
                    shared.put(ourGap.get(), theirGap.get());
                } else {
                    same = ourGap.isEmpty() && theirGap.isEmpty()
                            && before.get(at).equals(theirs.get(at));
                }
            }
            if (same) {
                return Optional.of(new Need(gap, creation, shared));
            }
        }
        // By kind only for a gap named like an identifier. A gap called {owner} or {username}
        // under an address about repositories is not asking for a repository's identifier, and
        // across the corpus most such pairings were of that sort.
        if (!ObservedValues.looksLikeAnIdentifier(gap)) {
            return Optional.empty();
        }
        List<String> kinds = new ArrayList<>();
        ObservedValues.kindOfThingBefore(consumer.path(), gap).ifPresent(kinds::add);
        ObservedValues.kindOfThingInTheName(gap).filter(kind -> !kinds.contains(kind))
                .ifPresent(kinds::add);
        for (String kind : kinds) {
            for (Operation creation : creations) {
                List<String> theirs = partsOf(creation.path());
                if (!creation.id().equals(consumer.id()) && !theirs.isEmpty()
                        && gapNamed(theirs.get(theirs.size() - 1)).isEmpty()
                        && ObservedValues.kindOfThingNamed(theirs.get(theirs.size() - 1))
                                .filter(kind::equals).isPresent()) {
                    return Optional.of(new Need(gap, creation, Map.of()));
                }
            }
        }
        return Optional.empty();
    }

    /** The name inside a part of an address that is one whole gap, such as {@code petId}. */
    private static Optional<String> gapNamed(String part) {
        return part.length() > 2 && part.startsWith("{") && part.endsWith("}")
                && part.indexOf('{', 1) < 0
                ? Optional.of(part.substring(1, part.length() - 1)) : Optional.empty();
    }

    private static List<String> partsOf(String path) {
        return Arrays.stream(path.split("/")).filter(part -> !part.isEmpty()).toList();
    }

    /**
     * One gap in an operation's address, and the operation that creates the thing it names.
     *
     * @param gap the gap's name, such as {@code petId}
     * @param producer the {@code POST} that creates such a thing
     * @param sharedGaps the gaps the creation's own address has in the same places, by the name
     *     each has in the address that needs it: {@code ownerId} to {@code ownerId} for
     *     {@code POST /owners/{ownerId}/pets} creating for {@code GET /owners/{ownerId}/pets/{petId}}.
     *     What the creation was sent there is sent there again, so the pet asked for belongs to
     *     the owner it was created under. Empty when the creation was found by kind
     */
    record Need(String gap, Operation producer, Map<String, String> sharedGaps) {

        Need {
            Objects.requireNonNull(gap, "gap");
            Objects.requireNonNull(producer, "producer");
            sharedGaps = Map.copyOf(sharedGaps);
        }
    }
}

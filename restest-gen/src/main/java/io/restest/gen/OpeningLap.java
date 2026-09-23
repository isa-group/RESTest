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

import io.restest.core.model.Operation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The order in which a run sends every operation once, at the very start, before it begins choosing
 * by chance.
 *
 * <p>Some operations can only succeed once others have answered. Asking an API for one of its
 * clusters needs the identifier of a cluster, and the only way to learn one may be to ask for the
 * list of clusters first; asking for a pet needs a pet that exists, and one way to have one is to
 * have just created it. So the first round goes in five steps, each one sent only once the answers
 * to the one before have come back:
 *
 * <ol>
 *   <li>the <b>lists</b>: reads whose address has nothing to fill in, such as {@code GET /owners},
 *       which show what is already there;
 *   <li>the <b>creations</b>: every {@code POST}, which make something new;
 *   <li>the <b>single things</b>: reads whose address names one thing in particular, such as
 *       {@code GET /owners/{ownerId}}, which by now can name something that exists;
 *   <li>the <b>changes</b>: every {@code PUT} and {@code PATCH};
 *   <li>the <b>deletions</b>, last, so that nothing the round needed is gone before it was used.
 * </ol>
 *
 * <p>Within a step, operations keep the order the document declares them in. Whether an address
 * "names one thing" is decided by whether it has a gap in it to fill - a {@code {…}} - and nothing
 * cleverer: a gap usually picks one thing out of the collection named before it, and where it does
 * not, the only cost is that the operation goes one step later in the same first second.
 *
 * <p>This decides only the order. The {@link Scheduler} uses it to decide what a run sends first and
 * asks for a wait between the steps; what goes into each request is filled in by {@link
 * RandomTestCaseGenerator}.
 */
final class OpeningLap {

    /** What one step of the round does, in the order the steps are taken. */
    enum Step {
        LISTS, CREATIONS, SINGLE_THINGS, CHANGES, DELETIONS
    }

    private OpeningLap() {
    }

    /**
     * These operations, divided into the steps of the round, in the order the round takes them.
     *
     * @param operations the operations to send, in the order the document declares them
     * @return one list per step that has anything in it, in step order; each list in document order
     */
    static List<List<Operation>> steps(List<Operation> operations) {
        Map<Step, List<Operation>> byStep = new EnumMap<>(Step.class);
        for (Operation operation : operations) {
            byStep.computeIfAbsent(stepOf(operation), step -> new ArrayList<>()).add(operation);
        }
        // EnumMap walks its keys in the order the steps are declared, and a step with nothing in
        // it is never added, so no step is ever waited on for nothing.
        return byStep.values().stream().map(List::copyOf).toList();
    }

    /** Which step of the round this operation belongs to. */
    static Step stepOf(Operation operation) {
        return switch (operation.method()) {
            case GET, HEAD, OPTIONS, TRACE, QUERY ->
                    namesOneThing(operation) ? Step.SINGLE_THINGS : Step.LISTS;
            case POST -> Step.CREATIONS;
            case PUT, PATCH -> Step.CHANGES;
            case DELETE -> Step.DELETIONS;
        };
    }

    /** Whether the operation's address has a gap to fill in, which usually picks out one thing. */
    private static boolean namesOneThing(Operation operation) {
        return operation.path().contains("{");
    }
}

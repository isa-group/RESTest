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
package io.restest.core.execution;

import java.util.List;
import java.util.Objects;

/**
 * Where a {@link TestCase} stands in a short series of requests a run built around a thing it
 * created itself.
 *
 * <p>Some faults only show across several requests: a thing still there after the API said it had
 * deleted it, a second deletion of the same thing that makes the API fail, something added under a
 * thing that no longer exists, the same creation sent twice, a change that leaves a different result
 * when it is repeated. To look for them, a run creates something of its own and sends a short, fixed
 * series of requests about it - read it, delete it, read it again - each one built only once the
 * answer to the one before has arrived, with the identifier the API gave the thing.
 *
 * <p>This records which kind of series a request belongs to, which step of it the request is, and
 * the earlier exchanges of the same series it was built from or should be compared with: the
 * creation whose identifier it carries, the deletion it follows, the read whose answer it should
 * repeat. What each kind of series expects of each of its steps is fixed by the kind, so a stored run
 * keeps everything needed to judge it later. Nothing records the series as a whole: its steps, and
 * the exchanges each one names, are the series.
 *
 * @param shape the kind of series, by the name a person switches it off with - {@code
 *     readAfterDelete}, {@code createTwice}
 * @param step which step this is, counting the creation as the first
 * @param follows the earlier exchanges of the same series this step was built from or should be
 *     compared with, in the order they happened; empty for the first step, which follows nothing
 * @param description what the step does and what it should show, for a person reading a stored run
 */
public record SequenceStep(String shape, int step, List<InteractionId> follows,
        String description) {

    public SequenceStep {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(follows, "follows");
        Objects.requireNonNull(description, "description");
        follows = List.copyOf(follows);
        if (shape.isBlank()) {
            throw new IllegalArgumentException("a step names the kind of series it belongs to");
        }
        if (step < 1) {
            throw new IllegalArgumentException("steps are counted from one, the creation: " + step);
        }
        if (step == 1 && !follows.isEmpty()) {
            throw new IllegalArgumentException("the first step of a series follows nothing");
        }
        if (step > 1 && follows.isEmpty()) {
            throw new IllegalArgumentException("a later step names the exchange it was built from");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("a step says in words what it does");
        }
    }

    /**
     * The first step of a series: the creation, which follows nothing.
     *
     * @param shape the kind of series
     * @param description what the step does
     * @return the step
     */
    public static SequenceStep first(String shape, String description) {
        return new SequenceStep(shape, 1, List.of(), description);
    }
}

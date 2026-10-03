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

import io.restest.core.model.ParameterLocation;
import java.util.Objects;

/**
 * The one thing that was changed to make a {@link TestCase} out of a request the API had already
 * accepted.
 *
 * <p>One way to find the faults hiding behind an API's checks is to take a request it accepted
 * and send it again with exactly one thing wrong: a value it insists on left out, a number one past
 * the largest it allows, a word where it wants a number. Everything else in the request is known
 * to be acceptable, so whatever the API does next can be put down to that one change - and a
 * change that gets past the checks, or makes the API fail, points at the code that handles it.
 *
 * <p>This records which accepted request the change was made to, by the {@link InteractionId} of
 * the exchange that accepted it, and what the change was. The rest is in the test case itself:
 * comparing the two shows the change exactly, and {@link Intent} says what the change was expected
 * to earn - a refusal, for one that breaks what the documentation says.
 *
 * @param of the exchange in which the API accepted the request this was made from
 * @param operator what kind of change it was, by the name a person switches it off with -
 *     {@code dropRequired}, {@code outsideABound}
 * @param location where the changed value is, or was: the path, the query string, a header, a
 *     cookie, or the body. For a value moved somewhere else, where it was declared
 * @param path the way down to it, written as everywhere else in RESTest: the parameter's name, or
 *     {@code body.owner.email} and {@code body.tags[].label} for something inside the body, the
 *     {@code []} standing for an element of a list
 * @param description the change in words, for a person reading a stored run
 */
public record Mutation(InteractionId of, String operator, ParameterLocation location, String path,
        String description) {

    public Mutation {
        Objects.requireNonNull(of, "of");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(description, "description");
        if (operator.isBlank()) {
            throw new IllegalArgumentException("a change names the kind of change it was");
        }
        if (path.isBlank()) {
            throw new IllegalArgumentException("a change names the value it was made to");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("a change says in words what it was");
        }
    }
}

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

import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One concrete test to run against the API: which operation to call, and what value to send for
 * each of its inputs.
 *
 * <p>A {@code TestCase} is plain data, not generated program code: it is built once by the part of
 * RESTest that decides what to test, sent to the API unchanged, and saved unchanged afterwards.
 * Nothing about it is regenerated or reinterpreted along the way, which is what lets a saved test
 * case be re-examined, or re-sent, long after the run that created it has ended.
 *
 * <p>It refers to its operation by {@link OperationId} rather than by holding the operation itself,
 * so that a test case can be read back later without needing the whole API description that
 * produced it still in memory - the two are kept independent, the same way a saved interaction stays
 * usable independently of the run that created it.
 *
 * <p>Once built, a test case cannot be changed: there is no method to replace its parameter values or
 * body on an existing instance. Its identifier is meant to stay stable and always point at the same
 * set of values, and allowing it to be attached to different values afterwards would break that
 * guarantee.
 *
 * <p>A "stateful" step - one that depends on an earlier request, such as reading back something just
 * created - is not a different kind of test case. It is simply one whose parameter values or body
 * carry a {@link ValueOrigin.Derived} origin instead of a {@link ValueOrigin.Generated} or
 * {@link ValueOrigin.Declared} one - see {@link ValueOrigin}. A whole sequence of dependent steps is
 * just a chain of such derived values across several test cases and interactions, not something this
 * type needs to represent as a group.
 *
 * @param id this test case's identity, stable across generation, execution and storage
 * @param operation which operation this attempts to invoke
 * @param parameterValues the chosen value for each parameter this attempt supplies, in no
 *     particular order beyond what {@link #parameterValues()} returns
 * @param body the chosen request body, absent when the operation takes none or none was supplied
 */
public record TestCase(
        TestCaseId id,
        OperationId operation,
        List<ParameterValue> parameterValues,
        Optional<BodyValue> body) {

    public TestCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(parameterValues, "parameterValues");
        Objects.requireNonNull(body, "body");
        parameterValues = List.copyOf(parameterValues);
        rejectDuplicateParameterValues(parameterValues);
    }

    /** A fresh test case for the given operation, with the given parameter values and no body. */
    public static TestCase of(OperationId operation, List<ParameterValue> parameterValues) {
        return new TestCase(TestCaseId.generate(), operation, parameterValues, Optional.empty());
    }

    /** A fresh test case for the given operation, with the given parameter values and body. */
    public static TestCase of(OperationId operation, List<ParameterValue> parameterValues,
            BodyValue body) {
        return new TestCase(TestCaseId.generate(), operation, parameterValues,
                Optional.of(Objects.requireNonNull(body, "body")));
    }

    /**
     * The chosen value for the named parameter in the given location, if this test case supplies
     * one.
     *
     * <p>Matched case-sensitively on the name: unlike an HTTP header, a parameter's name is part of
     * its identity as OpenAPI declares it, and {@code id} and {@code ID} are two different
     * parameters if a document were ever to declare both. Contrast
     * {@link HttpRequestRecord#headerValues(String)}, which is deliberately case-insensitive because
     * header names are.
     */
    public Optional<ParameterValue> parameterValue(String name, ParameterLocation location) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        return parameterValues.stream()
                .filter(value -> value.location() == location && value.name().equals(name))
                .findFirst();
    }

    /**
     * A parameter identified twice in one test case is the same failure
     * {@link io.restest.core.model.Operation} refuses among its declared parameters, for the same
     * reason: {@link #parameterValue(String, ParameterLocation)} would otherwise answer with
     * whichever value was supplied first, silently discarding the other.
     */
    private static void rejectDuplicateParameterValues(List<ParameterValue> values) {
        Set<String> seen = new HashSet<>();
        for (ParameterValue value : values) {
            if (!seen.add(value.location() + " " + value.name())) {
                throw new IllegalArgumentException("the parameter '" + value.name()
                        + "' is given two values in " + value.location());
            }
        }
    }
}

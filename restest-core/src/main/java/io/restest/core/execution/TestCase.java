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
 * One concrete attempt to invoke an operation: which one, and what value for each of its inputs.
 *
 * <p>ADR-0005's whole decision is that this is data, executed directly by an HTTP client - never
 * generated source, never compiled. A {@code TestCase} is planned by the generator (M1.5), sent
 * unchanged by the engine (M1.3), and persisted unchanged by the store (M1.4); nothing about it is
 * regenerated between those steps.
 *
 * <p>It names its operation by {@link OperationId} rather than holding the
 * {@link io.restest.core.model.Operation} itself, so that a test case read back by
 * {@code restest recheck} (M3.3) does not require the {@link io.restest.core.model.ApiModel} that
 * produced it still being in memory - the two are decoupled the same way a stored interaction is
 * decoupled from the run that created it.
 *
 * <p>{@link #id()} is built with everything else already decided, and there is deliberately no
 * {@code withX} method that would let a caller change the parameter values or the body of an
 * existing instance: {@link TestCaseId}'s own contract is an identity stable across generation,
 * execution and storage, and an identity that could be attached to two different sets of values
 * would not be stable at all - it is exactly the "answers its own lookup with whichever content was
 * built first" failure {@link #rejectDuplicateParameterValues} refuses one level down.
 *
 * <p>A stateful step is not a different kind of test case. It is one whose {@link ParameterValue}s
 * or {@link #body()} carry a {@link ValueOrigin.Derived} instead of a {@link ValueOrigin.Generated}
 * or {@link ValueOrigin.Declared} - see {@link ValueOrigin}. A whole stateful *test*, in the sense
 * `docs/DESIGN.md`'s glossary uses the word - a sequence where each step depends on the last - is
 * the chain of {@code Derived} edges across several test cases and interactions, not a container
 * this type introduces.
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

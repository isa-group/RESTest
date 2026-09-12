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

import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.TestCase;
import io.restest.core.gen.GeneratedValue;
import io.restest.core.gen.ValueProvider;
import io.restest.core.gen.ValueRequest;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.NothingSchema;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.UnsupportedSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Decides what to try against an API: which operation, and what to put in every parameter of it.
 *
 * <p>This is where the tool stops needing to be told anything. Given an API's description it works
 * out which operations it can attempt at all, and for each attempt it asks the sources of values in
 * turn - the specification's own defaults and allowed values first, invention second - until every
 * parameter the API requires has something in it. Optional parameters are included some of the time
 * and left out the rest, because an API behaves differently depending on which of them arrive and a
 * tool that always sent all of them would only ever see one of those behaviours.
 *
 * <p>Not every operation can be attempted yet, and the ones that cannot are named rather than quietly
 * skipped: an operation that requires a body, one whose parameters are written in a way requests
 * cannot yet be assembled for, and one that requires a value nothing can invent are each reported
 * with the reason. A run that tests eleven of an API's twenty operations should say so.
 *
 * <p>Every generator is given a number to start from, and the same number produces the same test
 * cases in the same order. That is what makes a surprising result worth investigating: it can be
 * reproduced exactly rather than chased. Two generators in one program never affect each other.
 *
 * <p>One generator belongs to one sequence of decisions, so it is used from one thread at a time.
 */
public final class RandomTestCaseGenerator {

    /** How often a parameter the API does not require is included anyway. */
    private static final double OPTIONAL_PARAMETER_CHANCE = 0.5;

    private final ApiModel model;
    private final long seed;
    private final RandomGenerator random;
    private final ValueProvider values;
    private final List<Operation> testable;
    private final Map<OperationId, String> untestable;

    /**
     * A generator for this API, starting from a number of the system's choosing.
     *
     * @param model the API to test
     */
    public RandomTestCaseGenerator(ApiModel model) {
        this(model, RandomGeneratorFactory.getDefault().create().nextLong());
    }

    /**
     * A generator for this API that will make the same decisions every time it is given the same
     * number.
     *
     * @param model the API to test
     * @param seed the number the whole run's randomness is derived from
     */
    public RandomTestCaseGenerator(ApiModel model, long seed) {
        this.model = Objects.requireNonNull(model, "model");
        this.seed = seed;
        this.random = RandomGeneratorFactory.getDefault().create(seed);
        this.values = ValueProviderChain.of(
                new DeclaredValueProvider(random),
                new RandomValueProvider(model, random));

        List<Operation> canBeTried = new ArrayList<>();
        Map<OperationId, String> cannot = new LinkedHashMap<>();
        for (Operation operation : model.operations()) {
            Optional<String> problem = whatStandsInTheWay(operation);
            if (problem.isPresent()) {
                cannot.put(operation.id(), problem.get());
            } else {
                canBeTried.add(operation);
            }
        }
        this.testable = List.copyOf(canBeTried);
        this.untestable = Map.copyOf(cannot);
    }

    /** The number this generator's decisions come from, so a run can be repeated exactly. */
    public long seed() {
        return seed;
    }

    /** The operations that can be attempted. */
    public List<Operation> testableOperations() {
        return testable;
    }

    /** The operations that cannot be attempted yet, and why, for the run to report. */
    public Map<OperationId, String> untestableOperations() {
        return untestable;
    }

    /** Where values come from, in the order they are asked. */
    public ValueProvider values() {
        return values;
    }

    /**
     * A test case for one of the operations that can be attempted, chosen at random.
     *
     * @return the test case, or empty if this API has no operation that can be attempted at all
     */
    public Optional<TestCase> generate() {
        if (testable.isEmpty()) {
            return Optional.empty();
        }
        return generate(testable.get(random.nextInt(testable.size())));
    }

    /**
     * A test case for one particular operation.
     *
     * @param operation the operation to build a test case for
     * @return the test case, or empty if a parameter the API requires could not be given a value
     *     this time
     */
    public Optional<TestCase> generate(Operation operation) {
        Objects.requireNonNull(operation, "operation");
        List<ParameterValue> chosen = new ArrayList<>();
        for (Parameter parameter : operation.parameters()) {
            if (!parameter.required() && random.nextDouble() >= OPTIONAL_PARAMETER_CHANCE) {
                continue;
            }
            Optional<GeneratedValue> value = values.offer(new ValueRequest(operation.id(),
                    parameter.name(), parameter.location(), resolved(parameter.schema())));
            if (value.isPresent()) {
                chosen.add(ParameterValue.of(parameter.name(), parameter.location(),
                        value.get().value(), value.get().origin()));
            } else if (parameter.required()) {
                return Optional.empty();
            }
        }
        return Optional.of(TestCase.of(operation.id(), chosen));
    }

    /**
     * Why this operation cannot be attempted, if it cannot.
     *
     * <p>Three things stand in the way today, and each is a limit of what has been built rather than
     * a fault in the specification: a body that must be sent, since bodies are not invented yet; a
     * parameter written down in a way requests cannot be assembled for; and a required parameter whose
     * shape allows no value or that the specification described in a way the parser could not read.
     */
    private Optional<String> whatStandsInTheWay(Operation operation) {
        if (operation.requiresBody()) {
            return Optional.of("it requires a request body, and bodies are not invented yet");
        }
        Optional<String> unassemblable = RequestBuilder.whatCannotBeAssembled(operation);
        if (unassemblable.isPresent()) {
            return unassemblable;
        }
        for (Parameter parameter : operation.parameters()) {
            if (!parameter.required()) {
                continue;
            }
            CanonicalSchema schema = resolved(parameter.schema());
            if (schema instanceof NothingSchema) {
                return Optional.of("the parameter '" + parameter.name() + "' is required and its "
                        + "description allows no value at all");
            }
            if (schema instanceof UnsupportedSchema unsupported) {
                return Optional.of("the parameter '" + parameter.name() + "' is required and its "
                        + "description could not be read: " + unsupported.reason());
            }
        }
        return Optional.empty();
    }

    /**
     * The shape itself, when the specification refers to one it defined elsewhere by name.
     *
     * <p>Only the first step is followed here. A name pointing at another name is followed by whoever
     * invents the value, which is also where a shape that contains itself is stopped from being
     * followed for ever.
     */
    private CanonicalSchema resolved(CanonicalSchema schema) {
        return schema instanceof SchemaReference reference
                ? model.resolve(reference).orElse(schema)
                : schema;
    }
}

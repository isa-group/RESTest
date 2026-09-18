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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

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
 * <p>Every generator is given a number to start from, and the same number produces the same
 * requests, carrying the same values, in the same order, on any machine and on any Java runtime.
 * That is what makes a surprising result worth investigating: it can be reproduced exactly rather
 * than chased. What is not repeated is the label each test case is filed under, which is drawn
 * fresh every time so that two runs happening at once cannot both claim the same one. Two
 * generators in one program never affect each other.
 *
 * <p>The randomness comes from a source every Java runtime is required to carry, on every release,
 * rather than from the best one a particular runtime happens to offer. Choosing by name would make
 * both halves of that promise conditional: the same number would mean one thing where the named
 * source is installed and something else where it is not, and on a runtime carrying only the
 * compulsory parts of an older Java - a small container image, say - the tool would refuse to start
 * at all.
 *
 * <p>That promise holds for as long as nothing this generator uses remembers what the API has been
 * answering. It is true of everything here today. It will stop being true of a source of values that
 * learns from the responses - one that reuses an identifier it saw in an earlier reply, say - because
 * then what gets chosen depends on when each answer arrived, which depends on the network. The way to
 * reproduce a run like that is not to run it again from the same number, but to send the stored
 * requests again, which is why every request is kept.
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
    private final List<Strategy> strategies;
    private final int sharesInTotal;
    private final List<Operation> testable;
    private final Map<OperationId, String> untestable;

    /**
     * A generator for this API, starting from a number of the system's choosing.
     *
     * @param model the API to test
     */
    public RandomTestCaseGenerator(ApiModel model) {
        this(model, new SplittableRandom().nextLong());
    }

    /**
     * A generator for this API using only the dictionaries that travel with the tool.
     *
     * @param model the API to test
     * @param seed the number the whole run's randomness is derived from
     */
    public RandomTestCaseGenerator(ApiModel model, long seed) {
        this(model, seed, Dictionaries.fuzzing().map(List::of).orElse(List.of()));
    }

    /**
     * A generator for this API using these lists of values, with the usual share of its requests
     * built to be refused.
     *
     * @param model the API to test
     * @param seed the number the whole run's randomness is derived from
     * @param dictionaries the lists of values to draw on
     */
    public RandomTestCaseGenerator(ApiModel model, long seed, List<Dictionary> dictionaries) {
        this(model, seed, dictionaries, AWKWARD_SHARE);
    }

    /**
     * A generator for this API that will make the same decisions every time it is given the same
     * number.
     *
     * @param model the API to test
     * @param seed the number the whole run's randomness is derived from
     */
    public RandomTestCaseGenerator(ApiModel model, long seed, List<Dictionary> dictionaries,
            int awkwardShare) {
        this.model = Objects.requireNonNull(model, "model");
        if (awkwardShare < 0 || awkwardShare > 100) {
            throw new IllegalArgumentException("the share of requests built to be refused is a "
                    + "percentage, so it is between 0 and 100: " + awkwardShare);
        }
        this.seed = seed;
        Objects.requireNonNull(dictionaries, "dictionaries");
        // Built directly rather than asked for by the name of an algorithm. The better-sounding
        // names - L64X128MixRandom and the rest of that family - are only compulsory from Java 23
        // on. On 21 and 22 they live in a separate, optional module, jdk.random, which the official
        // eclipse-temurin JRE images for those two releases do not carry, and neither does anything
        // jlink produces unless it is asked. Since 21 is the oldest release this tool supports,
        // asking by name means the tool cannot start on the oldest runtime it promises to run on.
        // This one is in java.base on every release, which every runtime has by definition.
        this.random = new SplittableRandom(seed);
        // What the document itself says about a value, in the order of preference the document's own
        // words imply: a closed list of accepted values leaves nothing to choose; a sample the author
        // wrote down is a value that worked against a real API; a default is what the API uses when
        // the caller says nothing. The same list answers questions about anything nested inside a
        // value as well as about the value itself, which is why it is built once and shared.
        ValueProvider fromTheDocument = ValueProviderChain.of(
                new ExampleValueProvider(random),
                new DeclaredValueProvider(random));
        ValueProvider invention = new RandomValueProvider(model, random, fromTheDocument);
        this.values = ValueProviderChain.of(fromTheDocument, invention);
        this.strategies = strategiesFor(dictionaries, awkwardShare, this.values, invention, random);
        this.sharesInTotal = this.strategies.stream().mapToInt(Strategy::share).sum();

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
        // Not Map.copyOf. That one's iteration order is randomised per process, so the same
        // document would list the operations it cannot test in a different order every run - and
        // the command line quotes the first of them as the example of what went wrong, meaning the
        // same command would explain itself differently each time it was run. The map is built here
        // and never handed anywhere else, so wrapping it keeps document order without a second copy.
        this.untestable = Collections.unmodifiableMap(cannot);
    }

    /**
     * The number this generator's decisions come from.
     *
     * <p>Worth recording with the results even when a run cannot be repeated from it: an odd result
     * among ten repetitions is worth going back to, and going back to it means generating again
     * rather than sending the same requests again.
     */
    public long seed() {
        return seed;
    }

    /** The operations that can be attempted. */
    public List<Operation> testableOperations() {
        return testable;
    }

    /**
     * The operations that cannot be attempted yet, and why, for the run to report - in the order the
     * document declares them, so that whoever runs the same command twice is told the same thing
     * twice.
     */
    public Map<OperationId, String> untestableOperations() {
        return untestable;
    }

    /** Where values come from, in the order they are asked. */
    public ValueProvider values() {
        return values;
    }

    /**
     * The names of the lists of values this run will send expecting them to be refused.
     *
     * <p>A report wants these so it can say how many of a run's refusals were asked for. Without
     * them, a run that deliberately sends awkward values reads as though the API were turning away
     * far more ordinary requests than it is.
     *
     * @return the names, empty when this run sends nothing it expects to be refused
     */
    public java.util.Set<String> sourcesExpectingRefusal() {
        return strategies.stream()
                .filter(way -> !way.name().equals("nominal"))
                .map(Strategy::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * A test case for one of the operations that can be attempted, chosen at random.
     *
     * @return the test case, or empty if this API has no operation that can be attempted at all, or
     *     if the one chosen this time could not be filled in
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
        if (untestable.containsKey(operation.id())) {
            // Asked for one of the operations this generator has already said it cannot attempt.
            // Building a test case anyway would produce a request nobody could send - a write with
            // no body, or a parameter written in a way the request cannot carry.
            return Optional.empty();
        }
        return fill(operation);
    }

    private Optional<TestCase> fill(Operation operation) {
        return fill(operation, nextStrategy());
    }

    private Optional<TestCase> fill(Operation operation, Strategy strategy) {
        List<ParameterValue> chosen = new ArrayList<>();
        for (Parameter parameter : operation.parameters()) {
            if (!parameter.required() && random.nextDouble() >= OPTIONAL_PARAMETER_CHANCE) {
                continue;
            }
            Optional<GeneratedValue> value = strategy.values().offer(ask(operation, parameter));
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
     * parameter written down in a way requests cannot be assembled for; and a required parameter no
     * value can be found for - because its description allows none, because the parser could not
     * read it, or because nothing available knows how to satisfy it.
     *
     * <p>That last one is decided by trying, once, rather than by reasoning about the shape. Shapes
     * that defeat the sources of values are not a list anybody can write down in advance, and an
     * operation quietly failing to produce a test case on every attempt - while being reported as
     * testable - is the outcome this check exists to prevent.
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
            if (values.offer(ask(operation, parameter)).isEmpty()) {
                return Optional.of("no value could be found for the required parameter '"
                        + parameter.name() + "'");
            }
        }
        return Optional.empty();
    }

    /**
     * How much of the testing time goes on requests built from values chosen to be awkward, unless
     * somebody says otherwise.
     *
     * <p>A quarter against three is a starting point rather than a measurement. It is the one
     * number here that has to be settled by running campaigns rather than by argument, and it lives
     * in one place so that settling it is a one-line change.
     */
    public static final int AWKWARD_SHARE = 25;

    /**
     * The ways this run will put a request together, and how the time divides between them.
     *
     * <p>Most of it goes on requests meant to be accepted. The rest goes on requests built entirely
     * from a dictionary of values designed to be refused - every parameter at once, on purpose,
     * because an API stops reading at the first thing it does not like and there is no sense
     * pretending a refusal could be pinned on any one of them. What such a request is good for is
     * the other answer: a server error is a fault whatever was sent, and unexpected input is how
     * they are found.
     *
     * <p>Three quarters against one is a starting point, not a measurement. It is the one number
     * here that has to be settled by running campaigns rather than by argument, and it lives in one
     * place so that settling it is a one-line change.
     *
     * <p>A dictionary that expects its values to be refused earns a way of building requests of its
     * own. One that does not is offered alongside the document's own answers instead, because its
     * values are meant to work.
     */
    private static List<Strategy> strategiesFor(List<Dictionary> dictionaries, int awkwardShare,
            ValueProvider nominal, ValueProvider invention, RandomGenerator random) {
        List<Strategy> ways = new ArrayList<>();
        ways.add(new Strategy("nominal", 100 - awkwardShare, nominal));
        if (awkwardShare == 0) {
            return List.copyOf(ways);
        }
        List<Dictionary> refusing = dictionaries.stream()
                .filter(held -> held.expects() == Dictionary.Expectation.REFUSAL)
                .toList();
        for (Dictionary dictionary : refusing) {
            // Shared between them rather than given to each, so asking for a quarter means a
            // quarter whether one list of awkward values is in play or three.
            ways.add(new Strategy(dictionary.name(), Math.max(1, awkwardShare / refusing.size()),
                    ValueProviderChain.of(new DictionaryValueProvider(dictionary, random),
                            invention)));
        }
        return List.copyOf(ways);
    }

    /**
     * Which way this request is being built.
     *
     * <p>Drawn once per request rather than once per value, because the whole point of the division
     * is that one request is built one way throughout.
     */
    private Strategy nextStrategy() {
        if (strategies.size() == 1) {
            return strategies.get(0);
        }
        int drawn = random.nextInt(sharesInTotal);
        for (Strategy way : strategies) {
            drawn -= way.share();
            if (drawn < 0) {
                return way;
            }
        }
        return strategies.get(strategies.size() - 1);
    }

    /**
     * What to ask the sources of values about one parameter of one operation.
     *
     * <p>The parameter's own sample values travel with the question rather than being folded into
     * the shape, because a shape the document named once may be used by dozens of parameters and a
     * sample belongs to the one that declared it.
     */
    private ValueRequest ask(Operation operation, Parameter parameter) {
        // The name survives being resolved: a shape the document declared once and named is worth
        // recognising again, because whoever keeps a list of values that worked for an Owner wants
        // to be asked about an Owner, not about "an object with four properties".
        Optional<String> shape = parameter.schema() instanceof SchemaReference reference
                ? Optional.of(reference.name()) : Optional.empty();
        return new ValueRequest(operation.id(), parameter.name(), parameter.location(),
                resolved(parameter.schema()), parameter.examples(), shape);
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

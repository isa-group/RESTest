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

import io.restest.core.execution.BodyValue;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.TestCase;
import io.restest.core.gen.GeneratedValue;
import io.restest.core.gen.ValueProvider;
import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.BodyContent;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.RequestBodyModel;
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
import java.util.stream.Stream;

/**
 * Decides what to try against an API: which operation, and what to put in every parameter of it.
 *
 * <p>This is where the tool stops needing to be told anything. Given an API's description it works
 * out which operations it can attempt at all, and for each attempt it fills in every parameter the
 * API requires. Optional parameters are included some of the time and left out the rest, because an
 * API behaves differently depending on which of them arrive and a tool that always sent all of them
 * would only ever see one of those behaviours.
 *
 * <p>Where the values come from is not decided here any more. A <em>plan</em> says it - which
 * sources are asked, in what order, which of them are chosen among rather than ranked, how much of
 * the run goes on requests meant to work as against requests meant to push at the API, and which of
 * the API's operations may be touched at all. A run given no plan of its own follows the one
 * RESTest carries. Everything this class does with a plan is turn its names into the things that
 * answer: a list of values somebody wrote, the samples in the document, a value invented to fit the
 * shape.
 *
 * <p>Operations that take a body get one: the shape the document declares is filled in the same way
 * every other value is, so creating and updating things is tested rather than skipped. The body is
 * sent as JSON where the document offers it and as the fields of a web form otherwise.
 *
 * <p>Not every operation can be attempted even so, and the ones that cannot are named rather than
 * quietly skipped: one whose parameters are written in a way requests cannot yet be assembled for,
 * one that requires a value nothing can invent, and one whose body is only offered in a form this
 * cannot write - a file upload, say - are each reported with the reason. A run that tests eleven of
 * an API's twenty operations should say so.
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

    /** How many bodies are drawn while looking for one that can be written as its media type. */
    private static final int WRITABLE_BODY_ATTEMPTS = 8;

    /**
     * The name of the list of values to push at an API with.
     *
     * <p>A plan names the lists each way of building a request draws on, so a run can push with
     * two lists, or push with one and fill ordinary requests from another. This is the name the
     * one RESTest carries answers to, and the one a user adds to by handing over a list of their
     * own called the same thing. It is also what makes a strategy one that pushes: a strategy is
     * pushing because of the values it draws on, not because anything says so.
     */
    static final String PUSHES_AT_THE_API = "fuzzing";

    /**
     * How much of the testing time goes on requests built from values chosen to be awkward, unless
     * somebody says otherwise.
     *
     * <p>A quarter against three is a starting point rather than a measurement. It is the one
     * number here that has to be settled by running campaigns rather than by argument, and it lives
     * in one place so that settling it is a one-line change.
     */
    public static final int AWKWARD_SHARE = 25;

    private final ApiModel model;
    private final long seed;
    private final RandomGenerator random;
    private final ValueProvider values;
    private final List<Dictionary> given;
    private final Campaign campaign;
    private final List<Strategy> strategies;
    private final int sharesInTotal;
    private final List<Operation> testable;
    private final Map<OperationId, String> untestable;
    private final List<OperationId> setAsideByThePlan;

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
     * A generator for this API, using these lists of values and spending this much of its time on
     * requests built to be refused.
     *
     * <p>A way of saying one thing about the plan RESTest carries without writing a plan: how much
     * of the run goes on pushing at the API. Everything else about it is left as it is.
     *
     * @param model the API to test
     * @param seed the number the whole run's randomness is derived from
     * @param dictionaries the lists of values to draw on
     * @param awkwardShare how much of the time, as a percentage, goes on requests built entirely
     *     from values meant to be refused. Nought sends none of them
     * @throws IllegalArgumentException if that is not a percentage
     */
    public RandomTestCaseGenerator(ApiModel model, long seed, List<Dictionary> dictionaries,
            int awkwardShare) {
        this(model, seed, dictionaries, carriedPlanPushing(awkwardShare));
    }

    /**
     * A generator for this API, following the plan it is given.
     *
     * <p>The plan says where values come from and how much of the run goes on each way of building
     * a request. The same number produces the same decisions every time, as long as no source the
     * plan names has a memory of what the API has already answered.
     *
     * @param model the API to test
     * @param seed the number the whole run's randomness is derived from
     * @param dictionaries the lists of values to draw on, the plan deciding which are used where
     * @param campaign the plan to follow
     */
    public RandomTestCaseGenerator(ApiModel model, long seed, List<Dictionary> dictionaries,
            Campaign campaign) {
        this.model = Objects.requireNonNull(model, "model");
        Objects.requireNonNull(campaign, "campaign");
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
        this.given = List.copyOf(dictionaries);
        this.campaign = campaign;
        this.strategies = strategiesFor(campaign, dictionaries, model, random);
        this.sharesInTotal = this.strategies.stream().mapToInt(Strategy::share).sum();
        // Whether an operation can be tested at all is asked of an ordinary way of building a
        // request, never of one built to push at the API: a list of awkward values answers for
        // almost anything, so asking it would count an operation testable and then leave it with
        // nothing for the three quarters of requests built the ordinary way. The plan may list its
        // pushing strategy first, so this is chosen by what a strategy is rather than by position.
        this.values = this.strategies.stream()
                .filter(way -> !way.pushesAtTheApi())
                .findFirst()
                .orElse(this.strategies.get(0))
                .values();

        List<Operation> canBeTried = new ArrayList<>();
        Map<OperationId, String> cannot = new LinkedHashMap<>();
        List<OperationId> setAside = new ArrayList<>();
        for (Operation operation : model.operations()) {
            // Kept apart from the ones that cannot be tested, because they are not the same thing
            // and a run that ran them together would be telling somebody their document was wrong
            // when in fact their plan said not to bother.
            if (!campaign.operations().matches(operation)) {
                setAside.add(operation.id());
                continue;
            }
            Optional<String> problem = whatStandsInTheWay(operation);
            if (problem.isPresent()) {
                cannot.put(operation.id(), problem.get());
            } else {
                canBeTried.add(operation);
            }
        }
        this.setAsideByThePlan = List.copyOf(setAside);
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

    /**
     * The lists this run was given and will not draw a single value from.
     *
     * <p>One thing puts a list here: being named for pushing at the API in a run told to do no
     * pushing. Somebody has asked for two things that cancel, and one of them is probably a
     * mistake - which is exactly when saying so is worth the line. Named once however many lists
     * answer to it, since the name is all anybody could act on.
     *
     * @return the names, empty when every list given will be used
     */
    public java.util.List<String> listsGivenButNotUsed() {
        if (!sourcesThatPushAtTheApi().isEmpty()) {
            return List.of();
        }
        return given.stream()
                .map(Dictionary::name)
                .filter(PUSHES_AT_THE_API::equals)
                .distinct()
                .toList();
    }

    /**
     * The operations this run's plan told it to leave alone.
     *
     * <p>Different from the ones that cannot be tested, and worth keeping apart: those are
     * something wrong with the document, these are something somebody asked for. A run that
     * confused the two would report a plan working exactly as intended as a problem with the API.
     *
     * @return them, in the order the document declares them, empty when the plan set none aside
     */
    public List<OperationId> operationsThePlanSetAside() {
        return setAsideByThePlan;
    }

    /** The plan this run is following. */
    public Campaign campaign() {
        return campaign;
    }

    /** Where values come from, in the order they are asked. */
    public ValueProvider values() {
        return values;
    }

    /**
     * The names of the lists this run draws on when it is pushing at the API rather than trying to
     * work.
     *
     * <p>A report wants these so it can say how many requests were of that kind. Without them, a run
     * that spends a quarter of its time sending values nobody sensible would send reads as though
     * the API were turning away far more ordinary traffic than it is.
     *
     * <p>The names of the <em>lists</em>, which is what a report was told each value came from -
     * not the names of the strategies drawing on them, which a plan may call anything.
     *
     * @return the names, empty when this run sends nothing of the kind
     */
    public java.util.Set<String> sourcesThatPushAtTheApi() {
        // The names of the lists, not of the strategies that draw on them. A report matches these
        // against what it was told each value came from, which is the list's name - so a plan
        // whose pushing strategy is called something else would otherwise have every one of its
        // requests counted as ordinary.
        return strategies.stream()
                .filter(Strategy::pushesAtTheApi)
                .flatMap(way -> given.stream().map(Dictionary::name)
                        .filter(PUSHES_AT_THE_API::equals))
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
            // Building a test case anyway would produce a request nobody could send - a body that
            // cannot be written, or a parameter written in a way the request cannot carry.
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
        Optional<RequestBodyModel> declared = operation.requestBody();
        if (declared.isEmpty()) {
            return Optional.of(TestCase.of(operation.id(), chosen));
        }
        Optional<BodyValue> body = body(operation, declared.get(), strategy);
        if (body.isEmpty()) {
            // An API that says it needs a body will refuse a request without one whatever else is
            // in it, so there is nothing to learn from sending it.
            return declared.get().required()
                    ? Optional.empty() : Optional.of(TestCase.of(operation.id(), chosen));
        }
        return Optional.of(TestCase.of(operation.id(), chosen, body.get()));
    }

    /**
     * The body to send with this request, if one is to be sent at all.
     *
     * <p>A body the API insists on is always sent. One it merely accepts is sent as often as an
     * optional parameter is included, because an operation behaves differently depending on whether
     * a body arrived, and a tool that always sent one would only ever see one of those behaviours.
     */
    private Optional<BodyValue> body(Operation operation, RequestBodyModel declared,
            Strategy strategy) {
        if (!declared.required() && random.nextDouble() >= OPTIONAL_PARAMETER_CHANCE) {
            return Optional.empty();
        }
        Optional<String> mediaType = RequestBuilder.mediaTypeToSend(declared);
        if (mediaType.isEmpty()) {
            return Optional.empty();
        }
        return writableBody(operation, declared, mediaType.get(), strategy.values())
                .map(value -> new BodyValue(mediaType.get(), value.value(), value.origin()));
    }

    /**
     * A body for this media type that can actually be written out as it.
     *
     * <p>Drawn more than once, because whether it can is a property of the value rather than of the
     * shape: a document describing a web form as a shape that allows anything is describing one
     * that is sometimes an object with fields to name and sometimes a bare word with none. Asking
     * once would report such an operation as untestable whenever the first draw came out badly.
     * The same eight attempts invention already makes when it is looking for a value a request
     * could carry.
     */
    private Optional<GeneratedValue> writableBody(Operation operation, RequestBodyModel declared,
            String mediaType, ValueProvider from) {
        for (int attempt = 0; attempt < WRITABLE_BODY_ATTEMPTS; attempt++) {
            Optional<GeneratedValue> offered = from.offer(askForBody(operation, declared,
                    mediaType));
            if (offered.isEmpty()) {
                return Optional.empty();
            }
            if (canBeWrittenAs(mediaType, offered.get().value())) {
                return offered;
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a value can be written out as this media type at all.
     *
     * <p>The fields of a web form are the members of an object, and there is nothing else to name
     * them after; a document describing such a body as anything but an object describes a request
     * nobody could send. Asked of the value rather than of the shape, because a shape allowing
     * anything can produce either.
     */
    private static boolean canBeWrittenAs(String mediaType, JsonValue value) {
        return !RequestBuilder.isForm(mediaType) || value instanceof JsonValue.JsonObject;
    }

    /**
     * Why this operation cannot be attempted, if it cannot.
     *
     * <p>Three things stand in the way today, and each is a limit of what has been built rather than
     * a fault in the specification: a body that must be sent and cannot be written or filled in; a
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
        Optional<String> body = whatStandsInTheWayOfTheBody(operation);
        if (body.isPresent()) {
            return body;
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
            // Asked of the ordinary way of building a request, always - even in a run spending all
            // its time on values meant to be refused. The question is whether a value anybody
            // believes in can be found for this parameter, and a list of awkward values answers for
            // anything at all: a word of no letters satisfies nothing, and offering it here would
            // report every operation as testable whatever its document said.
            if (values.offer(ask(operation, parameter)).isEmpty()) {
                return Optional.of("no value could be found for the required parameter '"
                        + parameter.name() + "'");
            }
        }
        return Optional.empty();
    }

    /**
     * Why an operation's body stands in the way of attempting it, if it does.
     *
     * <p>Only a body the API insists on can stand in the way. One it merely accepts is left out of
     * the requests that cannot carry it, and the operation is tested without it - which is a request
     * the document says is legitimate.
     *
     * <p>Three things stop a required body: it is only offered in a shape this cannot write, such as
     * a file upload; its description allows no value at all, or could not be read; or nothing has a
     * value to offer for it. The last is decided by trying once, exactly as a required parameter is.
     */
    private Optional<String> whatStandsInTheWayOfTheBody(Operation operation) {
        if (!operation.requiresBody()) {
            return Optional.empty();
        }
        RequestBodyModel declared = operation.requestBody().orElseThrow();
        Optional<String> mediaType = RequestBuilder.mediaTypeToSend(declared);
        if (mediaType.isEmpty()) {
            return Optional.of("it requires a request body, and the only way it is offered is "
                    + String.join(", ", declared.mediaTypes()) + ", which cannot be written yet");
        }
        CanonicalSchema schema =
                resolved(declared.contentFor(mediaType.get()).orElseThrow().schema());
        if (schema instanceof NothingSchema) {
            return Optional.of("it requires a request body and the description of that body allows "
                    + "no value at all");
        }
        if (schema instanceof UnsupportedSchema unsupported) {
            return Optional.of("it requires a request body and the description of that body could "
                    + "not be read: " + unsupported.reason());
        }
        // Asked of the ordinary way of building a request, for the same reason a parameter is: the
        // question is whether a body anybody believes in can be found, and a list of awkward values
        // answers for anything at all.
        // Asked of the value rather than of the shape. A document describing a web form as anything
        // but an object describes a request with no fields to name - but a shape that allows
        // several things, or anything at all, can still produce an object, and refusing those on
        // sight would skip operations this can perfectly well test.
        if (writableBody(operation, declared, mediaType.get(), values).isPresent()) {
            return Optional.empty();
        }
        // Nothing usable came out, and there are two reasons that can happen. Told apart by asking
        // once more, and only on this path: a body nobody has a value for at all, or - for a web
        // form alone, since anything at all can be written as JSON - values that keep coming out
        // as something with no fields to name.
        if (RequestBuilder.isForm(mediaType.get())
                && values.offer(askForBody(operation, declared, mediaType.get())).isPresent()) {
            return Optional.of("it requires a request body sent as the fields of a web form, and "
                    + "what can be built for that body is not an object, so there are no fields to "
                    + "name");
        }
        return Optional.of("no value could be found for the request body this operation requires");
    }

    /**
     * The plan RESTest carries, with one thing said about it: how much of the run goes on pushing.
     *
     * <p>What {@code --fuzzing} means. Rather than a second way of arranging sources, it adjusts
     * the shares of the plan already there - so the option and the file cannot come to disagree
     * about anything except the one number the option is about.
     */
    private static Campaign carriedPlanPushing(int awkwardShare) {
        if (awkwardShare < 0 || awkwardShare > Campaign.WHOLE) {
            throw new IllegalArgumentException("the share of requests built to be refused is a "
                    + "percentage, so it is between 0 and " + Campaign.WHOLE + ": " + awkwardShare);
        }
        return Campaigns.carried().withTheShareOfPushingSetTo(awkwardShare);
    }

    /**
     * One working strategy per strategy the plan describes.
     *
     * <p>This is where a plan stops being a document and starts being the thing that fills in
     * requests: each source it names by word is found, each list it names by name is looked up
     * among the ones this run was handed, and the arrangement it wrote them in is built.
     */
    private static List<Strategy> strategiesFor(Campaign campaign, List<Dictionary> dictionaries,
            ApiModel model, RandomGenerator random) {
        List<Strategy> ways = new ArrayList<>();
        for (Campaign.PlannedStrategy planned : campaign.strategies()) {
            if (nothingLeftButInvention(planned, dictionaries)) {
                continue;
            }
            ways.add(new Strategy(planned.name(), planned.share(), planned.pushesAtTheApi(),
                    valuesFor(planned, dictionaries, model, random)));
        }
        if (ways.isEmpty()) {
            // Every strategy asked for lists nobody handed over. Rather than a run that builds no
            // requests, the plainest plan there is - and Campaigns has already said, by name, which
            // lists were missing.
            return strategiesFor(Campaigns.plainest(), dictionaries, model, random);
        }
        // The shares are counted against each other rather than out of a hundred, so dropping one
        // strategy leaves the rest in the same proportion to one another as the plan wrote them.
        return List.copyOf(ways);
    }

    /**
     * Whether a strategy would have nothing left but invention.
     *
     * <p>A strategy naming lists nobody handed over is not automatically pointless: one that also
     * asks the document what it says still has most of its job. What is pointless is one with
     * nothing left but inventing a value to fit the shape - which is what the plan RESTest carries
     * comes to when a run is given no list to push with. That strategy would spend a quarter of the
     * budget on ordinary invented values under another name, and, having no step for the closed
     * list of values a document states, would send values the document says are not allowed.
     *
     * <p>Left out instead, and the remaining shares keep the proportions the plan wrote them in.
     */
    private static boolean nothingLeftButInvention(Campaign.PlannedStrategy planned,
            List<Dictionary> dictionaries) {
        List<Campaign.Source> surviving = planned.sources().stream()
                .flatMap(entry -> entry.sources().stream())
                .filter(source -> switch (source) {
                    case Campaign.Source.OneList list -> dictionaries.stream()
                            .anyMatch(held -> held.name().equals(list.name()));
                    case Campaign.Source.EveryListGiven ignored -> dictionaries.stream()
                            .anyMatch(held -> !held.name().equals(PUSHES_AT_THE_API));
                    case Campaign.Source.Builtin ignored -> true;
                })
                .toList();
        return surviving.stream().allMatch(source ->
                source instanceof Campaign.Source.Builtin builtin
                        && builtin.which() == Campaign.Builtin.RANDOM);
    }

    /**
     * Where one strategy's values come from.
     *
     * <p>Built twice, because invention is the one source that needs the others. Putting a value
     * together from its shape means asking about everything nested inside it, and what should
     * answer those questions is the rest of this same strategy - so the strategy without invention
     * is built first, handed to invention, and then the whole thing is built again with invention
     * in the place the plan put it.
     */
    private static ValueProvider valuesFor(Campaign.PlannedStrategy planned,
            List<Dictionary> dictionaries, ApiModel model, RandomGenerator random) {
        ValueProvider knownValues = asPlanned(planned, dictionaries, random, null);
        ValueProvider invention = new RandomValueProvider(model, random, knownValues);
        return asPlanned(planned, dictionaries, random, invention);
    }

    /**
     * The sources of one strategy, in the arrangement the plan wrote.
     *
     * @param invention what answers where the plan asks for an invented value, or {@code null} to
     *     leave those steps out - which is how the list invention itself consults is built
     */
    private static ValueProvider asPlanned(Campaign.PlannedStrategy planned,
            List<Dictionary> dictionaries, RandomGenerator random, ValueProvider invention) {
        List<ValueProvider> asked = new ArrayList<>();
        for (Campaign.Entry entry : planned.sources()) {
            switch (entry) {
                case Campaign.Entry.Single single ->
                    built(single.source(), dictionaries, random, invention).ifPresent(asked::add);
                case Campaign.Entry.Group group -> {
                    List<WeightedGroup.Weighted> among = new ArrayList<>();
                    for (Campaign.Share share : group.among()) {
                        built(share.source(), dictionaries, random, invention).ifPresent(source ->
                                among.add(new WeightedGroup.Weighted(source, share.weight())));
                    }
                    // A group whose sources all turned out to be absent is no group at all, and one
                    // with a single source left is simply that source: there is nothing to choose
                    // between. Both happen for ordinary reasons - a plan naming a list nobody
                    // handed over, or the group that invention itself is being built without.
                    if (among.size() == 1) {
                        asked.add(among.get(0).source());
                    } else if (!among.isEmpty()) {
                        asked.add(new WeightedGroup(among, random));
                    }
                }
            }
        }
        return ValueProviderChain.of(asked);
    }

    /**
     * What answers for one source a plan names, or nothing when nothing does.
     *
     * <p>Nothing when the plan asks for a list nobody handed over, or for invention while invention
     * is still being built.
     *
     * <p>One answerer even where several lists are meant, and that matters: a step in a plan
     * carries one weight, and handing back three answerers would quietly give that step three
     * times its say. Several lists become one by being asked in turn.
     */
    private static Optional<ValueProvider> built(Campaign.Source source,
            List<Dictionary> dictionaries, RandomGenerator random, ValueProvider invention) {
        return switch (source) {
            case Campaign.Source.Builtin builtin -> switch (builtin.which()) {
                case ENUM -> Optional.of(DeclaredValueProvider.onlyTheAcceptedList(random));
                case EXAMPLE -> Optional.of(new ExampleValueProvider(random));
                case DEFAULT -> Optional.of(DeclaredValueProvider.onlyTheStatedDefault(random));
                case RANDOM -> Optional.ofNullable(invention);
            };
            case Campaign.Source.OneList list -> asOne(dictionaries.stream()
                    .filter(held -> held.name().equals(list.name())).toList(), random);
            // Every list except one for pushing at the API. Those were gathered for a different
            // job, and sending their values as though somebody believed in them is not what
            // anybody asked for - which is why a strategy that wants them names them.
            //
            // Asked most particular first, which is the one piece of ranking that does not come
            // from the plan: a list somebody wrote for one parameter of one operation knows more
            // about that value than a list of every date in the world, and no plan should have to
            // say so.
            case Campaign.Source.EveryListGiven ignored -> asOne(Stream.concat(
                    handedOver(dictionaries).filter(Dictionary::isAboutOneValueInParticular),
                    handedOver(dictionaries).filter(held -> !held.isAboutOneValueInParticular()))
                    .toList(), random);
        };
    }

    /** The lists this run was handed, which is every one that is not for pushing with. */
    private static Stream<Dictionary> handedOver(List<Dictionary> dictionaries) {
        return dictionaries.stream().filter(held -> !held.name().equals(PUSHES_AT_THE_API));
    }

    /** Several lists as one answerer, asked in the order given, or nothing when there are none. */
    private static Optional<ValueProvider> asOne(List<Dictionary> lists, RandomGenerator random) {
        if (lists.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ValueProviderChain.of(lists.stream()
                .map(held -> (ValueProvider) new DictionaryValueProvider(held, random)).toList()));
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
        return new ValueRequest(operation.id(), parameter.name(), parameter.name(),
                parameter.location(), resolved(parameter.schema()), parameter.examples(), shape);
    }

    /**
     * What to ask the sources of values about the body of a request.
     *
     * <p>The same question a parameter gets, with the body's shape and the samples the document
     * wrote beside that media type - a whole body an author showed working, which is the most
     * valuable thing a document can offer here. The shape's name travels too, so that a list of
     * values written for an {@code Owner} answers when an {@code Owner} is what the API wants sent.
     */
    private ValueRequest askForBody(Operation operation, RequestBodyModel body, String mediaType) {
        BodyContent content = body.contentFor(mediaType).orElseThrow();
        Optional<String> shape = content.schema() instanceof SchemaReference reference
                ? Optional.of(reference.name()) : Optional.empty();
        return new ValueRequest(operation.id(), ValueRequest.THE_BODY, ValueRequest.THE_BODY,
                ParameterLocation.BODY, resolved(content.schema()), content.examples(), shape);
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

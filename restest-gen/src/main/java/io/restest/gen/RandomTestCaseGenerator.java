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

import io.restest.core.event.RunListener;
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
import io.restest.core.settings.GenerationSettings;
import io.restest.core.settings.Settings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
 * would only ever see one of those behaviours. How many of them arrive on any one request is decided
 * before which ones are, and decided to favour few: the request that asks for nothing beyond what
 * the API strictly requires stays one this sends often, whatever else the operation happens to
 * offer, rather than one that becomes rarer the more of them there are to choose from.
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
 * <p>It can also build, for any operation, the one request that operation is most likely to accept,
 * which is what a run sends each operation first. That request carries everything the API requires
 * and nothing it does not, except a body wherever the document describes one, because an operation
 * that takes a body rarely works without it whatever the document says. Its values come from the
 * same sources as an ordinary request, asked one after another instead of chosen among, in the order
 * most likely to be accepted: the closed list of values the document accepts, where it states one;
 * then a value the API has already handed back, then a list somebody wrote, then the document's own
 * sample, then its default, and only then one invented to fit. Building it
 * draws on numbers of its own, so asking for it changes nothing about the ordinary requests that
 * follow.
 *
 * <p>Not every operation can be attempted even so, and the ones that cannot are named rather than
 * quietly skipped: one whose parameters are written in a way requests cannot yet be assembled for,
 * one that requires a value nothing can invent, one whose body is only offered in a form this
 * cannot write - a file upload, say - and a {@code GET} or a {@code HEAD} that insists on a body,
 * which the client that sends requests refuses to build, are each reported with the reason. A run
 * that tests eleven of an API's twenty operations should say so.
 *
 * <p>Every generator is given a number to start from, and the same number produces the same
 * decisions, on any machine and on any Java runtime. That is what makes a surprising result worth
 * investigating: it can be reproduced rather than chased. What is not repeated is the label each
 * test case is filed under, which is drawn fresh every time so that two runs happening at once
 * cannot both claim the same one. Two generators in one program never affect each other.
 *
 * <p>The randomness comes from a source every Java runtime is required to carry, on every release,
 * rather than from the best one a particular runtime happens to offer. Choosing by name would make
 * both halves of that promise conditional: the same number would mean one thing where the named
 * source is installed and something else where it is not, and on a runtime carrying only the
 * compulsory parts of an older Java - a small container image, say - the tool would refuse to start
 * at all.
 *
 * <p>Whether the same number produces the same <em>requests</em> depends on the plan, and on one
 * thing in it. A plan that asks for what the API has already sent back gets values that depend on
 * what the API answered, and an API answers differently on a different day - so starting a run like
 * that from the same number gets a similar run rather than the same one, which is one of the
 * reasons every request it did send is worth keeping. Every other source reads the document, which
 * says the same thing every time. A run says for itself which kind it is: there is something to
 * listen to its replies with only when the plan asked for a source with a memory.
 *
 * <p>One generator belongs to one sequence of decisions, so it is used from one thread at a time.
 */
public final class RandomTestCaseGenerator {

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
    private final Settings settings;
    private final long seed;
    private final RandomGenerator random;
    private final ValueProvider values;
    private final List<Dictionary> given;
    private final ObservedValues observed;
    private final Campaign campaign;
    private final List<Strategy> strategies;

    /**
     * How the one request an operation is most likely to accept is filled in, or {@code null} when
     * the plan has no way of building a request meant to work.
     */
    private final Strategy likeliest;
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
        this(model, seed, dictionaries, campaign, Settings.defaults());
    }

    /**
     * The same, told how the tool itself should behave while it does it.
     *
     * <p>The plan and the settings answer different questions and both are needed here. The plan
     * says where a value comes from - the document's own samples, a list somebody wrote, what the
     * API has already returned - and the settings say what a value the tool invents for itself may
     * look like: how long a word, how many items in a list, how deep inside one another.
     *
     * @param model the API to test
     * @param seed the number the whole run's randomness is derived from
     * @param dictionaries the lists of values to draw on, the plan deciding which are used where
     * @param campaign the plan to follow
     * @param settings how the tool behaves: only what it invents and what it remembers is read here
     */
    public RandomTestCaseGenerator(ApiModel model, long seed, List<Dictionary> dictionaries,
            Campaign campaign, Settings settings) {
        this.model = Objects.requireNonNull(model, "model");
        this.settings = Objects.requireNonNull(settings, "settings");
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
        // Only when the plan asks for it. A run whose plan says nothing about what the API has
        // returned does not listen to its own replies at all, and stays repeatable from its seed.
        this.observed = namesWhatTheApiReturns(campaign)
                ? new ObservedValues(model, settings.memory()) : null;
        this.strategies = strategiesFor(campaign, dictionaries, model, random, observed,
                settings.generation());
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
        // The ordinary way of building a request, with its choices turned into a ranking, and a
        // sequence of numbers of its own. Built from a copy of the seed rather than split off the
        // generator's own source, since splitting would move that source on and change every
        // ordinary request after it - the one thing asking for these requests must never do.
        this.likeliest = campaign.strategies().stream()
                .filter(planned -> !planned.pushesAtTheApi())
                .findFirst()
                .map(planned -> new Strategy(planned.name(), planned.share(), false,
                        valuesFor(askedInTurn(planned), dictionaries, model,
                                new SplittableRandom(seed).split(), observed,
                                settings.generation())))
                .orElse(null);

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

    /**
     * What has to be told about every exchange, when the plan asks for a source that needs telling.
     *
     * <p>Only one source does: the one that reuses what the API has already sent back, which cannot
     * know anything unless somebody passes on what came back. Whoever runs the tests subscribes
     * this to the run's stream of announcements, the same way a report or a rule is subscribed.
     *
     * <p>Empty for every other plan, and it being empty rather than a listener that does nothing is
     * the point: a run whose plan says nothing about the API's replies does not watch them at all,
     * and stays repeatable from its starting number.
     *
     * @return what to subscribe, or nothing when this plan has no source with a memory
     */
    public Optional<RunListener> whatListensToTheRun() {
        return Optional.ofNullable(observed);
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
        // The name of the list, not of the strategy drawing on it: a report matches these against
        // what it was told each value came from, and that is the list's name. A plan may call its
        // strategy anything.
        return strategies.stream().anyMatch(Strategy::pushesAtTheApi)
                ? java.util.Set.of(PUSHES_AT_THE_API)
                : java.util.Set.of();
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

    /**
     * The one request this operation is most likely to accept.
     *
     * <p>Everything the API requires and nothing it does not, except a body wherever the document
     * describes one, since an operation that takes a body rarely works without it whatever the
     * document says - though not one a {@code GET} or a {@code HEAD} merely accepts, which the
     * client that sends requests refuses to build, so the request would not go out at all. Each
     * value is taken from the first of the plan's ordinary sources that has one, in the order most
     * likely to be accepted: the closed list of values the document accepts, what the API has
     * already handed back, a list somebody wrote, the document's own sample, its default, and last
     * a value invented to fit.
     *
     * <p>Drawn from numbers of its own, so the ordinary requests that follow are the same whether
     * this was asked for or not.
     *
     * @param operation the operation to build it for
     * @return the request, or empty if a value the API requires could not be found, if this
     *     operation cannot be attempted at all, or if the plan has no way of building a request
     *     meant to work
     */
    Optional<TestCase> likeliestRequest(Operation operation) {
        Objects.requireNonNull(operation, "operation");
        if (likeliest == null || untestable.containsKey(operation.id())) {
            return Optional.empty();
        }
        return fill(operation, likeliest, Filling.LIKELIEST);
    }

    /**
     * Whether this generator can build the request an operation is most likely to accept.
     *
     * <p>It cannot when every way the plan builds a request is meant to push at the API, because
     * then nothing in the plan says where a value anybody believes in would come from.
     */
    boolean canBuildTheLikeliestRequest() {
        return likeliest != null;
    }

    private Optional<TestCase> fill(Operation operation) {
        return fill(operation, nextStrategy(), Filling.DRAWN);
    }

    /**
     * How much of what an operation merely accepts goes into a request - under either, never a body
     * on a {@code GET} or a {@code HEAD}, which the client that sends requests refuses to build.
     */
    private enum Filling {

        /** Some of it, decided by chance, as every ordinary request is. */
        DRAWN,

        /** None of the optional parameters, and a body wherever one is described. */
        LIKELIEST
    }

    private Optional<TestCase> fill(Operation operation, Strategy strategy, Filling filling) {
        List<ParameterValue> chosen = new ArrayList<>();
        int optionalRemaining = 0;
        for (Parameter parameter : operation.parameters()) {
            if (!parameter.required()) {
                optionalRemaining++;
            }
        }
        // Which ones, given how many: chosen one pass through the operation's own declared order,
        // so that every subset of that size is exactly as likely as any other - not just a prefix
        // of it. (A single running "keep going?" flag that stops for good at the first "no" would
        // also favour small counts, but could only ever produce a prefix - never {B} alone, never
        // {A, C} without {B} - which would quietly favour whichever parameters happen to be
        // declared first.) Each optional parameter still to be decided is included with probability
        // exactly however many are still wanted divided by however many are still to be decided,
        // which is what leaves every equally-sized subset equally likely.
        // Not drawn at all for the likeliest request, rather than drawn and ignored: drawing would
        // move the generator's own numbers on, and the ordinary requests after it would change.
        int stillToInclude = filling == Filling.DRAWN
                ? howManyOptionalParametersToInclude(optionalRemaining) : 0;
        for (Parameter parameter : operation.parameters()) {
            if (!parameter.required()) {
                boolean include = stillToInclude > 0 && (stillToInclude == optionalRemaining
                        || random.nextDouble() < (double) stillToInclude / optionalRemaining);
                optionalRemaining--;
                if (include) {
                    stillToInclude--;
                } else {
                    continue;
                }
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
        Optional<BodyValue> body = body(operation, declared.get(), strategy, filling);
        if (body.isEmpty()) {
            // An API that says it needs a body will refuse a request without one whatever else is
            // in it, so there is nothing to learn from sending it.
            return declared.get().required()
                    ? Optional.empty() : Optional.of(TestCase.of(operation.id(), chosen));
        }
        return Optional.of(TestCase.of(operation.id(), chosen, body.get()));
    }

    /**
     * How many of an operation's optional parameters this request includes.
     *
     * <p>Drawn before any of them are chosen individually. Starts at zero, and whether to add one
     * more - the first as much as any later one - is its own chance, asked again after every one
     * added, so the count may stop anywhere from zero up to how many there are. That chance is
     * more often no than yes, which is what keeps the request asking for nothing but what the API
     * insists on a request this sends often, whatever else the operation offers - rather than one
     * it all but never reaches once there are several such things to decide.
     *
     * @param howManyThereAre how many optional parameters the operation has
     * @return how many of them this request will try to include, never more than that
     */
    private int howManyOptionalParametersToInclude(int howManyThereAre) {
        int howManyToInclude = 0;
        while (howManyToInclude < howManyThereAre
                && random.nextDouble() < settings.generation().optionalParameterContinueChance()) {
            howManyToInclude++;
        }
        return howManyToInclude;
    }

    /**
     * The body to send with this request, if one is to be sent at all.
     *
     * <p>A body the API insists on is always sent. One it merely accepts is never sent with a
     * {@code GET} or a {@code HEAD}, which the client that sends requests refuses to build with a
     * body: the request goes without it, which the document allows. With any other method it is
     * always sent in the request most likely to be accepted, and otherwise left out some of the
     * time, on its own chance rather than the one deciding how many optional parameters go in,
     * because an operation behaves differently depending on whether a body arrived, and a tool that
     * always sent one would only ever see one of those behaviours.
     */
    private Optional<BodyValue> body(Operation operation, RequestBodyModel declared,
            Strategy strategy, Filling filling) {
        if (!declared.required()) {
            // Never where the client refuses a body, and then nothing is drawn: the answer is
            // already known, and a draw would move the numbers every later decision comes from.
            // Otherwise decided by chance for an ordinary request, drawn exactly as it always was,
            // and always sent in the likeliest request.
            boolean leftOut = RequestBuilder.cannotBeSentWithABody(operation.method())
                    || (filling == Filling.DRAWN
                            && random.nextDouble() >= settings.generation().optionalBodyChance());
            if (leftOut) {
                return Optional.empty();
            }
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
        for (int attempt = 0; attempt < settings.generation().writableBodyAttempts(); attempt++) {
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
     * <p>Four things stand in the way today, and each is a limit of what has been built rather than
     * a fault in the specification: a parameter written down in a way requests cannot be assembled
     * for; a body a {@code GET} or a {@code HEAD} insists on, which the client that sends requests
     * refuses to build; a body that must be sent and cannot be written or filled in; and a required
     * parameter no value can be found for - because its description allows none, because the parser
     * could not read it, or because nothing available knows how to satisfy it.
     *
     * <p>The first two are asked first, because the document answers them on its own. An operation
     * that could not be sent has no value drawn for it, so every later decision comes from the
     * numbers it would have come from anyway.
     *
     * <p>Whether a value can be found for a required parameter is decided by trying, once, rather
     * than by reasoning about the shape. Shapes that defeat the sources of values are not a list
     * anybody can write down in advance, and an operation quietly failing to produce a test case on
     * every attempt - while being reported as testable - is the outcome this check exists to
     * prevent.
     */
    private Optional<String> whatStandsInTheWay(Operation operation) {
        Optional<String> unassemblable = RequestBuilder.whatCannotBeAssembled(operation);
        if (unassemblable.isPresent()) {
            return unassemblable;
        }
        Optional<String> body = whatStandsInTheWayOfTheBody(operation);
        if (body.isPresent()) {
            return body;
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
     * Whether this plan asks anywhere for what the API has already sent back.
     *
     * <p>Asked before anything is built, because the answer decides whether this run listens to its
     * own replies at all - and a run that does not is one that can be repeated from its starting
     * number.
     */
    private static boolean namesWhatTheApiReturns(Campaign campaign) {
        return campaign.strategies().stream()
                .flatMap(strategy -> strategy.sources().stream())
                .flatMap(entry -> entry.sources().stream())
                .anyMatch(source -> source instanceof Campaign.Source.Builtin builtin
                        && builtin.which() == Campaign.Builtin.OBSERVED);
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
            ApiModel model, RandomGenerator random, ObservedValues observed,
            GenerationSettings inventing) {
        List<Strategy> ways = new ArrayList<>();
        for (Campaign.PlannedStrategy planned : campaign.strategies()) {
            if (pushesWithNothingToPushWith(planned, dictionaries)) {
                continue;
            }
            ValueProvider values =
                    valuesFor(planned, dictionaries, model, random, observed, inventing);
            if (values instanceof ValueProviderChain chain && chain.providers().isEmpty()) {
                // Every source this strategy names turned out to be a list nobody handed over, so
                // it has nothing at all to fill a value with. It would still be drawn for its
                // share of the requests, and every one of them would be abandoned unbuilt - the
                // operation counted among those under test and sending nothing.
                throw new IllegalArgumentException("the strategy called '" + planned.name()
                        + "' names no source that this run has, so it could not fill in a single "
                        + "value");
            }
            ways.add(new Strategy(planned.name(), planned.share(), planned.pushesAtTheApi(),
                    values));
        }
        if (ways.isEmpty()) {
            // Every strategy in the plan pushes, and there is nothing to push with. Running some
            // other plan instead is what this increment spent a review learning not to do: the
            // person asked for something specific and would be told, in the summary, that they
            // got it.
            throw new IllegalArgumentException("every strategy in this plan pushes at the API "
                    + "with a list of values called '" + PUSHES_AT_THE_API + "', and no list of "
                    + "that name was handed over, so there is nothing for the run to do");
        }
        // The shares are counted against each other rather than out of a hundred, so dropping one
        // strategy leaves the rest in the same proportion to one another as the plan wrote them.
        return List.copyOf(ways);
    }

    /**
     * Whether a strategy is one that pushes at the API with no list to push with.
     *
     * <p>The plan RESTest carries spends a quarter of the run sending values nobody sensible would
     * send. A run given no such list has nothing of the kind to send, so that strategy would spend
     * the quarter on ordinary invented values under another name - and, having no step for the
     * closed list of values a document states, would send values the document says are not
     * allowed. Left out instead, and the remaining shares keep the proportions the plan wrote.
     *
     * <p>Deliberately this narrow. An earlier version asked the more general question - has this
     * strategy anything left but invention? - and could not tell {@code [dictionary: fuzzing,
     * source: random]} from {@code [dictionary: mine, source: random]}, because they are the same
     * shape. It threw away the second, which is the plainest thing anybody would write for a list
     * of their own, and handed the whole run to the strategy that pushes. A strategy that merely
     * lost a list still invents, which is what it would have done for every value the list had
     * nothing for anyway.
     */
    private static boolean pushesWithNothingToPushWith(Campaign.PlannedStrategy planned,
            List<Dictionary> dictionaries) {
        return planned.pushesAtTheApi() && dictionaries.stream()
                .noneMatch(held -> held.name().equals(PUSHES_AT_THE_API));
    }

    /**
     * The same strategy with every group in it asked in turn rather than chosen among, the source
     * likeliest to give a value the API will accept first.
     *
     * <p>Choosing among sources is right for a run that sends thousands of requests, because a
     * value pinned to one source for a whole run stops being varied. It is wrong for one request
     * that has one chance, which should simply carry the best value there is. Sources asked on
     * their own keep the place the plan gave them: the plan's order says what it trusts.
     */
    private static Campaign.PlannedStrategy askedInTurn(Campaign.PlannedStrategy planned) {
        List<Campaign.Entry> inTurn = new ArrayList<>();
        for (Campaign.Entry entry : planned.sources()) {
            switch (entry) {
                case Campaign.Entry.Single single -> inTurn.add(single);
                case Campaign.Entry.Group group -> group.among().stream()
                        .map(Campaign.Share::source)
                        .sorted(Comparator.comparingInt(RandomTestCaseGenerator::trustedFirst))
                        .forEach(source -> inTurn.add(new Campaign.Entry.Single(source)));
            }
        }
        return new Campaign.PlannedStrategy(planned.name(), planned.share(), inTurn);
    }

    /**
     * Where a source comes in the ranking of the likeliest request, lowest first.
     *
     * <p>A closed list of accepted values first, because nothing outside it may be sent at all.
     * Then a value the API itself has handed back, because it names something that exists - an
     * identifier the API generated when it started, which no document can know, is the case this
     * ranking exists for. Then a list somebody wrote for this API, then the sample the
     * document's author wrote down, then the default, and last a value invented to fit the shape.
     */
    private static int trustedFirst(Campaign.Source source) {
        return switch (source) {
            case Campaign.Source.Builtin builtin -> switch (builtin.which()) {
                case ENUM -> 0;
                case OBSERVED -> 1;
                case EXAMPLE -> 3;
                case DEFAULT -> 4;
                case RANDOM -> 5;
            };
            case Campaign.Source.OneList ignored -> 2;
            case Campaign.Source.EveryListGiven ignored -> 2;
        };
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
            List<Dictionary> dictionaries, ApiModel model, RandomGenerator random,
            ObservedValues observed, GenerationSettings inventing) {
        ValueProvider knownValues = asPlanned(planned, dictionaries, model, random, observed, null);
        ValueProvider invention =
                new RandomValueProvider(model, random, knownValues, inventing);
        return asPlanned(planned, dictionaries, model, random, observed,
                new Assembled(invention, ValueProviderChain.of(knownValues, invention)));
    }

    /**
     * The two sources that cannot exist until the rest of a strategy does.
     *
     * @param invention what fills a value from its shape alone
     * @param everythingThisStrategyKnows the whole strategy, the document's own statements first
     *     and invention last. What a source needs when it has to fill in one value of its own -
     *     asking invention outright would send a value where the document states the closed list
     *     it accepts, since fitting the shape and being on that list are different things
     */
    private record Assembled(ValueProvider invention, ValueProvider everythingThisStrategyKnows) {
    }

    /**
     * The sources of one strategy, in the arrangement the plan wrote.
     *
     * @param assembled the sources that need the rest of the strategy to exist first, or
     *     {@code null} to leave those steps out - which is how the list invention itself consults
     *     is built
     */
    private static ValueProvider asPlanned(Campaign.PlannedStrategy planned,
            List<Dictionary> dictionaries, ApiModel model, RandomGenerator random,
            ObservedValues observed, Assembled assembled) {
        List<ValueProvider> asked = new ArrayList<>();
        for (Campaign.Entry entry : planned.sources()) {
            switch (entry) {
                case Campaign.Entry.Single single -> built(single.source(), dictionaries, model,
                        random, observed, assembled).ifPresent(asked::add);
                case Campaign.Entry.Group group -> {
                    List<WeightedGroup.Weighted> among = new ArrayList<>();
                    for (Campaign.Share share : group.among()) {
                        built(share.source(), dictionaries, model, random, observed, assembled)
                                .ifPresent(source -> among.add(
                                        new WeightedGroup.Weighted(source, share.weight())));
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
            List<Dictionary> dictionaries, ApiModel model, RandomGenerator random,
            ObservedValues observed, Assembled assembled) {
        return switch (source) {
            case Campaign.Source.Builtin builtin -> switch (builtin.which()) {
                case ENUM -> Optional.of(DeclaredValueProvider.onlyTheAcceptedList(random));
                case EXAMPLE -> Optional.of(new ExampleValueProvider(random));
                case DEFAULT -> Optional.of(DeclaredValueProvider.onlyTheStatedDefault(random));
                case OBSERVED -> Optional.ofNullable(observed).map(memory ->
                        new ObservedValueProvider(model, memory, random, assembled == null
                                ? null : assembled.everythingThisStrategyKnows()));
                case RANDOM -> Optional.ofNullable(assembled).map(Assembled::invention);
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

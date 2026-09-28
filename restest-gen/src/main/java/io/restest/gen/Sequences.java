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
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Intent;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionId;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.Payload;
import io.restest.core.execution.SequenceStep;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.TestCaseId;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.gen.GeneratedValue;
import io.restest.core.gen.ValueProvider;
import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.BodyContent;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.StringSchema;
import io.restest.core.settings.SequenceSettings;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * The short series of requests a run sends around a thing it created itself.
 *
 * <p>Some faults only show across several requests. An API may say it deleted a thing and still
 * hand it back when asked; fail on a second deletion of the same thing; let something be added under
 * a thing that no longer exists; break when the same creation arrives twice; give a different result
 * when the same replacement is sent twice; or change a thing merely because it was read. No single
 * request reaches any of those, however well it is chosen. A series does: it creates something of
 * its own and then sends a short, fixed list of requests about it, each one built only once the
 * answer to the one before has arrived, so that it carries the identifier the API gave the thing.
 *
 * <p>Every series asks one question, and nothing in it that is not a read may change what the
 * question is about. The six:
 *
 * <ul>
 *   <li><b>readAfterDelete</b> - create, read, delete, read again, and read what hangs from it: is a
 *       deleted thing gone for whoever reads it?</li>
 *   <li><b>deleteTwice</b> - create, delete, delete again: is a second deletion answered calmly?</li>
 *   <li><b>writeUnderDeleted</b> - create, delete, then one request that adds or changes something
 *       under it: can something still be written under a thing that no longer exists?</li>
 *   <li><b>putTwice</b> - create, replace, read, the same replacement again, read again: does
 *       repeating a replacement leave the same thing, and did it replace the whole of it?</li>
 *   <li><b>safeGet</b> - create, read, other reads, read again: does reading change anything?</li>
 *   <li><b>createTwice</b> - the same creation twice: does creating the same thing twice break
 *       anything?</li>
 * </ul>
 *
 * <p>A series begins when the way of building requests that sends them is drawn for a creation: that
 * creation is its first step. Which series is drawn among the ones that can be asked of what the
 * creation makes - {@link Creations} says where the thing lives and what hangs from it - and are
 * switched on. A step whose answer the question needs ends the series when it is not a success: a
 * creation refused makes nothing to ask about, and a deletion refused deletes nothing. A step that
 * only adds an observation is left out when it cannot be built.
 *
 * <p>The identifier is read from the creation's reply, the way a person would: a property named like
 * the gap it goes in, then one called {@code id}, then the kind of thing followed by id - in the
 * reply itself, or inside a wrapper around it, but never inside a list, which may be listing things
 * that were there before the creation. A value the creation was itself sent in its address is never
 * taken either: that names what the thing was made under, such as the owner of a new pet. Only when
 * the reply carries none that fits is the {@code Location} header read, whose address ends with the
 * thing's own. Gaps before the thing's own are sent what the creation was sent; the thing's own gap,
 * and a body's own identifier where a step sends one, are sent the thing's identifier. So every
 * identifier a series uses is one the API gave back for what the series made - or, for a thing its
 * creation names itself, as a topic is named, the name it was given.
 *
 * <p>How much of a reply is read does not depend on how much the memory of what the API returned
 * reads, so switching that memory off leaves the series alone. A reply is read whenever the engine
 * kept all of it, and no value read out of one is sent longer, written out, than the text it was
 * read from: a number such as {@code 1e2147483647} is a dozen characters in a reply and two thousand
 * million in an address.
 *
 * <p>Every step says so on the request - see {@link SequenceStep} - naming the kind of series, the
 * step, and the earlier exchanges it follows, so that a stored run can be judged later. What a
 * series learns stays with it: the memory of what the API returned does not hear its replies.
 *
 * <p>Used from one thread, the one that builds requests. The answers reach it through the
 * {@link Scheduler}, which hears them from whichever thread they arrive on.
 */
final class Sequences {

    /**
     * How far through names pointing at other names a shape is followed. A safeguard, like the one
     * the memory of what the API returned keeps for the same job.
     */
    private static final int HOPS = 6;

    private final ApiModel model;
    private final Creations creations;
    private final int mostAwaited;
    private final RandomGenerator shapes;
    private final RandomGenerator steps;
    private final Filler filler;
    private final Map<OperationId, List<Shape>> possible;
    /** In the order the steps were built, so that the oldest is the one forgotten. */
    private final Map<TestCaseId, Open> awaiting = new LinkedHashMap<>();

    /**
     * Series for the things these creations make.
     *
     * @param model the API
     * @param creations what each creation makes, and where it lives
     * @param settings which series are switched on
     * @param mostAwaited how many steps may await their answers at once: as many as a run keeps
     *     awaiting an answer, and as many again answered but not yet read. Beyond it the oldest is
     *     forgotten and its series ends - which a run never reaches, and which keeps a generator
     *     asked for requests by something that never hands the answers back from keeping every
     *     series it began
     * @param shapes where the choice of series, and of what to write under a deleted thing, comes
     *     from - numbers of its own, so choosing never moves the ordinary requests on
     * @param steps where the optional parameters of the steps that draw them come from, for the same
     *     reason
     * @param filler how one request is built
     * @throws IllegalArgumentException if {@code mostAwaited} is less than one
     */
    Sequences(ApiModel model, Creations creations, SequenceSettings settings, int mostAwaited,
            RandomGenerator shapes, RandomGenerator steps, Filler filler) {
        this.model = Objects.requireNonNull(model, "model");
        this.creations = Objects.requireNonNull(creations, "creations");
        Objects.requireNonNull(settings, "settings");
        if (mostAwaited < 1) {
            throw new IllegalArgumentException("at least one step has to be able to await its "
                    + "answer, not " + mostAwaited);
        }
        this.mostAwaited = mostAwaited;
        this.shapes = Objects.requireNonNull(shapes, "shapes");
        this.steps = Objects.requireNonNull(steps, "steps");
        this.filler = Objects.requireNonNull(filler, "filler");
        Map<OperationId, List<Shape>> byCreation = new LinkedHashMap<>();
        for (Operation operation : model.operations()) {
            creations.of(operation).ifPresent(creation -> {
                List<Shape> here = new ArrayList<>();
                for (Shape shape : Shape.values()) {
                    if (shape.isOn(settings) && canAsk(shape, creation)) {
                        here.add(shape);
                    }
                }
                if (!here.isEmpty()) {
                    byCreation.put(operation.id(), List.copyOf(here));
                }
            });
        }
        this.possible = Collections.unmodifiableMap(byCreation);
    }

    /**
     * Whether this operation, drawn for the way of building requests that sends series, starts one.
     * Worked out without drawing anything, so an operation that starts none costs nothing.
     */
    boolean startsOn(Operation operation) {
        return possible.containsKey(Objects.requireNonNull(operation, "operation").id());
    }

    /** Whether any creation of the run starts a series at all. */
    boolean startsAny() {
        return !possible.isEmpty();
    }

    /**
     * How many steps the series a step belongs to plans, its creation included, asked while the
     * step awaits its answer - which is how a test tells a series that went all the way from one
     * that stopped short.
     *
     * @param step a step that was built and not yet heard
     * @return the number of steps, or zero for a request that awaits nothing here
     */
    int stepsPlanned(TestCase step) {
        Open open = awaiting.get(Objects.requireNonNull(step, "step").id());
        return open == null ? 0 : open.plan.size() + 1;
    }

    /**
     * The first step of a series about what this creation makes: the creation itself.
     *
     * @param creation the creation, one {@link #startsOn} said yes to
     * @param values where the creation's values come from
     * @param later where the values of the later steps come from
     * @return the creation as the first step, or nothing when a value it requires could not be found
     */
    Optional<TestCase> begin(Operation creation, ValueProvider values, ValueProvider later) {
        List<Shape> here = possible.get(Objects.requireNonNull(creation, "creation").id());
        if (here == null) {
            return Optional.empty();
        }
        Shape shape = here.size() == 1 ? here.get(0) : here.get(shapes.nextInt(here.size()));
        Creations.Creation made = creations.of(creation).orElseThrow();
        List<Planned> plan = plan(shape, made);
        Optional<TestCase> built = filler.fill(creation, values, Optional.empty());
        if (built.isEmpty()) {
            return Optional.empty();
        }
        TestCase first = TestCase.stepOf(creation.id(), built.get().parameterValues(),
                built.get().body(), Intent.UNKNOWN,
                SequenceStep.first(shape.named(), shape.firstStep()));
        Open open = new Open(shape, made, plan, later);
        open.sent.put(1, first);
        await(first, open);
        return Optional.of(first);
    }

    /**
     * What comes after a step, now that its answer is in.
     *
     * @param step the step, as it was built
     * @param answer what came back, or nothing when nothing did - it could not be sent, or nobody
     *     answered
     * @return the next step to build, or nothing when the series is over
     */
    Optional<Next> heard(TestCase step, Optional<Interaction> answer) {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(answer, "answer");
        Open open = awaiting.remove(step.id());
        if (open == null || open.over || step.sequence().isEmpty()) {
            return Optional.empty();
        }
        int number = step.sequence().get().step();
        Optional<Integer> status = answer.flatMap(Interaction::statusCode);
        answer.ifPresent(heard -> open.exchanges.put(number, heard.id()));
        status.ifPresent(code -> open.statuses.put(number, code));
        boolean succeeded = status.filter(code -> code >= 200 && code < 300).isPresent();
        if (number == 1) {
            if (!succeeded) {
                return end(open);
            }
            open.created = answer.get();
        } else if (open.planned(number).essential() && !succeeded) {
            return end(open);
        }
        return following(open, number + 1);
    }

    /**
     * What comes after a step that could not be built, and so was never sent.
     *
     * @param next the step
     * @return the step after it, or nothing when the series cannot go on without it
     */
    Optional<Next> notBuilt(Next next) {
        Objects.requireNonNull(next, "next");
        if (next.open.over || next.planned().essential()) {
            return end(next.open);
        }
        return following(next.open, next.step + 1);
    }

    /**
     * The request for a step, built now.
     *
     * @param next the step
     * @return the request, or nothing when it cannot be built: an identifier that does not fit, a
     *     value the operation requires that nothing has, an earlier step it repeats that was never
     *     sent
     */
    Optional<TestCase> build(Next next) {
        Objects.requireNonNull(next, "next");
        Open open = next.open;
        if (open.over) {
            return Optional.empty();
        }
        Planned planned = next.planned();
        Optional<TestCase> built = planned.copyOf() > 0
                ? again(open, next.step, planned)
                : fresh(open, next.step, planned);
        built.ifPresent(request -> {
            open.sent.put(next.step, request);
            await(request, open);
        });
        return built;
    }

    /** Keeps a step until its answer is heard, the oldest forgotten beyond the most awaited. */
    private void await(TestCase step, Open open) {
        awaiting.put(step.id(), open);
        if (awaiting.size() > mostAwaited) {
            Iterator<TestCaseId> oldest = awaiting.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    private static Optional<Next> end(Open open) {
        open.over = true;
        return Optional.empty();
    }

    private static Optional<Next> following(Open open, int step) {
        return step > open.plan.size() + 1 ? end(open) : Optional.of(new Next(open, step));
    }

    /** A step that sends an earlier one again, unchanged. */
    private Optional<TestCase> again(Open open, int step, Planned planned) {
        TestCase earlier = open.sent.get(planned.copyOf());
        if (earlier == null) {
            return Optional.empty();
        }
        return Optional.of(TestCase.stepOf(earlier.operation(), earlier.parameterValues(),
                earlier.body(), intentOf(open, planned), recorded(open, step, planned)));
    }

    /** A step built afresh, the thing's identifier and what the creation was sent put in place. */
    private Optional<TestCase> fresh(Open open, int step, Planned planned) {
        Target target = planned.target();
        TestCase creation = open.sent.get(1);
        Map<String, GeneratedValue> pinned = new LinkedHashMap<>();
        Optional<GeneratedValue> identifier = Optional.empty();
        if (target.gap().isPresent()) {
            identifier = identifierFor(open, target.operation(), target.gap().get());
            if (identifier.isEmpty()) {
                // Not the end of the series on its own account: another operation may declare the
                // gap in a form the identifier fits. Whether the series can do without this step
                // is the step's to say.
                return Optional.empty();
            }
            pinned.put(target.gap().get(), identifier.get());
        }
        target.shared().forEach((here, there) -> creation.parameterValue(there,
                ParameterLocation.PATH).ifPresent(sent -> pinned.put(here, new GeneratedValue(
                        sent.value(), new ValueOrigin.Derived(open.created.id(), "the '" + there
                                + "' " + open.createdBy() + " was sent, earlier in this series")))));
        Optional<TestCase> filled = filler.fill(target.operation(), pinning(open.values, pinned),
                planned.optionalParameters() ? Optional.of(steps) : Optional.empty());
        if (filled.isEmpty()) {
            return Optional.empty();
        }
        Optional<GeneratedValue> itsIdentifier = identifier;
        Optional<BodyValue> body = filled.get().body().map(sent -> itsIdentifier
                .map(value -> withTheThingsIdentifier(open, target, sent, value.value()))
                .orElse(sent));
        return Optional.of(TestCase.stepOf(target.operation().id(),
                filled.get().parameterValues(), body, intentOf(open, planned),
                recorded(open, step, planned)));
    }

    /**
     * What is expected of a step: what its plan says, except after a deletion the API only
     * accepted. {@code 202 Accepted} promises the deletion without saying it is done, so a thing
     * read or written under a moment later may rightly still be there, and a refusal is not
     * expected of what follows.
     */
    private static Intent intentOf(Open open, Planned planned) {
        if (planned.intent() != Intent.REFUSAL_EXPECTED) {
            return planned.intent();
        }
        for (int earlier : planned.follows()) {
            if (earlier > 1
                    && open.planned(earlier).target().operation().method() == HttpMethod.DELETE
                    && Integer.valueOf(202).equals(open.statuses.get(earlier))) {
                return Intent.UNKNOWN;
            }
        }
        return Intent.REFUSAL_EXPECTED;
    }

    /** Where a step stands, naming the earlier exchanges it follows that were answered. */
    private static SequenceStep recorded(Open open, int step, Planned planned) {
        List<InteractionId> follows = new ArrayList<>();
        for (int earlier : planned.follows()) {
            InteractionId exchange = open.exchanges.get(earlier);
            if (exchange != null) {
                follows.add(exchange);
            }
        }
        return new SequenceStep(open.shape.named(), step, follows, planned.description());
    }

    // --- the thing's identifier ----------------------------------------------------------------

    /**
     * The identifier of the thing the series created, for a gap in this operation's address.
     *
     * <p>Read from the creation's reply first, and only then from its {@code Location} header. In
     * the reply, three rules in turn: a property named like one of the gaps the thing's own
     * addresses have, then one called {@code id} or {@code _id}, then the kind of thing followed by
     * id. A property that is only written like an identifier - {@code ownerId} inside a pet - is not
     * taken: it is some other thing's. Nor is a value the creation was sent in its own address, which
     * a reply that hands back the thing the new one was made under would otherwise offer.
     */
    private Optional<GeneratedValue> identifierFor(Open open, Operation operation, String gap) {
        Optional<Parameter> declared = operation.parameter(gap, ParameterLocation.PATH);
        HttpResponseRecord response = open.created.response().orElse(null);
        if (declared.isEmpty() || response == null) {
            return Optional.empty();
        }
        CanonicalSchema schema = Shapes.resolved(model, declared.get().schema(), HOPS);
        Reply reply = replyOf(open, response);
        Set<String> gaps = new LinkedHashSet<>();
        gaps.add(gap);
        open.creation.own().forEach(address -> gaps.add(address.gap()));
        Optional<String> kind = ObservedValues.kindOfThingAt(open.creation.creation().path());
        List<Predicate<String>> rules = List.of(
                gaps::contains,
                ObservedValues::isABareIdentifier,
                name -> kind.isPresent() && ObservedValues.kindOfThingInTheName(name)
                        .filter(kind.get()::equals).isPresent());
        for (Predicate<String> rule : rules) {
            for (JsonValue.JsonObject thing : reply.things()) {
                for (Map.Entry<String, JsonValue> member : thing.members().entrySet()) {
                    if (!rule.test(member.getKey())) {
                        continue;
                    }
                    Optional<JsonValue> fits = fitting(member.getValue(), schema,
                            ParameterLocation.PATH, reply.size());
                    if (fits.isPresent()
                            && !reply.itsAddress().contains(asAnAddressWritesIt(fits.get()))) {
                        return Optional.of(new GeneratedValue(fits.get(),
                                new ValueOrigin.Derived(open.created.id(), "the '"
                                        + member.getKey() + "' of what " + open.createdBy()
                                        + " created, earlier in this series")));
                    }
                }
            }
        }
        for (String location : response.headerValues("Location")) {
            Optional<JsonValue> fits = fromTheLocation(location, open.creation)
                    .flatMap(text -> fitting(JsonValue.of(text), schema, ParameterLocation.PATH,
                            text.length()));
            if (fits.isPresent()) {
                return Optional.of(new GeneratedValue(fits.get(), new ValueOrigin.Derived(
                        open.created.id(), "the identifier in the Location header "
                                + open.createdBy() + " answered with, earlier in this series")));
            }
        }
        return Optional.empty();
    }

    /** What the creation's answer says, read the first time a step needs it and kept after. */
    private static Reply replyOf(Open open, HttpResponseRecord response) {
        if (open.reply == null) {
            // Only a reply the engine kept all of: the engine's limit is the only one, since the
            // memory's, meant for the listings a run reads by the thousand, is not this one's.
            List<JsonValue.JsonObject> things = ObservedValues.readable(response, Integer.MAX_VALUE)
                    .map(Sequences::thingsIn).orElse(List.of());
            Set<String> itsAddress = new HashSet<>();
            for (ParameterValue sent : open.sent.get(1).parameterValues()) {
                if (sent.location() == ParameterLocation.PATH) {
                    itsAddress.add(asAnAddressWritesIt(sent.value()));
                }
            }
            open.reply = new Reply(things, response.body().map(Payload::size).orElse(0),
                    Set.copyOf(itsAddress));
        }
        return open.reply;
    }

    /**
     * The things a creation's reply may be about, outermost first: the reply, when it is one
     * object, and what is inside an object with nothing in it named like an identifier, which is
     * usually a wrapper around the thing. Never what is inside a list: a creation that answers with
     * a list may be listing things that were there before it. How deep a reply can go is the
     * reader's to limit, and it does.
     */
    private static List<JsonValue.JsonObject> thingsIn(JsonValue reply) {
        List<JsonValue.JsonObject> found = new ArrayList<>();
        if (reply instanceof JsonValue.JsonObject thing) {
            collectTheThingsIn(thing, found);
        }
        return List.copyOf(found);
    }

    private static void collectTheThingsIn(JsonValue.JsonObject thing,
            List<JsonValue.JsonObject> found) {
        found.add(thing);
        if (thing.members().keySet().stream().noneMatch(ObservedValues::looksLikeAnIdentifier)) {
            for (JsonValue inside : thing.members().values()) {
                if (inside instanceof JsonValue.JsonObject wrapped) {
                    collectTheThingsIn(wrapped, found);
                }
            }
        }
    }

    /**
     * A word or a number the way an address carries it, so that {@code 12} and {@code "12"} are
     * one value there. Anything else is written as JSON, which no word or number is.
     */
    private static String asAnAddressWritesIt(JsonValue value) {
        return switch (value) {
            case JsonValue.JsonString text -> text.value();
            case JsonValue.JsonNumber number -> number.value().stripTrailingZeros().toPlainString();
            default -> value.toString();
        };
    }

    /**
     * The thing's identifier, as it is written in a {@code Location} header whose address ends with
     * one of the thing's own addresses. The end rather than the whole, because an API behind a base
     * address often leaves the base out: pet-clinic answers a creation under {@code /petclinic/api}
     * with {@code /api/owners/895}. Each part of the address is read with its escapes undone, once
     * it has been told apart from the others: {@code a%2Fb} is one part, the word {@code a/b}.
     */
    private static Optional<String> fromTheLocation(String location, Creations.Creation creation) {
        String path;
        try {
            path = URI.create(location.trim()).getRawPath();
        } catch (IllegalArgumentException notAnAddress) {
            return Optional.empty();
        }
        if (path == null) {
            return Optional.empty();
        }
        List<String> parts = new ArrayList<>();
        for (String written : Creations.partsOf(path)) {
            Optional<String> part = unescaped(written);
            if (part.isEmpty()) {
                return Optional.empty();
            }
            parts.add(part.get());
        }
        for (Creations.Address own : creation.own()) {
            List<String> template = Creations.partsOf(own.path());
            if (parts.size() < template.size() || template.isEmpty()) {
                continue;
            }
            List<String> tail = parts.subList(parts.size() - template.size(), parts.size());
            String found = null;
            boolean matches = true;
            for (int at = 0; at < template.size() && matches; at++) {
                Optional<String> gap = Creations.gapNamed(template.get(at));
                if (gap.isEmpty()) {
                    matches = template.get(at).equals(tail.get(at));
                } else if (at == template.size() - 1) {
                    found = tail.get(at);
                }
            }
            if (matches && found != null && !found.isEmpty()) {
                return Optional.of(found);
            }
        }
        return Optional.empty();
    }

    /** One part of an address with its escapes undone, or nothing when they are not escapes. */
    private static Optional<String> unescaped(String part) {
        try {
            // A plus in an address is a plus; only a form turns it into a space.
            return Optional.of(URLDecoder.decode(part.replace("+", "%2B"), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException badlyEscaped) {
            return Optional.empty();
        }
    }

    /**
     * A value the API returned, as one that could go where this shape is declared: no longer,
     * written out, than the text it was read from; a word or a number of the kind wanted; in the
     * form declared; and sendable there. An address writes a number and a word the same way, so a
     * number written as a word is read as the number it is where a number is wanted, and a number
     * is written as a word where a word is.
     */
    private Optional<JsonValue> fitting(JsonValue value, CanonicalSchema wanted,
            ParameterLocation where, int longest) {
        List<JsonValue> candidates = new ArrayList<>();
        if (value instanceof JsonValue.JsonString || value instanceof JsonValue.JsonNumber) {
            candidates.add(value);
        }
        if (value instanceof JsonValue.JsonString text && wanted instanceof NumberSchema) {
            try {
                candidates.add(JsonValue.of(new BigDecimal(text.value())));
            } catch (NumberFormatException notANumber) {
                // Only a word, which the shape wants none of.
            }
        }
        if (value instanceof JsonValue.JsonNumber number && wanted instanceof StringSchema
                && ObservedValues.smallEnoughToSend(number, longest)) {
            candidates.add(JsonValue.of(number.value().toPlainString()));
        }
        for (JsonValue candidate : candidates) {
            // The length first, before anything writes the value out: every later question does,
            // and a number like 1e2147483647 written out does not fit in memory.
            if (ObservedValues.smallEnoughToSend(candidate, longest)
                    && Shapes.couldSatisfy(model, candidate, wanted, HOPS)
                    && ObservedValueProvider.inTheDeclaredForm(candidate, wanted)
                    && RequestBuilder.canBeSentFrom(candidate, where)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * A body with the thing's own identifier put where the body declares one: a property named like
     * the gap, or the kind of thing followed by id, and for a request about the thing itself one
     * called {@code id}. A property the API only ever returns is left alone, since a request does not
     * carry it.
     */
    private BodyValue withTheThingsIdentifier(Open open, Target target, BodyValue body,
            JsonValue identifier) {
        if (!(body.value() instanceof JsonValue.JsonObject sent) || body.sentAs().isPresent()) {
            return body;
        }
        Optional<CanonicalSchema> declared = target.operation().requestBody()
                .flatMap(described -> described.contentFor(body.mediaType()))
                .map(BodyContent::schema)
                .map(schema -> Shapes.resolved(model, schema, HOPS));
        if (declared.isEmpty() || !(declared.get() instanceof ObjectSchema shape)) {
            return body;
        }
        String gap = target.gap().orElseThrow();
        Optional<String> kind = ObservedValues.kindOfThingAt(open.creation.creation().path());
        for (Map.Entry<String, CanonicalSchema> property : shape.properties().entrySet()) {
            String name = property.getKey();
            boolean itsIdentifier = name.equals(gap)
                    || target.aboutTheThingItself() && ObservedValues.isABareIdentifier(name)
                    || kind.isPresent() && ObservedValues.kindOfThingInTheName(name)
                            .filter(kind.get()::equals).isPresent();
            if (!itsIdentifier || Shapes.onlyEverReturned(model, property.getValue(), HOPS)) {
                continue;
            }
            // A word the identifier was found as may be read as a number here, and may not grow in
            // the reading; a number goes as it already was found fit to go.
            int asLong = identifier instanceof JsonValue.JsonString word
                    ? word.value().length() : Integer.MAX_VALUE;
            Optional<JsonValue> fits = fitting(identifier,
                    Shapes.resolved(model, property.getValue(), HOPS), ParameterLocation.BODY,
                    asLong);
            if (fits.isPresent()) {
                Map<String, JsonValue> members = new LinkedHashMap<>(sent.members());
                members.put(name, fits.get());
                return new BodyValue(body.mediaType(), new JsonValue.JsonObject(members),
                        body.origin());
            }
        }
        return body;
    }

    /**
     * The sources a step is built from, with these gaps in its address answered first. Only a whole
     * gap is answered, never a part of one or a value somewhere else with the same name.
     */
    private static ValueProvider pinning(ValueProvider values, Map<String, GeneratedValue> pinned) {
        if (pinned.isEmpty()) {
            return values;
        }
        Map<String, GeneratedValue> fixed = Map.copyOf(pinned);
        return new ValueProvider() {
            @Override
            public Optional<GeneratedValue> offer(ValueRequest request) {
                boolean aWholeGap = request.location() == ParameterLocation.PATH
                        && request.path().equals(request.name());
                return aWholeGap && fixed.containsKey(request.name())
                        ? Optional.of(fixed.get(request.name()))
                        : values.offer(request);
            }

            @Override
            public String name() {
                return values.name();
            }
        };
    }

    // --- what each series sends ----------------------------------------------------------------

    /** Whether this series can be asked of what this creation makes, from its addresses alone. */
    private static boolean canAsk(Shape shape, Creations.Creation creation) {
        boolean reads = creation.first(HttpMethod.GET).isPresent();
        boolean deletes = creation.first(HttpMethod.DELETE).isPresent();
        return switch (shape) {
            case READ_AFTER_DELETE -> deletes && reads;
            case DELETE_TWICE -> deletes;
            case WRITE_UNDER_DELETED -> deletes && !writesUnder(creation).isEmpty();
            case PUT_TWICE -> creation.first(HttpMethod.PUT).isPresent() && reads;
            case SAFE_GET -> reads;
            case CREATE_TWICE -> true;
        };
    }

    /**
     * The steps after the creation, decided when the series begins - the one write under a deleted
     * thing among them, drawn now.
     */
    private List<Planned> plan(Shape shape, Creations.Creation creation) {
        List<Planned> plan = new ArrayList<>();
        switch (shape) {
            case READ_AFTER_DELETE -> {
                Optional<Creations.Address> both =
                        creation.addressWith(HttpMethod.GET, HttpMethod.DELETE);
                Target read = onTheThing(both.flatMap(address -> address.with(HttpMethod.GET))
                        .or(() -> creation.first(HttpMethod.GET)).orElseThrow());
                Target delete = onTheThing(both.flatMap(address -> address.with(HttpMethod.DELETE))
                        .or(() -> creation.first(HttpMethod.DELETE)).orElseThrow());
                plan.add(needed(read, List.of(1), "read what was created: it should be there"));
                plan.add(needed(delete, List.of(1), "delete it"));
                plan.add(again(2, read, List.of(1, 3), Intent.REFUSAL_EXPECTED,
                        "read it again: the API said it deleted it, so it should be gone"));
                for (Creations.Under under : creation.under()) {
                    HttpMethod method = under.operation().method();
                    if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
                        plan.add(observing(underIt(under), List.of(1, 3),
                                Intent.REFUSAL_EXPECTED, "read " + under.operation().path()
                                        + " under what was deleted: it should be gone too"));
                    }
                }
            }
            case DELETE_TWICE -> {
                Target delete = onTheThing(creation.first(HttpMethod.DELETE).orElseThrow());
                plan.add(needed(delete, List.of(1), "delete what was created"));
                plan.add(again(2, delete, List.of(1, 2), Intent.UNKNOWN, "delete it again: not "
                        + "found and a success are both right answers, a server error is not"));
            }
            case WRITE_UNDER_DELETED -> {
                Target delete = onTheThing(creation.first(HttpMethod.DELETE).orElseThrow());
                List<Creations.Under> writes = writesUnder(creation);
                Creations.Under chosen = writes.size() == 1
                        ? writes.get(0) : writes.get(shapes.nextInt(writes.size()));
                HttpMethod method = chosen.operation().method();
                plan.add(needed(delete, List.of(1), "delete what was created"));
                plan.add(observing(underIt(chosen), List.of(1, 2),
                        method == HttpMethod.DELETE ? Intent.UNKNOWN : Intent.REFUSAL_EXPECTED,
                        method + " " + chosen.operation().path() + " under what was deleted: "
                                + (method == HttpMethod.DELETE
                                        ? "not found and a success are both right answers, a "
                                                + "server error is not"
                                        : "it should be refused")));
            }
            case PUT_TWICE -> {
                Optional<Creations.Address> both =
                        creation.addressWith(HttpMethod.PUT, HttpMethod.GET);
                Target put = onTheThing(both.flatMap(address -> address.with(HttpMethod.PUT))
                        .or(() -> creation.first(HttpMethod.PUT)).orElseThrow());
                Target read = onTheThing(both.flatMap(address -> address.with(HttpMethod.GET))
                        .or(() -> creation.first(HttpMethod.GET)).orElseThrow());
                plan.add(needed(put, List.of(1), "replace what was created"));
                plan.add(observing(read, List.of(1, 2), Intent.UNKNOWN,
                        "read it: it should be what was sent"));
                plan.add(again(2, put, List.of(1, 2), Intent.UNKNOWN,
                        "the same replacement again"));
                plan.add(again(3, read, List.of(1, 3, 4), Intent.UNKNOWN,
                        "read it again: it should be as it was after the first replacement"));
            }
            case SAFE_GET -> {
                Creations.Address at = creation.own().stream()
                        .filter(address -> address.operations().containsKey(HttpMethod.GET))
                        .findFirst().orElseThrow();
                Target read = onTheThing(at.with(HttpMethod.GET).orElseThrow());
                plan.add(needed(read, List.of(1), "read what was created"));
                if (read.operation().parameters().stream().anyMatch(declared ->
                        !declared.required())) {
                    plan.add(withSomeOptionalParameters(read, List.of(1),
                            "read it with some of its optional parameters"));
                }
                at.with(HttpMethod.HEAD).ifPresent(head -> plan.add(observing(onTheThing(head),
                        List.of(1), Intent.UNKNOWN, "ask for its headers only")));
                creation.list().ifPresent(list -> plan.add(observing(theListOf(creation, list),
                        List.of(1), Intent.UNKNOWN, "read the list it belongs to")));
                plan.add(again(2, read, List.of(1, 2), Intent.UNKNOWN,
                        "read it once more: reading should have changed nothing"));
            }
            case CREATE_TWICE -> plan.add(again(1, new Target(creation.creation(),
                    Optional.empty(), Map.of(), false), List.of(1), Intent.UNKNOWN,
                    "the same creation again: refused as a duplicate or made twice, but not a "
                            + "server error"));
        }
        return List.copyOf(plan);
    }

    /** What may be written under the thing: an addition, a change, or a deletion under it. */
    private static List<Creations.Under> writesUnder(Creations.Creation creation) {
        return creation.under().stream()
                .filter(under -> switch (under.operation().method()) {
                    case POST, PUT, PATCH, DELETE -> true;
                    default -> false;
                })
                .toList();
    }

    /**
     * A step the question needs, so that the series ends when it is not answered with a success:
     * nothing is expected of it but that success.
     */
    private static Planned needed(Target target, List<Integer> follows, String description) {
        return new Planned(target, 0, false, true, follows, Intent.UNKNOWN, description);
    }

    /** A step that only adds an observation, left out when it cannot be built. */
    private static Planned observing(Target target, List<Integer> follows, Intent intent,
            String description) {
        return new Planned(target, 0, false, false, follows, intent, description);
    }

    /** A read that only adds an observation, with some of its optional parameters drawn. */
    private static Planned withSomeOptionalParameters(Target target, List<Integer> follows,
            String description) {
        return new Planned(target, 0, true, false, follows, Intent.UNKNOWN, description);
    }

    /** An earlier step sent again unchanged, left out when that one was never sent. */
    private static Planned again(int copyOf, Target target, List<Integer> follows, Intent intent,
            String description) {
        return new Planned(target, copyOf, false, false, follows, intent, description);
    }

    private static Target onTheThing(Creations.Placed placed) {
        return new Target(placed.operation(), Optional.of(placed.gap()), placed.shared(), true);
    }

    private static Target underIt(Creations.Under under) {
        return new Target(under.operation(), Optional.of(under.gap()), under.shared(), false);
    }

    /** The list the thing joins, its gaps the creation's own, sent what the creation was sent. */
    private static Target theListOf(Creations.Creation creation, Operation list) {
        Map<String, String> same = new LinkedHashMap<>();
        for (String part : Creations.partsOf(creation.creation().path())) {
            Creations.gapNamed(part).ifPresent(gap -> same.put(gap, gap));
        }
        return new Target(list, Optional.empty(), same, false);
    }

    // --- the pieces ----------------------------------------------------------------------------

    /** How one request of a series is built, by whatever builds every other request. */
    @FunctionalInterface
    interface Filler {

        /**
         * One request for an operation.
         *
         * @param operation the operation
         * @param values where its values come from
         * @param optionalParameters where the choice of which optional parameters to send comes
         *     from, or nothing to send none of them and a body wherever one is described - the
         *     request an operation is likeliest to accept
         * @return the request, or nothing when a value it requires could not be found
         */
        Optional<TestCase> fill(Operation operation, ValueProvider values,
                Optional<RandomGenerator> optionalParameters);
    }

    /** The kinds of series, by the names their switches have. */
    enum Shape {

        READ_AFTER_DELETE("readAfterDelete", "create a thing to read after deleting it"),
        DELETE_TWICE("deleteTwice", "create a thing to delete twice"),
        WRITE_UNDER_DELETED("writeUnderDeleted",
                "create a thing to write under once it has been deleted"),
        PUT_TWICE("putTwice", "create a thing to replace twice"),
        SAFE_GET("safeGet", "create a thing to read around"),
        CREATE_TWICE("createTwice", "create a thing, to send the same creation again");

        private final String named;
        private final String firstStep;

        Shape(String named, String firstStep) {
            this.named = named;
            this.firstStep = firstStep;
        }

        /** The name its switch has, and the one a step records. */
        String named() {
            return named;
        }

        String firstStep() {
            return firstStep;
        }

        boolean isOn(SequenceSettings settings) {
            return switch (this) {
                case READ_AFTER_DELETE -> settings.readAfterDelete();
                case DELETE_TWICE -> settings.deleteTwice();
                case WRITE_UNDER_DELETED -> settings.writeUnderDeleted();
                case PUT_TWICE -> settings.putTwice();
                case SAFE_GET -> settings.safeGet();
                case CREATE_TWICE -> settings.createTwice();
            };
        }
    }

    /**
     * Where a step goes.
     *
     * @param operation the operation it sends
     * @param gap the gap in its address the thing's identifier goes in; nothing for the list the
     *     thing joins, and for a step that repeats the creation
     * @param shared the gaps that are sent what the creation was sent, by the name each has here and
     *     the name it has in the creation's address
     * @param aboutTheThingItself whether it is at one of the thing's own addresses, rather than under
     *     one, so that a body's {@code id} is the thing's own
     */
    private record Target(Operation operation, Optional<String> gap, Map<String, String> shared,
            boolean aboutTheThingItself) {
    }

    /**
     * One step after the creation, decided when the series begins.
     *
     * @param target where it goes
     * @param copyOf the earlier step it sends again unchanged, or zero for one built afresh
     * @param optionalParameters whether its optional parameters are drawn rather than left out
     * @param essential whether the series ends when this step is not answered with a success
     * @param follows the earlier steps it is built on or compared with
     * @param intent what is expected of it
     * @param description what it does, and what it should show
     */
    private record Planned(Target target, int copyOf, boolean optionalParameters,
            boolean essential, List<Integer> follows, Intent intent, String description) {
    }

    /**
     * What a series reads out of its creation's answer, once for all its steps.
     *
     * @param things the objects in the reply that may be the thing: the reply, and what a wrapper
     *     around it holds
     * @param size how long the reply was, in bytes; nothing read out of it is sent longer than this
     * @param itsAddress the values the creation was sent in its own address, as an address writes
     *     them
     */
    private record Reply(List<JsonValue.JsonObject> things, int size, Set<String> itsAddress) {
    }

    /** One series under way. */
    private static final class Open {

        private final Shape shape;
        private final Creations.Creation creation;
        private final List<Planned> plan;
        private final ValueProvider values;
        private final Map<Integer, TestCase> sent = new HashMap<>();
        private final Map<Integer, InteractionId> exchanges = new HashMap<>();
        private final Map<Integer, Integer> statuses = new HashMap<>();
        private Interaction created;
        private Reply reply;
        private boolean over;

        private Open(Shape shape, Creations.Creation creation, List<Planned> plan,
                ValueProvider values) {
            this.shape = shape;
            this.creation = creation;
            this.plan = plan;
            this.values = values;
        }

        private Planned planned(int step) {
            return plan.get(step - 2);
        }

        /** The creation as a person would name it, such as {@code POST /owners}. */
        private String createdBy() {
            return creation.creation().method() + " " + creation.creation().path();
        }
    }

    /**
     * The next step of a series under way, not yet built.
     *
     * <p>Built only once there is room to send it, like every other request, and by the same one
     * thread.
     */
    static final class Next {

        private final Open open;
        private final int step;

        private Next(Open open, int step) {
            this.open = open;
            this.step = step;
        }

        /** The operation the step sends. */
        Operation operation() {
            return planned().target().operation();
        }

        private Planned planned() {
            return open.planned(step);
        }

        @Override
        public String toString() {
            return "step " + step + " of " + open.shape.named() + " for " + open.createdBy();
        }
    }
}

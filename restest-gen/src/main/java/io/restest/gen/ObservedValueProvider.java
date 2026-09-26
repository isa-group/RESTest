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

import io.restest.core.execution.InteractionId;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.gen.GeneratedValue;
import io.restest.core.gen.ValueProvider;
import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.ParameterLocation;
import io.restest.core.schema.AnySchema;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.BooleanSchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.NothingSchema;
import io.restest.core.schema.NullSchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import io.restest.core.schema.UnsupportedSchema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Suggests what the API itself has already sent back.
 *
 * <p>Two different suggestions, and which one applies depends on what is being asked for.
 *
 * <p>Asked about <b>one value</b> - a pet's identifier, an owner's e-mail address - it offers one
 * seen under that name in an earlier reply. This is the common case and the useful one: an
 * identifier that came out of the API is one that exists, where an invented number is one that
 * almost certainly does not.
 *
 * <p>Asked about a <b>whole thing</b> the document gives a name to - "send me an Owner" - it offers
 * an owner the API itself produced, with one value inside it changed. Two things happen to that
 * owner first. Anything the API only ever sends back and never accepts is taken out, along with
 * anything the operation being tested does not ask for, because a resource an API returns is not
 * the same as a resource it accepts. And then exactly one value inside what is left is replaced
 * with a fresh one. That last step is what makes it a test rather than an echo: sending back an
 * unchanged copy of something that already exists usually means asking the API to create a
 * duplicate, while changing one thing asks it to accept something genuinely new that is otherwise
 * exactly as real as what it sent.
 *
 * <p>Nothing is offered that could not satisfy what was asked for. A word seen under the name
 * {@code id} is not offered where a number is wanted, and no value is offered where the document
 * states the closed list of values it will accept and this is not one of them - that list is the
 * whole set of values the API takes, and a value seen elsewhere in the API is not made acceptable
 * by having been seen.
 *
 * <p>Every value offered here says which exchange it was read out of, so a report can answer "where
 * did that come from" with the request that produced it rather than with the word "observed".
 */
public final class ObservedValueProvider implements ValueProvider {

    /** How far into a value the search for something to change goes. */
    private static final int AS_DEEP_AS_A_CHANGE_REACHES = 6;

    /**
     * How many times one value is asked for a replacement before the next one is tried.
     *
     * <p>More than one because what answers is usually choosing among several sources, and among
     * several values within one of them, so an answer equal to what is already there on the first
     * ask is often not the only answer available.
     */
    private static final int ATTEMPTS_AT_A_DIFFERENT_VALUE = 4;

    private final ApiModel model;
    private final ObservedValues seen;
    private final RandomGenerator random;
    private final ValueProvider fillsTheChangedValue;

    /**
     * A source drawing on what this run has seen.
     *
     * @param model the API being tested, which is what says what shape anything has
     * @param seen the memory of what the API has sent back
     * @param random where the choice among several remembered values comes from
     * @param fillsTheChangedValue what supplies the one fresh value put into a remembered thing -
     *     the whole strategy, so that a value the document states a closed list for is one of
     *     those - or {@code null} where there is nothing to supply it, in which case a remembered
     *     thing is offered exactly as it came back
     */
    public ObservedValueProvider(ApiModel model, ObservedValues seen, RandomGenerator random,
            ValueProvider fillsTheChangedValue) {
        this.model = Objects.requireNonNull(model, "model");
        this.seen = Objects.requireNonNull(seen, "seen");
        this.random = Objects.requireNonNull(random, "random");
        this.fillsTheChangedValue = fillsTheChangedValue;
    }

    @Override
    public Optional<GeneratedValue> offer(ValueRequest request) {
        Objects.requireNonNull(request, "request");
        // The whole thing first. Where the document names the shape of what it wants sent and the
        // API has produced one, that is a better answer than any single value inside it - the
        // fields of a real resource make sense together, and assembling them one at a time loses
        // exactly that.
        Optional<GeneratedValue> whole = oneTheApiProduced(request);
        if (whole.isPresent()) {
            return whole;
        }
        if (!isAGapInTheAddress(request) || !seen.settings().identifiersByResource()) {
            return oneValueSeenUnderThisName(request);
        }
        List<Kind> kinds = kindsOfThingFor(request);
        if (!seen.settings().identifiersByResourceFirst()) {
            Optional<GeneratedValue> byName = oneValueSeenUnderThisName(request);
            return byName.isPresent() ? byName : anIdentifierOfTheThingsFor(request, kinds);
        }
        // The kind of thing the address is about is asked before the name, because for a gap in
        // an address the name is often the worse guide: {id} under /flights/{id} would otherwise
        // take the id of an airport as readily as the id of a flight.
        Optional<GeneratedValue> convincing =
                anIdentifierOfTheThingsFor(request, kinds, Likeness.CONVINCING);
        if (convincing.isPresent()) {
            return convincing;
        }
        Optional<GeneratedValue> byName = oneValueSeenUnderThisName(request);
        return byName.isPresent()
                ? byName
                : anIdentifierOfTheThingsFor(request, kinds, Likeness.ONLY_LOOKS_LIKE_ONE);
    }

    /** A gap in the web address, as a whole rather than one piece of it. */
    private static boolean isAGapInTheAddress(ValueRequest request) {
        return request.location() == ParameterLocation.PATH
                && request.path().equals(request.name());
    }

    /**
     * One kind of thing a gap may be the identifier of: the word it is kept under, and the word
     * the document wrote for it, which is the one a person reading where a value came from knows.
     */
    private record Kind(String kept, String written) {
    }

    /**
     * The kinds of thing whose identifier this gap may be: the one the address names just before
     * it, and then the one the gap's own name names, when the two differ.
     */
    private List<Kind> kindsOfThingFor(ValueRequest request) {
        List<Kind> kinds = new ArrayList<>();
        model.operation(request.operation())
                .flatMap(operation -> ObservedValues.fixedPartBefore(operation.path(),
                        request.name()))
                .ifPresent(written -> ObservedValues.kindOfThingNamed(written)
                        .ifPresent(kept -> kinds.add(new Kind(kept, written))));
        ObservedValues.nameWithoutTheIdentifierEnding(request.name())
                .ifPresent(written -> ObservedValues.kindOfThingNamed(written)
                        .filter(kept -> kinds.stream().noneMatch(kind -> kind.kept().equals(kept)))
                        .ifPresent(kept -> kinds.add(new Kind(kept, written))));
        return kinds;
    }

    /** How sure a property of a thing is to be that thing's identifier. */
    private enum Likeness {
        /** Named like the gap itself, or {@code id}, or the kind of thing followed by id. */
        CONVINCING,
        /** Only written the way identifiers are, like {@code ownerId} inside a pet. */
        ONLY_LOOKS_LIKE_ONE
    }

    /** Every step of asking the things of these kinds, the convincing ones first. */
    private Optional<GeneratedValue> anIdentifierOfTheThingsFor(ValueRequest request,
            List<Kind> kinds) {
        Optional<GeneratedValue> convincing =
                anIdentifierOfTheThingsFor(request, kinds, Likeness.CONVINCING);
        return convincing.isPresent()
                ? convincing
                : anIdentifierOfTheThingsFor(request, kinds, Likeness.ONLY_LOOKS_LIKE_ONE);
    }

    /**
     * An identifier of one of the things of these kinds the API has returned, when one of them has
     * a property alike enough and a value that fits.
     *
     * <p>Within {@link Likeness#CONVINCING}, a property named exactly like the gap is preferred to
     * one called {@code id}: a thing that carries both, such as a cluster with an {@code id} and a
     * {@code cluster_id}, is telling us which one the address wants.
     *
     * <p>Only a gap whose own name is written like an identifier - {@code {petId}},
     * {@code {id}} - takes a property that is not named exactly like it. A gap called
     * {@code {username}} or {@code {slug}} is asking for something else, and a thing's {@code id}
     * put there would only push aside the document's own sample for it.
     */
    private Optional<GeneratedValue> anIdentifierOfTheThingsFor(ValueRequest request,
            List<Kind> kinds, Likeness likeness) {
        boolean namedLikeAnIdentifier = ObservedValues.looksLikeAnIdentifier(request.name());
        if (likeness == Likeness.ONLY_LOOKS_LIKE_ONE && !namedLikeAnIdentifier) {
            return Optional.empty();
        }
        for (Kind kind : kinds) {
            List<ObservedValues.Observation> things =
                    seen.underTheKindOfThingTheyAre().thingsOfKind(kind.kept());
            List<PropertyMatch> steps = new ArrayList<>();
            if (likeness == Likeness.CONVINCING) {
                steps.add(name -> name.equals(request.name()));
                if (namedLikeAnIdentifier) {
                    steps.add(name -> ObservedValues.isTheIdentifierOf(name, kind.kept()));
                }
            } else {
                steps.add(ObservedValues::looksLikeAnIdentifier);
            }
            for (PropertyMatch step : steps) {
                List<Sendable> usable = new ArrayList<>();
                List<String> from = new ArrayList<>();
                for (ObservedValues.Observation thing : things) {
                    ((JsonValue.JsonObject) thing.value()).members().forEach((name, value) -> {
                        if (step.matches(name) && fitsAnIdentifier(value, request)) {
                            usable.add(new Sendable(value, thing.from()));
                            from.add(name);
                        }
                    });
                }
                if (!usable.isEmpty()) {
                    int chosen = random.nextInt(usable.size());
                    return Optional.of(new GeneratedValue(usable.get(chosen).value(),
                            new ValueOrigin.Derived(usable.get(chosen).from(), "the '"
                                    + from.get(chosen) + "' of one of the " + kind.written()
                                    + " an earlier reply returned")));
                }
            }
        }
        return Optional.empty();
    }

    /** One way of recognising which property of a thing is its identifier. */
    @FunctionalInterface
    private interface PropertyMatch {
        boolean matches(String property);
    }

    /**
     * Whether a word or number the API returned could go in this gap: of the kind the gap wants,
     * one of its closed list of values if it has one, in its declared form where that form is one
     * identifiers come in, and sendable in an address at all.
     */
    private boolean fitsAnIdentifier(JsonValue value, ValueRequest request) {
        return couldSatisfy(value, request.schema())
                && inTheDeclaredForm(value, resolved(request.schema()))
                && canGoThere(value, request);
    }

    /**
     * The forms identifiers are declared in, checked: a {@code uuid} has to read as one, and an
     * {@code int32} or {@code int64} has to fit. Any other form is not judged here.
     */
    private static boolean inTheDeclaredForm(JsonValue value, CanonicalSchema wanted) {
        if (wanted instanceof StringSchema text && value instanceof JsonValue.JsonString word
                && text.format().filter("uuid"::equals).isPresent()) {
            try {
                return UUID.fromString(word.value()).toString()
                        .equalsIgnoreCase(word.value());
            } catch (IllegalArgumentException notOne) {
                return false;
            }
        }
        if (wanted instanceof NumberSchema number && value instanceof JsonValue.JsonNumber written) {
            BigDecimal amount = written.value();
            return switch (number.format().orElse("")) {
                case "int32" -> amount.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) >= 0
                        && amount.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0;
                case "int64" -> amount.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) >= 0
                        && amount.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0;
                default -> true;
            };
        }
        return true;
    }

    @Override
    public String name() {
        return ObservedValues.NAME;
    }

    /**
     * A value seen under the name being asked about, when one of them fits.
     *
     * <p>Cut down first, exactly as a whole thing is. What is kept under a name is not only words
     * and numbers: an owner inside a reply is kept under {@code owner}, and an owner the API
     * returns is not an owner it accepts - it carries the identifier the API allots and whatever
     * else that API sends and never receives. Sending it back whole would put those on the wire
     * under the one rule this increment has: that a property a document says is only ever returned
     * is never sent. Both ways of asking go through the same cutting down, so there is one set of
     * rules rather than one set and an exception.
     */
    private Optional<GeneratedValue> oneValueSeenUnderThisName(ValueRequest request) {
        List<Sendable> usable = new ArrayList<>();
        for (ObservedValues.Observation what : seen.underTheirOwnNames().observationsFor(request)) {
            keptOf(what.value(), request.schema(), 0)
                    .filter(fits -> canGoThere(fits, request))
                    .ifPresent(fits -> usable.add(new Sendable(fits, what.from())));
        }
        if (usable.isEmpty()) {
            return Optional.empty();
        }
        Sendable chosen = usable.get(random.nextInt(usable.size()));
        return Optional.of(new GeneratedValue(chosen.value(), new ValueOrigin.Derived(
                chosen.from(), "the '" + request.name() + "' of an earlier reply")));
    }

    /**
     * A whole thing the API produced, cut down to what this operation accepts and with one value in
     * it changed.
     */
    private Optional<GeneratedValue> oneTheApiProduced(ValueRequest request) {
        if (request.shape().isEmpty() || !(request.schema() instanceof ObjectSchema wanted)) {
            return Optional.empty();
        }
        List<ObservedValues.Observation> remembered =
                seen.underTheNameOfTheirShape().observationsFor(request);
        List<Sendable> sendable = new ArrayList<>();
        for (ObservedValues.Observation what : remembered) {
            if (what.value() instanceof JsonValue.JsonObject thing) {
                keptOf(thing, wanted, 0).ifPresent(kept ->
                        sendable.add(new Sendable(kept, what.from())));
            }
        }
        if (sendable.isEmpty()) {
            return Optional.empty();
        }
        Sendable chosen = sendable.get(random.nextInt(sendable.size()));
        GeneratedValue offered = withOneValueChanged(chosen, wanted, request);
        return canGoThere(offered.value(), request) ? Optional.of(offered) : Optional.empty();
    }

    /** One remembered value, already cut down to what the operation accepts. */
    private record Sendable(JsonValue value, InteractionId from) {
    }

    /**
     * What is left of a thing the API returned once the parts this operation will not accept are
     * taken out.
     *
     * <p>Four kinds go. A property the document says is only ever returned, which is what
     * {@code readOnly} means and is the document's own way of saying "do not send me this". A
     * property this operation's shape does not declare at all, because an API that refuses what it
     * did not ask for is ordinary - unless the shape names no properties and refuses none, which is
     * how a free-form map is written and means every property is welcome. A property whose value is
     * not of the kind wanted here, which happens when one name means different things in different
     * parts of an API. And one too large to be sent, or so large that it cannot be written down.
     *
     * <p>Nothing is left if a property the operation insists on did not survive: a body knowingly
     * missing something required is not worth sending when something else could build a complete
     * one.
     */
    private Optional<JsonValue.JsonObject> keptOf(JsonValue.JsonObject thing, ObjectSchema wanted,
            int depth) {
        if (depth > AS_DEEP_AS_A_CHANGE_REACHES) {
            return Optional.empty();
        }
        Map<String, JsonValue> kept = new LinkedHashMap<>();
        for (Map.Entry<String, JsonValue> member : thing.members().entrySet()) {
            JsonValue was = member.getValue();
            if (was instanceof JsonValue.JsonNull) {
                continue;
            }
            // The shape this operation accepts for that name: the one it declares, or - where it
            // declares none and says other properties are welcome - the one it allows them under.
            // Without the second, a body declared as a free-form map loses every member of the
            // thing that came back and is sent as an empty object under its name.
            CanonicalSchema declared = wanted.properties().get(member.getKey());
            if (declared == null && wanted.properties().isEmpty() && !wanted.isClosed()) {
                // A shape that names no properties and does not refuse them is a free-form map,
                // and a document writes one two ways: by saying what its members look like, or by
                // saying nothing beyond "an object". Both mean the same thing, and the second is
                // the commoner spelling.
                declared = wanted.additionalProperties().orElseGet(AnySchema::of);
            }
            if (declared == null || onlyEverReturned(declared)) {
                continue;
            }
            keptOf(was, resolved(declared), depth)
                    .ifPresent(fits -> kept.put(member.getKey(), fits));
        }
        if (kept.isEmpty() && !thing.members().isEmpty()) {
            // Everything it came back with was refused, so what is left is not the thing the API
            // returned; it is an empty object wearing that thing's name.
            return Optional.empty();
        }
        for (String insisted : wanted.required()) {
            CanonicalSchema declared = wanted.properties().get(insisted);
            if (declared != null && !onlyEverReturned(declared) && !kept.containsKey(insisted)) {
                return Optional.empty();
            }
        }
        if (kept.size() < wanted.minProperties().orElse(0)
                || kept.size() > wanted.maxProperties().orElse(Integer.MAX_VALUE)) {
            return Optional.empty();
        }
        return Optional.of(new JsonValue.JsonObject(kept));
    }

    /** What is left of one value once the parts the shape does not accept are taken out. */
    private Optional<JsonValue> keptOf(JsonValue was, CanonicalSchema wanted, int depth) {
        if (depth > AS_DEEP_AS_A_CHANGE_REACHES) {
            return Optional.empty();
        }
        if (wanted instanceof ObjectSchema object && was instanceof JsonValue.JsonObject thing) {
            return keptOf(thing, object, depth + 1).map(JsonValue.class::cast);
        }
        if (wanted instanceof ArraySchema list && was instanceof JsonValue.JsonArray elements) {
            List<JsonValue> kept = new ArrayList<>();
            for (JsonValue element : elements.elements()) {
                keptOf(element, resolved(list.items()), depth + 1).ifPresent(kept::add);
            }
            return kept.size() >= list.minItems().orElse(0)
                    && kept.size() <= list.maxItems().orElse(Integer.MAX_VALUE)
                    ? Optional.of(new JsonValue.JsonArray(kept))
                    : Optional.empty();
        }
        if (wanted instanceof ChoiceSchema choice) {
            for (CanonicalSchema alternative : choice.alternatives()) {
                Optional<JsonValue> fits = keptOf(was, resolved(alternative), depth + 1);
                if (fits.isPresent()) {
                    return fits;
                }
            }
            return Optional.empty();
        }
        // Measured here as well as when it was kept, because a value inside a whole thing the API
        // returned reaches this without having been measured at all: the thing is filed under the
        // name of its shape in one piece. A number of eight characters that cannot be written down
        // at all, or a word of four hundred kilobytes, arrives this way and no other.
        return couldSatisfy(was, wanted) && seen.smallEnoughToSend(was)
                ? Optional.of(was)
                : Optional.empty();
    }

    /**
     * The remembered thing with one value inside it replaced by a different one.
     *
     * <p>Which value is picked at random among those there are, and the replacement has to be
     * <em>different</em> from what it replaces - which is not a formality. What supplies it is the
     * rest of the strategy, and the rest of the strategy contains this very source, so the likeliest
     * single answer for a value called {@code status} inside a remembered thing is the {@code status}
     * that is already in it. Taking that would send an exact copy of something that already exists,
     * which is the one outcome changing a value is here to avoid, and would record that a value had
     * been changed when none had.
     *
     * <p>Each value is asked for a few times before moving on to the next, because what answers is
     * usually choosing among several and may give a different one next time.
     *
     * <p>Where nothing different can be had - every value inside is one nothing else knows how to
     * fill, or the thing has only values the API keeps sending back unchanged, or this source is
     * being consulted while the sources that would supply a replacement are still being put
     * together - the thing is offered exactly as it came back, which is still a real resource and
     * still worth sending, and is recorded as exactly that.
     */
    private GeneratedValue withOneValueChanged(Sendable chosen, ObjectSchema wanted,
            ValueRequest request) {
        List<Value> inside = new ArrayList<>();
        valuesInside(chosen.value(), wanted, request, new ArrayList<>(), inside, 0);
        inAnyOrder(inside);
        String thing = "a " + request.shape().orElseThrow() + " the API returned";
        if (fillsTheChangedValue != null) {
            for (Value value : inside) {
                for (int attempt = 0; attempt < ATTEMPTS_AT_A_DIFFERENT_VALUE; attempt++) {
                    Optional<JsonValue> fresh = fillsTheChangedValue.offer(value.asked())
                            .map(GeneratedValue::value)
                            .filter(offered -> !offered.equals(value.was()));
                    if (fresh.isPresent()) {
                        JsonValue changed =
                                replaced(chosen.value(), value.steps(), 0, fresh.get());
                        return new GeneratedValue(changed, new ValueOrigin.Derived(chosen.from(),
                                thing + ", with '" + value.asked().path() + "' changed"));
                    }
                }
            }
        }
        return new GeneratedValue(chosen.value(),
                new ValueOrigin.Derived(chosen.from(), thing + ", unchanged"));
    }

    /**
     * Puts them in no particular order, so that it is not always the first value of a resource that
     * changes - which would leave the rest of it never tested with anything but what came back.
     */
    private void inAnyOrder(List<Value> values) {
        for (int at = values.size() - 1; at > 0; at--) {
            Collections.swap(values, at, random.nextInt(at + 1));
        }
    }

    /**
     * One single value inside a remembered thing: the way down to it, what is there now, and how to
     * ask for a new one. What is there now is carried because a replacement equal to it is not a
     * replacement.
     */
    private record Value(List<Object> steps, JsonValue was, ValueRequest asked) {
    }

    /**
     * Every single value inside a remembered thing, with the question that would fill it again.
     *
     * <p>Single values only - a word, a number, a yes or no. Replacing a whole object or a whole
     * list inside a resource would be replacing most of it, and what makes this worth doing is that
     * everything except the one changed value is exactly what the API sent.
     */
    private void valuesInside(JsonValue value, CanonicalSchema declared, ValueRequest asked,
            List<Object> steps, List<Value> into, int depth) {
        if (depth > AS_DEEP_AS_A_CHANGE_REACHES) {
            return;
        }
        if (value instanceof JsonValue.JsonObject thing && declared instanceof ObjectSchema object) {
            thing.members().forEach((name, member) -> {
                CanonicalSchema property = object.properties().get(name);
                if (property != null) {
                    List<Object> down = new ArrayList<>(steps);
                    down.add(name);
                    valuesInside(member, resolved(property),
                            asked.about(name, resolved(property)), down, into, depth + 1);
                }
            });
            return;
        }
        if (value instanceof JsonValue.JsonArray list && declared instanceof ArraySchema elements) {
            for (int at = 0; at < list.elements().size(); at++) {
                List<Object> down = new ArrayList<>(steps);
                down.add(at);
                valuesInside(list.elements().get(at), resolved(elements.items()),
                        asked.aboutAPieceOf(resolved(elements.items())), down, into, depth + 1);
            }
            return;
        }
        // Single values only. Replacing a whole object or a whole list would be replacing most of
        // the thing, and what makes this worth doing is that everything but the one changed value
        // is exactly what the API sent.
        if (!steps.isEmpty() && !(value instanceof JsonValue.JsonObject)
                && !(value instanceof JsonValue.JsonArray)) {
            into.add(new Value(List.copyOf(steps), value, asked));
        }
    }

    /** The same value with the one at the end of this way down replaced. */
    private static JsonValue replaced(JsonValue in, List<Object> steps, int at, JsonValue with) {
        if (at == steps.size()) {
            return with;
        }
        if (steps.get(at) instanceof String name && in instanceof JsonValue.JsonObject thing) {
            Map<String, JsonValue> members = new LinkedHashMap<>(thing.members());
            members.put(name, replaced(members.get(name), steps, at + 1, with));
            return new JsonValue.JsonObject(members);
        }
        if (steps.get(at) instanceof Integer index && in instanceof JsonValue.JsonArray list) {
            List<JsonValue> elements = new ArrayList<>(list.elements());
            elements.set(index, replaced(elements.get(index), steps, at + 1, with));
            return new JsonValue.JsonArray(elements);
        }
        return in;
    }

    /** A property the document says the API sends and never receives. */
    private boolean onlyEverReturned(CanonicalSchema property) {
        return property.metadata().access() == SchemaMetadata.Access.READ_ONLY
                || resolved(property).metadata().access() == SchemaMetadata.Access.READ_ONLY;
    }

    /**
     * Whether a value can go where this one is going, and is not too large to put there.
     *
     * <p>Two different limits. One is the request builder's own: a value that would leave a gap in
     * the path empty, or carry a line break into a header, cannot be sent at all. The other is
     * size, and it matters here in a way it does not for a list somebody wrote by hand: a reply is
     * written by the thing being tested, and a whole object out of one is fine inside a body and is
     * not fine written into a web address. Single words and numbers need no check, having been
     * measured before they were kept.
     */
    private boolean canGoThere(JsonValue value, ValueRequest request) {
        if (!RequestBuilder.canBeSentFrom(value, request.location())) {
            return false;
        }
        if (request.location() == ParameterLocation.BODY
                || !(value instanceof JsonValue.JsonObject
                        || value instanceof JsonValue.JsonArray)) {
            return true;
        }
        return JsonText.write(value).length() <= seen.settings().longestValueKept();
    }

    /**
     * Whether a value the API sent could be sent back here.
     *
     * <p>The kind of value has to match, which is what stops a word seen under one name filling a
     * number of the same name somewhere else in the API. And where the document states the closed
     * list of values it accepts, the value has to be one of them: that list is not advice, it is
     * the whole set, and a value seen elsewhere in the API is not made acceptable by having been
     * seen.
     */
    private boolean couldSatisfy(JsonValue value, CanonicalSchema wanted) {
        return couldSatisfy(value, wanted, 0);
    }

    private boolean couldSatisfy(JsonValue value, CanonicalSchema wanted, int depth) {
        // Counted, because a document may point one shape at another and that one back again. It
        // parses cleanly and it is nobody's mistake to make a request for; following it without
        // counting ends the run, which design principle 2 forbids for any document at all.
        if (depth > AS_DEEP_AS_A_CHANGE_REACHES) {
            return false;
        }
        List<JsonValue> allowed = wanted.metadata().enumeration();
        if (!allowed.isEmpty() && !allowed.contains(value)) {
            return false;
        }
        return switch (wanted) {
            case StringSchema ignored -> value instanceof JsonValue.JsonString;
            case NumberSchema number -> value instanceof JsonValue.JsonNumber written
                    && (number.kind() != NumberKind.INTEGER
                            || written.value().stripTrailingZeros().scale() <= 0);
            case BooleanSchema ignored -> value instanceof JsonValue.JsonBoolean;
            case ArraySchema ignored -> value instanceof JsonValue.JsonArray;
            case ObjectSchema ignored -> value instanceof JsonValue.JsonObject;
            case NullSchema ignored -> value instanceof JsonValue.JsonNull;
            case AnySchema ignored -> true;
            case ChoiceSchema choice -> choice.alternatives().stream()
                    .anyMatch(alternative -> couldSatisfy(value, alternative, depth + 1));
            case SchemaReference reference -> model.resolve(reference)
                    .map(named -> couldSatisfy(value, named, depth + 1)).orElse(false);
            // Nothing satisfies a shape that accepts nothing, and a shape nobody could read is not
            // one to guess at.
            case NothingSchema ignored -> false;
            case UnsupportedSchema ignored -> false;
        };
    }

    /** The shape itself, where the document referred to one it declared elsewhere by name. */
    private CanonicalSchema resolved(CanonicalSchema schema) {
        CanonicalSchema here = schema;
        for (int hops = 0; hops < AS_DEEP_AS_A_CHANGE_REACHES
                && here instanceof SchemaReference reference; hops++) {
            CanonicalSchema named = model.resolve(reference).orElse(null);
            if (named == null) {
                return here;
            }
            here = named;
        }
        return here;
    }
}

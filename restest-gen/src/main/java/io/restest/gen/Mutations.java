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
import io.restest.core.execution.Intent;
import io.restest.core.execution.Mutation;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.ParameterLocation;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.BooleanSchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.NothingSchema;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.StringSchema;
import io.restest.core.schema.UnsupportedSchema;
import io.restest.core.settings.GenerationSettings;
import io.restest.core.settings.MutationSettings;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.random.RandomGenerator;

/**
 * Makes a new request out of one the API accepted, by changing exactly one thing in it.
 *
 * <p>An API checks what it is sent before it acts on it, and a request with several things wrong is
 * turned away by the first check it meets - so the code behind that check, where the faults a
 * correct request never reaches tend to live, never runs. Changing one thing in a request that
 * worked gets past every check but one. What the API does then is down to that one change: it
 * refuses it, which is right; it accepts it, which says the check is missing; or it fails, which is
 * a fault in the code that handles it.
 *
 * <p>The thing changed is one value in the request: a parameter, or one property inside a JSON body,
 * however deep. There are eleven kinds of change, and they come in two families. The first breaks
 * something the API's documentation states - a required value left out, a number one past the
 * largest allowed, a word where a number is declared, a value that is not on the list of accepted
 * ones - so the API is expected to refuse it, and the request says so. The second goes where the
 * documentation says nothing - ten thousand characters where no longest length is given, an empty
 * word where nothing says a word may not be empty - so nobody can say in advance which answer is
 * right, and the request says that instead. Each kind, and each family, can be switched off.
 *
 * <p>Which kind of change, and where, is chosen by chance: first a kind among those that have
 * somewhere to go in this request, then one of the places it can go. The requests to change come
 * from {@link AcceptedRequests}; the result is an ordinary {@link TestCase}, whose {@link Mutation}
 * names the request it was made from and the change, so the two can be compared afterwards.
 *
 * <p>Some things are never changed, because the change would not be the one recorded. A value in the
 * path is never left out or emptied, since the address would then be a different one. A header the
 * client that sends requests writes for itself - {@code Content-Type}, {@code Accept} and a few
 * others - is never left out or moved, since the client would put it back. A body sent as the
 * fields of a web form is left alone, since a form cannot say {@code null}. And a body's root is left
 * as it is: changing the shape of a whole body is a different kind of test.
 */
final class Mutations {

    /**
     * The headers the client that sends requests writes itself, in lower case. Leaving one of them
     * out, or moving it, would change nothing on the wire.
     */
    private static final Set<String> WRITTEN_BY_THE_CLIENT = Set.of("accept", "accept-encoding",
            "connection", "content-length", "content-type", "cookie", "host", "transfer-encoding",
            "user-agent");

    /** The kinds of word a document can name for which an empty word is never one. */
    private static final Set<String> NEVER_EMPTY = Set.of("date-time", "date", "time", "duration",
            "email", "idn-email", "hostname", "idn-hostname", "ipv4", "ipv6", "uri", "url", "iri",
            "uuid");

    /** What a header's or a cookie's name may be made of. */
    private static final Pattern TOKEN = Pattern.compile("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+");

    /**
     * Beyond this many characters, variations of a value are not held against its spelling rule;
     * only short fixed words are. A rule is a program the document wrote, and a long input is where
     * a badly written one takes for ever.
     */
    private static final int LONGEST_HELD_AGAINST_A_PATTERN = 256;

    /** What is sent where a number or a yes-or-no is declared: plainly neither. */
    private static final String NEITHER_A_NUMBER_NOR_A_YES_OR_NO = "abc";

    /** How much of a value a description quotes before it stops. */
    private static final int QUOTED_AT_MOST = 40;

    private final ApiModel model;
    private final MutationSettings settings;
    private final GenerationSettings generation;
    private final RandomGenerator random;

    /**
     * Something to change accepted requests with.
     *
     * @param model the API being tested, which says what every value is allowed to be
     * @param settings which kinds of change may be made, and how large an oversized value is
     * @param generation how deep inside a body a change may go, and the longest word and list a
     *     change one past a limit may build
     * @param random where every choice comes from
     */
    Mutations(ApiModel model, MutationSettings settings, GenerationSettings generation,
            RandomGenerator random) {
        this.model = Objects.requireNonNull(model, "model");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.generation = Objects.requireNonNull(generation, "generation");
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * A request made by changing one thing in one the API accepted.
     *
     * @param operation the operation both requests are for
     * @param accepted the request the API accepted, and the exchange in which it did
     * @return the changed request, or nothing when no kind of change that is switched on has
     *     anywhere to go in it
     */
    Optional<TestCase> changeOneThingIn(Operation operation, AcceptedRequests.Accepted accepted) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(accepted, "accepted");
        if (!settings.anyFamilyOn()) {
            return Optional.empty();
        }
        List<Place> places = placesIn(operation, accepted.testCase());
        Map<Operator, List<Place>> possible = new EnumMap<>(Operator.class);
        for (Operator operator : Operator.values()) {
            if (!operator.isOn(settings)) {
                continue;
            }
            List<Place> where = new ArrayList<>();
            for (Place place : places) {
                if (applies(operator, place, operation)) {
                    where.add(place);
                }
            }
            if (!where.isEmpty()) {
                possible.put(operator, where);
            }
        }
        // A kind first and then a place, so that a kind with somewhere to go in every value - an
        // empty word, say - does not crowd out one with a single place to go. A change that turns
        // out not to be possible after all is taken off the list and another drawn, so that a
        // request with anything at all to change gets changed.
        while (!possible.isEmpty()) {
            List<Operator> kinds = new ArrayList<>(possible.keySet());
            Operator operator = kinds.get(random.nextInt(kinds.size()));
            List<Place> where = possible.get(operator);
            Place place = where.remove(random.nextInt(where.size()));
            if (where.isEmpty()) {
                possible.remove(operator);
            }
            Optional<TestCase> changed = edit(operator, place, operation)
                    .flatMap(edit -> applied(edit, operator, place, operation, accepted));
            if (changed.isPresent()) {
                return changed;
            }
        }
        return Optional.empty();
    }

    // --- where a change can go -------------------------------------------------------------------

    /** Every value in a request that a change could be made to. */
    private List<Place> placesIn(Operation operation, TestCase testCase) {
        List<Place> places = new ArrayList<>();
        for (ParameterValue given : testCase.parameterValues()) {
            operation.parameter(given.name(), given.location()).ifPresent(declared ->
                    places.add(new Place(given.location(), given.name(), List.of(),
                            declared.schema(), resolved(declared.schema()), given.value(),
                            declared.required())));
        }
        testCase.body()
                .filter(body -> !RequestBuilder.isForm(body.mediaType()))
                .ifPresent(body -> operation.requestBody()
                        .flatMap(declared -> declared.schemaFor(body.mediaType()))
                        .ifPresent(schema -> inside(body.value(), schema, List.of(),
                                ValueRequest.THE_BODY, 0, places)));
        return places;
    }

    /**
     * The values inside a body, walked alongside the shape the document gives it.
     *
     * <p>Every property of an object, and one element of a list, chosen by chance: every element of
     * a long list would be a great many places that are all the same place. A property the API only
     * ever returns is not one: a request does not carry it, so leaving it out or breaking it changes
     * nothing the API reads. The walk goes no further than a shape that is a choice between several,
     * or one that allows anything, since what is inside those has no single shape to break.
     */
    private void inside(JsonValue value, CanonicalSchema declared, List<Object> steps, String path,
            int depth, List<Place> into) {
        if (depth >= generation.hardNestingDepth()) {
            return;
        }
        CanonicalSchema shape = resolved(declared);
        if (shape instanceof ObjectSchema object && value instanceof JsonValue.JsonObject thing) {
            thing.members().forEach((name, member) -> {
                CanonicalSchema property = object.properties().get(name);
                if (property == null || Shapes.onlyEverReturned(model, property, hops())) {
                    return;
                }
                List<Object> down = followedBy(steps, name);
                String there = path + ValueRequest.STEP + name;
                into.add(new Place(ParameterLocation.BODY, there, down, property,
                        resolved(property), member, object.required().contains(name)));
                inside(member, property, down, there, depth + 1, into);
            });
        } else if (shape instanceof ArraySchema list && value instanceof JsonValue.JsonArray elements
                && !elements.elements().isEmpty()) {
            int at = random.nextInt(elements.elements().size());
            List<Object> down = followedBy(steps, at);
            String there = path + ValueRequest.EVERY_ELEMENT;
            JsonValue element = elements.elements().get(at);
            into.add(new Place(ParameterLocation.BODY, there, down, list.items(),
                    resolved(list.items()), element, false));
            inside(element, list.items(), down, there, depth + 1, into);
        }
    }

    /** Whether a kind of change has somewhere to go at this place. Cheap: nothing is built. */
    private boolean applies(Operator operator, Place place, Operation operation) {
        if (!operator.equals(Operator.DROP_REQUIRED) && !understood(place.shape())) {
            return false;
        }
        return switch (operator) {
            case DROP_REQUIRED -> place.required() && (place.inTheBody()
                    ? place.steps().getLast() instanceof String
                    : canBeLeftOutOrMoved(place));
            case WRONG_LOCATION -> !place.inTheBody() && place.required()
                    && canBeLeftOutOrMoved(place) && !destinations(place, operation).isEmpty();
            case WRONG_TYPE -> place.inTheBody()
                    ? !kindsItIsNot(place).isEmpty()
                    : place.shape() instanceof NumberSchema || place.shape() instanceof BooleanSchema;
            case OUTSIDE_A_BOUND -> !stepsPastALimit(place).isEmpty();
            case BREAK_AN_ENUMERATION -> !acceptedList(place).isEmpty();
            case BREAK_A_PATTERN -> place.shape() instanceof StringSchema text
                    && text.pattern().isPresent() && place.value() instanceof JsonValue.JsonString;
            case SEND_NULL -> place.inTheBody() && !(place.value() instanceof JsonValue.JsonNull)
                    && !mayBeNull(place);
            case SEND_EMPTY -> canBeEmptied(place) && emptyIsForbidden(place);
            case OVERSIZE -> statedMost(place).filter(most -> most < oversized(place)).isPresent();
            case OVERSIZE_WITH_NO_LIMIT -> canBeOversizedWithNoLimit(place);
            case EMPTY_WITH_NO_RULE -> canBeEmptied(place) && !emptyIsForbidden(place);
        };
    }

    // --- what a change is ------------------------------------------------------------------------

    /** The change itself, or nothing when it turns out not to be possible at this place. */
    private Optional<Edit> edit(Operator operator, Place place, Operation operation) {
        return switch (operator) {
            case DROP_REQUIRED -> Optional.of(new Edit.Remove("left out the required "
                    + place.described()));
            case WRONG_LOCATION -> {
                List<ParameterLocation> to = destinations(place, operation);
                ParameterLocation chosen = to.get(random.nextInt(to.size()));
                yield Optional.of(new Edit.Move(chosen, "moved the required " + place.described()
                        + " to " + where(chosen)));
            }
            case WRONG_TYPE -> {
                JsonValue with;
                if (place.inTheBody()) {
                    List<JsonValue> kinds = kindsItIsNot(place);
                    with = kinds.get(random.nextInt(kinds.size()));
                } else {
                    with = JsonValue.of(NEITHER_A_NUMBER_NOR_A_YES_OR_NO);
                }
                yield Optional.of(new Edit.Replace(with, "sent " + quoted(with) + " for "
                        + place.described() + ", declared as " + kindOf(place.shape())));
            }
            case OUTSIDE_A_BOUND -> {
                List<Function<Place, Edit.Replace>> ways = stepsPastALimit(place);
                yield Optional.of(ways.get(random.nextInt(ways.size())).apply(place));
            }
            case BREAK_AN_ENUMERATION -> outsideTheList(place).map(with -> new Edit.Replace(with,
                    "sent " + quoted(with) + " for " + place.described()
                            + ", which is not on its list of accepted values"));
            case BREAK_A_PATTERN -> refusedByThePattern(place).map(with -> new Edit.Replace(with,
                    "sent " + quoted(with) + " for " + place.described() + ", which its pattern "
                            + ((StringSchema) place.shape()).pattern().orElseThrow()
                            + " refuses"));
            case SEND_NULL -> Optional.of(new Edit.Replace(JsonValue.NULL, "sent null for "
                    + place.described() + ", which may not be null"));
            case SEND_EMPTY -> emptyOfItsKind(place).map(with -> new Edit.Replace(with,
                    "sent " + emptiness(with) + " for " + place.described()
                            + ", which may not be empty"));
            case OVERSIZE -> Optional.of(new Edit.Replace(oversizedValue(place), "sent "
                    + size(place, oversized(place)) + " for " + place.described()
                    + ", where the most allowed is " + statedMost(place).orElseThrow()));
            case OVERSIZE_WITH_NO_LIMIT -> Optional.of(new Edit.Replace(oversizedValue(place),
                    "sent " + size(place, oversized(place)) + " for " + place.described()
                            + ", which states no most"));
            case EMPTY_WITH_NO_RULE -> emptyOfItsKind(place).map(with -> new Edit.Replace(with,
                    "sent " + emptiness(with) + " for " + place.described()
                            + ", which nothing says may not be empty"));
        };
    }

    /** The accepted request with the change made to it, or nothing if it could not be sent. */
    private Optional<TestCase> applied(Edit edit, Operator operator, Place place,
            Operation operation, AcceptedRequests.Accepted accepted) {
        TestCase base = accepted.testCase();
        List<ParameterValue> values = new ArrayList<>(base.parameterValues());
        Optional<BodyValue> body = base.body();
        if (place.inTheBody()) {
            BodyValue was = body.orElseThrow();
            JsonValue changed = switch (edit) {
                case Edit.Replace replace -> Shapes.replaced(was.value(), place.steps(),
                        replace.with());
                case Edit.Remove ignored -> Shapes.removed(was.value(), place.steps());
                case Edit.Move ignored -> was.value();
            };
            if (changed.equals(was.value())) {
                return Optional.empty();
            }
            body = Optional.of(new BodyValue(was.mediaType(), changed, was.origin()));
        } else {
            int at = indexOf(values, place);
            ParameterValue was = values.get(at);
            switch (edit) {
                case Edit.Replace replace -> {
                    if (replace.with().equals(was.value())
                            || !RequestBuilder.canBeSentFrom(replace.with(), place.location())) {
                        return Optional.empty();
                    }
                    values.set(at, ParameterValue.of(was.name(), was.location(), replace.with(),
                            new ValueOrigin.Generated(operator.written())));
                }
                case Edit.Remove ignored -> values.remove(at);
                case Edit.Move move -> values.set(at, ParameterValue.of(was.name(), move.to(),
                        was.value(), was.origin()));
            }
        }
        Mutation mutation = new Mutation(accepted.from(), operator.written(), place.location(),
                place.path(), edit.description());
        return Optional.of(TestCase.changed(operation.id(), values, body, operator.intent(),
                mutation));
    }

    private static int indexOf(List<ParameterValue> values, Place place) {
        for (int at = 0; at < values.size(); at++) {
            ParameterValue value = values.get(at);
            if (value.location() == place.location() && value.name().equals(place.path())) {
                return at;
            }
        }
        throw new IllegalStateException("the place " + place.path() + " was found in this request "
                + "and is no longer in it");
    }

    // --- leaving out and moving ------------------------------------------------------------------

    /**
     * Whether a parameter can be left out of a request, or moved elsewhere in it, with the change
     * reaching the API: never one in the path, whose address would stop being this operation's, and
     * never a header the client would write back in.
     */
    private static boolean canBeLeftOutOrMoved(Place place) {
        return switch (place.location()) {
            case QUERY, COOKIE -> true;
            case HEADER -> !writtenByTheClient(place.path());
            case PATH, BODY -> false;
        };
    }

    /**
     * Where a required parameter could be moved to: the query string, a header or a cookie other
     * than its own, provided the operation declares nothing of that name there and the name and the
     * value can travel there.
     */
    private static List<ParameterLocation> destinations(Place place, Operation operation) {
        List<ParameterLocation> to = new ArrayList<>();
        for (ParameterLocation location : List.of(ParameterLocation.QUERY,
                ParameterLocation.HEADER, ParameterLocation.COOKIE)) {
            if (location == place.location()) {
                continue;
            }
            boolean taken = operation.parameters(location).stream().anyMatch(declared ->
                    location == ParameterLocation.HEADER
                            ? declared.name().equalsIgnoreCase(place.path())
                            : declared.name().equals(place.path()));
            boolean nameTravels = location == ParameterLocation.QUERY
                    || TOKEN.matcher(place.path()).matches();
            boolean clientsOwn = location == ParameterLocation.HEADER
                    && writtenByTheClient(place.path());
            if (!taken && nameTravels && !clientsOwn
                    && RequestBuilder.canBeSentFrom(place.value(), location)) {
                to.add(location);
            }
        }
        return to;
    }

    private static boolean writtenByTheClient(String header) {
        return WRITTEN_BY_THE_CLIENT.contains(header.toLowerCase(Locale.ROOT));
    }

    private static String where(ParameterLocation location) {
        return switch (location) {
            case QUERY -> "the query string";
            case HEADER -> "a header";
            case COOKIE -> "a cookie";
            case PATH -> "the path";
            case BODY -> "the body";
        };
    }

    // --- the wrong kind of value -----------------------------------------------------------------

    /**
     * Values of other kinds that the shape does not also accept, for a place inside a body. Never
     * {@code null}, which is a change of its own.
     */
    private List<JsonValue> kindsItIsNot(Place place) {
        List<JsonValue> others = new ArrayList<>();
        for (JsonValue candidate : List.of(JsonValue.of(NEITHER_A_NUMBER_NOR_A_YES_OR_NO),
                JsonValue.of(1), JsonValue.TRUE, JsonValue.object(Map.of()),
                JsonValue.array(List.of()))) {
            if (!sameKind(candidate, place.value())
                    && !Shapes.couldSatisfy(model, candidate, place.declared(), hops())) {
                others.add(candidate);
            }
        }
        return others;
    }

    private static boolean sameKind(JsonValue one, JsonValue other) {
        return one.getClass() == other.getClass();
    }

    private static String kindOf(CanonicalSchema shape) {
        return switch (shape) {
            case StringSchema ignored -> "a word";
            case NumberSchema ignored -> "a number";
            case BooleanSchema ignored -> "true or false";
            case ArraySchema ignored -> "a list";
            case ObjectSchema ignored -> "an object";
            default -> "something else";
        };
    }

    // --- one step past a limit -------------------------------------------------------------------

    /**
     * Every way of stepping one past a limit the document states for this place, each built only
     * when chosen.
     */
    private List<Function<Place, Edit.Replace>> stepsPastALimit(Place place) {
        List<Function<Place, Edit.Replace>> ways = new ArrayList<>();
        switch (place.shape()) {
            case NumberSchema number when place.value() instanceof JsonValue.JsonNumber -> {
                number.minimum().ifPresent(least -> ways.add(here -> number(here,
                        least.subtract(BigDecimal.ONE), "one below the smallest allowed, "
                                + least.toPlainString())));
                number.exclusiveMinimum().ifPresent(bound -> ways.add(here -> number(here, bound,
                        "which the description rules out as the smallest")));
                number.maximum().ifPresent(most -> ways.add(here -> number(here,
                        most.add(BigDecimal.ONE), "one above the largest allowed, "
                                + most.toPlainString())));
                number.exclusiveMaximum().ifPresent(bound -> ways.add(here -> number(here, bound,
                        "which the description rules out as the largest")));
            }
            case StringSchema text when place.value() instanceof JsonValue.JsonString word -> {
                int length = word.value().codePointCount(0, word.value().length());
                text.minLength().filter(least -> least >= 1 && least - 1 < length)
                        .ifPresent(least -> ways.add(here -> new Edit.Replace(
                                JsonValue.of(lengthened(word.value(), least - 1)), "sent "
                                        + characters(least - 1) + " for " + here.described()
                                        + ", one fewer than the fewest allowed, " + least)));
                text.maxLength().filter(most -> most < generation.longestString())
                        .ifPresent(most -> ways.add(here -> new Edit.Replace(
                                JsonValue.of(lengthened(word.value(), most + 1)), "sent "
                                        + characters(most + 1) + " for " + here.described()
                                        + ", one more than the most allowed, " + most)));
            }
            case ArraySchema list when place.value() instanceof JsonValue.JsonArray elements -> {
                int size = elements.elements().size();
                list.minItems().filter(least -> least >= 1 && least - 1 < size)
                        .ifPresent(least -> ways.add(here -> new Edit.Replace(
                                JsonValue.array(elements.elements().subList(0, least - 1)),
                                "sent " + items(least - 1) + " for " + here.described()
                                        + ", one fewer than the fewest allowed, " + least)));
                list.maxItems().filter(most -> most < generation.mostItems() && size > 0)
                        .ifPresent(most -> ways.add(here -> new Edit.Replace(
                                repeated(elements, most + 1), "sent " + items(most + 1) + " for "
                                        + here.described() + ", one more than the most allowed, "
                                        + most)));
            }
            default -> {
            }
        }
        return ways;
    }

    private static Edit.Replace number(Place place, BigDecimal value, String why) {
        JsonValue with = JsonValue.of(value);
        return new Edit.Replace(with, "sent " + value.toPlainString() + " for "
                + place.described() + ", " + why);
    }

    // --- off the list ----------------------------------------------------------------------------

    /** The closed list of values this place accepts, where the document states one. */
    private static List<JsonValue> acceptedList(Place place) {
        List<JsonValue> stated = place.declared().metadata().enumeration();
        return stated.isEmpty() ? place.shape().metadata().enumeration() : stated;
    }

    /**
     * A value of the same kind as those on the list, and not on it. For words, either one of them
     * with its capitals turned round - which is where a comparison that ignores case gives itself
     * away - or one of them with a letter added; for numbers, one above the largest; for a list of
     * one yes-or-no, the other one.
     */
    private Optional<JsonValue> outsideTheList(Place place) {
        List<JsonValue> list = acceptedList(place);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        if (list.stream().allMatch(JsonValue.JsonString.class::isInstance)) {
            String first = ((JsonValue.JsonString) list.get(0)).value();
            List<JsonValue> candidates = new ArrayList<>();
            for (String candidate : List.of(caseTurnedRound(first), first + "X")) {
                JsonValue value = JsonValue.of(candidate);
                if (!list.contains(value) && !candidates.contains(value)) {
                    candidates.add(value);
                }
            }
            return candidates.isEmpty()
                    ? Optional.empty()
                    : Optional.of(candidates.get(random.nextInt(candidates.size())));
        }
        if (list.stream().allMatch(JsonValue.JsonNumber.class::isInstance)) {
            BigDecimal largest = list.stream()
                    .map(value -> ((JsonValue.JsonNumber) value).value())
                    .reduce(BigDecimal::max).orElseThrow();
            return Optional.of(JsonValue.of(largest.add(BigDecimal.ONE)));
        }
        if (list.size() == 1 && list.get(0) instanceof JsonValue.JsonBoolean only) {
            return Optional.of(JsonValue.of(!only.value()));
        }
        return Optional.empty();
    }

    private static String caseTurnedRound(String word) {
        StringBuilder turned = new StringBuilder(word.length());
        word.codePoints().forEach(point -> turned.appendCodePoint(Character.isUpperCase(point)
                ? Character.toLowerCase(point) : Character.toUpperCase(point)));
        return turned.toString();
    }

    // --- against the spelling rule ---------------------------------------------------------------

    /**
     * A word close to this one that its stated pattern refuses: the first of a few short
     * variations the rule turns down. Nothing when the rule accepts all of them, or cannot be read.
     */
    private static Optional<JsonValue> refusedByThePattern(Place place) {
        StringSchema text = (StringSchema) place.shape();
        String word = ((JsonValue.JsonString) place.value()).value();
        List<String> variations = new ArrayList<>();
        if (word.length() <= LONGEST_HELD_AGAINST_A_PATTERN) {
            if (!word.isEmpty()) {
                variations.add("!" + word.substring(word.offsetByCodePoints(0, 1)));
                int middle = word.offsetByCodePoints(0, word.codePointCount(0, word.length()) / 2);
                variations.add(word.substring(0, middle) + " " + word.substring(middle));
            }
            variations.add(word + "!");
        }
        variations.add("!");
        variations.add("!!!");
        for (String variation : variations) {
            if (!MatchingStrings.allows(text.pattern(), variation)) {
                return Optional.of(JsonValue.of(variation));
            }
        }
        return Optional.empty();
    }

    // --- nothing ---------------------------------------------------------------------------------

    /** Whether the document allows this place to be {@code null}, wherever it says so. */
    private boolean mayBeNull(Place place) {
        return place.declared().metadata().nullable() || place.shape().metadata().nullable()
                || Shapes.couldSatisfy(model, JsonValue.NULL, place.declared(), hops());
    }

    /**
     * Whether this place can be sent empty at all: a word, a list or an object inside a body, or a
     * word, number or yes-or-no outside it - but never in the path, whose address an empty value
     * would change, and never a list or an object outside the body, which an empty value makes
     * disappear from the request rather than arrive empty.
     */
    private static boolean canBeEmptied(Place place) {
        if (place.location() == ParameterLocation.PATH || isEmpty(place.value())) {
            return false;
        }
        return emptyOfItsKind(place).isPresent();
    }

    private static Optional<JsonValue> emptyOfItsKind(Place place) {
        return switch (place.shape()) {
            case StringSchema ignored -> Optional.of(JsonValue.of(""));
            case NumberSchema ignored when !place.inTheBody() -> Optional.of(JsonValue.of(""));
            case BooleanSchema ignored when !place.inTheBody() -> Optional.of(JsonValue.of(""));
            case ArraySchema ignored when place.inTheBody() ->
                    Optional.of(JsonValue.array(List.of()));
            case ObjectSchema ignored when place.inTheBody() ->
                    Optional.of(JsonValue.object(Map.of()));
            default -> Optional.empty();
        };
    }

    private static boolean isEmpty(JsonValue value) {
        return switch (value) {
            case JsonValue.JsonString word -> word.value().isEmpty();
            case JsonValue.JsonArray list -> list.elements().isEmpty();
            case JsonValue.JsonObject object -> object.members().isEmpty();
            default -> false;
        };
    }

    /**
     * Whether the document rules out an empty value here: a word with a fewest length, a spelling
     * rule or a list of accepted words that turns the empty word down, or a named kind no empty
     * word is; a list with a fewest number of items; an object with properties it must have; and
     * outside a body, any number or yes-or-no, since an empty word is neither.
     */
    private boolean emptyIsForbidden(Place place) {
        return switch (place.shape()) {
            case StringSchema text -> text.minLength().filter(least -> least >= 1).isPresent()
                    || (text.pattern().isPresent() && !MatchingStrings.allows(text.pattern(), ""))
                    || (!acceptedList(place).isEmpty()
                            && !acceptedList(place).contains(JsonValue.of("")))
                    || text.format().filter(NEVER_EMPTY::contains).isPresent();
            case ArraySchema list -> list.minItems().filter(least -> least >= 1).isPresent();
            case ObjectSchema object -> object.minProperties().filter(least -> least >= 1)
                    .isPresent()
                    || object.required().stream().anyMatch(name -> object.property(name)
                            .filter(property -> !Shapes.onlyEverReturned(model, property, hops()))
                            .isPresent());
            case NumberSchema ignored -> true;
            case BooleanSchema ignored -> true;
            default -> false;
        };
    }

    private static String emptiness(JsonValue empty) {
        return switch (empty) {
            case JsonValue.JsonArray ignored -> "an empty list";
            case JsonValue.JsonObject ignored -> "an empty object";
            default -> "an empty word";
        };
    }

    // --- far too long ----------------------------------------------------------------------------

    /** The most characters or items the document allows here, when it states one. */
    private static Optional<Integer> statedMost(Place place) {
        return switch (place.shape()) {
            case StringSchema text when place.value() instanceof JsonValue.JsonString ->
                    text.maxLength();
            case ArraySchema list when place.value() instanceof JsonValue.JsonArray elements
                    && !elements.elements().isEmpty() -> list.maxItems();
            default -> Optional.empty();
        };
    }

    /**
     * Whether this place can be made far too long where the document says nothing about how long
     * it may be. A word only when nothing else about it is stated either - no pattern, no named
     * kind, no closed list - since ten thousand characters would break those, and then the document
     * would have ruled after all; a list only when its items need not differ, for the same reason,
     * and when it has items to repeat.
     */
    private static boolean canBeOversizedWithNoLimit(Place place) {
        return switch (place.shape()) {
            case StringSchema text when place.value() instanceof JsonValue.JsonString ->
                    text.maxLength().isEmpty() && text.pattern().isEmpty()
                            && text.format().isEmpty() && acceptedList(place).isEmpty();
            case ArraySchema list when place.value() instanceof JsonValue.JsonArray elements ->
                    list.maxItems().isEmpty() && !list.uniqueItems()
                            && !elements.elements().isEmpty();
            default -> false;
        };
    }

    /** How long an oversized value at this place is: characters for a word, items for a list. */
    private int oversized(Place place) {
        return place.shape() instanceof ArraySchema
                ? settings.oversizedItems() : settings.oversizedLength();
    }

    private JsonValue oversizedValue(Place place) {
        return switch (place.value()) {
            case JsonValue.JsonArray elements -> repeated(elements, settings.oversizedItems());
            case JsonValue.JsonString word ->
                    JsonValue.of(lengthened(word.value(), settings.oversizedLength()));
            default -> throw new IllegalStateException("only a word or a list is made too long");
        };
    }

    private static String size(Place place, int howMany) {
        return place.shape() instanceof ArraySchema ? items(howMany) : characters(howMany);
    }

    // --- building --------------------------------------------------------------------------------

    /**
     * The word cut or stretched to exactly this many characters: its own first characters, and past
     * its end its last one repeated - {@code a} for a word with none.
     */
    private static String lengthened(String word, int length) {
        int[] points = word.codePoints().toArray();
        int filler = points.length == 0 ? 'a' : points[points.length - 1];
        StringBuilder built = new StringBuilder(length);
        for (int at = 0; at < length; at++) {
            built.appendCodePoint(at < points.length ? points[at] : filler);
        }
        return built.toString();
    }

    /** A list of exactly this many items, the list's own items over and over. */
    private static JsonValue repeated(JsonValue.JsonArray elements, int howMany) {
        List<JsonValue> built = new ArrayList<>(howMany);
        for (int at = 0; at < howMany; at++) {
            built.add(elements.elements().get(at % elements.elements().size()));
        }
        return JsonValue.array(built);
    }

    private static String characters(int howMany) {
        return howMany + (howMany == 1 ? " character" : " characters");
    }

    private static String items(int howMany) {
        return howMany + (howMany == 1 ? " item" : " items");
    }

    private static String quoted(JsonValue value) {
        String written = JsonText.write(value);
        return written.length() <= QUOTED_AT_MOST
                ? written : written.substring(0, QUOTED_AT_MOST) + "...";
    }

    /** Whether a shape says enough about a value for anything but leaving it out to be judged. */
    private static boolean understood(CanonicalSchema shape) {
        return !(shape instanceof UnsupportedSchema) && !(shape instanceof NothingSchema);
    }

    private CanonicalSchema resolved(CanonicalSchema schema) {
        return Shapes.resolved(model, schema, hops());
    }

    private int hops() {
        return generation.hardNestingDepth();
    }

    private static List<Object> followedBy(List<Object> steps, Object step) {
        List<Object> down = new ArrayList<>(steps);
        down.add(step);
        return List.copyOf(down);
    }

    // --- the pieces ------------------------------------------------------------------------------

    /**
     * One value in a request that a change could be made to.
     *
     * @param location where it travels
     * @param path the way down to it, as a change records it; for a parameter, its name
     * @param steps the way down inside the body, empty for a parameter
     * @param declared its shape as declared where it is used, which may be a name for one declared
     *     elsewhere and may say it can be {@code null}
     * @param shape that shape, with any such name followed
     * @param value what the accepted request sent there
     * @param required whether the document says it must be sent
     */
    private record Place(ParameterLocation location, String path, List<Object> steps, CanonicalSchema declared, CanonicalSchema shape, JsonValue value,
            boolean required) {

        boolean inTheBody() {
            return location == ParameterLocation.BODY;
        }

        /** This place in words. */
        String described() {
            return switch (location) {
                case QUERY -> "query parameter '" + path + "'";
                case HEADER -> "header '" + path + "'";
                case COOKIE -> "cookie '" + path + "'";
                case PATH -> "path parameter '" + path + "'";
                case BODY -> path;
            };
        }
    }

    /** What a change does to the value at its place. */
    private sealed interface Edit {

        String description();

        /** Sends something else there. */
        record Replace(JsonValue with, String description) implements Edit {
        }

        /** Sends nothing there at all. */
        record Remove(String description) implements Edit {
        }

        /** Sends the same value somewhere else. */
        record Move(ParameterLocation to, String description) implements Edit {
        }
    }

    /**
     * The kinds of change, each with the name a person switches it off by and the family it
     * belongs to.
     */
    enum Operator {

        DROP_REQUIRED("dropRequired", true),
        WRONG_LOCATION("wrongLocation", true),
        WRONG_TYPE("wrongType", true),
        OUTSIDE_A_BOUND("outsideABound", true),
        BREAK_AN_ENUMERATION("breakAnEnumeration", true),
        BREAK_A_PATTERN("breakAPattern", true),
        SEND_NULL("sendNull", true),
        SEND_EMPTY("sendEmpty", true),
        OVERSIZE("oversize", true),
        OVERSIZE_WITH_NO_LIMIT("oversizeWithNoLimit", false),
        EMPTY_WITH_NO_RULE("emptyWithNoRule", false);

        private final String written;
        private final boolean breaksTheDocument;

        Operator(String written, boolean breaksTheDocument) {
            this.written = written;
            this.breaksTheDocument = breaksTheDocument;
        }

        /** Its name, as a setting, and as a change records what kind it was. */
        String written() {
            return written;
        }

        /** Whether what it sends is something the document rules out. */
        boolean breaksTheDocument() {
            return breaksTheDocument;
        }

        /** What a request it makes expects of the API. */
        Intent intent() {
            return breaksTheDocument ? Intent.REFUSAL_EXPECTED : Intent.UNKNOWN;
        }

        /** Whether it and its family are both switched on. */
        boolean isOn(MutationSettings settings) {
            boolean family = breaksTheDocument ? settings.violations() : settings.probes();
            return family && switch (this) {
                case DROP_REQUIRED -> settings.dropRequired();
                case WRONG_LOCATION -> settings.wrongLocation();
                case WRONG_TYPE -> settings.wrongType();
                case OUTSIDE_A_BOUND -> settings.outsideABound();
                case BREAK_AN_ENUMERATION -> settings.breakAnEnumeration();
                case BREAK_A_PATTERN -> settings.breakAPattern();
                case SEND_NULL -> settings.sendNull();
                case SEND_EMPTY -> settings.sendEmpty();
                case OVERSIZE -> settings.oversize();
                case OVERSIZE_WITH_NO_LIMIT -> settings.oversizeWithNoLimit();
                case EMPTY_WITH_NO_RULE -> settings.emptyWithNoRule();
            };
        }
    }
}

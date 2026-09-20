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

import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.schema.AnySchema;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.SchemaReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every place one operation's request has for a value to go, worked out from the document.
 *
 * <p>Somebody writing a list of values for an API writes one entry per place: a parameter by its
 * name, the whole body under {@code body}, and anything inside either of those by the way down to
 * it - {@code body.owner.email}, {@code tags[]}. This works out which of those places the document
 * actually has, so that an entry naming one it does not - a parameter that was renamed, a property
 * misspelled, an operation somebody remembered wrong - can be pointed at before a single request is
 * sent, instead of sitting in the file looking useful and doing nothing.
 *
 * <p>It also knows the two ways a place can exist and still never take a value from a list: where
 * the document gives the closed list of values it accepts, whose word is final, and a property the
 * document says the API only ever sends back, which is never ours to send.
 *
 * <p>Saying nothing is always safe here and saying the wrong thing is not, so two things are left
 * unjudged. Where the document runs out - a shape written in a way the parser could not read, or
 * the point at which one starts repeating itself - nothing below that is judged, though everything
 * above it still is. And a name the document declares in two places at once, where one entry feeds
 * both, is not judged either, because what settles one of them need not settle the other.
 */
final class WhereAValueCanGo {

    /** How deep into a value this looks. Deeper than any list of values anybody writes by hand. */
    private static final int AS_DEEP_AS_ITEMS_GO = 6;

    /** Why a closed list of accepted values leaves a dictionary nothing to say. */
    static final String THE_DOCUMENT_SETTLES_IT =
            "a place whose whole list of values the document declares";

    /** Why a property the API only sends back is never filled from anywhere. */
    static final String ONLY_EVER_RETURNED =
            "a property the document says the API only ever sends back";

    /** Why nothing inside a body is asked for when the document shows that body in full. */
    static final String A_BODY_SHOWN_IN_FULL =
            "a piece of a body the document itself writes out in full, which is sent as written";

    private final Set<String> places;
    private final Set<String> whereAnyNameIsPossible;
    private final Map<String, String> thatNothingWouldUse;

    private WhereAValueCanGo(Set<String> places, Set<String> whereAnyNameIsPossible,
            Map<String, String> thatNothingWouldUse) {
        this.places = places;
        this.whereAnyNameIsPossible = whereAnyNameIsPossible;
        this.thatNothingWouldUse = thatNothingWouldUse;
    }

    /**
     * The places this operation's request has.
     *
     * @param operation the operation
     * @param model the API, for the shapes the document declares elsewhere by name
     * @return the places
     */
    static WhereAValueCanGo in(Operation operation, ApiModel model) {
        Set<String> places = new LinkedHashSet<>();
        Set<String> open = new LinkedHashSet<>();
        Map<String, String> unused = new LinkedHashMap<>();

        Map<String, List<Parameter>> byName = new LinkedHashMap<>();
        for (Parameter parameter : operation.parameters()) {
            byName.computeIfAbsent(parameter.name(), ignored -> new ArrayList<>()).add(parameter);
        }
        byName.forEach((name, declared) -> {
            places.add(name);
            // A name declared twice is one entry feeding two parameters, and what is true of one of
            // them need not be true of the other. The safe answer is to find its places and judge
            // none of them.
            Map<String, String> judgement = declared.size() == 1 ? unused : new LinkedHashMap<>();
            declared.forEach(parameter -> walk(name, parameter.schema(), parameter.location(),
                    model, places, open, judgement, Optional.empty(), true, 0,
                    new LinkedHashSet<>()));
        });

        body(operation, model).ifPresent(schema -> {
            places.add(ValueRequest.THE_BODY);
            walk(ValueRequest.THE_BODY, schema, ParameterLocation.BODY, model, places, open, unused,
                    Optional.empty(), true, 0, new LinkedHashSet<>());
        });
        // A body the document writes out in full is sent as written, so the request is never taken
        // apart and nothing inside it is ever asked for. The body itself is a different matter: a
        // list written for it is asked before the document's sample and wins.
        if (theDocumentShowsTheBodyInFull(operation)) {
            places.stream().filter(WhereAValueCanGo::isInsideTheBody)
                    .forEach(place -> unused.putIfAbsent(place, A_BODY_SHOWN_IN_FULL));
        }
        return new WhereAValueCanGo(places, open, unused);
    }

    /**
     * Whether the document has a place of this name at all.
     *
     * <p>A place under one nothing could be named below - a shape the parser could not read, the
     * point at which one repeats itself - counts as one this cannot judge, and is treated as
     * present.
     * The comparison is step by step rather than letter by letter, so that {@code body.city} is
     * not taken for a piece of {@code body.citizenship}.
     */
    boolean has(String place) {
        return places.contains(place)
                || whereAnyNameIsPossible.stream().anyMatch(prefix -> isUnder(place, prefix));
    }

    /** Whether this place is a piece of the request body rather than the body itself. */
    static boolean isInsideTheBody(String place) {
        return isUnder(place, ValueRequest.THE_BODY);
    }

    private static boolean isUnder(String place, String prefix) {
        return place.startsWith(prefix + ValueRequest.STEP)
                || place.startsWith(prefix + ValueRequest.EVERY_ELEMENT);
    }

    /**
     * Why nothing would ever take a value from a list written for this place, when nothing would.
     *
     * @param place the place
     * @return the reason, in the words a person should read, or nothing when values here are used
     */
    Optional<String> whyNothingWouldUseIt(String place) {
        return Optional.ofNullable(thatNothingWouldUse.get(place));
    }

    /** Whether the document offers a whole body of its own for the media type that would be sent. */
    private static boolean theDocumentShowsTheBodyInFull(Operation operation) {
        return operation.requestBody()
                .flatMap(declared -> RequestBuilder.mediaTypeToSend(declared)
                        .flatMap(declared::contentFor))
                .filter(content -> content.examples().stream()
                        .anyMatch(sample -> RequestBuilder.canBeSentFrom(sample,
                                ParameterLocation.BODY)))
                .isPresent();
    }

    private static Optional<CanonicalSchema> body(Operation operation, ApiModel model) {
        Optional<RequestBodyModel> declared = operation.requestBody();
        if (declared.isEmpty()) {
            return Optional.empty();
        }
        return RequestBuilder.mediaTypeToSend(declared.get())
                .flatMap(declared.get()::schemaFor)
                .map(schema -> resolved(schema, model));
    }

    /**
     * Works through one value's shape, writing down every place inside it.
     *
     * <p>A reason travels downwards: everything under a property the API only sends back, or under
     * one whose values the document lists in full, is as unusable as the property itself, because
     * nothing ever asks for a piece of a value it does not build.
     */
    private static void walk(String path, CanonicalSchema declared, ParameterLocation where,
            ApiModel model, Set<String> places, Set<String> open, Map<String, String> unused,
            Optional<String> inherited, boolean itsOwnListSettlesIt, int depth,
            Set<String> shapesAlreadyEntered) {
        if (depth > AS_DEEP_AS_ITEMS_GO) {
            open.add(path);
            return;
        }
        if (declared instanceof SchemaReference reference
                && !shapesAlreadyEntered.add(reference.name())) {
            // A shape that contains itself. Everything its names could spell has been spelt once
            // already, and going round again would only spell it longer.
            open.add(path);
            return;
        }
        CanonicalSchema schema = resolved(declared, model);
        Optional<String> carried = inherited.or(() -> itsOwnListSettlesIt
                ? settledByTheDocument(schema, where) : Optional.empty());
        carried.ifPresent(why -> unused.putIfAbsent(path, why));
        switch (schema) {
            case ObjectSchema object -> object.properties().forEach((name, property) -> {
                // Deliberately not opened up for an object that allows properties it does not
                // name, which is nearly every object OpenAPI declares. What the document would
                // accept is not the question: a value is only ever asked for under a name the
                // document writes down, so an entry for any other name would sit there unused
                // however willing the API is to receive it.
                String below = path + ValueRequest.STEP + name;
                places.add(below);
                Optional<String> why =
                        property.metadata().access() == SchemaMetadata.Access.READ_ONLY
                                ? Optional.of(ONLY_EVER_RETURNED) : carried;
                walk(below, property, where, model, places, open, unused, why, true, depth + 1,
                        new LinkedHashSet<>(shapesAlreadyEntered));
            });
            case ArraySchema list -> {
                String element = path + ValueRequest.EVERY_ELEMENT;
                places.add(element);
                walk(element, list.items(), where, model, places, open, unused, carried, true,
                        depth + 1, shapesAlreadyEntered);
            }
            // Every alternative's names are names this place can have, but none of their lists of
            // allowed values settles it: what is asked about is the choice, whose own list is
            // empty, so a list of allowed values on one branch of it decides nothing.
            case ChoiceSchema choice -> choice.alternatives().forEach(one ->
                    walk(path, one, where, model, places, open, unused, carried, false, depth + 1,
                            new LinkedHashSet<>(shapesAlreadyEntered)));
            case AnySchema ignored -> open.add(path);
            case io.restest.core.schema.UnsupportedSchema ignored -> open.add(path);
            case SchemaReference ignored -> open.add(path);
            default -> {
                // A word, a number, true or false: a place in its own right, already written down
                // by whoever named it, with nothing underneath.
            }
        }
    }

    /**
     * Why a closed list of allowed values leaves nothing for a dictionary to say about a place.
     *
     * <p>Only when at least one of the allowed values could actually be sent where the value goes.
     * A document that allows a path parameter nothing but empty words has contradicted itself, and
     * the run falls through to whatever else can answer - a list somebody wrote included.
     */
    private static Optional<String> settledByTheDocument(CanonicalSchema schema,
            ParameterLocation where) {
        List<JsonValue> allowed = schema.metadata().enumeration();
        if (allowed.isEmpty()
                || allowed.stream().noneMatch(value -> RequestBuilder.canBeSentFrom(value, where))) {
            return Optional.empty();
        }
        return Optional.of(THE_DOCUMENT_SETTLES_IT);
    }

    private static CanonicalSchema resolved(CanonicalSchema schema, ApiModel model) {
        CanonicalSchema seen = schema;
        for (int hops = 0; hops < 8 && seen instanceof SchemaReference reference; hops++) {
            CanonicalSchema next = model.resolve(reference).orElse(seen);
            if (next == seen) {
                return seen;
            }
            seen = next;
        }
        return seen;
    }
}

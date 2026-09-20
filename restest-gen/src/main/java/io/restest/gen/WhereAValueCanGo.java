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
import io.restest.core.model.RequestBodyModel;
import io.restest.core.schema.AnySchema;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.SchemaReference;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every place one operation's request has for a value to go, worked out from the document.
 *
 * <p>Somebody writing a list of values for an API writes one entry per place: a parameter by its
 * name, the whole body under {@code body}, a piece of the body by the way down to it
 * ({@code body.owner.email}). This works out which of those places the document actually has, so
 * that an entry naming one it does not - a parameter that was renamed, a property misspelled, an
 * operation somebody remembered wrong - can be pointed at before a single request is sent, instead
 * of sitting in the file looking useful and doing nothing.
 *
 * <p>It also knows the two ways a place can exist and still never take a value from a list: a
 * parameter the document gives a closed list of allowed values for, where the document's word is
 * final, and a property the document says the API only ever sends back.
 *
 * <p>Where the document runs out - a shape written in a way the parser could not read, or one that
 * contains itself - nothing below that point is judged, because being unable to name something is
 * not the same as knowing it is wrong. An object that merely allows properties it does not name is
 * not such a case: a value is only ever asked for under a name the document writes down, so an
 * entry for any other name would go unused however willing the API is to receive it.
 */
final class WhereAValueCanGo {

    /** How deep into a body this looks. Deeper than any list of values anybody writes by hand. */
    private static final int AS_DEEP_AS_ITEMS_GO = 6;

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
        for (Parameter parameter : operation.parameters()) {
            places.add(parameter.name());
            closedListFor(parameter, model)
                    .ifPresent(why -> unused.put(parameter.name(), why));
        }
        body(operation, model).ifPresent(schema -> {
            places.add(ValueRequest.THE_BODY);
            walk(ValueRequest.THE_BODY, schema, model, places, open, unused, 0, new LinkedHashSet<>());
        });
        return new WhereAValueCanGo(places, open, unused);
    }

    /**
     * Whether the document has a place of this name at all.
     *
     * <p>A place under one nothing could be named below - a shape the parser could not read, a
     * shape that contains itself - counts as one this cannot judge, and is treated as present.
     * The comparison is step by step rather than letter by letter, so that {@code body.city} is
     * not taken for a piece of {@code body.citizenship}.
     */
    boolean has(String place) {
        return places.contains(place)
                || whereAnyNameIsPossible.stream().anyMatch(prefix -> isUnder(place, prefix));
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

    /**
     * Why a closed list of allowed values leaves nothing for a dictionary to say about a parameter.
     *
     * <p>Only when at least one of the allowed values could actually be sent where the parameter
     * goes. A document that allows a path parameter nothing but empty words has contradicted itself,
     * and the run falls through to whatever else can answer - a list somebody wrote included.
     */
    private static Optional<String> closedListFor(Parameter parameter, ApiModel model) {
        CanonicalSchema schema = resolved(parameter.schema(), model);
        java.util.List<JsonValue> allowed = schema.metadata().enumeration();
        if (allowed.isEmpty()
                || allowed.stream().noneMatch(value ->
                        RequestBuilder.canBeSentFrom(value, parameter.location()))) {
            return Optional.empty();
        }
        return Optional.of("a parameter whose whole list of values the document declares");
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

    private static void walk(String path, CanonicalSchema declared, ApiModel model,
            Set<String> places, Set<String> open, Map<String, String> unused, int depth,
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
        switch (schema) {
            case ObjectSchema object -> {
                // Deliberately not opened up for an object that allows properties it does not
                // name, which is nearly every object OpenAPI declares. What the document would
                // accept is not the question: a value is only ever asked for under a name the
                // document writes down, so an entry for any other name would sit there unused
                // however willing the API is to receive it.
                object.properties().forEach((name, property) -> {
                    String below = path + ValueRequest.STEP + name;
                    places.add(below);
                    if (property.metadata().access() == SchemaMetadata.Access.READ_ONLY) {
                        unused.put(below, "a property the document says the API only ever sends back");
                        return;
                    }
                    walk(below, property, model, places, open, unused, depth + 1,
                            new LinkedHashSet<>(shapesAlreadyEntered));
                });
            }
            case ArraySchema list -> {
                String element = path + ValueRequest.EVERY_ELEMENT;
                places.add(element);
                walk(element, list.items(), model, places, open, unused, depth + 1,
                        shapesAlreadyEntered);
            }
            case ChoiceSchema choice -> choice.alternatives().forEach(one ->
                    walk(path, one, model, places, open, unused, depth + 1,
                            new LinkedHashSet<>(shapesAlreadyEntered)));
            case AnySchema ignored -> open.add(path);
            case io.restest.core.schema.UnsupportedSchema ignored -> open.add(path);
            case SchemaReference ignored -> open.add(path);
            default -> {
                // A word, a number, true or false: a place in its own right, already added by
                // whoever named it, with nothing underneath.
            }
        }
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

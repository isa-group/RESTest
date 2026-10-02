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
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.Parameter;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaReference;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The names a request to this API can ask a value for, and which of them a name heard in a reply
 * answers.
 *
 * <p>Every value the API sends back comes with a name, and the memory of what it returned keeps
 * each value under its name so that the next request wanting something of that name can be sent
 * one that exists. A request only ever asks under a name the document gives - a parameter's, or a
 * property's somewhere inside a body - so a value kept under any other name is never asked for. And
 * there can be a great many of those: an API's diagnostic pages return one name for every class in
 * the program, two thousand of them within three seconds, and a memory that keeps them has no room
 * left for the token a login hands back a minute later.
 *
 * <p>So the memory asks this first, for every name it hears: which of the names the document asks
 * for does this one answer? It files the value under those, and under nothing else. Today a name
 * answers only itself, and only when some operation asks for it. Recognising two different names as
 * the same thing - a reply's {@code token} where a request wants a {@code refreshToken} - would be
 * a change to this one question, with nothing else in the memory or in what reads it changed.
 *
 * <p>The names are worked out once, from the document, the way a request names its places: a
 * parameter by its own name, the whole body as {@code body}, and anything inside either by the name
 * of the property it is, however deep - a piece of a list goes by the name of the list. A body on a
 * {@code GET} or a {@code HEAD} adds nothing, since the client that sends requests never sends one,
 * and neither does a property the document says the API only ever sends back.
 */
final class NamesTheDocumentAsksFor {

    /**
     * How many steps into a value the walk goes - into a property, a list, a choice or a named
     * shape. Only a guard: a named shape is entered once whatever refers to it, which is what ends
     * a shape that contains itself, and no document goes nearly this deep. A document built to go
     * down for ever does not end the run.
     */
    private static final int DEEPER_THAN_ANY_DOCUMENT = 64;

    private final Set<String> names;

    private NamesTheDocumentAsksFor(Set<String> names) {
        this.names = Set.copyOf(names);
    }

    /**
     * The names this document asks values for.
     *
     * @param model the API being tested
     * @return them
     */
    static NamesTheDocumentAsksFor in(ApiModel model) {
        Objects.requireNonNull(model, "model");
        Set<String> names = new HashSet<>();
        Set<String> shapesEntered = new HashSet<>();
        for (Operation operation : model.operations()) {
            for (Parameter parameter : operation.parameters()) {
                names.add(parameter.name());
                namesInside(parameter.schema(), model, names, shapesEntered, 0);
            }
            if (RequestBuilder.cannotBeSentWithABody(operation.method())) {
                continue;
            }
            operation.requestBody().ifPresent(body -> RequestBuilder.mediaTypeToSend(body)
                    .flatMap(body::schemaFor)
                    .ifPresent(schema -> {
                        names.add(ValueRequest.THE_BODY);
                        namesInside(schema, model, names, shapesEntered, 0);
                    }));
        }
        return new NamesTheDocumentAsksFor(names);
    }

    /**
     * The names the document asks for that a name heard in a reply answers: the name itself when
     * some operation asks for it, and nothing otherwise.
     *
     * @param heard the name a value came with in a reply
     * @return the names to keep the value under, empty when no operation would ever ask for it
     */
    Set<String> answeredBy(String heard) {
        Objects.requireNonNull(heard, "heard");
        return names.contains(heard) ? Set.of(heard) : Set.of();
    }

    /** How many different names the document asks values for. */
    int size() {
        return names.size();
    }

    private static void namesInside(CanonicalSchema schema, ApiModel model, Set<String> names,
            Set<String> shapesEntered, int depth) {
        if (depth > DEEPER_THAN_ANY_DOCUMENT) {
            return;
        }
        switch (schema) {
            case SchemaReference reference -> {
                if (shapesEntered.add(reference.name())) {
                    model.resolve(reference).ifPresent(named ->
                            namesInside(named, model, names, shapesEntered, depth + 1));
                }
            }
            case ObjectSchema object -> {
                object.properties().forEach((name, property) -> {
                    // A property the API only ever sends back is never built into a request, so
                    // nothing ever asks for it, or for anything inside it.
                    if (Shapes.onlyEverReturned(model, property, DEEPER_THAN_ANY_DOCUMENT)) {
                        return;
                    }
                    names.add(name);
                    namesInside(property, model, names, shapesEntered, depth + 1);
                });
                // A member of a map has no name the document gives, but what is inside one does.
                object.additionalProperties().ifPresent(member ->
                        namesInside(member, model, names, shapesEntered, depth + 1));
            }
            case ArraySchema list -> namesInside(list.items(), model, names, shapesEntered,
                    depth + 1);
            case ChoiceSchema choice -> choice.alternatives().forEach(one ->
                    namesInside(one, model, names, shapesEntered, depth + 1));
            default -> {
                // A word, a number, true or false, or a shape nobody could read: nothing named
                // inside it.
            }
        }
    }
}

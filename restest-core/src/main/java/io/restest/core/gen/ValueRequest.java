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
package io.restest.core.gen;

import io.restest.core.json.JsonValue;
import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import io.restest.core.schema.CanonicalSchema;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A question put to whoever might know a good value: "for this part of this operation's request,
 * whose shape is this, what should I send?"
 *
 * <p>Everything a knowledgeable answerer might use is here. The name matters because a parameter
 * called {@code email} wants an e-mail address whatever its schema says; the operation matters
 * because the same name can mean different things in different places; the schema matters because
 * whatever is sent has to satisfy it.
 *
 * <p>Usually the part in question is a parameter. It can also be the <em>whole request body</em>,
 * which is asked about in exactly the same way: the location is then the body, the shape is the one
 * the document declares for the media type being sent, and the name is the word {@code body} -
 * there being no name of its own to use, since OpenAPI 3.x gives a body none. An answerer that
 * keeps whole objects, rather than single words and numbers, is answering this kind of question.
 *
 * <p>The schema handed over is always a real shape, never a pointer to one named elsewhere: chasing
 * those down is done once, before anyone is asked, so that no answerer has to know how the
 * specification was organised. What survives that chase is the shape's <em>name</em>, when the
 * document gave it one, because "this is a Pet" is worth knowing to anybody keeping a list of
 * values that worked for a Pet.
 *
 * @param operation the operation whose request is being built
 * @param name the parameter's name, as the specification writes it, or {@code body} for the request
 *     body itself
 * @param location where the value goes in the request: the path, the query string, a header, a
 *     cookie, or the body
 * @param schema the shape the value has to satisfy
 * @param examples sample values the document offers for the parameter itself - or, for a body, for
 *     the media type being sent - rather than for its shape, in the order it wrote them. Empty for
 *     anything nested inside that shape, since a sample of the whole is not a sample of one of its
 *     parts
 * @param shape the name the document gave this shape, when it declared it once and referred to it
 *     by name. Absent when the shape was written out where it is used, which has no name to give
 */
public record ValueRequest(
        OperationId operation,
        String name,
        ParameterLocation location,
        CanonicalSchema schema,
        List<JsonValue> examples,
        Optional<String> shape) {

    public ValueRequest {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(examples, "examples");
        Objects.requireNonNull(shape, "shape");
        examples = List.copyOf(examples);
        if (name.isBlank()) {
            throw new IllegalArgumentException("a value is asked for by the name of a parameter");
        }
    }

    /**
     * A question about a parameter the document offers no sample value of its own for.
     *
     * @param operation the operation whose request is being built
     * @param name the parameter's name, as the specification writes it
     * @param location where the value goes in the request
     * @param schema the shape the value has to satisfy
     * @return the question
     */
    public static ValueRequest of(OperationId operation, String name, ParameterLocation location,
            CanonicalSchema schema) {
        return new ValueRequest(operation, name, location, schema, List.of(), Optional.empty());
    }

    /**
     * A question about a parameter the document offers sample values of its own for.
     *
     * @param operation the operation whose request is being built
     * @param name the parameter's name, as the specification writes it
     * @param location where the value goes in the request
     * @param schema the shape the value has to satisfy
     * @param examples the sample values the document offers for the parameter itself
     * @return the question
     */
    public static ValueRequest of(OperationId operation, String name, ParameterLocation location,
            CanonicalSchema schema, List<JsonValue> examples) {
        return new ValueRequest(operation, name, location, schema, examples, Optional.empty());
    }

    /**
     * The same question about a different shape, for an answerer working through a nested one.
     *
     * <p>Still the same value, so a sample the document offered for it still applies: this is how a
     * shape named elsewhere is looked up, not how a piece of one is reached.
     */
    public ValueRequest about(CanonicalSchema value) {
        return new ValueRequest(operation, name, location, value, examples, shape);
    }

    /**
     * The same question about the shape the document declared under this name.
     *
     * <p>Still the same value - a pointer to a shape has been followed to the shape itself - so
     * what the document said about the value still applies, and the name now travels with it. That
     * is what lets a list of values that worked for a {@code Pet} be found again the next time a
     * {@code Pet} is wanted.
     */
    public ValueRequest aboutTheShapeNamed(String named, CanonicalSchema value) {
        return new ValueRequest(operation, name, location, value, examples,
                Optional.of(Objects.requireNonNull(named, "named")));
    }

    /**
     * The same question about a named piece of a larger shape - one property of an object.
     *
     * <p>A different value, so the samples offered for the whole are left behind: a sample owner is
     * not a sample of the owner's first name.
     */
    public ValueRequest about(String property, CanonicalSchema value) {
        return new ValueRequest(operation, property, location, value, List.of(), Optional.empty());
    }

    /**
     * The same question about an unnamed piece of a larger shape - one element of a list.
     *
     * <p>Like a named piece, and for the same reason: a sample list of three numbers is a sample of
     * the list, not of each number in it. The name stays, there being no better one to give an
     * element than the name of the list it belongs to.
     */
    public ValueRequest aboutAPieceOf(CanonicalSchema value) {
        return new ValueRequest(operation, name, location, value, List.of(), Optional.empty());
    }
}

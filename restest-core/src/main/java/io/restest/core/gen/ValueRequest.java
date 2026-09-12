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

import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import io.restest.core.schema.CanonicalSchema;
import java.util.Objects;

/**
 * A question put to whoever might know a good value: "for this parameter of this operation, whose
 * shape is this, what should I send?"
 *
 * <p>Everything a knowledgeable answerer might use is here. The name matters because a parameter
 * called {@code email} wants an e-mail address whatever its schema says; the operation matters
 * because the same name can mean different things in different places; the schema matters because
 * whatever is sent has to satisfy it.
 *
 * <p>The schema handed over is always a real shape, never a pointer to one named elsewhere: chasing
 * those down is done once, before anyone is asked, so that no answerer has to know how the
 * specification was organised.
 *
 * @param operation the operation whose request is being built
 * @param name the parameter's name, as the specification writes it
 * @param location where the value goes in the request: the path, the query string, a header or a
 *     cookie
 * @param schema the shape the value has to satisfy
 */
public record ValueRequest(
        OperationId operation,
        String name,
        ParameterLocation location,
        CanonicalSchema schema) {

    public ValueRequest {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(schema, "schema");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a value is asked for by the name of a parameter");
        }
    }

    /** The same question about a different shape, for an answerer working through a nested one. */
    public ValueRequest about(CanonicalSchema value) {
        return new ValueRequest(operation, name, location, value);
    }

    /** The same question about a named piece of a larger shape - one property of an object. */
    public ValueRequest about(String property, CanonicalSchema value) {
        return new ValueRequest(operation, property, location, value);
    }
}

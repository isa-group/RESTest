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
package io.restest.core.model;

import java.util.Objects;

/**
 * How a parameter's value is written onto the wire.
 *
 * <p>It matters as soon as a value is not a single scalar: a list of tags in the query string is
 * {@code tags=a&tags=b} in one style and {@code tags=a,b} in another, and an API that documents one
 * will reject the other. The engine at M1.3 needs to be told which, so the model carries it rather
 * than guessing.
 *
 * <p>The names are OpenAPI's. Where a document says nothing, {@link #defaultFor} gives the default
 * the format defines.
 */
public enum ParameterStyle {
    /** {@code ;name=value}, path only. */
    MATRIX,
    /** {@code .value}, path only. */
    LABEL,
    /** {@code name=value}, the default for query and cookie parameters. */
    FORM,
    /** {@code value}, the default for path and header parameters. */
    SIMPLE,
    /** Space-separated array values, query only. */
    SPACE_DELIMITED,
    /** Pipe-separated array values, query only. */
    PIPE_DELIMITED,
    /** {@code name[property]=value} for objects, query only. */
    DEEP_OBJECT;

    /** The style OpenAPI uses when a parameter in that location does not name one. */
    public static ParameterStyle defaultFor(ParameterLocation location) {
        return switch (Objects.requireNonNull(location, "location")) {
            case PATH, HEADER -> SIMPLE;
            case QUERY, COOKIE -> FORM;
        };
    }

    /**
     * Whether this style expands a composite value into several name/value pairs when exploded.
     *
     * <p>OpenAPI's default for {@code explode} is {@code true} for {@link #FORM} and {@code false}
     * for every other style, which is the one piece of that table a caller needs before it has a
     * value in hand.
     */
    public boolean explodesByDefault() {
        return this == FORM;
    }
}

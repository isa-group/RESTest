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

import io.restest.core.schema.CanonicalSchema;
import java.util.Objects;
import java.util.Optional;

/**
 * One input an operation takes, and everything needed to send it.
 *
 * <p>A parameter carries its value in one of two ways, and the model keeps both. Most declare a
 * shape and a serialisation style: {@code ?tags=a&tags=b}. Some declare a media type instead -
 * legal in every OpenAPI 3.x and used for JSON in a query string, {@code ?filter={"status":"sold"}}
 * - and for those {@link #mediaType()} is present and the style is not used. Without somewhere to
 * put that, such an operation could only be skipped, over a parameter the tool can generate values
 * for perfectly well.
 *
 * @param name the name as the API expects it, case included
 * @param location where it travels
 * @param required whether the API refuses the request without it
 * @param schema the shape of accepted values
 * @param style how a value is written onto the wire, when it is written by style
 * @param explode whether a composite value expands into several name/value pairs
 * @param mediaType the media type the value is serialised as, when the document declares one
 *     instead of a style
 * @param description what the document says it is for
 */
public record Parameter(
        String name,
        ParameterLocation location,
        boolean required,
        CanonicalSchema schema,
        ParameterStyle style,
        boolean explode,
        Optional<String> mediaType,
        Optional<String> description) {

    public Parameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(description, "description");
        // Normalised like every other media type in the model, and for the same reason: a document
        // writing APPLICATION/JSON or application/json; charset=utf-8 means application/json, and a
        // serialiser comparing this literally would fall through to a default.
        mediaType = mediaType.map(MediaTypes::normalise);
        if (name.isBlank()) {
            throw new IllegalArgumentException("a parameter has a name");
        }
        // A path parameter is required by definition - the request cannot be assembled without it,
        // whatever the document says. OpenAPI requires `required: true` there, and a large share of
        // real documents omit it anyway. Reading them charitably costs nothing and keeps operations
        // testable that would otherwise be skipped over a formality; the alternative, throwing, would
        // punish the user for someone else's omission.
        if (location == ParameterLocation.PATH) {
            required = true;
        }
    }

    /** A parameter with the default style and explode for its location. */
    public static Parameter of(String name, ParameterLocation location, boolean required,
            CanonicalSchema schema) {
        ParameterStyle style = ParameterStyle.defaultFor(location);
        return new Parameter(name, location, required, schema, style, style.explodesByDefault(),
                Optional.empty(), Optional.empty());
    }

    /** A parameter whose value is serialised as the given media type rather than by style. */
    public static Parameter ofContent(String name, ParameterLocation location, boolean required,
            CanonicalSchema schema, String mediaType) {
        return new Parameter(name, location, required, schema,
                ParameterStyle.defaultFor(location), false,
                Optional.of(Objects.requireNonNull(mediaType, "mediaType")), Optional.empty());
    }

    /** Whether the value is serialised as a media type rather than by style and explode. */
    public boolean isContentSerialised() {
        return mediaType.isPresent();
    }
}

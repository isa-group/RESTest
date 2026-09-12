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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What an operation accepts in the body of a request.
 *
 * <p>One shape per media type, because an API may take the same resource as JSON, as a form or as
 * XML and describe each differently. Which one to send is a generation decision, not a modelling
 * one, so the model keeps them all.
 *
 * @param required whether the API refuses a request that has no body
 * @param content the shape accepted for each media type, keyed by the normalised media type
 * @param description what the document says the body is for
 */
public record RequestBodyModel(
        boolean required,
        Map<String, CanonicalSchema> content,
        Optional<String> description) {

    public RequestBodyModel {
        Objects.requireNonNull(description, "description");
        content = MediaTypes.normaliseKeys(content, "content");
        // A body that must be sent and that declares no media type describes a request nobody can
        // assemble: there is no content type to send it as and no shape to fill in. Refused here so
        // that the parser reports one operation, rather than the engine failing on every request it
        // generates for it.
        if (required && content.isEmpty()) {
            throw new IllegalArgumentException(
                    "a required request body declares no media type, so no request could be sent");
        }
    }

    /** A body of the given shape, sent as {@code application/json}. */
    public static RequestBodyModel json(CanonicalSchema schema, boolean required) {
        return new RequestBodyModel(required, Map.of("application/json", schema), Optional.empty());
    }

    /**
     * The shape accepted for the given media type.
     *
     * <p>Exact match first, then the ranges the document may have used instead - {@code
     * application/*}, then <code>*&#47;*</code> - and whatever case and parameters either was written with.
     */
    public Optional<CanonicalSchema> schemaFor(String mediaType) {
        return MediaTypes.lookup(content, mediaType);
    }

    /** The media types this body can be sent as, normalised, in declaration order. */
    public Set<String> mediaTypes() {
        return content.keySet();
    }
}

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
 * A header that a response declares it will carry.
 *
 * <p>{@code required} is kept alongside the header's shape because it is what makes it possible to
 * check for a missing header later: without it, a header the API promised and failed to send would
 * be indistinguishable from one it never promised at all.
 *
 * @param required whether the API states the header is always present
 * @param schema the shape of its value
 * @param description what the document says it means
 */
public record HeaderModel(boolean required, CanonicalSchema schema, Optional<String> description) {

    public HeaderModel {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(description, "description");
    }

    /** A header of the given shape. */
    public static HeaderModel of(CanonicalSchema schema, boolean required) {
        return new HeaderModel(required, schema, Optional.empty());
    }
}

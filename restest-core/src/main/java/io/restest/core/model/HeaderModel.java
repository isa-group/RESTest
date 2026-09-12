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
 * A header a response declares it will carry.
 *
 * <p>{@code required} is kept as well as the shape, because it is what the "missing required
 * header" oracle at M3.1 judges against: without it, a header the API promised and did not send is
 * indistinguishable from one it never promised.
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

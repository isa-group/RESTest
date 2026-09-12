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
package io.restest.core.schema;

import java.util.Objects;

/**
 * A schema no value satisfies: the JSON Schema literal {@code false}.
 *
 * <p>Its common spelling is {@code additionalProperties: false}, which is one of the most frequent
 * things a strict API says about itself. Without a way to write it down, that statement has to be
 * mapped either to "the document is silent", which invites the generator to add a property the API
 * will reject, or to {@link UnsupportedSchema}, which claims we did not understand something we
 * understood perfectly.
 *
 * <p>The exact dual of {@link AnySchema}: there, every value is accepted; here, none is.
 */
public record NothingSchema(SchemaMetadata metadata) implements CanonicalSchema {

    public NothingSchema {
        Objects.requireNonNull(metadata, "metadata");
    }

    /** No value at all. */
    public static NothingSchema of() {
        return new NothingSchema(SchemaMetadata.none());
    }
}

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
 * A schema that declares no type: any value is accepted.
 *
 * <p>Legal and common - an empty schema, or one carrying only a description. It is a statement about
 * the API, not a failure to read the document, which is what separates it from
 * {@link UnsupportedSchema}: a generator meeting this one is free to send anything, while a
 * generator meeting an unsupported schema knows it is working blind and can say so.
 */
public record AnySchema(SchemaMetadata metadata) implements CanonicalSchema {

    public AnySchema {
        Objects.requireNonNull(metadata, "metadata");
    }

    /** Any value at all. */
    public static AnySchema of() {
        return new AnySchema(SchemaMetadata.none());
    }
}

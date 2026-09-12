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
 * A value that can only be {@code null}: {@code type: "null"}, which OpenAPI 3.1 allows and 3.0
 * does not.
 *
 * <p>Distinct from a nullable schema. {@code SchemaMetadata.nullable()} says "this string may also
 * be null"; this says "null is the only accepted value", which is rare but real - most often one
 * arm of a composition that M2.1 will fold in.
 */
public record NullSchema(SchemaMetadata metadata) implements CanonicalSchema {

    public NullSchema {
        Objects.requireNonNull(metadata, "metadata");
    }

    /** The null-only schema. */
    public static NullSchema of() {
        return new NullSchema(SchemaMetadata.none());
    }
}

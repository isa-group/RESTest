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
import java.util.Optional;

/**
 * An array of values that all share one shape.
 *
 * <p>Arrays whose elements each have their own, different declared shape (JSON Schema's tuple form,
 * {@code prefixItems}) are not represented here. A document using that is read as an
 * {@link UnsupportedSchema}, which says so, rather than being silently misread as an array of just
 * the first element's shape.
 *
 * @param metadata the type-independent facts
 * @param items the shape every element has
 * @param minItems the fewest elements an accepted array may have
 * @param maxItems the most
 * @param uniqueItems whether the API requires the elements to differ from each other
 */
public record ArraySchema(
        SchemaMetadata metadata,
        CanonicalSchema items,
        Optional<Integer> minItems,
        Optional<Integer> maxItems,
        boolean uniqueItems) implements CanonicalSchema {

    public ArraySchema {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(items, "items");
        minItems = SchemaChecks.nonNegative(minItems, "minItems");
        maxItems = SchemaChecks.nonNegative(maxItems, "maxItems");
        SchemaChecks.ordered(minItems, "minItems", maxItems, "maxItems");
    }

    /** An array of the given shape, with no length constraints. */
    public static ArraySchema of(CanonicalSchema items) {
        return new ArraySchema(SchemaMetadata.none(), items, Optional.empty(), Optional.empty(),
                false);
    }
}

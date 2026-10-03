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

import io.restest.core.internal.Copies;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An object: named properties, some of them required.
 *
 * @param metadata the type-independent facts
 * @param properties the declared properties, in declaration order
 * @param required the names the document marks as required, in declaration order
 * @param additionalProperties the shape of properties beyond those declared, when the document says
 *     anything about them. Absent means the document is silent, which JSON Schema reads as "any
 *     additional property is allowed"; {@link NothingSchema} is how {@code additionalProperties:
 *     false} arrives, and it means the opposite
 * @param minProperties the fewest properties an accepted object may have
 * @param maxProperties the most
 */
public record ObjectSchema(
        SchemaMetadata metadata,
        Map<String, CanonicalSchema> properties,
        Set<String> required,
        Optional<CanonicalSchema> additionalProperties,
        Optional<Integer> minProperties,
        Optional<Integer> maxProperties) implements CanonicalSchema {

    public ObjectSchema {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(additionalProperties, "additionalProperties");
        properties = Copies.orderedMap(properties, "properties");
        required = Copies.orderedSet(required, "required");
        minProperties = SchemaChecks.nonNegative(minProperties, "minProperties");
        maxProperties = SchemaChecks.nonNegative(maxProperties, "maxProperties");
        SchemaChecks.ordered(minProperties, "minProperties", maxProperties, "maxProperties");
    }

    /** An object with the given properties, none of them required. */
    public static ObjectSchema of(Map<String, CanonicalSchema> properties) {
        return of(properties, Set.of());
    }

    /** An object with the given properties and required names. */
    public static ObjectSchema of(Map<String, CanonicalSchema> properties, Set<String> required) {
        return new ObjectSchema(SchemaMetadata.none(), properties, required, Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    /** The same object, accepting no property beyond those declared. */
    public ObjectSchema closed() {
        return new ObjectSchema(metadata, properties, required, Optional.of(NothingSchema.of()),
                minProperties, maxProperties);
    }

    /**
     * Whether the document forbids properties beyond those declared.
     *
     * <p>The question a body generator asks before adding one, and the question a response oracle
     * asks before reporting an undeclared field.
     */
    public boolean isClosed() {
        return additionalProperties.orElse(null) instanceof NothingSchema;
    }

    /**
     * The shape of the named property, if it is declared.
     *
     * <p>A name in {@link #required()} need not be declared here. That combination is a mistake in
     * the document rather than in the model, and it is kept as written so that the parser can report
     * it and a reader of the report can see what the document actually said.
     */
    public Optional<CanonicalSchema> property(String name) {
        return Optional.ofNullable(properties.get(Objects.requireNonNull(name, "name")));
    }

    /** Whether the named property must be present for an object to be accepted. */
    public boolean isRequired(String name) {
        return required.contains(Objects.requireNonNull(name, "name"));
    }
}

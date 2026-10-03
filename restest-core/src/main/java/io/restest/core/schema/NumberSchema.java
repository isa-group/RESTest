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

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * A number, whole or not, with the bounds the document states.
 *
 * <p>{@link BigDecimal} is used instead of {@code double} throughout: a bound is a fact the API will
 * enforce exactly, and one useful way RESTest tests that is by sending precisely the documented limit
 * and precisely one step past it. Rounding the limit on the way in would turn that check into a
 * guess.
 *
 * <p>The exclusive bounds ("must be strictly less/greater than") are stored as numbers, not as a flag
 * next to a plain bound, since that is the shape that loses no information across OpenAPI versions:
 * an older document writing {@code minimum: 5} alongside {@code exclusiveMinimum: true} is converted
 * on reading so that the value moves to {@link #exclusiveMinimum()} and {@link #minimum()} is left
 * empty, and a newer document writing {@code exclusiveMinimum: 5} directly has a natural place to put
 * it too.
 *
 * @param metadata the type-independent facts
 * @param kind whether the value must be whole
 * @param minimum the smallest accepted value, itself accepted
 * @param exclusiveMinimum a value every accepted number must be strictly greater than
 * @param maximum the largest accepted value, itself accepted
 * @param exclusiveMaximum a value every accepted number must be strictly less than
 * @param multipleOf the value every accepted number must be a multiple of, necessarily positive
 * @param format the format name - {@code int32}, {@code int64}, {@code float}, {@code double} -
 *     kept as a string for the same reason as in {@link StringSchema}
 */
public record NumberSchema(
        SchemaMetadata metadata,
        NumberKind kind,
        Optional<BigDecimal> minimum,
        Optional<BigDecimal> exclusiveMinimum,
        Optional<BigDecimal> maximum,
        Optional<BigDecimal> exclusiveMaximum,
        Optional<BigDecimal> multipleOf,
        Optional<String> format) implements CanonicalSchema {

    public NumberSchema {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(exclusiveMinimum, "exclusiveMinimum");
        Objects.requireNonNull(maximum, "maximum");
        Objects.requireNonNull(exclusiveMaximum, "exclusiveMaximum");
        Objects.requireNonNull(multipleOf, "multipleOf");
        Objects.requireNonNull(format, "format");
        multipleOf.ifPresent(factor -> {
            if (factor.signum() <= 0) {
                throw new IllegalArgumentException(
                        "multipleOf must be greater than zero: " + factor.toPlainString());
            }
        });
        SchemaChecks.numericRange(minimum, exclusiveMinimum, maximum, exclusiveMaximum);
    }

    /** A number of the given kind, unbounded. */
    public static NumberSchema of(NumberKind kind) {
        return new NumberSchema(SchemaMetadata.none(), kind, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** A number of the given kind, between the two bounds, both accepted. */
    public static NumberSchema between(NumberKind kind, BigDecimal minimum, BigDecimal maximum) {
        return new NumberSchema(SchemaMetadata.none(), kind,
                Optional.of(Objects.requireNonNull(minimum, "minimum")), Optional.empty(),
                Optional.of(Objects.requireNonNull(maximum, "maximum")), Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    /*
     * There is deliberately no lowerBound()/upperBound() pair collapsing the four components into
     * two. A bound without knowing whether it is inclusive or exclusive is the wrong answer to every
     * question worth asking here, since whether the limit itself is accepted is the whole difference
     * between the two. Collapsing them would also have to decide what a document stating both an
     * inclusive and an exclusive bound means, which JSON Schema already answers (the tighter one
     * wins) and a convenience accessor could easily get wrong.
     */
}

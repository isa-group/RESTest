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
 * <p>{@link BigDecimal} rather than {@code double} throughout: a bound is a fact the API will
 * enforce exactly, and the whole point of M2.3's boundary walk is to send precisely the documented
 * limit and precisely one step past it. Rounding the limit on the way in would make that test a
 * guess.
 *
 * <p>The exclusive bounds are numbers, not flags, which is the JSON Schema 2020-12 and OpenAPI 3.1
 * shape and the one that loses nothing. OpenAPI 3.0 writes the same fact as {@code minimum: 5}
 * alongside {@code exclusiveMinimum: true}, and the parser converts: the value moves to
 * {@link #exclusiveMinimum()} and {@link #minimum()} is left empty. Modelled the other way round -
 * a {@code minimum} plus a boolean - a 3.1 document writing {@code exclusiveMinimum: 5} with no
 * {@code minimum} has nowhere to put the number, and the bound is silently lost.
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
     * two. A bound without its strictness is the wrong answer to every question worth asking:
     * M2.3's boundary walk sends precisely the documented limit and precisely one step past it, and
     * whether the limit itself is accepted is the whole difference between the two. Collapsing also
     * has to decide what a document stating both an inclusive and an exclusive bound means, which
     * JSON Schema answers (the tighter wins) and a convenience accessor would get wrong quietly.
     * Whatever M2.3 needs, it can introduce with the strictness attached.
     */
}

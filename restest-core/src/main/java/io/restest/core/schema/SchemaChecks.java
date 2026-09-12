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
 * Bound checks shared by the schema records.
 *
 * <p>What they reject is a deliberate and deliberately narrow line: bounds that contradict each
 * other on their face. {@code minLength: 5, maxLength: 3} describes a value that cannot exist, and
 * {@code minItems: -1} describes nothing at all. Letting either through would mean every generator
 * and every oracle downstream re-discovering the contradiction, or worse, not noticing it.
 *
 * <p>This is not a full check of whether any value could ever satisfy the bounds, and nothing
 * downstream should treat it as one. {@code type: integer, minimum: 1.2, maximum: 1.8} and
 * {@code minimum: 10, maximum: 19, multipleOf: 100} are both accepted here even though no value
 * actually satisfies either; working that out needs a more thorough check elsewhere. The promise
 * here is only that the bounds a simple comparison can catch do not contradict each other.
 *
 * <p>Rejecting a contradiction here does not make the whole specification unusable. RESTest never
 * crashes on a bad specification: the part of RESTest that reads the document catches this rejection,
 * records it as an issue, skips just the affected operation, and reads the next one.
 */
final class SchemaChecks {

    private SchemaChecks() {
    }

    /** Requires an optional count to be present-and-not-negative, or absent. */
    static Optional<Integer> nonNegative(Optional<Integer> value, String what) {
        Objects.requireNonNull(value, what);
        value.ifPresent(count -> {
            if (count < 0) {
                throw new IllegalArgumentException(what + " cannot be negative: " + count);
            }
        });
        return value;
    }

    /** Requires a lower bound not to exceed an upper bound, when both are present. */
    static void ordered(Optional<Integer> lower, String lowerName,
            Optional<Integer> upper, String upperName) {
        if (lower.isPresent() && upper.isPresent() && lower.get() > upper.get()) {
            throw new IllegalArgumentException(lowerName + " (" + lower.get() + ") is greater than "
                    + upperName + " (" + upper.get() + "), so no value can satisfy both");
        }
    }

    /**
     * Requires the numeric bounds to leave a range, whichever of the four are present.
     *
     * <p>An exclusive bound is compared more strictly than an inclusive one, which is the whole
     * difference between them: {@code minimum: 5, maximum: 5} accepts exactly one value, while
     * {@code exclusiveMinimum: 5, maximum: 5} accepts none and is therefore refused.
     */
    static void numericRange(Optional<BigDecimal> minimum, Optional<BigDecimal> exclusiveMinimum,
            Optional<BigDecimal> maximum, Optional<BigDecimal> exclusiveMaximum) {
        compare(minimum, "minimum", maximum, "maximum", false);
        compare(exclusiveMinimum, "exclusiveMinimum", maximum, "maximum", true);
        compare(minimum, "minimum", exclusiveMaximum, "exclusiveMaximum", true);
        compare(exclusiveMinimum, "exclusiveMinimum", exclusiveMaximum, "exclusiveMaximum", true);
    }

    private static void compare(Optional<BigDecimal> lower, String lowerName,
            Optional<BigDecimal> upper, String upperName, boolean exclusive) {
        if (lower.isEmpty() || upper.isEmpty()) {
            return;
        }
        int order = lower.get().compareTo(upper.get());
        if (order > 0 || (exclusive && order == 0)) {
            throw new IllegalArgumentException(lowerName + " (" + lower.get().toPlainString()
                    + ") leaves nothing below " + upperName + " (" + upper.get().toPlainString()
                    + "), so no value can satisfy both");
        }
    }
}

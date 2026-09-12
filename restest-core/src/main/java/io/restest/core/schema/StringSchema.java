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
 * A string, with the length, pattern and format constraints the document states.
 *
 * @param metadata the type-independent facts
 * @param minLength the shortest accepted string
 * @param maxLength the longest
 * @param pattern the regular expression an accepted string must match, exactly as the document
 *     wrote it. It is not compiled here: JSON Schema's dialect is ECMA-262 and Java's is not, so
 *     translating it is the generator's job at M2.4, and compiling it here would reject patterns
 *     that are valid in the format we are reading
 * @param format the format name - {@code date-time}, {@code email}, {@code uuid} and the rest -
 *     kept as a string because the list is open: a document may use a name nobody has standardised,
 *     and an enum would lose it. Format-aware generation arrives at M2.4
 */
public record StringSchema(
        SchemaMetadata metadata,
        Optional<Integer> minLength,
        Optional<Integer> maxLength,
        Optional<String> pattern,
        Optional<String> format) implements CanonicalSchema {

    public StringSchema {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(format, "format");
        minLength = SchemaChecks.nonNegative(minLength, "minLength");
        maxLength = SchemaChecks.nonNegative(maxLength, "maxLength");
        SchemaChecks.ordered(minLength, "minLength", maxLength, "maxLength");
    }

    /** A string with nothing else stated about it. */
    public static StringSchema of() {
        return new StringSchema(SchemaMetadata.none(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    /** A string in the named format. */
    public static StringSchema ofFormat(String format) {
        return new StringSchema(SchemaMetadata.none(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(Objects.requireNonNull(format, "format")));
    }
}

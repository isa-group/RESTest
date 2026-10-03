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
 *     wrote it. It is not compiled here: JSON Schema's regular-expression dialect is not quite the
 *     same as Java's, so translating it is left to the code that generates values, and compiling it
 *     here could reject patterns that are perfectly valid in the format being read
 * @param format the format name - {@code date-time}, {@code email}, {@code uuid} and the rest -
 *     kept as a plain string rather than a fixed list, since a document may use a name nobody has
 *     standardised and a fixed list would lose it
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

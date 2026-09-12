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
 * A shape the tool could not represent, carrying why.
 *
 * <p>This is design principle 2 - never crash on a bad specification - expressed in the data.
 * Something will always be unreadable: a composition we do not fold in until M2.1, a reference that
 * does not resolve, a keyword from a version of the format newer than our parser. The three ways of
 * dealing with that are to throw, to pretend the schema said nothing, or to record the gap. Only the
 * third lets the run continue and still tells the truth afterwards.
 *
 * <p>So a generator meeting one knows it is working blind and can weight its guesses accordingly, an
 * oracle knows not to report a mismatch it cannot judge, and {@code restest explain} can name the
 * construct that defeated us instead of showing a blank.
 *
 * @param metadata whatever type-independent facts survived - often a description and nothing else
 * @param reason what could not be represented, in a form fit to print in a report: "oneOf with 3
 *     alternatives is not folded in until M2.1", not "unsupported"
 */
public record UnsupportedSchema(SchemaMetadata metadata, String reason) implements CanonicalSchema {

    public UnsupportedSchema {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) {
            throw new IllegalArgumentException(
                    "an unsupported schema must say what defeated it; a blank reason is a gap "
                            + "nobody can act on");
        }
    }

    /** An unrepresentable shape, with the reason it is unrepresentable. */
    public static UnsupportedSchema of(String reason) {
        return new UnsupportedSchema(SchemaMetadata.none(), reason);
    }
}

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
 * A shape that RESTest could not represent, together with why.
 *
 * <p>RESTest's rule is to never crash on a badly written or unusual specification, and this is that
 * rule applied to data. Something will always be unreadable: a combination of shapes not yet
 * understood, a reference that does not resolve, a keyword from a newer version of the format than
 * RESTest supports. The alternatives to recording the gap here would be to stop the run entirely, or
 * to silently pretend the schema said nothing - and only recording the gap lets the run continue
 * while still telling the truth about what happened.
 *
 * <p>That way, whatever generates values knows it is working blind here and can be more cautious,
 * whatever checks a response knows not to report a mismatch it cannot actually judge, and a later
 * explanation of the run can name the exact construct that defeated it instead of showing a blank.
 *
 * @param metadata whatever type-independent facts survived - often a description and nothing else
 * @param reason what could not be represented, in a form fit to print in a report, such as
 *     "a value declared as one of several alternative shapes is not supported yet" - not merely
 *     "unsupported"
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

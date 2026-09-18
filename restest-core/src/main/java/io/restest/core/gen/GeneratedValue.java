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
package io.restest.core.gen;

import io.restest.core.execution.ValueOrigin;
import io.restest.core.json.JsonValue;
import java.util.Objects;

/**
 * A value to put in a request, and where it came from.
 *
 * <p>The second half is not bookkeeping. When a request fails, the first question anybody asks is
 * "where did that value come from" - the document said so, we invented it, or we read it out of an
 * earlier reply - and a run that cannot answer that is a run nobody can learn from. Tying the two
 * together in one value means no way of contributing a value exists that forgets to say where it
 * came from.
 *
 * @param value what to send
 * @param origin where it came from
 */
public record GeneratedValue(JsonValue value, ValueOrigin origin) {

    public GeneratedValue {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(origin, "origin");
    }

    /**
     * A value the specification itself states.
     *
     * @param value what to send
     * @param statement which of the things a document states this was read from - the value used
     *     when none is sent, one of the values it allows, or a sample its author wrote down
     * @return the value and its origin
     */
    public static GeneratedValue declared(JsonValue value,
            ValueOrigin.Declared.Statement statement) {
        return new GeneratedValue(value, ValueOrigin.declared(statement));
    }

    /**
     * A value invented by something that says what it is.
     *
     * @param value what to send
     * @param source what invented it, in a word a person would recognise in a report
     * @return the value and its origin
     */
    public static GeneratedValue generatedBy(JsonValue value, String source) {
        return new GeneratedValue(value, new ValueOrigin.Generated(source));
    }
}

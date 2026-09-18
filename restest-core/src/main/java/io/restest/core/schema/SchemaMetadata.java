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

import io.restest.core.json.JsonValue;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The facts JSON Schema states about a value whatever its type.
 *
 * <p>They are held once, here, rather than repeated as seven components on each of the eleven
 * schema variants. {@code enum} is the clearest case: it constrains a string, a number and an
 * object identically, and a generator asking "is this value one of a fixed set?" should not have to
 * ask it eleven times.
 *
 * @param description what the document says the value is for, if anything
 * @param nullable whether {@code null} is an accepted value. Written as {@code nullable: true} in
 *     OpenAPI 3.0 and as a {@code "null"} member of a type array in 3.1; both arrive here
 * @param enumeration the fixed set of accepted values, empty when the value is not enumerated
 * @param defaultValue the value the API uses when none is sent
 * @param examples sample values the document offers for this value, in the order it wrote them,
 *     empty when it offers none. Written as a single {@code example} in OpenAPI 3.0 and as a list
 *     of {@code examples} in 3.1; both arrive here. Unlike everything else on this record they
 *     constrain nothing - they are suggestions, and a document is free to suggest a value its own
 *     rules would refuse
 * @param deprecated whether the document marks the value as on its way out
 * @param access whether the value may be sent, returned, or both
 */
public record SchemaMetadata(
        Optional<String> description,
        boolean nullable,
        List<JsonValue> enumeration,
        Optional<JsonValue> defaultValue,
        List<JsonValue> examples,
        boolean deprecated,
        SchemaMetadata.Access access) {

    /**
     * Which direction a value may travel.
     *
     * <p>One enum rather than the two booleans OpenAPI uses, because {@code readOnly} and
     * {@code writeOnly} being true at once is a contradiction this type should not be able to
     * express. It matters because a property the API only ever returns must not be sent in a
     * request body, and one it only ever accepts must not be expected in a response.
     */
    public enum Access {
        /** Sent in requests and returned in responses. The default. */
        READ_WRITE,
        /** Returned in responses only. {@code readOnly: true}. */
        READ_ONLY,
        /** Sent in requests only. {@code writeOnly: true}. */
        WRITE_ONLY
    }

    private static final SchemaMetadata NONE = new SchemaMetadata(Optional.empty(), false,
            List.of(), Optional.empty(), List.of(), false, Access.READ_WRITE);

    public SchemaMetadata {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(enumeration, "enumeration");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(examples, "examples");
        Objects.requireNonNull(access, "access");
        enumeration = List.copyOf(enumeration);
        examples = List.copyOf(examples);
    }

    /**
     * Nothing stated: not nullable, not enumerated, no default, no sample values, readable and
     * writable.
     */
    public static SchemaMetadata none() {
        return NONE;
    }

    /** The same facts, with {@code nullable} set as given. */
    public SchemaMetadata withNullable(boolean value) {
        return new SchemaMetadata(description, value, enumeration, defaultValue, examples,
                deprecated, access);
    }

    /** The same facts, enumerated by the given values. */
    public SchemaMetadata withEnumeration(List<JsonValue> values) {
        return new SchemaMetadata(description, nullable, values, defaultValue, examples, deprecated,
                access);
    }

    /** The same facts, with the given default value. */
    public SchemaMetadata withDefault(JsonValue value) {
        return new SchemaMetadata(description, nullable, enumeration,
                Optional.of(Objects.requireNonNull(value, "value")), examples, deprecated, access);
    }

    /** The same facts, with the given sample values. */
    public SchemaMetadata withExamples(List<JsonValue> values) {
        return new SchemaMetadata(description, nullable, enumeration, defaultValue, values,
                deprecated, access);
    }

    /** The same facts, with the given description. */
    public SchemaMetadata withDescription(String text) {
        return new SchemaMetadata(Optional.of(Objects.requireNonNull(text, "text")), nullable,
                enumeration, defaultValue, examples, deprecated, access);
    }

    /** The same facts, with the given access. */
    public SchemaMetadata withAccess(Access value) {
        return new SchemaMetadata(description, nullable, enumeration, defaultValue, examples,
                deprecated, Objects.requireNonNull(value, "value"));
    }

    /** Whether the value is restricted to a fixed set. */
    public boolean isEnumerated() {
        return !enumeration.isEmpty();
    }

    /** Whether the document offers at least one sample value. */
    public boolean hasExamples() {
        return !examples.isEmpty();
    }
}

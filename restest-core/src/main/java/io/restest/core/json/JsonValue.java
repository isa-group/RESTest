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
package io.restest.core.json;

import io.restest.core.internal.Copies;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A piece of JSON data: a value that is a JSON null, a boolean, a number, a string, an array, or an
 * object - the six things any JSON document is built from, and nothing else.
 *
 * <p>RESTest uses this to hold values that come from an API's specification - a field's default
 * value, the fixed set of choices in an {@code enum}, a documented example - and later to build the
 * bodies it sends in requests. Since these values are JSON, they are represented as JSON here
 * instead of as a generic, loosely-typed value.
 *
 * <p>This is a small representation written for this project rather than borrowed from an external
 * library, so that using RESTest never forces a consumer to also depend on some particular JSON
 * library and keep it in sync with their own. The module that reads OpenAPI documents does use such
 * a library, and converts into this type at the boundary.
 *
 * <p>Every variant is immutable, and the two that hold collections (arrays and objects) copy what
 * they are given, so a caller cannot change a value after handing it over.
 *
 * <p>Reading a value means pattern matching, and the interface is sealed so the compiler can tell
 * you when you have missed a case:
 *
 * {@snippet :
 * String rendered = switch (value) {
 *     case JsonValue.JsonNull ignored -> "null";
 *     case JsonValue.JsonBoolean b -> String.valueOf(b.value());
 *     case JsonValue.JsonNumber n -> n.value().toPlainString();
 *     case JsonValue.JsonString s -> s.value();
 *     case JsonValue.JsonArray a -> a.elements().size() + " elements";
 *     case JsonValue.JsonObject o -> o.members().keySet().toString();
 * };
 * }
 */
public sealed interface JsonValue {

    /** The JSON literal {@code null}. */
    JsonNull NULL = new JsonNull();

    /** The JSON literal {@code true}. */
    JsonBoolean TRUE = new JsonBoolean(true);

    /** The JSON literal {@code false}. */
    JsonBoolean FALSE = new JsonBoolean(false);

    /** {@code null}. A record with no components, so any two instances are equal. */
    record JsonNull() implements JsonValue {
    }

    /** {@code true} or {@code false}. */
    record JsonBoolean(boolean value) implements JsonValue {
    }

    /**
     * A number.
     *
     * <p>{@link BigDecimal} because JSON does not say how big a number may be or how it is
     * represented, and {@code double} silently rounds what a specification wrote down. A schema
     * saying {@code maximum: 9007199254740993} means it.
     *
     * <p>The value is normalised with {@link BigDecimal#stripTrailingZeros()} so that equality is
     * numeric: {@code 1}, {@code 1.0} and {@code 1.00} are the same number in JSON, and are the
     * same {@code JsonNumber} here. Without that they would be three unequal values, and a test
     * asserting "the default is 1" would fail against a document that wrote {@code 1.0}.
     */
    record JsonNumber(BigDecimal value) implements JsonValue {
        public JsonNumber {
            Objects.requireNonNull(value, "value");
            value = value.stripTrailingZeros();
        }
    }

    /** A string. */
    record JsonString(String value) implements JsonValue {
        public JsonString {
            Objects.requireNonNull(value, "value");
        }
    }

    /** An array, in its declared order. */
    record JsonArray(List<JsonValue> elements) implements JsonValue {
        public JsonArray {
            Objects.requireNonNull(elements, "elements");
            elements = List.copyOf(elements);
        }
    }

    /**
     * An object, in its declared member order.
     *
     * <p>Order is kept because it reaches the wire: a body is written in the order its members are
     * held, and a run that must be reproducible cannot have that order change between JVMs. See
     * {@link Copies}.
     */
    record JsonObject(Map<String, JsonValue> members) implements JsonValue {
        public JsonObject {
            members = Copies.orderedMap(members, "members");
        }

        /** The member under {@code name}, if the object has one. */
        public Optional<JsonValue> member(String name) {
            return Optional.ofNullable(members.get(Objects.requireNonNull(name, "name")));
        }
    }

    /** {@code true} or {@code false}. */
    static JsonBoolean of(boolean value) {
        return value ? TRUE : FALSE;
    }

    /** A whole number. */
    static JsonNumber of(long value) {
        return new JsonNumber(BigDecimal.valueOf(value));
    }

    /** A number. */
    static JsonNumber of(BigDecimal value) {
        return new JsonNumber(value);
    }

    /** A string. */
    static JsonString of(String value) {
        return new JsonString(value);
    }

    /** An array of the given elements, in order. */
    static JsonArray array(JsonValue... elements) {
        return new JsonArray(List.of(elements));
    }

    /** An array of the given elements, in order. */
    static JsonArray array(List<JsonValue> elements) {
        return new JsonArray(elements);
    }

    /** An object with the given members, in the order the map iterates. */
    static JsonObject object(Map<String, JsonValue> members) {
        return new JsonObject(members);
    }
}

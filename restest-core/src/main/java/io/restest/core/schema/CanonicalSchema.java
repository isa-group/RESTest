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

/**
 * The shape of a value: what an API says it will accept or return, in types we own.
 *
 * <p>A specification describes shapes in JSON Schema, whose vocabulary is far wider than a black-box
 * tester can act on and whose spelling differs between OpenAPI 2.0, 3.0 and 3.1. Everything after
 * the parser - generating a value, judging a response, explaining a failure - works against this
 * hierarchy instead, so those differences are dealt with once, in {@code restest-spec}, and nowhere
 * else.
 *
 * <p>Sealed, so that a generator or an oracle that handles nine of the ten cases fails to
 * compile rather than falling through to a default and quietly producing nothing:
 *
 * {@snippet :
 * Object value = switch (schema) {
 *     case StringSchema s -> "text";
 *     case NumberSchema n -> 1;
 *     case BooleanSchema b -> true;
 *     case NullSchema n -> null;
 *     case ArraySchema a -> List.of();
 *     case ObjectSchema o -> Map.of();
 *     case AnySchema a -> "anything";
 *     case NothingSchema n -> null;       // nothing satisfies it: additionalProperties: false
 *     case SchemaReference r -> api.schema(r.name());  // named elsewhere, possibly recursively
 *     case UnsupportedSchema u -> null;   // u.reason() says why nothing better is possible
 * };
 * }
 *
 * <p>Three of those cases are worth separating. {@link AnySchema} is a schema that declares no type -
 * legal, common, and meaning "any value will do". {@link UnsupportedSchema} is a shape we could not
 * represent: a composition we do not read until M2.1, a reference that does not resolve, a
 * construct from a version of the format we have not caught up with. Design principle 2 says a bad
 * specification must never crash the tool, and this is where that principle lands in the data: the
 * problem is carried, with its reason, instead of being thrown or silently flattened into
 * "anything". {@link NothingSchema} is the third: the document was understood and what it said is
 * that no value is acceptable.
 *
 * <p>Every implementation is an immutable record whose first component is its {@link SchemaMetadata}
 * - the facts JSON Schema attaches to a value regardless of its type.
 */
public sealed interface CanonicalSchema
        permits AnySchema, ArraySchema, BooleanSchema, NothingSchema, NullSchema, NumberSchema,
                ObjectSchema, SchemaReference, StringSchema, UnsupportedSchema {

    /** The type-independent facts: description, nullability, enumeration, default, access. */
    SchemaMetadata metadata();
}

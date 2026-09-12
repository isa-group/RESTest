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
 * The shape of a value: what an API says a field, a parameter or a whole request or response body
 * is allowed to look like.
 *
 * <p>An API's specification describes these shapes using JSON Schema, a much richer notation than a
 * tool that only sends requests and checks responses actually needs, and one whose exact spelling
 * differs across OpenAPI versions. Everything in RESTest that comes after reading the specification -
 * choosing what value to send, checking whether a response matches, explaining a failure - works
 * against this simpler set of ten shapes instead, so those version differences only ever have to be
 * dealt with once, in the part of RESTest that reads the specification, and nowhere else.
 *
 * <p>There are exactly ten kinds of shape, which the compiler enforces: code that is meant to handle
 * every kind must actually handle all ten, rather than falling through to a default and quietly
 * doing nothing useful for the one it missed:
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
 * <p>Three of those ten are worth calling out. {@link AnySchema} is a schema that declares no type at
 * all - legal, common, and meaning "any value will do". {@link UnsupportedSchema} is a shape RESTest
 * could not represent: a combination of shapes it does not yet understand, a reference that does not
 * resolve, a construct from a newer version of the format. RESTest's rule is that a badly written or
 * unusual specification must never crash the tool, and this is that rule applied to data: the
 * problem is recorded, with its reason, instead of stopping the run or silently pretending "anything
 * goes". {@link NothingSchema} is the third: the document was understood perfectly, and what it said
 * is that no value at all is acceptable.
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

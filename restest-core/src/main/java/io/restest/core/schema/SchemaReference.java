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
 * A shape named elsewhere in the document, referred to rather than copied.
 *
 * <p>This is what makes a recursive shape representable, and a recursive shape is not exotic: a
 * comment with replies, a category with sub-categories, a tree of any kind. Held by value, such a
 * schema cannot be built at all - the object graph would have to contain itself - so a model without
 * this variant leaves the parser with nothing but bad options: recurse until the stack ends, inline
 * to some arbitrary depth and describe a shape the document does not, or declare a construct
 * unsupported that the tool could in fact test.
 *
 * <p>Referring by name also keeps the name itself, which would otherwise be thrown away, and the
 * name is useful later: it helps relate one part of the API to another, it can be used to look up a
 * value someone has prepared specifically for that named shape, and an explanation of a test reads
 * better saying "a Pet" than repeating the whole shape.
 *
 * <p>Resolution is {@link io.restest.core.model.ApiModel#schema(String)}, which holds the named
 * schemas the document declared. A reference whose name the document never declares resolves to
 * empty - a fact for the parser to report, not a reason to refuse the model.
 *
 * @param metadata facts stated at the point of reference, which JSON Schema 2020-12 allows
 *     alongside {@code $ref} and OpenAPI 3.0 does not
 * @param name the name under which the target is declared, {@code Pet} for
 *     {@code #/components/schemas/Pet}
 */
public record SchemaReference(SchemaMetadata metadata, String name) implements CanonicalSchema {

    public SchemaReference {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a reference names the schema it refers to");
        }
    }

    /** A reference to the named schema. */
    public static SchemaReference to(String name) {
        return new SchemaReference(SchemaMetadata.none(), name);
    }
}

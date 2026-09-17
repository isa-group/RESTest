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

import java.util.List;
import java.util.Objects;

/**
 * A value the specification says may have any one of several shapes.
 *
 * <p>Documents write this when a field genuinely accepts more than one kind of thing: an identifier
 * that may be a number or a name, a payment that is either a card or a bank transfer. Unlike a value
 * that has to be several shapes <em>at once</em> - which can be worked out and stored as one
 * ordinary shape - a choice has to be carried as it was written, because no single shape is all of
 * them.
 *
 * <p>Whatever invents a value to send picks one of the shapes and works from it, so across a run all
 * of them get used. Whether a reply is valid is judged elsewhere, against the specification document
 * itself, which is why nothing here records whether the document allowed exactly one of the shapes
 * or any of them: it is a difference RESTest never acts on.
 *
 * <p>The alternatives are ordinary shapes, and one of them may well be a shape named elsewhere. That
 * is what lets a document describe something recursive - a comment that is either a piece of text or
 * a thread of further comments.
 *
 * @param metadata the type-independent facts: description, nullability, default, access
 * @param alternatives the shapes the value may have, in the order the document listed them; never
 *     empty, because a choice between nothing describes nothing
 */
public record ChoiceSchema(SchemaMetadata metadata, List<CanonicalSchema> alternatives)
        implements CanonicalSchema {

    public ChoiceSchema {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(alternatives, "alternatives");
        alternatives = List.copyOf(alternatives);
        if (alternatives.isEmpty()) {
            throw new IllegalArgumentException(
                    "a choice offers at least one shape to choose from");
        }
    }

    /** A choice between the given shapes, with nothing else stated about the value. */
    public static ChoiceSchema of(List<CanonicalSchema> alternatives) {
        return new ChoiceSchema(SchemaMetadata.none(), alternatives);
    }
}

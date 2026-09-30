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
package io.restest.core.model;

import io.restest.core.internal.Copies;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What a document says a request has to prove before the API will answer it: nothing, or one of
 * several ways of proving who it comes from.
 *
 * <p>The ways themselves are declared once, by name, as {@link SecurityScheme}s. A requirement only
 * names them, and it names them as alternatives. {@code [{api_key}, {petstore_auth}]} says a request
 * may carry the key or may be signed in through OAuth 2, and either will do. One alternative can
 * name several schemes, which then all have to be satisfied at once; one can name none, which says
 * the API also answers somebody who proves nothing at all.
 *
 * <p>A document may state a requirement for the whole API and another for a single operation, which
 * then wins; {@link ApiModel#securityFor(Operation)} works out which one applies. Stating one with no
 * alternatives - {@code security: []} - is how a document says that one operation asks for nothing,
 * even though the rest of the API does.
 *
 * @param alternatives the ways a request may prove who it comes from, in the order the document
 *     lists them, each the names of the schemes it needs at once. None at all means the request has
 *     to prove nothing
 */
public record SecurityRequirement(List<Set<String>> alternatives) {

    public SecurityRequirement {
        Objects.requireNonNull(alternatives, "alternatives");
        alternatives = alternatives.stream()
                .map(alternative -> Copies.orderedSet(alternative, "an alternative"))
                .toList();
    }

    /** What {@code security: []} says: nothing has to be proved. */
    public static SecurityRequirement none() {
        return new SecurityRequirement(List.of());
    }

    /** Whether a request has to prove nothing, which is what {@code security: []} says. */
    public boolean asksForNothing() {
        return alternatives.isEmpty();
    }

    /** Every scheme some alternative names, each once, in the order the document names them. */
    public Set<String> schemesNamed() {
        Set<String> named = new LinkedHashSet<>();
        alternatives.forEach(named::addAll);
        return Copies.orderedSet(named, "the schemes named");
    }
}

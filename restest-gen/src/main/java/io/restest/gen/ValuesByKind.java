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
package io.restest.gen;

import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonValue;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.BooleanSchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.StringSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A list of values written for kinds of value rather than for the places of one API: some for every
 * piece of text, some for every whole number, and some under {@code any} for every kind at once.
 *
 * <p>This is the shape of the list of awkward values RESTest carries to push at an API with. That
 * list cannot know the operations of an API it has never seen, so it says what is worth trying for
 * a kind of value and leaves it to whoever asks to say where. A list somebody hands over is a
 * {@link ValueDictionary} instead, written for the places of the API it was made for.
 */
final class ValuesByKind implements Dictionary {

    /** The bucket whose values apply whatever the kind of value, which is where {@code null} lives. */
    static final String ANY = "any";

    private final String name;
    private final Map<String, List<JsonValue>> values;

    ValuesByKind(String name, Map<String, List<JsonValue>> values) {
        this.name = Objects.requireNonNull(name, "name");
        // Not Map.copyOf, whose iteration order is randomised per process: the values for a kind are
        // offered in the order the file wrote them, so that the same run draws the same way twice.
        this.values = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    @Override
    public List<JsonValue> valuesFor(ValueRequest request) {
        Objects.requireNonNull(request, "request");
        List<JsonValue> found = new ArrayList<>(typeOf(request.schema())
                .map(kind -> values.getOrDefault(kind, List.of()))
                .orElse(List.of()));
        found.addAll(values.getOrDefault(ANY, List.of()));
        return List.copyOf(found);
    }

    /**
     * The kind of value a shape describes, in the words JSON uses for them.
     *
     * <p>A shape that accepts anything, one that accepts nothing and one nobody could read have no
     * kind to look up, and neither does a choice between several - the values that suit a number do
     * not suit the text it might be instead, and picking one of the two here would be guessing.
     */
    private static Optional<String> typeOf(CanonicalSchema schema) {
        return switch (schema) {
            case StringSchema ignored -> Optional.of("string");
            case NumberSchema number -> Optional.of(
                    number.kind() == NumberKind.INTEGER ? "integer" : "number");
            case BooleanSchema ignored -> Optional.of("boolean");
            case ArraySchema ignored -> Optional.of("array");
            case ObjectSchema ignored -> Optional.of("object");
            case io.restest.core.schema.NullSchema ignored -> Optional.of("null");
            case io.restest.core.schema.AnySchema ignored -> Optional.empty();
            case io.restest.core.schema.ChoiceSchema ignored -> Optional.empty();
            case io.restest.core.schema.NothingSchema ignored -> Optional.empty();
            case io.restest.core.schema.SchemaReference ignored -> Optional.empty();
            case io.restest.core.schema.UnsupportedSchema ignored -> Optional.empty();
        };
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return "dictionary '" + name + "' keyed by kind of value";
    }
}

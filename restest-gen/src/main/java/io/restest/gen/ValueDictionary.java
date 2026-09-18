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
 * A dictionary whose values were written down in advance, indexed one particular way.
 *
 * <p>This is the shape almost every dictionary takes: somebody wrote a list of values, said what
 * decides which of them apply, and the tool looks them up. The values live in a file so that adding
 * good values for an API is something anybody can do without touching the tool, and so that a list
 * worth having can be kept next to the specification it belongs to.
 *
 * <p>Two lists are consulted for any one input: the one under whatever key applies - the kind of
 * value, the format the document declares, the name of the shape, the name of the parameter, or the
 * operation and parameter together - and the one under {@code any}, which applies whatever the key
 * and is where values that make sense everywhere live, {@code null} being the obvious one.
 *
 * <p>Nothing here decides whether a value is a good idea. That is the job of whatever asks.
 */
public final class ValueDictionary implements Dictionary {

    /** The bucket whose values apply whatever the key. */
    public static final String ANY = "any";

    /** What decides which of a dictionary's values apply to a given input. */
    public enum Keying {
        /** The kind of value wanted: text, a whole number, a number, true or false, a list, an object. */
        TYPE,
        /** The format the document declares for the value: a date, an e-mail address, an identifier. */
        FORMAT,
        /** The name the document gave the shape - the values that worked for an {@code Owner}. */
        SCHEMA,
        /** The parameter's name, wherever it appears - every {@code petId} in the API. */
        NAME,
        /** One named parameter of one named operation, which is as specific as it gets. */
        OPERATION_AND_PARAMETER;

        /**
         * Whether this picks out one value in particular rather than a whole kind of them.
         *
         * <p>A parameter's name and the pair of operation and parameter both name a value somebody
         * meant. A shape does not: a document declares a shape once and however many parameters
         * refer to it get the same one, so a list written for a shape is a list about a kind of
         * value, and belongs beside the ones written for a format or a type. The document's own
         * sample of one parameter is the more particular statement, and goes first.
         *
         * @return whether it is about one value in particular
         */
        public boolean isAboutOneValueInParticular() {
            return this == NAME || this == OPERATION_AND_PARAMETER;
        }
    }

    private final String name;
    private final Keying keying;
    private final Expectation expects;
    private final Map<String, List<JsonValue>> values;
    private final Map<String, Map<String, List<JsonValue>>> perOperation;

    ValueDictionary(String name, Keying keying, Expectation expects,
            Map<String, List<JsonValue>> values,
            Map<String, Map<String, List<JsonValue>>> perOperation) {
        this.name = Objects.requireNonNull(name, "name");
        this.keying = Objects.requireNonNull(keying, "keying");
        this.expects = Objects.requireNonNull(expects, "expects");
        // Not Map.copyOf, whose iteration order is randomised per process. The order these were
        // written in is what a run quotes back when it says a dictionary names operations this API
        // does not have, and the same command has to explain itself the same way twice.
        this.values = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values));
        Map<String, Map<String, List<JsonValue>>> byOperation = new LinkedHashMap<>();
        perOperation.forEach((operation, parameters) ->
                byOperation.put(operation, java.util.Collections.unmodifiableMap(
                        new LinkedHashMap<>(parameters))));
        this.perOperation = java.util.Collections.unmodifiableMap(byOperation);
    }

    @Override
    public List<JsonValue> valuesFor(ValueRequest request) {
        Objects.requireNonNull(request, "request");
        List<JsonValue> found = new ArrayList<>(under(request));
        found.addAll(values.getOrDefault(ANY, List.of()));
        return List.copyOf(found);
    }

    private List<JsonValue> under(ValueRequest request) {
        if (keying == Keying.OPERATION_AND_PARAMETER) {
            return perOperation
                    .getOrDefault(request.operation().value(), Map.of())
                    .getOrDefault(request.name(), List.of());
        }
        return keyOf(request).map(key -> values.getOrDefault(key, List.of())).orElse(List.of());
    }

    /** The one key this dictionary looks an input up under, when the input has one at all. */
    private Optional<String> keyOf(ValueRequest request) {
        return switch (keying) {
            case TYPE -> typeOf(request.schema());
            case FORMAT -> formatOf(request.schema());
            case SCHEMA -> request.shape();
            case NAME -> Optional.of(request.name());
            // Handled before this is reached, because its key has two parts rather than one.
            case OPERATION_AND_PARAMETER -> Optional.empty();
        };
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

    private static Optional<String> formatOf(CanonicalSchema schema) {
        return switch (schema) {
            case StringSchema text -> text.format();
            case NumberSchema number -> number.format();
            default -> Optional.empty();
        };
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Expectation expects() {
        return expects;
    }

    @Override
    public boolean isAboutOneValueInParticular() {
        return keying.isAboutOneValueInParticular();
    }

    /** What decides which of this dictionary's values apply. */
    public Keying keyedBy() {
        return keying;
    }

    /**
     * The keys this dictionary holds values under, for a run to say when none of them matches
     * anything in the document it was pointed at.
     *
     * @return the keys, in the order the file wrote them
     */
    public List<String> keys() {
        if (keying == Keying.OPERATION_AND_PARAMETER) {
            return List.copyOf(perOperation.keySet());
        }
        Map<String, List<JsonValue>> named = new LinkedHashMap<>(values);
        named.remove(ANY);
        return List.copyOf(named.keySet());
    }

    @Override
    public String toString() {
        return "dictionary '" + name + "' keyed by " + keying.name().toLowerCase(java.util.Locale.ROOT);
    }
}

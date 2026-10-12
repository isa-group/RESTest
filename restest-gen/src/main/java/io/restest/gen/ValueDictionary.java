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
 * <p>The values live in a file so that adding good values for an API is something anybody can do
 * without touching the tool, and so that a list worth having can be kept next to the specification
 * it belongs to.
 *
 * <p>Two shapes, for two kinds of list. One somebody hands over puts each list under one place in
 * one operation's request: a parameter by name, the whole body under {@code body}, or one piece of
 * the body by the way down to it, {@code body.owner.email}. The list RESTest carries to push at an
 * API with cannot know any API's places, so it puts each list under a kind of value - every piece of
 * text, every whole number - and keeps one more under {@code any}, which applies to every kind and
 * is where {@code null} lives.
 *
 * <p>Nothing here decides whether a value is a good idea. That is the job of whatever asks.
 */
public final class ValueDictionary implements Dictionary {

    /** The bucket whose values apply whatever the kind of value, in the list RESTest carries. */
    public static final String ANY = "any";

    /** What decides which of a dictionary's values apply to a given input. */
    public enum Keying {
        /**
         * The kind of value wanted: text, a whole number, a number, true or false, a list, an
         * object. How the list RESTest carries to push with is written, and no other.
         */
        TYPE,
        /**
         * One place in one operation's request: a parameter by name, the whole body under
         * {@code body}, or one piece of the body by the way down to it, {@code body.owner.email}.
         * How every list somebody hands over is written.
         */
        OPERATION_AND_PARAMETER
    }

    private final String name;
    private final Keying keying;
    private final Map<String, List<JsonValue>> values;
    private final Map<String, Map<String, List<JsonValue>>> perOperation;

    ValueDictionary(String name, Keying keying, Map<String, List<JsonValue>> values,
            Map<String, Map<String, List<JsonValue>>> perOperation) {
        this.name = Objects.requireNonNull(name, "name");
        this.keying = Objects.requireNonNull(keying, "keying");
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
        return switch (keying) {
            case TYPE -> typeOf(request.schema())
                    .map(key -> values.getOrDefault(key, List.of()))
                    .orElse(List.of());
            // The way down to the value rather than the name of its last step: 'body.owner.email'
            // is a different entry from 'email', and a document may well have both.
            case OPERATION_AND_PARAMETER -> perOperation
                    .getOrDefault(request.operation().value(), Map.of())
                    .getOrDefault(request.path(), List.of());
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

    @Override
    public String name() {
        return name;
    }

    /** What decides which of this dictionary's values apply. */
    public Keying keyedBy() {
        return keying;
    }

    /**
     * The entries this dictionary holds for each operation it names, in the order the file wrote
     * them.
     *
     * <p>Only a dictionary somebody handed over has any. A run reads this to say which of their
     * entries name a place the document it was pointed at does not have.
     *
     * @return the entries, operation by operation
     */
    public Map<String, Map<String, List<JsonValue>>> entriesByOperation() {
        return perOperation;
    }

    /**
     * The same dictionary with some of its operations under different names.
     *
     * <p>An operation can be written down two ways - the identifier the document declares for it,
     * and the method and path anybody can read off the document without looking for one. Both are
     * allowed in a file, and both mean the same operation, so one of them is turned into the other
     * as the file is read and nothing downstream has to know there were ever two.
     *
     * @param renaming what to call each operation instead, for the ones being renamed
     * @return the dictionary, or this one when there is nothing to rename
     */
    ValueDictionary withOperationsRenamed(Map<String, String> renaming) {
        if (renaming.isEmpty()) {
            return this;
        }
        Map<String, Map<String, List<JsonValue>>> renamed = new LinkedHashMap<>();
        // The order the file wrote them in, first, so that what a run quotes back about a file
        // reads in the order somebody wrote it.
        perOperation.keySet().forEach(operation -> renamed.computeIfAbsent(
                renaming.getOrDefault(operation, operation), ignored -> new LinkedHashMap<>()));
        // Then the entries, in two passes rather than one, so that which of two spellings of an
        // operation wins does not depend on which of them the file happened to write first: where
        // both give a value for the same place, the one under the operation's own identifier is
        // kept, because that is the name the run prints.
        perOperation.forEach((operation, entries) -> {
            if (!renaming.containsKey(operation)) {
                renamed.get(operation).putAll(entries);
            }
        });
        perOperation.forEach((operation, entries) -> {
            if (renaming.containsKey(operation)) {
                entries.forEach(renamed.get(renaming.get(operation))::putIfAbsent);
            }
        });
        return new ValueDictionary(name, keying, values, renamed);
    }

    /**
     * Whether there is not one value anywhere in this dictionary.
     *
     * <p>A file can be read perfectly well and still hold nothing - every entry commented out, or
     * never filled in - and such a file adds nothing to a run. A run says so, because otherwise it
     * would look exactly like a file that was used.
     *
     * @return true when no key and no place has a single value under it
     */
    public boolean holdsNoValues() {
        return values.values().stream().allMatch(List::isEmpty)
                && perOperation.values().stream()
                        .allMatch(places -> places.values().stream().allMatch(List::isEmpty));
    }

    @Override
    public String toString() {
        return "dictionary '" + name + "' keyed by " + keying.name().toLowerCase(java.util.Locale.ROOT);
    }
}

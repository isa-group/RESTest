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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A list of values somebody wrote down for the places of one API, each under the operation it is
 * for and the place in that operation's request it goes.
 *
 * <p>The values live in a file so that adding good values for an API is something anybody can do
 * without touching the tool, and so that a list worth having can be kept next to the specification
 * it belongs to. A place is a parameter by name, the whole body under {@code body}, or one piece of
 * the body by the way down to it, {@code body.owner.email}.
 *
 * <p>The list of awkward values RESTest carries is written for kinds of value instead, since it
 * knows no API, and is a {@link ValuesByKind}.
 *
 * <p>Nothing here decides whether a value is a good idea. That is the job of whatever asks.
 */
public final class ValueDictionary implements Dictionary {

    private final String name;
    private final Map<String, Map<String, List<JsonValue>>> perOperation;

    ValueDictionary(String name, Map<String, Map<String, List<JsonValue>>> perOperation) {
        this.name = Objects.requireNonNull(name, "name");
        // Not Map.copyOf, whose iteration order is randomised per process. The order these were
        // written in is what a run quotes back when it says a dictionary names operations this API
        // does not have, and the same command has to explain itself the same way twice.
        Map<String, Map<String, List<JsonValue>>> byOperation = new LinkedHashMap<>();
        perOperation.forEach((operation, parameters) ->
                byOperation.put(operation, java.util.Collections.unmodifiableMap(
                        new LinkedHashMap<>(parameters))));
        this.perOperation = java.util.Collections.unmodifiableMap(byOperation);
    }

    @Override
    public List<JsonValue> valuesFor(ValueRequest request) {
        Objects.requireNonNull(request, "request");
        // The way down to the value rather than the name of its last step: 'body.owner.email' is a
        // different entry from 'email', and a document may well have both.
        return perOperation
                .getOrDefault(request.operation().value(), Map.of())
                .getOrDefault(request.path(), List.of());
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * The entries this dictionary holds for each operation it names, in the order the file wrote
     * them.
     *
     * <p>A run reads this to say which entries name a place the document it was pointed at does
     * not have.
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
        return new ValueDictionary(name, renamed);
    }

    /**
     * Whether there is not one value anywhere in this dictionary.
     *
     * <p>A file can be read perfectly well and still hold nothing - every entry commented out, or
     * never filled in - and such a file adds nothing to a run. A run says so, because otherwise it
     * would look exactly like a file that was used.
     *
     * @return true when no place has a single value under it
     */
    public boolean holdsNoValues() {
        return perOperation.values().stream()
                .allMatch(places -> places.values().stream().allMatch(List::isEmpty));
    }

    @Override
    public String toString() {
        return "dictionary '" + name + "' keyed by operation and place";
    }
}

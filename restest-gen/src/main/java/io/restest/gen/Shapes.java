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

import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.schema.AnySchema;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.BooleanSchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.NothingSchema;
import io.restest.core.schema.NullSchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import io.restest.core.schema.UnsupportedSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A handful of questions about a value and the shape the API's documentation gives it, asked from
 * more than one place.
 *
 * <p>Two parts of the tool take a value apart: the one that sends back what the API returned with
 * one piece of it changed, and the one that sends a request the API accepted with one thing broken.
 * Both need to follow a shape the document named elsewhere, to know whether a value could satisfy a
 * shape, to replace or remove the value at the end of a way down into a larger one, and to recognise
 * a property the API only ever returns. Those are here, once, so the two cannot come to answer them
 * differently.
 *
 * <p>A way down is a list of steps: a property's name for a step into an object, a position for a
 * step into a list.
 */
final class Shapes {

    private Shapes() {
    }

    /**
     * The shape itself, where the document referred to one it declared elsewhere by name.
     *
     * <p>A name pointing at another name is followed too, but only so far: a document may point one
     * shape at another and that one back again, and following that without counting would never
     * end.
     *
     * @param model the document's shapes
     * @param schema the shape, or a reference to one
     * @param mostHops how many names in a row are followed before giving up
     * @return the shape, or the last reference reached if it leads nowhere
     */
    static CanonicalSchema resolved(ApiModel model, CanonicalSchema schema, int mostHops) {
        CanonicalSchema here = schema;
        for (int hops = 0; hops < mostHops && here instanceof SchemaReference reference; hops++) {
            CanonicalSchema named = model.resolve(reference).orElse(null);
            if (named == null) {
                return here;
            }
            here = named;
        }
        return here;
    }

    /**
     * Whether a value could be sent where this shape is declared: of the right kind, and on the
     * closed list of accepted values where the document states one.
     *
     * @param model the document's shapes
     * @param value the value
     * @param wanted the shape
     * @param mostDepth how many shapes inside one another, or names in a row, are followed
     * @return whether it could
     */
    static boolean couldSatisfy(ApiModel model, JsonValue value, CanonicalSchema wanted,
            int mostDepth) {
        return couldSatisfy(model, value, wanted, 0, mostDepth);
    }

    private static boolean couldSatisfy(ApiModel model, JsonValue value, CanonicalSchema wanted,
            int depth, int mostDepth) {
        // Counted, because a document may point one shape at another and that one back again. It
        // parses cleanly and it is nobody's mistake to make a request for; following it without
        // counting ends the run, which design principle 2 forbids for any document at all.
        if (depth > mostDepth) {
            return false;
        }
        List<JsonValue> allowed = wanted.metadata().enumeration();
        if (!allowed.isEmpty() && !allowed.contains(value)) {
            return false;
        }
        return switch (wanted) {
            case StringSchema ignored -> value instanceof JsonValue.JsonString;
            case NumberSchema number -> value instanceof JsonValue.JsonNumber written
                    && (number.kind() != NumberKind.INTEGER
                            || written.value().stripTrailingZeros().scale() <= 0);
            case BooleanSchema ignored -> value instanceof JsonValue.JsonBoolean;
            case ArraySchema ignored -> value instanceof JsonValue.JsonArray;
            case ObjectSchema ignored -> value instanceof JsonValue.JsonObject;
            case NullSchema ignored -> value instanceof JsonValue.JsonNull;
            case AnySchema ignored -> true;
            case ChoiceSchema choice -> choice.alternatives().stream()
                    .anyMatch(alternative -> couldSatisfy(model, value, alternative, depth + 1,
                            mostDepth));
            case SchemaReference reference -> model.resolve(reference)
                    .map(named -> couldSatisfy(model, value, named, depth + 1, mostDepth))
                    .orElse(false);
            // Nothing satisfies a shape that accepts nothing, and a shape nobody could read is not
            // one to guess at.
            case NothingSchema ignored -> false;
            case UnsupportedSchema ignored -> false;
        };
    }

    /**
     * Whether the document says the API sends this property and never receives it.
     *
     * @param model the document's shapes
     * @param property the property's shape, as declared where it is used
     * @param mostHops how many names in a row are followed to find out
     * @return whether it is only ever returned
     */
    static boolean onlyEverReturned(ApiModel model, CanonicalSchema property, int mostHops) {
        return property.metadata().access() == SchemaMetadata.Access.READ_ONLY
                || resolved(model, property, mostHops).metadata().access()
                        == SchemaMetadata.Access.READ_ONLY;
    }

    /**
     * The same value with the one at the end of this way down replaced.
     *
     * @param in the value to look inside
     * @param steps the way down
     * @param with what goes there instead
     * @return the whole value with that one piece replaced, or unchanged if the way leads nowhere
     */
    static JsonValue replaced(JsonValue in, List<Object> steps, JsonValue with) {
        return replaced(in, steps, 0, with);
    }

    private static JsonValue replaced(JsonValue in, List<Object> steps, int at, JsonValue with) {
        if (at == steps.size()) {
            return with;
        }
        if (steps.get(at) instanceof String name && in instanceof JsonValue.JsonObject thing) {
            Map<String, JsonValue> members = new LinkedHashMap<>(thing.members());
            members.put(name, replaced(members.get(name), steps, at + 1, with));
            return new JsonValue.JsonObject(members);
        }
        if (steps.get(at) instanceof Integer index && in instanceof JsonValue.JsonArray list) {
            List<JsonValue> elements = new ArrayList<>(list.elements());
            elements.set(index, replaced(elements.get(index), steps, at + 1, with));
            return new JsonValue.JsonArray(elements);
        }
        return in;
    }

    /**
     * The same value with the property at the end of this way down taken out altogether.
     *
     * @param in the value to look inside
     * @param steps the way down, ending in the name of a property of an object
     * @return the whole value without that property, or unchanged if the way leads nowhere
     */
    static JsonValue removed(JsonValue in, List<Object> steps) {
        if (steps.isEmpty()) {
            return in;
        }
        return removed(in, steps, 0);
    }

    private static JsonValue removed(JsonValue in, List<Object> steps, int at) {
        boolean last = at == steps.size() - 1;
        if (steps.get(at) instanceof String name && in instanceof JsonValue.JsonObject thing) {
            if (!thing.members().containsKey(name)) {
                return in;
            }
            Map<String, JsonValue> members = new LinkedHashMap<>(thing.members());
            if (last) {
                members.remove(name);
            } else {
                members.put(name, removed(members.get(name), steps, at + 1));
            }
            return new JsonValue.JsonObject(members);
        }
        if (!last && steps.get(at) instanceof Integer index
                && in instanceof JsonValue.JsonArray list && index < list.elements().size()) {
            List<JsonValue> elements = new ArrayList<>(list.elements());
            elements.set(index, removed(elements.get(index), steps, at + 1));
            return new JsonValue.JsonArray(elements);
        }
        return in;
    }
}

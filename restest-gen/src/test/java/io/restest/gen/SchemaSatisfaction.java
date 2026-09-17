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
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import io.restest.core.schema.UnsupportedSchema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks that a value really does satisfy the description it was invented from.
 *
 * <p>This is the assertion the generation tests are built on, and it is written independently of the
 * generator on purpose: a test that asked the generator whether it had done the right thing would
 * agree with it whatever it did. Here the schema is read again, from scratch, and every statement it
 * makes is checked against the value - the lengths, the bounds, the step, the required properties,
 * the closed list of allowed values.
 */
final class SchemaSatisfaction {

    private SchemaSatisfaction() {
    }

    /**
     * Everything about this value that its description forbids.
     *
     * @param value the value to check
     * @param schema what it is supposed to satisfy
     * @param model the API, for shapes referred to by name
     * @return one readable complaint per violation; empty when the value is acceptable
     */
    static List<String> violations(JsonValue value, CanonicalSchema schema, ApiModel model) {
        List<String> found = new ArrayList<>();
        check(value, schema, model, "the value", found);
        return found;
    }

    private static void check(JsonValue value, CanonicalSchema schema, ApiModel model, String where,
            List<String> found) {
        if (schema instanceof SchemaReference reference) {
            model.resolve(reference).ifPresent(named -> check(value, named, model, where, found));
            return;
        }
        if (value instanceof JsonValue.JsonNull) {
            if (!(schema instanceof NullSchema) && !schema.metadata().nullable()) {
                found.add(where + " is nothing at all, which this shape does not allow");
            }
            return;
        }
        if (schema.metadata().isEnumerated() && !schema.metadata().enumeration().contains(value)) {
            found.add(where + " is " + value + ", which is not one of the allowed values "
                    + schema.metadata().enumeration());
        }
        switch (schema) {
            case StringSchema string -> checkString(value, string, where, found);
            case NumberSchema number -> checkNumber(value, number, where, found);
            case BooleanSchema ignored -> require(value instanceof JsonValue.JsonBoolean,
                    where + " should be true or false", found);
            case ArraySchema array -> checkList(value, array, model, where, found);
            case ObjectSchema object -> checkObject(value, object, model, where, found);
            case NothingSchema ignored ->
                    found.add(where + " exists, and this shape allows no value at all");
            case NullSchema ignored ->
                    found.add(where + " is not nothing, and this shape allows nothing else");
            case AnySchema ignored -> { }
            case UnsupportedSchema ignored -> { }
            case ChoiceSchema choice -> checkChoice(value, choice, model, where, found);
            case SchemaReference ignored -> { }
        }
    }

    /** A value satisfies a choice when it satisfies any one of the shapes on offer. */
    private static void checkChoice(JsonValue value, ChoiceSchema choice, ApiModel model,
            String where, List<String> found) {
        for (CanonicalSchema alternative : choice.alternatives()) {
            List<String> against = new ArrayList<>();
            check(value, alternative, model, where, against);
            if (against.isEmpty()) {
                return;
            }
        }
        found.add(where + " is " + value + ", which fits none of the shapes this value may have");
    }

    private static void checkString(JsonValue value, StringSchema schema, String where,
            List<String> found) {
        if (!(value instanceof JsonValue.JsonString text)) {
            found.add(where + " should be text but is " + value);
            return;
        }
        int length = text.value().length();
        schema.minLength().ifPresent(lowest -> require(length >= lowest,
                where + " is " + length + " characters, fewer than the " + lowest + " required",
                found));
        schema.maxLength().ifPresent(highest -> require(length <= highest,
                where + " is " + length + " characters, more than the " + highest + " allowed",
                found));
    }

    private static void checkNumber(JsonValue value, NumberSchema schema, String where,
            List<String> found) {
        if (!(value instanceof JsonValue.JsonNumber number)) {
            found.add(where + " should be a number but is " + value);
            return;
        }
        BigDecimal held = number.value();
        if (schema.kind() == NumberKind.INTEGER) {
            require(held.stripTrailingZeros().scale() <= 0,
                    where + " is " + held + ", which is not a whole number", found);
        }
        schema.minimum().ifPresent(bound -> require(held.compareTo(bound) >= 0,
                where + " is " + held + ", below the minimum of " + bound, found));
        schema.maximum().ifPresent(bound -> require(held.compareTo(bound) <= 0,
                where + " is " + held + ", above the maximum of " + bound, found));
        schema.exclusiveMinimum().ifPresent(bound -> require(held.compareTo(bound) > 0,
                where + " is " + held + ", which is not above " + bound, found));
        schema.exclusiveMaximum().ifPresent(bound -> require(held.compareTo(bound) < 0,
                where + " is " + held + ", which is not below " + bound, found));
        schema.multipleOf().ifPresent(step -> require(
                held.remainder(step).compareTo(BigDecimal.ZERO) == 0,
                where + " is " + held + ", which is not a multiple of " + step, found));
    }

    private static void checkList(JsonValue value, ArraySchema schema, ApiModel model, String where,
            List<String> found) {
        if (!(value instanceof JsonValue.JsonArray list)) {
            found.add(where + " should be a list but is " + value);
            return;
        }
        List<JsonValue> elements = list.elements();
        schema.minItems().ifPresent(fewest -> require(elements.size() >= fewest,
                where + " has " + elements.size() + " items, fewer than the " + fewest + " required",
                found));
        schema.maxItems().ifPresent(most -> require(elements.size() <= most,
                where + " has " + elements.size() + " items, more than the " + most + " allowed",
                found));
        if (schema.uniqueItems()) {
            require(elements.size() == elements.stream().distinct().count(),
                    where + " repeats an item, and every item here must differ", found);
        }
        for (int i = 0; i < elements.size(); i++) {
            check(elements.get(i), schema.items(), model, where + "[" + i + "]", found);
        }
    }

    private static void checkObject(JsonValue value, ObjectSchema schema, ApiModel model,
            String where, List<String> found) {
        if (!(value instanceof JsonValue.JsonObject object)) {
            found.add(where + " should be an object but is " + value);
            return;
        }
        Map<String, JsonValue> members = object.members();
        for (String required : schema.required()) {
            require(members.containsKey(required),
                    where + " is missing '" + required + "', which is required", found);
        }
        schema.minProperties().ifPresent(fewest -> require(members.size() >= fewest,
                where + " has " + members.size() + " properties, fewer than the " + fewest
                        + " required", found));
        schema.maxProperties().ifPresent(most -> require(members.size() <= most,
                where + " has " + members.size() + " properties, more than the " + most + " allowed",
                found));
        members.forEach((name, held) -> schema.property(name).ifPresent(shape ->
                check(held, shape, model, where + "." + name, found)));
    }

    private static void require(boolean satisfied, String complaint, List<String> found) {
        if (!satisfied) {
            found.add(complaint);
        }
    }
}

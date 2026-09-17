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
package io.restest.spec;

import io.restest.core.json.JsonValue;
import io.restest.core.schema.AnySchema;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.BooleanSchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.NothingSchema;
import io.restest.core.schema.NullSchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import io.restest.core.schema.UnsupportedSchema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Works out the single shape a value must have in order to satisfy several shapes at once.
 *
 * <p>A specification often describes a thing by saying "it is everything one of these is, and
 * everything the other is too". A pet owner, for example, is described as all the ordinary owner
 * fields - name, address, telephone - plus an identifier the API assigns. That saves the document
 * from repeating five fields everywhere an owner appears, and it is the most common way real APIs
 * describe their resources.
 *
 * <p>RESTest cannot work with a description like that directly: whatever invents a value to send,
 * and whatever checks the reply that comes back, wants one shape rather than a list of shapes to
 * combine. This class does the combining, once, while the specification is being read. Afterwards
 * the owner is an ordinary object with six properties and nothing else in RESTest need ever know
 * the document described it in two halves.
 *
 * <p>Most of the time combining is simply putting two sets of properties together, because the two
 * halves talk about different things. The interesting part is what happens when they talk about the
 * same thing and disagree, and there the rule is always the same: <b>both halves have to hold, so
 * the stricter one wins.</b> If one says a telephone number is at most fifty characters and the
 * other says at most twenty, the answer is twenty - a thirty-character number would satisfy the
 * first half and not the second, and the document asked for both.
 *
 * <p>Three answers here are not a value at all, and each is deliberate:
 *
 * <ul>
 *   <li><b>Nothing fits.</b> One half demands at least five characters and the other at most three.
 *       That was understood perfectly; what it describes is a value that cannot exist, and saying so
 *       lets whatever comes next report an operation it cannot test, with the reason.
 *   <li><b>We know there is an answer but cannot work it out.</b> Two halves that each demand a
 *       different text pattern do have a combined meaning, but computing it is a much harder problem
 *       than it looks. That one field is reported as unreadable; the rest of the shape is still
 *       combined normally.
 *   <li><b>The halves refer to each other in a circle.</b> Reported rather than followed for ever.
 * </ul>
 *
 * <p>One rule is subtler than "the stricter wins", and it is the one most easily got wrong. When a
 * half says what it allows for properties it did <em>not</em> name, that rule applies to the other
 * half's properties too, because the other half's names are exactly the ones this half did not name.
 * A half listing {@code name} and forbidding anything else, combined with a half listing {@code id},
 * describes an object in which {@code id} is forbidden - not one that accepts both.
 */
final class AllOfMerger {

    private AllOfMerger() {
    }

    /**
     * The one shape a value must have to satisfy every one of the given shapes at once.
     *
     * <p>Combining starts from the first half rather than from a shape that says nothing, because
     * every fact here narrows when two halves disagree and a shape that says nothing disagrees with
     * all of them. Starting from one would quietly withdraw what a lone half allows - most visibly
     * that the value may be null, which is stated by one half and denied by the empty one.
     *
     * @param halves the shapes to combine, in the order the document declared them
     * @return their combination, which may be a shape nothing satisfies or one that could not be
     *     worked out
     */
    static CanonicalSchema fold(List<CanonicalSchema> halves) {
        if (halves.isEmpty()) {
            return AnySchema.of();
        }
        CanonicalSchema folded = halves.get(0);
        for (CanonicalSchema half : halves.subList(1, halves.size())) {
            folded = merge(folded, half);
        }
        return folded;
    }

    /** The one shape a value must have to satisfy both of the given shapes. */
    static CanonicalSchema merge(CanonicalSchema first, CanonicalSchema second) {
        return withoutADefaultThatNoLongerFits(mergeShapes(first, second));
    }

    private static CanonicalSchema mergeShapes(CanonicalSchema first, CanonicalSchema second) {
        if (first instanceof UnsupportedSchema unreadable) {
            return unreadable;
        }
        if (second instanceof UnsupportedSchema unreadable) {
            return unreadable;
        }
        if (first instanceof NothingSchema || second instanceof NothingSchema) {
            return NothingSchema.of();
        }

        SchemaMetadata metadata = mergeMetadata(first.metadata(), second.metadata());
        if (enumerationRuledEverythingOut(first.metadata(), second.metadata(), metadata)) {
            return NothingSchema.of();
        }

        // A half that names no type constrains nothing about the shape itself, so all it can
        // contribute is what it says about the value regardless of type: that it may be null, that
        // it is one of a fixed set, what it defaults to.
        if (first instanceof AnySchema) {
            return withMetadata(second, metadata);
        }
        if (second instanceof AnySchema) {
            return withMetadata(first, metadata);
        }

        if (first instanceof ObjectSchema left && second instanceof ObjectSchema right) {
            return mergeObjects(left, right, metadata);
        }
        if (first instanceof StringSchema left && second instanceof StringSchema right) {
            return mergeStrings(left, right, metadata);
        }
        if (first instanceof NumberSchema left && second instanceof NumberSchema right) {
            return mergeNumbers(left, right, metadata);
        }
        if (first instanceof ArraySchema left && second instanceof ArraySchema right) {
            return mergeArrays(left, right, metadata);
        }
        if (first instanceof BooleanSchema && second instanceof BooleanSchema) {
            return new BooleanSchema(metadata);
        }
        if (first instanceof NullSchema && second instanceof NullSchema) {
            return new NullSchema(metadata);
        }
        if (first instanceof SchemaReference left && second instanceof SchemaReference right) {
            return left.name().equals(right.name())
                    ? new SchemaReference(metadata, left.name())
                    : new UnsupportedSchema(metadata, "this has to be both '" + left.name()
                            + "' and '" + right.name() + "' at once, and combining two shapes named "
                            + "elsewhere is not supported yet");
        }
        if (first instanceof SchemaReference || second instanceof SchemaReference) {
            return new UnsupportedSchema(metadata, "this has to be both a shape named elsewhere and "
                    + "one written out in place, and combining those is not supported yet");
        }
        // Two different types, each understood: a value cannot be a word and a number at once.
        return NothingSchema.of();
    }

    /**
     * Two objects combined.
     *
     * <p>The subtle part is the rule a half states for properties it did not name. It applies to
     * every property that half does not name - including the ones the <em>other</em> half names - so
     * a property only one half describes still has to satisfy the other half's rule for strangers.
     * A half that names {@code name} and forbids anything else, combined with one that names
     * {@code id}, describes an object in which {@code id} is forbidden, not one that accepts both.
     */
    private static CanonicalSchema mergeObjects(ObjectSchema first, ObjectSchema second,
            SchemaMetadata metadata) {
        Set<String> names = new LinkedHashSet<>(first.properties().keySet());
        names.addAll(second.properties().keySet());

        Map<String, CanonicalSchema> properties = new LinkedHashMap<>();
        for (String name : names) {
            CanonicalSchema inFirst = first.properties().get(name);
            CanonicalSchema inSecond = second.properties().get(name);
            if (inFirst != null && inSecond != null) {
                properties.put(name, merge(inFirst, inSecond));
            } else if (inFirst != null) {
                properties.put(name, alsoSatisfying(inFirst, second.additionalProperties()));
            } else {
                properties.put(name, alsoSatisfying(inSecond, first.additionalProperties()));
            }
        }

        Set<String> required = new LinkedHashSet<>(first.required());
        required.addAll(second.required());
        // A property the object must carry, and that no value satisfies, is an object no value
        // satisfies. Saying so here is the difference between a shape that states something untrue
        // and one every consumer would have to work this out from for itself.
        for (String name : required) {
            if (properties.get(name) instanceof NothingSchema) {
                return NothingSchema.of();
            }
        }

        Optional<Integer> minProperties = largest(first.minProperties(), second.minProperties());
        Optional<Integer> maxProperties = smallest(first.maxProperties(), second.maxProperties());
        if (leavesNoRoom(minProperties, maxProperties)) {
            return NothingSchema.of();
        }

        return new ObjectSchema(metadata, properties, required,
                mergeAdditionalProperties(first.additionalProperties(), second.additionalProperties()),
                minProperties, maxProperties);
    }

    /** A shape, narrowed by a rule the other half states for properties it does not name. */
    private static CanonicalSchema alsoSatisfying(CanonicalSchema shape,
            Optional<CanonicalSchema> ruleForUnnamedProperties) {
        return ruleForUnnamedProperties.map(rule -> merge(shape, rule)).orElse(shape);
    }

    /**
     * What both halves allow for properties neither of them named. An absent rule allows anything,
     * so a half that states one is always the stricter of the two.
     */
    private static Optional<CanonicalSchema> mergeAdditionalProperties(
            Optional<CanonicalSchema> first, Optional<CanonicalSchema> second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        return Optional.of(merge(first.get(), second.get()));
    }

    private static CanonicalSchema mergeStrings(StringSchema first, StringSchema second,
            SchemaMetadata metadata) {
        Optional<String> pattern = onlyPossibleValue(first.pattern(), second.pattern());
        if (pattern == null) {
            return new UnsupportedSchema(metadata, "this has to match two different patterns at "
                    + "once ('" + first.pattern().orElseThrow() + "' and '"
                    + second.pattern().orElseThrow() + "'), and working out what satisfies both is "
                    + "not supported yet");
        }
        Optional<String> format = onlyPossibleValue(first.format(), second.format());
        if (format == null) {
            return new UnsupportedSchema(metadata, "this is written in two different formats at "
                    + "once ('" + first.format().orElseThrow() + "' and '"
                    + second.format().orElseThrow() + "'), and working out what satisfies both is "
                    + "not supported yet");
        }

        Optional<Integer> minLength = largest(first.minLength(), second.minLength());
        Optional<Integer> maxLength = smallest(first.maxLength(), second.maxLength());
        if (leavesNoRoom(minLength, maxLength)) {
            return NothingSchema.of();
        }
        return new StringSchema(metadata, minLength, maxLength, pattern, format);
    }

    private static CanonicalSchema mergeNumbers(NumberSchema first, NumberSchema second,
            SchemaMetadata metadata) {
        Optional<String> format = onlyPossibleValue(first.format(), second.format());
        if (format == null) {
            return new UnsupportedSchema(metadata, "this is written in two different formats at "
                    + "once ('" + first.format().orElseThrow() + "' and '"
                    + second.format().orElseThrow() + "'), and working out what satisfies both is "
                    + "not supported yet");
        }
        if (bothStatedDifferent(first.multipleOf(), second.multipleOf())) {
            return new UnsupportedSchema(metadata, "this has to be a multiple of two different "
                    + "numbers at once (" + first.multipleOf().orElseThrow().toPlainString() + " and "
                    + second.multipleOf().orElseThrow().toPlainString() + "), and working out what "
                    + "satisfies both is not supported yet");
        }

        // Whole numbers are the stricter of the two, so one half asking for one settles it.
        NumberKind kind = first.kind() == NumberKind.INTEGER || second.kind() == NumberKind.INTEGER
                ? NumberKind.INTEGER : NumberKind.NUMBER;
        Optional<BigDecimal> minimum = largestNumber(first.minimum(), second.minimum());
        Optional<BigDecimal> exclusiveMinimum =
                largestNumber(first.exclusiveMinimum(), second.exclusiveMinimum());
        Optional<BigDecimal> maximum = smallestNumber(first.maximum(), second.maximum());
        Optional<BigDecimal> exclusiveMaximum =
                smallestNumber(first.exclusiveMaximum(), second.exclusiveMaximum());
        if (leavesNoNumber(minimum, exclusiveMinimum, maximum, exclusiveMaximum)) {
            return NothingSchema.of();
        }
        return new NumberSchema(metadata, kind, minimum, exclusiveMinimum, maximum, exclusiveMaximum,
                first.multipleOf().or(second::multipleOf), format);
    }

    private static CanonicalSchema mergeArrays(ArraySchema first, ArraySchema second,
            SchemaMetadata metadata) {
        Optional<Integer> minItems = largest(first.minItems(), second.minItems());
        Optional<Integer> maxItems = smallest(first.maxItems(), second.maxItems());
        if (leavesNoRoom(minItems, maxItems)) {
            return NothingSchema.of();
        }
        return new ArraySchema(metadata, merge(first.items(), second.items()), minItems, maxItems,
                first.uniqueItems() || second.uniqueItems());
    }

    /**
     * The facts both halves state about the value whatever its type. Every one of them narrows:
     * {@code null} is acceptable only if both halves accept it, and the set of allowed values is
     * whatever appears in both lists.
     */
    private static SchemaMetadata mergeMetadata(SchemaMetadata first, SchemaMetadata second) {
        List<JsonValue> enumeration;
        if (first.enumeration().isEmpty()) {
            enumeration = second.enumeration();
        } else if (second.enumeration().isEmpty()) {
            enumeration = first.enumeration();
        } else {
            enumeration = new ArrayList<>(first.enumeration());
            enumeration.retainAll(second.enumeration());
        }
        return new SchemaMetadata(
                first.description().or(second::description),
                first.nullable() && second.nullable(),
                enumeration,
                first.defaultValue().or(second::defaultValue),
                first.deprecated() || second.deprecated(),
                stricterAccess(first.access(), second.access()));
    }

    /**
     * Whether two lists of allowed values have nothing in common, which is a value that cannot
     * exist rather than a value with no list.
     */
    private static boolean enumerationRuledEverythingOut(SchemaMetadata first, SchemaMetadata second,
            SchemaMetadata merged) {
        return !first.enumeration().isEmpty() && !second.enumeration().isEmpty()
                && merged.enumeration().isEmpty();
    }

    /**
     * Which direction the value may travel when two halves disagree. "Both ways" is whatever the
     * other half says. Halves that say the value is only ever returned and only ever sent
     * contradict each other, and the answer is "only ever returned" whichever order they were
     * written in: an intersection that depended on which line came first would not be one, and of
     * the two answers that is the one that cannot put a value into a request the API refuses.
     */
    private static SchemaMetadata.Access stricterAccess(SchemaMetadata.Access first,
            SchemaMetadata.Access second) {
        if (first == second || second == SchemaMetadata.Access.READ_WRITE) {
            return first;
        }
        if (first == SchemaMetadata.Access.READ_WRITE) {
            return second;
        }
        return SchemaMetadata.Access.READ_ONLY;
    }

    /** The same shape, carrying the facts both halves stated about the value. */
    private static CanonicalSchema withMetadata(CanonicalSchema schema, SchemaMetadata metadata) {
        return switch (schema) {
            case ObjectSchema o -> new ObjectSchema(metadata, o.properties(), o.required(),
                    o.additionalProperties(), o.minProperties(), o.maxProperties());
            case StringSchema s -> new StringSchema(metadata, s.minLength(), s.maxLength(),
                    s.pattern(), s.format());
            case NumberSchema n -> new NumberSchema(metadata, n.kind(), n.minimum(),
                    n.exclusiveMinimum(), n.maximum(), n.exclusiveMaximum(), n.multipleOf(),
                    n.format());
            case ArraySchema a -> new ArraySchema(metadata, a.items(), a.minItems(), a.maxItems(),
                    a.uniqueItems());
            case BooleanSchema ignored -> new BooleanSchema(metadata);
            case NullSchema ignored -> new NullSchema(metadata);
            case AnySchema ignored -> new AnySchema(metadata);
            case NothingSchema ignored -> NothingSchema.of();
            case SchemaReference r -> new SchemaReference(metadata, r.name());
            case UnsupportedSchema u -> new UnsupportedSchema(metadata, u.reason());
        };
    }

    /**
     * The value both halves state, when they agree or only one of them states it, and {@code null}
     * when they state different ones - a caller's signal that it has to say so rather than pick.
     */
    private static Optional<String> onlyPossibleValue(Optional<String> first,
            Optional<String> second) {
        if (bothStatedDifferent(first, second)) {
            return null;
        }
        return first.or(() -> second);
    }

    private static <T extends Comparable<T>> boolean bothStatedDifferent(Optional<T> first,
            Optional<T> second) {
        return first.isPresent() && second.isPresent()
                && first.get().compareTo(second.get()) != 0;
    }

    private static Optional<Integer> largest(Optional<Integer> first, Optional<Integer> second) {
        return first.isEmpty() ? second
                : second.isEmpty() ? first : Optional.of(Math.max(first.get(), second.get()));
    }

    private static Optional<Integer> smallest(Optional<Integer> first, Optional<Integer> second) {
        return first.isEmpty() ? second
                : second.isEmpty() ? first : Optional.of(Math.min(first.get(), second.get()));
    }

    private static Optional<BigDecimal> largestNumber(Optional<BigDecimal> first,
            Optional<BigDecimal> second) {
        return first.isEmpty() ? second
                : second.isEmpty() ? first : Optional.of(first.get().max(second.get()));
    }

    private static Optional<BigDecimal> smallestNumber(Optional<BigDecimal> first,
            Optional<BigDecimal> second) {
        return first.isEmpty() ? second
                : second.isEmpty() ? first : Optional.of(first.get().min(second.get()));
    }

    private static boolean leavesNoRoom(Optional<Integer> lower, Optional<Integer> upper) {
        return lower.isPresent() && upper.isPresent() && lower.get() > upper.get();
    }

    /**
     * Whether the four bounds leave no number between them. An exclusive bound is compared more
     * strictly than an inclusive one, which is the whole difference between the two: a minimum and a
     * maximum that are equal accept exactly one number, while an <em>exclusive</em> minimum equal to
     * the maximum accepts none.
     */
    private static boolean leavesNoNumber(Optional<BigDecimal> minimum,
            Optional<BigDecimal> exclusiveMinimum, Optional<BigDecimal> maximum,
            Optional<BigDecimal> exclusiveMaximum) {
        return empty(minimum, maximum, false)
                || empty(exclusiveMinimum, maximum, true)
                || empty(minimum, exclusiveMaximum, true)
                || empty(exclusiveMinimum, exclusiveMaximum, true);
    }

    /**
     * The same shape without a default value it would itself refuse.
     *
     * <p>A default is inherited from whichever half stated one, and the other half may well forbid
     * it: a half saying the value defaults to ten characters, combined with one saying it may be at
     * most three, leaves a shape whose own default breaks it. Left in place that default is not a
     * harmless annotation - it is offered first when a value is needed, so RESTest would send a value
     * its own model says is wrong, read the refusal as the API's fault, and report it. Dropping it is
     * the same answer the parser already gives to a default it cannot read: the shape then says it
     * has no default, which is true.
     */
    private static CanonicalSchema withoutADefaultThatNoLongerFits(CanonicalSchema schema) {
        Optional<JsonValue> declared = schema.metadata().defaultValue();
        if (declared.isEmpty() || accepts(schema, declared.get())) {
            return schema;
        }
        SchemaMetadata without = new SchemaMetadata(schema.metadata().description(),
                schema.metadata().nullable(), schema.metadata().enumeration(), Optional.empty(),
                schema.metadata().deprecated(), schema.metadata().access());
        return withMetadata(schema, without);
    }

    /**
     * Whether a value satisfies a shape, as far as can be told cheaply.
     *
     * <p>Deliberately one-sided: it answers "yes" whenever it cannot tell, because the only thing
     * that depends on it is whether to discard something the document stated, and discarding a
     * default that might have been fine is worse than keeping one we could not check.
     */
    private static boolean accepts(CanonicalSchema schema, JsonValue value) {
        if (value instanceof JsonValue.JsonNull && schema.metadata().nullable()) {
            return true;
        }
        List<JsonValue> allowed = schema.metadata().enumeration();
        if (!allowed.isEmpty() && !allowed.contains(value)) {
            return false;
        }
        return switch (schema) {
            case NothingSchema ignored -> false;
            case StringSchema text -> value instanceof JsonValue.JsonString string
                    && withinLength(string.value(), text) && matching(string.value(), text);
            case NumberSchema number -> value instanceof JsonValue.JsonNumber amount
                    && withinBounds(amount.value(), number);
            case BooleanSchema ignored -> value instanceof JsonValue.JsonBoolean;
            case NullSchema ignored -> value instanceof JsonValue.JsonNull;
            case ArraySchema ignored -> value instanceof JsonValue.JsonArray;
            case ObjectSchema ignored -> value instanceof JsonValue.JsonObject;
            // A shape named elsewhere, one that accepts anything, one we could not read: not checked.
            case AnySchema ignored -> true;
            case SchemaReference ignored -> true;
            case UnsupportedSchema ignored -> true;
        };
    }

    private static boolean withinLength(String value, StringSchema schema) {
        int length = value.codePointCount(0, value.length());
        return schema.minLength().map(minimum -> length >= minimum).orElse(true)
                && schema.maxLength().map(maximum -> length <= maximum).orElse(true);
    }

    private static boolean matching(String value, StringSchema schema) {
        if (schema.pattern().isEmpty()) {
            return true;
        }
        try {
            return java.util.regex.Pattern.compile(schema.pattern().get()).matcher(value).find();
        } catch (java.util.regex.PatternSyntaxException cannotBeJudged) {
            // A pattern Java will not compile is one this cannot check, not one nothing satisfies.
            return true;
        }
    }

    private static boolean withinBounds(BigDecimal value, NumberSchema schema) {
        if (schema.kind() == NumberKind.INTEGER && value.stripTrailingZeros().scale() > 0) {
            return false;
        }
        if (schema.minimum().map(bound -> value.compareTo(bound) < 0).orElse(false)
                || schema.maximum().map(bound -> value.compareTo(bound) > 0).orElse(false)
                || schema.exclusiveMinimum().map(bound -> value.compareTo(bound) <= 0).orElse(false)
                || schema.exclusiveMaximum().map(bound -> value.compareTo(bound) >= 0).orElse(false)) {
            return false;
        }
        return schema.multipleOf()
                .map(factor -> value.remainder(factor).compareTo(BigDecimal.ZERO) == 0)
                .orElse(true);
    }

    private static boolean empty(Optional<BigDecimal> lower, Optional<BigDecimal> upper,
            boolean exclusive) {
        if (lower.isEmpty() || upper.isEmpty()) {
            return false;
        }
        int order = lower.get().compareTo(upper.get());
        return order > 0 || (exclusive && order == 0);
    }
}

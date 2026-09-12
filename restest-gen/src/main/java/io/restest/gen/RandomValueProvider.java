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

import io.restest.core.gen.GeneratedValue;
import io.restest.core.gen.ValueProvider;
import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.schema.AnySchema;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.BooleanSchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.NothingSchema;
import io.restest.core.schema.NullSchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import io.restest.core.schema.UnsupportedSchema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Invents a value from the shape the specification describes.
 *
 * <p>This is the source of last resort, and the one that makes the tool work at all against an API
 * nobody has configured anything for. Told that a parameter is a string of between three and ten
 * characters, it invents one of those; told that it is a whole number no smaller than one and a
 * multiple of five, it invents one of those; told that it is a list of objects, it invents the list
 * and each object in it, filling in the properties the specification says are required.
 *
 * <p>Two limits are worth knowing, because they are visible in the results. A specification can
 * describe the *characters* a string must be made of - a date, an e-mail address, a pattern - and this
 * pays no attention to that yet, so those parameters get an ordinary word and the API will often
 * refuse it. And a shape the specification describes in a way the parser could not read is declined
 * outright rather than guessed at, which means the parameter is left out if the API allows that and
 * the operation is reported as untestable if it does not - an honest gap rather than a request that
 * was never going to work.
 *
 * <p>A shape that refers to itself - a comment with replies, each of which is a comment - is followed
 * only a few levels deep and then wound up, because the specification permits an infinitely large
 * value and no API wants to receive one.
 */
public final class RandomValueProvider implements ValueProvider {

    /** How deep a shape that contains itself is followed before it is wound up. */
    private static final int MAX_DEPTH = 4;

    private static final String LETTERS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /** Enough to look like a real value, short enough that no API refuses it for being long. */
    private static final int DEFAULT_STRING_LENGTH = 8;

    /** Room to move in, for a number the specification left unbounded. */
    private static final BigDecimal DEFAULT_LOWEST = BigDecimal.ZERO;
    private static final BigDecimal DEFAULT_HIGHEST = BigDecimal.valueOf(1000);

    private static final int DEFAULT_ITEMS = 2;
    private static final int MAX_ITEMS = 4;

    /** How often a property the specification does not require is included anyway. */
    private static final double OPTIONAL_PROPERTY_CHANCE = 0.5;

    private final ApiModel model;
    private final RandomGenerator random;

    /**
     * A provider that invents values for the API the model describes.
     *
     * @param model the API, needed to look up shapes the specification refers to by name
     * @param random where the values come from. Sharing one seeded source across the whole run is
     *     what makes a run repeatable
     */
    public RandomValueProvider(ApiModel model, RandomGenerator random) {
        this.model = Objects.requireNonNull(model, "model");
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public Optional<GeneratedValue> offer(ValueRequest request) {
        Objects.requireNonNull(request, "request");
        return value(request.schema(), 0)
                .map(value -> GeneratedValue.generatedBy(value, name()));
    }

    @Override
    public String name() {
        return "random";
    }

    private Optional<JsonValue> value(CanonicalSchema schema, int depth) {
        if (schema.metadata().nullable() && random.nextInt(8) == 0) {
            return Optional.of(JsonValue.NULL);
        }
        return switch (schema) {
            case StringSchema string -> Optional.of(text(string));
            case NumberSchema number -> number(number);
            case BooleanSchema ignored -> Optional.of(JsonValue.of(random.nextBoolean()));
            case NullSchema ignored -> Optional.of(JsonValue.NULL);
            case ArraySchema array -> list(array, depth);
            case ObjectSchema object -> object(object, depth);
            case AnySchema ignored -> Optional.of(anything());
            case SchemaReference reference -> named(reference, depth);
            // Nothing can be sent that satisfies a schema saying no value is acceptable, and a shape
            // nobody could read is not one to guess at: both decline, and the caller reports it.
            case NothingSchema ignored -> Optional.empty();
            case UnsupportedSchema ignored -> Optional.empty();
        };
    }

    private JsonValue text(StringSchema schema) {
        // At least one character unless the specification insists otherwise: an empty value is
        // legal almost everywhere and useful almost nowhere, and it makes for requests like
        // "?widgetId=" that tell nobody anything.
        int lowest = schema.minLength().orElse(1);
        int highest = schema.maxLength().orElse(Math.max(lowest, DEFAULT_STRING_LENGTH));
        lowest = Math.min(lowest, highest);
        int length = lowest == highest ? lowest : lowest + random.nextInt(highest - lowest + 1);
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            value.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        }
        return JsonValue.of(value.toString());
    }

    /**
     * A number inside every bound the schema states, and on its step if it has one.
     *
     * <p>Empty when the bounds and the step leave nothing to choose - a whole number strictly between
     * 1 and 2, or a multiple of 10 between 3 and 7. The specification permits that combination and no
     * value satisfies it, so saying so is the only honest answer.
     */
    private Optional<JsonValue> number(NumberSchema schema) {
        boolean whole = schema.kind() == NumberKind.INTEGER;
        BigDecimal step = schema.multipleOf().orElse(whole ? BigDecimal.ONE : null);

        BigDecimal lowest = schema.minimum()
                .or(() -> schema.exclusiveMinimum().map(bound -> nextAbove(bound, step, whole)))
                .orElse(DEFAULT_LOWEST);
        BigDecimal highest = schema.maximum()
                .or(() -> schema.exclusiveMaximum().map(bound -> nextBelow(bound, step, whole)))
                .orElse(null);
        if (highest == null) {
            highest = lowest.max(DEFAULT_LOWEST).add(DEFAULT_HIGHEST);
        }
        if (lowest.compareTo(highest) > 0) {
            return Optional.empty();
        }

        BigDecimal chosen = between(lowest, highest, whole);
        if (step != null) {
            chosen = onStep(chosen, step, lowest, highest);
            if (chosen == null) {
                return Optional.empty();
            }
        }
        return Optional.of(JsonValue.of(chosen));
    }

    private BigDecimal between(BigDecimal lowest, BigDecimal highest, boolean whole) {
        BigDecimal span = highest.subtract(lowest);
        BigDecimal offset = span.multiply(BigDecimal.valueOf(random.nextDouble()));
        BigDecimal chosen = lowest.add(offset);
        return whole ? chosen.setScale(0, RoundingMode.DOWN) : chosen.setScale(
                Math.min(3, Math.max(span.scale(), lowest.scale())), RoundingMode.DOWN);
    }

    /** The nearest value on the step at or below the choice, or above it if that falls out of range. */
    private static BigDecimal onStep(BigDecimal chosen, BigDecimal step, BigDecimal lowest,
            BigDecimal highest) {
        BigDecimal down = chosen.divide(step, 0, RoundingMode.FLOOR).multiply(step);
        if (down.compareTo(lowest) >= 0) {
            return down;
        }
        BigDecimal up = chosen.divide(step, 0, RoundingMode.CEILING).multiply(step);
        return up.compareTo(highest) <= 0 && up.compareTo(lowest) >= 0 ? up : null;
    }

    /** The smallest value strictly above an exclusive bound, on the step when there is one. */
    private static BigDecimal nextAbove(BigDecimal bound, BigDecimal step, boolean whole) {
        BigDecimal increment = step != null ? step : smallestStep(bound, whole);
        return bound.add(increment);
    }

    private static BigDecimal nextBelow(BigDecimal bound, BigDecimal step, boolean whole) {
        BigDecimal decrement = step != null ? step : smallestStep(bound, whole);
        return bound.subtract(decrement);
    }

    private static BigDecimal smallestStep(BigDecimal bound, boolean whole) {
        return whole ? BigDecimal.ONE : BigDecimal.ONE.movePointLeft(Math.max(bound.scale(), 1));
    }

    private Optional<JsonValue> list(ArraySchema schema, int depth) {
        int lowest = schema.minItems().orElse(depth >= MAX_DEPTH ? 0 : 1);
        int highest = schema.maxItems().orElse(Math.max(lowest, DEFAULT_ITEMS));
        int wanted = Math.min(MAX_ITEMS,
                lowest == highest ? lowest : lowest + random.nextInt(highest - lowest + 1));
        if (depth >= MAX_DEPTH) {
            wanted = lowest;
        }

        List<JsonValue> elements = new ArrayList<>(wanted);
        for (int i = 0; i < wanted; i++) {
            Optional<JsonValue> element = value(schema.items(), depth + 1);
            if (element.isEmpty()) {
                // Nothing can fill the list, so the list can only be made if it may be empty.
                return lowest == 0 ? Optional.of(JsonValue.array(List.of())) : Optional.empty();
            }
            if (!schema.uniqueItems() || !elements.contains(element.get())) {
                elements.add(element.get());
            }
        }
        return elements.size() >= lowest
                ? Optional.of(JsonValue.array(elements))
                : Optional.empty();
    }

    private Optional<JsonValue> object(ObjectSchema schema, int depth) {
        Map<String, JsonValue> members = new LinkedHashMap<>();
        for (Map.Entry<String, CanonicalSchema> property : schema.properties().entrySet()) {
            boolean required = schema.isRequired(property.getKey());
            if (!required && (depth >= MAX_DEPTH
                    || random.nextDouble() >= OPTIONAL_PROPERTY_CHANCE)) {
                continue;
            }
            Optional<JsonValue> value = value(property.getValue(), depth + 1);
            if (value.isPresent()) {
                members.put(property.getKey(), value.get());
            } else if (required) {
                // A property the API insists on and nothing can satisfy makes the whole object
                // impossible; saying so beats sending an object that is knowingly incomplete.
                return Optional.empty();
            }
        }
        return Optional.of(JsonValue.object(members));
    }

    /** A value for a shape that says nothing about itself, which nearly always means a string. */
    private JsonValue anything() {
        return switch (random.nextInt(4)) {
            case 0 -> JsonValue.of(random.nextBoolean());
            case 1 -> JsonValue.of(random.nextInt(1000));
            default -> text(StringSchema.of());
        };
    }

    private Optional<JsonValue> named(SchemaReference reference, int depth) {
        if (depth >= MAX_DEPTH) {
            return Optional.empty();
        }
        return model.resolve(reference).flatMap(schema -> value(schema, depth + 1));
    }
}

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
import io.restest.core.model.ParameterLocation;
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
 * <p>For anything inside that list or object it asks a better-informed source first, and only invents
 * when nobody answers. That matters more than it sounds: a list of statuses whose members must be one
 * of {@code available}, {@code pending} or {@code sold} is useless if only the list itself is built
 * from the specification and its contents are made up.
 *
 * <p>Two limits are worth knowing, because they are visible in the results. A specification can
 * describe the <em>characters</em> a string must be made of - a date, an e-mail address, a pattern -
 * and this pays no attention to that yet, so those parameters get an ordinary word and the API will
 * often refuse it. And a shape the specification describes in a way the parser could not read is
 * declined outright rather than guessed at, which means the parameter is left out if the API allows
 * that and the operation is reported as untestable if it does not - an honest gap rather than a
 * request that was never going to work.
 *
 * <p>Everything it invents is kept small on purpose. A specification may permit a string of two
 * million characters or a list nested twelve deep, and building one would cost the run its budget and
 * the API under test its patience without testing anything a short value does not. Values the
 * specification <em>insists</em> are enormous are declined and reported rather than built.
 */
public final class RandomValueProvider implements ValueProvider {

    /** Where optional nesting stops: below this, only what the specification insists on is built. */
    private static final int MAX_DEPTH = 4;

    /** Where everything stops, however insistent the specification is. */
    private static final int HARD_DEPTH = 8;

    private static final String LETTERS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /** Enough to look like a real value, short enough that no API refuses it for being long. */
    private static final int DEFAULT_STRING_LENGTH = 8;

    /** The longest string invented when the specification does not demand more. */
    private static final int USUAL_LONGEST_STRING = 64;

    /** Beyond this, a demanded length is declined rather than built. */
    private static final int LONGEST_STRING = 10_000;

    /** Room to move in, for a number the specification left unbounded. */
    private static final BigDecimal DEFAULT_LOWEST = BigDecimal.ZERO;
    private static final BigDecimal DEFAULT_HIGHEST = BigDecimal.valueOf(1000);

    /** Decimal places for a number that is allowed to have them. */
    private static final int DECIMAL_PLACES = 2;

    private static final int DEFAULT_ITEMS = 2;
    private static final int USUAL_MOST_ITEMS = 4;

    /** Beyond this, a demanded number of items is declined rather than built. */
    private static final int MOST_ITEMS = 100;

    /** How often a property the specification does not require is included anyway. */
    private static final double OPTIONAL_PROPERTY_CHANCE = 0.5;

    /** How often a value that is allowed to be absent is sent as nothing at all. */
    private static final int NULL_IN_ONE_IN = 8;

    /** How many times a fresh element is attempted for a list whose items must all differ. */
    private static final int UNIQUE_ATTEMPTS = 8;

    private final ApiModel model;
    private final RandomGenerator random;
    private final ValueProvider inside;

    /**
     * A provider that invents values, asking the specification's own declared values for anything
     * nested inside what it builds.
     *
     * @param model the API, needed to look up shapes the specification refers to by name
     * @param random where the values come from. Sharing one seeded source across the whole run is
     *     what makes a run repeatable
     */
    public RandomValueProvider(ApiModel model, RandomGenerator random) {
        this(model, random, new DeclaredValueProvider(random));
    }

    /**
     * A provider that invents values, asking somebody better informed about anything nested inside
     * what it builds.
     *
     * @param model the API, needed to look up shapes the specification refers to by name
     * @param random where the values come from
     * @param inside who to ask about the contents of a list or an object before inventing them. The
     *     parameter itself is not asked here, because whoever put this provider in a chain has
     *     already asked everybody ahead of it
     */
    public RandomValueProvider(ApiModel model, RandomGenerator random, ValueProvider inside) {
        this.model = Objects.requireNonNull(model, "model");
        this.random = Objects.requireNonNull(random, "random");
        this.inside = Objects.requireNonNull(inside, "inside");
    }

    @Override
    public Optional<GeneratedValue> offer(ValueRequest request) {
        Objects.requireNonNull(request, "request");
        return value(request, request.schema(), 0)
                .map(value -> GeneratedValue.generatedBy(value, name()));
    }

    @Override
    public String name() {
        return "random";
    }

    private Optional<JsonValue> value(ValueRequest request, CanonicalSchema schema, int depth) {
        if (depth > HARD_DEPTH) {
            return Optional.empty();
        }
        if (depth > 0) {
            Optional<GeneratedValue> known = inside.offer(request.about(schema));
            if (known.isPresent()) {
                return Optional.of(known.get().value());
            }
        }
        // A value that may be absent is sometimes sent as nothing, because an API treats "absent"
        // and "empty" differently and both deserve trying - except in the path, where nothing at all
        // would silently address a different resource.
        if (schema.metadata().nullable() && request.location() != ParameterLocation.PATH
                && random.nextInt(NULL_IN_ONE_IN) == 0) {
            return Optional.of(JsonValue.NULL);
        }
        return switch (schema) {
            case StringSchema string -> text(string);
            case NumberSchema number -> number(number);
            case BooleanSchema ignored -> Optional.of(JsonValue.of(random.nextBoolean()));
            case NullSchema ignored -> Optional.of(JsonValue.NULL);
            case ArraySchema array -> list(request, array, depth);
            case ObjectSchema object -> object(request, object, depth);
            case AnySchema ignored -> Optional.of(anything());
            case SchemaReference reference -> named(request, reference, depth);
            // Nothing can be sent that satisfies a schema saying no value is acceptable, and a shape
            // nobody could read is not one to guess at: both decline, and the caller reports it.
            case NothingSchema ignored -> Optional.empty();
            case UnsupportedSchema ignored -> Optional.empty();
        };
    }

    /**
     * A string of a length the specification allows.
     *
     * <p>All the arithmetic is done in {@code long}s: a specification saying a string may be up to
     * {@code 2147483647} characters is ordinary - that is what a Java {@code @Size} annotation
     * produces - and computing the range of lengths in {@code int}s would overflow and end the run.
     */
    private Optional<JsonValue> text(StringSchema schema) {
        long stated = schema.maxLength().map(Integer::longValue).orElse(Long.MAX_VALUE);
        // One character unless the specification says otherwise - an empty value is legal almost
        // everywhere and useful almost nowhere - but never more than it allows: a string that must
        // be empty is empty.
        long lowest = Math.min(schema.minLength().orElse(1), stated);
        if (lowest > LONGEST_STRING) {
            return Optional.empty();
        }
        long highest = Math.min(stated, Math.max(lowest, USUAL_LONGEST_STRING));
        long length = lowest == highest ? lowest
                : lowest + random.nextLong(highest - lowest + 1);

        StringBuilder value = new StringBuilder((int) length);
        for (long i = 0; i < length; i++) {
            value.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        }
        return Optional.of(JsonValue.of(value.toString()));
    }

    /**
     * A number inside every bound the schema states, and on its step if it has one.
     *
     * <p>A schema may state both an inclusive and an exclusive bound on the same side, and the
     * tighter of the two is the one that has to be obeyed - a value no smaller than 1 but strictly
     * greater than 10 is a value of at least 11, not of at least 1.
     *
     * <p>Empty when the bounds and the step leave nothing to choose - a whole number strictly between
     * 1 and 2, or a multiple of 10 between 3 and 7. The specification permits that combination and no
     * value satisfies it, so saying so is the only honest answer.
     */
    private Optional<JsonValue> number(NumberSchema schema) {
        boolean whole = schema.kind() == NumberKind.INTEGER;
        BigDecimal step = schema.multipleOf().orElse(whole ? BigDecimal.ONE : null);

        BigDecimal lowest = tighter(schema.minimum(),
                schema.exclusiveMinimum().map(bound -> nextAbove(bound, step, whole)), true)
                .orElse(DEFAULT_LOWEST);
        BigDecimal highest = tighter(schema.maximum(),
                schema.exclusiveMaximum().map(bound -> nextBelow(bound, step, whole)), false)
                .orElseGet(() -> lowest.max(DEFAULT_LOWEST).add(DEFAULT_HIGHEST));
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

    /** The stricter of two bounds on the same side, when the schema states both. */
    private static Optional<BigDecimal> tighter(Optional<BigDecimal> inclusive,
            Optional<BigDecimal> fromExclusive, boolean lowerSide) {
        if (inclusive.isEmpty() || fromExclusive.isEmpty()) {
            return inclusive.or(() -> fromExclusive);
        }
        return Optional.of(lowerSide
                ? inclusive.get().max(fromExclusive.get())
                : inclusive.get().min(fromExclusive.get()));
    }

    private BigDecimal between(BigDecimal lowest, BigDecimal highest, boolean whole) {
        BigDecimal span = highest.subtract(lowest);
        BigDecimal offset = span.multiply(BigDecimal.valueOf(random.nextDouble()));
        BigDecimal chosen = lowest.add(offset);
        return whole
                ? chosen.setScale(0, RoundingMode.DOWN)
                // A number allowed to have decimals gets them: an API that stores a price or a
                // latitude is never exercised by a tool that only ever sends whole numbers.
                : chosen.setScale(Math.max(DECIMAL_PLACES, lowest.scale()), RoundingMode.DOWN);
    }

    /** The nearest value on the step at or below the choice, or above it if that falls out of range. */
    private static BigDecimal onStep(BigDecimal chosen, BigDecimal step, BigDecimal lowest,
            BigDecimal highest) {
        BigDecimal down = chosen.divide(step, 0, RoundingMode.FLOOR).multiply(step);
        if (down.compareTo(lowest) >= 0 && down.compareTo(highest) <= 0) {
            return down;
        }
        BigDecimal up = chosen.divide(step, 0, RoundingMode.CEILING).multiply(step);
        return up.compareTo(highest) <= 0 && up.compareTo(lowest) >= 0 ? up : null;
    }

    /** The smallest value strictly above an exclusive bound, on the step when there is one. */
    private static BigDecimal nextAbove(BigDecimal bound, BigDecimal step, boolean whole) {
        return bound.add(step != null ? step : smallestStep(bound, whole));
    }

    private static BigDecimal nextBelow(BigDecimal bound, BigDecimal step, boolean whole) {
        return bound.subtract(step != null ? step : smallestStep(bound, whole));
    }

    private static BigDecimal smallestStep(BigDecimal bound, boolean whole) {
        return whole ? BigDecimal.ONE
                : BigDecimal.ONE.movePointLeft(Math.max(bound.scale(), DECIMAL_PLACES));
    }

    private Optional<JsonValue> list(ValueRequest request, ArraySchema schema, int depth) {
        int lowest = schema.minItems().orElse(depth >= MAX_DEPTH ? 0 : 1);
        if (lowest > MOST_ITEMS) {
            return Optional.empty();
        }
        int stated = schema.maxItems().orElse(Integer.MAX_VALUE);
        int highest = Math.min(stated, Math.max(lowest, USUAL_MOST_ITEMS));
        int wanted = depth >= MAX_DEPTH || lowest == highest
                ? lowest
                : lowest + random.nextInt(highest - lowest + 1);

        List<JsonValue> elements = new ArrayList<>(wanted);
        for (int i = 0; i < wanted; i++) {
            Optional<JsonValue> element = element(request, schema, depth, elements);
            if (element.isEmpty()) {
                break;
            }
            elements.add(element.get());
        }
        return elements.size() >= lowest ? Optional.of(JsonValue.array(elements)) : Optional.empty();
    }

    /**
     * One element for a list.
     *
     * <p>When the specification says every element must differ, a repeat is tried again a few times
     * rather than dropped: for a list of two booleans that must differ, dropping the repeat would
     * leave a list of one and the whole value would be abandoned, when trying again finds the other
     * value immediately.
     */
    private Optional<JsonValue> element(ValueRequest request, ArraySchema schema, int depth,
            List<JsonValue> already) {
        int attempts = schema.uniqueItems() ? UNIQUE_ATTEMPTS : 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            Optional<JsonValue> element = value(request, schema.items(), depth + 1);
            if (element.isEmpty()) {
                return Optional.empty();
            }
            if (!schema.uniqueItems() || !already.contains(element.get())) {
                return element;
            }
        }
        return Optional.empty();
    }

    private Optional<JsonValue> object(ValueRequest request, ObjectSchema schema, int depth) {
        int fewest = schema.minProperties().orElse(0);
        int most = schema.maxProperties().orElse(Integer.MAX_VALUE);
        if (schema.required().size() > most) {
            return Optional.empty();
        }

        Map<String, JsonValue> members = new LinkedHashMap<>();
        List<Map.Entry<String, CanonicalSchema>> optional = new ArrayList<>();
        for (Map.Entry<String, CanonicalSchema> property : schema.properties().entrySet()) {
            if (schema.isRequired(property.getKey())) {
                Optional<JsonValue> value =
                        value(request.about(property.getKey(), property.getValue()), depth + 1);
                if (value.isEmpty()) {
                    // A property the API insists on and nothing can satisfy makes the whole object
                    // impossible; saying so beats sending an object that is knowingly incomplete.
                    return Optional.empty();
                }
                members.put(property.getKey(), value.get());
            } else {
                optional.add(property);
            }
        }

        for (Map.Entry<String, CanonicalSchema> property : optional) {
            boolean needed = members.size() < fewest;
            boolean wanted = depth < MAX_DEPTH && random.nextDouble() < OPTIONAL_PROPERTY_CHANCE;
            if (members.size() >= most || (!needed && !wanted)) {
                continue;
            }
            value(request.about(property.getKey(), property.getValue()), depth + 1)
                    .ifPresent(value -> members.put(property.getKey(), value));
        }

        // A specification can insist on more properties than it names, which only a value with
        // properties of its own invention can satisfy - and only if the API allows those at all.
        for (int extra = 1; members.size() < fewest && !schema.isClosed(); extra++) {
            CanonicalSchema shape = schema.additionalProperties().orElseGet(AnySchema::of);
            Optional<JsonValue> value = value(request.about("extra" + extra, shape), depth + 1);
            if (value.isEmpty()) {
                break;
            }
            members.put("extra" + extra, value.get());
        }
        return members.size() >= fewest ? Optional.of(JsonValue.object(members)) : Optional.empty();
    }

    private Optional<JsonValue> value(ValueRequest request, int depth) {
        return value(request, request.schema(), depth);
    }

    /** A value for a shape that says nothing about itself, which nearly always means a string. */
    private JsonValue anything() {
        return switch (random.nextInt(4)) {
            case 0 -> JsonValue.of(random.nextBoolean());
            case 1 -> JsonValue.of(random.nextInt(1000));
            default -> text(StringSchema.of()).orElse(JsonValue.of("value"));
        };
    }

    private Optional<JsonValue> named(ValueRequest request, SchemaReference reference, int depth) {
        if (depth >= MAX_DEPTH) {
            return Optional.empty();
        }
        return model.resolve(reference)
                .flatMap(schema -> value(request, schema, depth + 1));
    }
}

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
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
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
import io.restest.core.settings.GenerationSettings;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

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
 * <p>It reads what a specification says about the <em>characters</em> a piece of text must be made
 * of, as well as what it says about its length: told that a value is a date, it invents a date;
 * told that one is spelled as three capital letters, it invents three capital letters. Those two
 * statements are the cheapest information a document carries and the most valuable, because an
 * ordinary word in place of a date is refused by every API that looks at what it is given.
 *
 * <p>One limit is worth knowing, because it is visible in the results. A shape the specification
 * describes in a way the parser could not read is declined outright rather than guessed at, which
 * means the parameter is left out if the API allows that and the operation is reported as untestable
 * if it does not - an honest gap rather than a request that was never going to work.
 *
 * <p>Everything it invents is kept small on purpose. A specification may permit a string of two
 * million characters or a list nested twelve deep, and building one would cost the run its budget and
 * the API under test its patience without testing anything a short value does not. Values the
 * specification <em>insists</em> are enormous are declined and reported rather than built.
 *
 * <p>One of these belongs to one run and is asked for one value at a time. It remembers a little
 * about the spelling rules it has met, so that reading the same rule is not paid for again, and that
 * memory is not built to be shared between threads. Nothing in the tool shares one: a run's requests
 * are sent side by side, but the values in them are chosen one after another, and the source of
 * numbers that makes a run repeatable would not survive being shared either.
 */
public final class RandomValueProvider implements ValueProvider {

    private static final String LETTERS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final ApiModel model;
    private final RandomGenerator random;
    private final ValueProvider inside;

    /** How long, how deep and how many: what an invented value is allowed to look like. */
    private final GenerationSettings settings;
    private final Map<Spelling, Optional<MatchingStrings>> spellings = new LinkedHashMap<>();

    /**
     * What each place's name and description were found to imply, worked out once per place. The
     * same place is asked about thousands of times in a run, and its description does not change.
     */
    private final Map<Clues, Optional<ImpliedFormats.Implied>> implied = new LinkedHashMap<>();

    private record Clues(String name, String description) {
    }

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
        this(model, random, inside, GenerationSettings.defaults());
    }

    /**
     * The same, with the shapes an invented value is allowed to take.
     *
     * @param model the API, needed to look up shapes the specification refers to by name
     * @param random where the values come from
     * @param inside who to ask about the contents of a list or an object before inventing them
     * @param settings how long an invented word is, how many items a list holds, how deep nesting
     *     goes, and how many times any of it is attempted again
     */
    public RandomValueProvider(ApiModel model, RandomGenerator random, ValueProvider inside,
            GenerationSettings settings) {
        this.model = Objects.requireNonNull(model, "model");
        this.random = Objects.requireNonNull(random, "random");
        this.inside = Objects.requireNonNull(inside, "inside");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public Optional<GeneratedValue> offer(ValueRequest request) {
        Objects.requireNonNull(request, "request");
        // Invented again when what came out could not be put in the request - a word of no letters
        // where the path needs one, a line break on its way into a header. Most shapes that can
        // produce such a value can also produce a usable one, so trying again costs nothing and
        // usually works; a shape that can produce nothing else - a string allowed no characters, a
        // list allowed no items - runs out of attempts and says it has no value to offer. Saying
        // so is the point: the operation is then reported as one that cannot be tested, instead of
        // being counted among those being tested while every one of its requests is thrown away.
        for (int attempt = 0; attempt < settings.sendableAttempts(); attempt++) {
            Optional<JsonValue> invented = value(request, request.schema(), 0);
            if (invented.isEmpty()) {
                return Optional.empty();
            }
            if (RequestBuilder.canBeSentFrom(invented.get(), request.location())) {
                return Optional.of(GeneratedValue.generatedBy(invented.get(), name()));
            }
        }
        return Optional.empty();
    }

    @Override
    public String name() {
        return "random";
    }

    private Optional<JsonValue> value(ValueRequest request, CanonicalSchema schema, int depth) {
        if (depth > settings.hardNestingDepth()) {
            return Optional.empty();
        }
        // Nobody is asked about a pointer to a shape, only about the shape it points at. What is on
        // the far side is followed first, a few lines down, and the question is put again there.
        // Asking here would hand every answerer a shape with nothing in it: a closed list of
        // allowed values behind a pointer would look like no list at all, and a source that should
        // have deferred to it would answer instead.
        if (depth > 0 && !(schema instanceof SchemaReference)) {
            Optional<GeneratedValue> known = inside.offer(request.about(schema));
            if (known.isPresent()) {
                return Optional.of(known.get().value());
            }
        }
        // A value that may be absent is sometimes sent as nothing, because an API treats "absent"
        // and "empty" differently and both deserve trying - except in the path, where nothing at all
        // would silently address a different resource.
        if (schema.metadata().nullable() && request.location() != ParameterLocation.PATH
                && settings.nullInOneIn() > 0
                && random.nextInt(settings.nullInOneIn()) == 0) {
            return Optional.of(JsonValue.NULL);
        }
        return switch (schema) {
            case StringSchema string -> text(Optional.of(request), string);
            case NumberSchema number -> number(number);
            case BooleanSchema ignored -> Optional.of(JsonValue.of(random.nextBoolean()));
            case NullSchema ignored -> Optional.of(JsonValue.NULL);
            case ArraySchema array -> list(request, array, depth);
            case ObjectSchema object -> object(request, object, depth);
            case AnySchema ignored -> Optional.of(anything());
            case ChoiceSchema choice -> oneOfTheShapes(request, choice, depth);
            case SchemaReference reference -> named(request, reference, depth);
            // Nothing can be sent that satisfies a schema saying no value is acceptable, and a shape
            // nobody could read is not one to guess at: both decline, and the caller reports it.
            case NothingSchema ignored -> Optional.empty();
            case UnsupportedSchema ignored -> Optional.empty();
        };
    }

    /**
     * A string the specification would accept: of a length it allows, of the kind it names, and
     * spelled the way it demands.
     *
     * <p>Three things can be said about a piece of text and they are tried in that order. The kind
     * of value comes first - a date, an e-mail address, an identifier - because it describes the
     * whole value and not merely its characters. A spelling rule comes next, and it is also what
     * keeps or rejects the kind: where a specification states both, the value has to satisfy both.
     * Where it names no kind, the kind the value's name or description implies - an e-mail address
     * for {@code billing_email}, a date in the form a description writes out - is tried some of the
     * time, and the spelling rule holds it to the same account. When none of these is stated, or
     * none could be honoured, what is left is an ordinary word, which is what every string used to
     * be.
     *
     * <p>Not being able to honour a rule means two different things and they end differently. A rule
     * nobody could read is treated as though it had not been written, because refusing to test a
     * parameter over a notation nobody here understands helps nobody. A rule that was read, but that
     * nothing satisfying it is also short enough for the length the specification demands, means
     * there is genuinely no value to send - so nothing is offered, and the operation is reported
     * rather than being counted as tested while every one of its requests is thrown away.
     *
     * <p>All the arithmetic is done in {@code long}s: a specification saying a string may be up to
     * {@code 2147483647} characters is ordinary - that is what a Java {@code @Size} annotation
     * produces - and computing the range of lengths in {@code int}s would overflow and end the run.
     */
    private Optional<JsonValue> text(Optional<ValueRequest> asked, StringSchema schema) {
        long stated = schema.maxLength().map(Integer::longValue).orElse(Long.MAX_VALUE);
        // One character unless the specification says otherwise - an empty value is legal almost
        // everywhere and useful almost nowhere - but never more than it allows: a string that must
        // be empty is empty.
        long lowest = Math.min(schema.minLength().orElse(1), stated);
        if (lowest > settings.longestString()) {
            return Optional.empty();
        }

        // Read before the kind is asked for, because the rule is also what vets the kind's answer,
        // and because reading it is done once per run rather than once per value.
        // The lengths the shape permits, and the lengths worth having. "At least one character" is
        // the second of those and not the first: a rule that can only build the empty string - an
        // empty rule, or one asking only where the value ends, both of which refuse nothing - would
        // otherwise leave the parameter with no value at all.
        Optional<MatchingStrings> spelling = schema.pattern().flatMap(rule -> spellingsFor(rule,
                new MatchLength(schema.minLength().orElse(0), stated),
                new MatchLength(lowest, longestWorthSending(lowest, stated))));

        Optional<String> ofTheKindNamed = schema.format()
                .flatMap(kind -> FormattedStrings.of(kind, random))
                .filter(value -> value.length() >= lowest && value.length() <= stated)
                // A rule nobody could read holds nothing against the value, which is the same
                // answer the rest of this method gives such a rule.
                .filter(value -> spelling.map(rule -> rule.allows(value)).orElse(true));
        if (ofTheKindNamed.isPresent()) {
            return ofTheKindNamed.map(JsonValue::of);
        }

        Optional<String> ofTheKindImplied = asked.flatMap(request ->
                ofTheKindImplied(request, schema, lowest, stated, spelling));
        if (ofTheKindImplied.isPresent()) {
            return ofTheKindImplied.map(JsonValue::of);
        }

        if (spelling.isPresent()) {
            return spelling.get().next(random).map(JsonValue::of);
        }
        return Optional.of(JsonValue.of(word(lowest, stated)));
    }

    /**
     * A value of the kind the place's name or description implies, where the document declares no
     * kind of its own - and only some of the time.
     *
     * <p>Only where the document leaves the value open: no kind named, no closed list, no sample of
     * its own, any of which says more than a name could. A spelling rule and the lengths still hold,
     * and a value they refuse is not sent: what is sent instead is what would have been sent
     * anyway. And only as often as the settings say, because the API's answer to an ordinary word
     * in an e-mail address's place is worth having too.
     *
     * <p>Nothing is drawn where nothing is implied, so that a place no rule recognises is invented
     * exactly as it was before any of this existed.
     */
    private Optional<String> ofTheKindImplied(ValueRequest request, StringSchema schema,
            long lowest, long stated, Optional<MatchingStrings> spelling) {
        if (!settings.impliedFormats() || schema.format().isPresent()
                || !schema.metadata().enumeration().isEmpty()
                || !schema.metadata().examples().isEmpty() || !request.examples().isEmpty()) {
            return Optional.empty();
        }
        Clues clues = new Clues(request.name(), descriptionOf(request, schema));
        Optional<ImpliedFormats.Implied> kind = implied.computeIfAbsent(clues,
                ignored -> ImpliedFormats.of(clues.name(), clues.description()));
        if (kind.isEmpty() || random.nextDouble() >= settings.impliedFormatChance()) {
            return Optional.empty();
        }
        return Optional.of(kind.get().valueFor(random))
                .filter(value -> value.length() >= lowest && value.length() <= stated)
                .filter(value -> spelling.map(rule -> rule.allows(value)).orElse(true));
    }

    /**
     * What the document says this value is: the description beside its shape, or, for a parameter
     * whose shape says nothing, the description of the parameter itself.
     */
    private String descriptionOf(ValueRequest request, StringSchema schema) {
        Optional<String> beside = schema.metadata().description();
        if (beside.isPresent() || request.location() == ParameterLocation.BODY) {
            return beside.orElse("");
        }
        return model.operation(request.operation())
                .flatMap(operation -> operation.parameters().stream()
                        .filter(parameter -> parameter.name().equals(request.name())
                                && parameter.location() == request.location())
                        .findFirst())
                .flatMap(Parameter::description)
                .orElse("");
    }

    /**
     * The strings one spelling rule allows, read once and kept.
     *
     * <p>Reading a rule is parsing a small language, and a run asks the same question thousands of
     * times: one API in the corpus states a rule for fifteen of the values in every request it
     * takes. Measured on that one, reading each rule afresh for every value put the cost of building
     * a whole request up from eight microseconds to eleven; reading each once brings it back to
     * eight, which is where it was before any of this.
     *
     * <p>Kept here rather than anywhere shared, because everything in this class belongs to one run.
     * Two runs in the same program keep their own, which is what lets them be two runs. It cannot
     * grow without bound either: there are only as many entries as the document has rules.
     */
    private Optional<MatchingStrings> spellingsFor(String rule, MatchLength allowed,
            MatchLength preferred) {
        return spellings.computeIfAbsent(new Spelling(rule, allowed, preferred), asked ->
                MatchingStrings.reading(asked.rule(), asked.allowed(), asked.preferred(), random));
    }

    /**
     * A spelling rule together with the lengths it has to fit inside and the ones worth having.
     *
     * <p>All of them, because the lengths change the answer: the same rule asked for a string of
     * forty characters and for one of three is two different questions.
     */
    private record Spelling(String rule, MatchLength allowed, MatchLength preferred) {
    }

    /**
     * The longest value worth building: what the shape allows, or the usual limit, whichever is
     * smaller - and always at least what the shape insists on.
     */
    private long longestWorthSending(long lowest, long stated) {
        return Math.min(stated, Math.max(lowest, settings.usualLongestString()));
    }

    /** A word of no particular kind, of a length the specification allows. */
    private String word(long lowest, long stated) {
        long highest = longestWorthSending(lowest, stated);
        long length = lowest == highest ? lowest
                : lowest + random.nextLong(highest - lowest + 1);

        StringBuilder value = new StringBuilder((int) length);
        for (long i = 0; i < length; i++) {
            value.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        }
        return value.toString();
    }

    /**
     * A number inside every bound the schema states, and on its step if it has one.
     *
     * <p>The numbers a schema allows lie on a ladder: whole numbers for a whole number, multiples
     * of the step when there is one, and otherwise numbers with as many decimal places as the
     * settings ask for, or as the bounds themselves are written with - one more, when two limits
     * the value must stay strictly inside leave nothing between them at that many. The lowest rung
     * inside the bounds and the highest are found first, and then one of the rungs from the one to
     * the other is chosen, both ends included. A limit is where an API is likeliest to get its own
     * rule wrong, so a value at either limit has to be one a run can send.
     *
     * <p>A schema may state both an inclusive and an exclusive bound on the same side, and the
     * tighter of the two is the one that has to be obeyed - a value no smaller than 1 but strictly
     * greater than 10 is a value of at least 11, not of at least 1.
     *
     * <p>A side the schema says nothing about is filled in from the settings: numbers start at
     * {@code lowestNumber} and run {@code roomAboveIt} above the bottom - or, when the only limit
     * stated is a top below where numbers usually start, they run that far below it instead.
     *
     * <p>Empty when the bounds and the step leave nothing to choose - a whole number strictly between
     * 1 and 2, or a multiple of 10 between 3 and 7. The specification permits that combination and no
     * value satisfies it, so saying so is the only honest answer.
     */
    private Optional<JsonValue> number(NumberSchema schema) {
        BigDecimal rung = rungOf(schema);
        Optional<Ends> ends = ends(schema, rung);
        boolean decimalsWithoutAStep =
                schema.kind() != NumberKind.INTEGER && schema.multipleOf().isEmpty();
        if (ends.isEmpty() && decimalsWithoutAStep) {
            // Two limits that must both be passed, say 0 and 0.01, leave no hundredth between
            // them but do leave a thousandth. One more place is enough for any two that differ.
            rung = rung.movePointLeft(1);
            ends = ends(schema, rung);
        }
        if (ends.isEmpty()) {
            return Optional.empty();
        }

        // Both ends are rungs, so the distance between them is a whole number of rungs. One draw
        // picks one of them, the top one included, each as likely as any other.
        BigDecimal lowest = ends.get().lowest();
        BigDecimal rungs = ends.get().highest().subtract(lowest)
                .divide(rung, 0, RoundingMode.UNNECESSARY).add(BigDecimal.ONE);
        BigDecimal chosen = rungs.multiply(BigDecimal.valueOf(random.nextDouble()))
                .setScale(0, RoundingMode.FLOOR);
        return Optional.of(JsonValue.of(lowest.add(chosen.multiply(rung))));
    }

    /** The lowest and the highest rung a number may take. */
    private record Ends(BigDecimal lowest, BigDecimal highest) {
    }

    /** The lowest and the highest rung inside every limit, or empty when there is none. */
    private Optional<Ends> ends(NumberSchema schema, BigDecimal rung) {
        Optional<BigDecimal> bottom = tighter(
                schema.minimum().map(bound -> atOrAbove(bound, rung)),
                schema.exclusiveMinimum().map(bound -> atOrBelow(bound, rung).add(rung)), true);
        Optional<BigDecimal> top = tighter(
                schema.maximum().map(bound -> atOrBelow(bound, rung)),
                schema.exclusiveMaximum().map(bound -> atOrAbove(bound, rung).subtract(rung)),
                false);
        BigDecimal usualStart = settings.lowestNumber();
        boolean onlyATopBelowTheUsualStart =
                bottom.isEmpty() && top.isPresent() && top.get().compareTo(usualStart) < 0;
        BigDecimal lowest = bottom.orElseGet(() -> atOrAbove(onlyATopBelowTheUsualStart
                ? top.get().subtract(settings.roomAboveIt()) : usualStart, rung));
        BigDecimal highest = top.orElseGet(() ->
                atOrBelow(lowest.max(usualStart).add(settings.roomAboveIt()), rung));
        return lowest.compareTo(highest) > 0 ? Optional.empty()
                : Optional.of(new Ends(lowest, highest));
    }

    /**
     * How far apart two neighbouring numbers the schema allows are.
     *
     * <p>For a whole number with a step that is not whole - a multiple of 1.5 - only the multiples
     * that are themselves whole will do, so the distance is the smallest of those: 3.
     */
    private BigDecimal rungOf(NumberSchema schema) {
        boolean whole = schema.kind() == NumberKind.INTEGER;
        if (schema.multipleOf().isPresent()) {
            BigDecimal step = schema.multipleOf().get().stripTrailingZeros();
            if (!whole || step.scale() <= 0) {
                return step;
            }
            BigInteger digits = step.unscaledValue();
            return new BigDecimal(digits.divide(digits.gcd(BigInteger.TEN.pow(step.scale()))));
        }
        if (whole) {
            return BigDecimal.ONE;
        }
        int places = Stream.of(schema.minimum(), schema.exclusiveMinimum(), schema.maximum(),
                        schema.exclusiveMaximum())
                .flatMap(Optional::stream)
                .mapToInt(BigDecimal::scale)
                .reduce(settings.decimalPlaces(), Math::max);
        return BigDecimal.ONE.movePointLeft(places);
    }

    /** The lowest rung at or above a value. */
    private static BigDecimal atOrAbove(BigDecimal value, BigDecimal rung) {
        return value.divide(rung, 0, RoundingMode.CEILING).multiply(rung);
    }

    /** The highest rung at or below a value. */
    private static BigDecimal atOrBelow(BigDecimal value, BigDecimal rung) {
        return value.divide(rung, 0, RoundingMode.FLOOR).multiply(rung);
    }

    /** The stricter of two limits on the same side, when the schema states both. */
    private static Optional<BigDecimal> tighter(Optional<BigDecimal> inclusive,
            Optional<BigDecimal> fromExclusive, boolean lowerSide) {
        if (inclusive.isEmpty() || fromExclusive.isEmpty()) {
            return inclusive.or(() -> fromExclusive);
        }
        return Optional.of(lowerSide
                ? inclusive.get().max(fromExclusive.get())
                : inclusive.get().min(fromExclusive.get()));
    }

    private Optional<JsonValue> list(ValueRequest request, ArraySchema schema, int depth) {
        int lowest = schema.minItems().orElse(depth >= settings.optionalNestingDepth() ? 0 : 1);
        if (lowest > settings.mostItems()) {
            return Optional.empty();
        }
        int stated = schema.maxItems().orElse(Integer.MAX_VALUE);
        int highest = Math.min(stated, Math.max(lowest, settings.usualMostItems()));
        int wanted = depth >= settings.optionalNestingDepth() || lowest == highest
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
        int attempts = schema.uniqueItems() ? settings.uniqueAttempts() : 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            Optional<JsonValue> element =
                    value(request.aboutAPieceOf(schema.items()), schema.items(), depth + 1);
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
            // A property the document says is only ever returned is not ours to send. Skipped even
            // where it is also marked as required, because "required" in a shape an API both
            // returns and accepts is a statement about the replies: the API promises to send it,
            // not to be sent it. A sample the author wrote out in full is a different matter and is
            // still sent exactly as written - the author showed a whole body that works.
            if (property.getValue().metadata().access() == SchemaMetadata.Access.READ_ONLY) {
                continue;
            }
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
            boolean wanted = depth < settings.optionalNestingDepth()
                    && random.nextDouble() < settings.optionalPropertyChance();
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
            // A word with no place to imply anything from.
            default -> text(Optional.empty(), StringSchema.of()).orElse(JsonValue.of("value"));
        };
    }

    /**
     * A value of one of the shapes the specification allows.
     *
     * <p>Which one is chosen at random rather than taken in order, because taking the first would
     * make what gets tested depend on the order the document happened to list the shapes in, and the
     * later ones would never be sent at all.
     *
     * <p>The rest are tried when the chosen one yields nothing. A value satisfies a choice if it
     * satisfies any one of the shapes, so giving up on the first that declines would abandon values
     * the document plainly allows - and for a required parameter, abandoning a value costs the whole
     * operation.
     */
    private Optional<JsonValue> oneOfTheShapes(ValueRequest request, ChoiceSchema choice,
            int depth) {
        List<CanonicalSchema> shapes = new ArrayList<>(choice.alternatives());
        // Nothing at all in the path would silently address a different resource, which is the same
        // rule the value of any other shape obeys - and here it is a whole shape to avoid, not a
        // chance to decline.
        if (request.location() == ParameterLocation.PATH) {
            shapes.removeIf(shape -> shape instanceof NullSchema);
        }
        if (shapes.isEmpty()) {
            return Optional.empty();
        }
        int chosen = random.nextInt(shapes.size());
        for (int tried = 0; tried < shapes.size(); tried++) {
            Optional<JsonValue> value =
                    value(request, shapes.get((chosen + tried) % shapes.size()), depth + 1);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private Optional<JsonValue> named(ValueRequest request, SchemaReference reference, int depth) {
        Optional<CanonicalSchema> shape = model.resolve(reference);
        if (shape.isEmpty()) {
            return Optional.empty();
        }
        ValueRequest about = request.aboutTheShapeNamed(reference.name(), shape.get());
        if (depth >= settings.optionalNestingDepth()) {
            // Deep enough that nothing more is invented here - but somebody may still know this
            // value, and being too deep to build is not a reason to stop asking. Without this, a
            // shape the document named and one written out where it is used would behave
            // differently at the same depth, which is a difference nobody wrote down on purpose.
            return inside.offer(about).map(GeneratedValue::value);
        }
        return value(about, shape.get(), depth + 1);
    }
}

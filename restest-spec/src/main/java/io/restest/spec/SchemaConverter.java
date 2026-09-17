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

import io.restest.core.json.JsonException;
import io.restest.core.json.JsonText;
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
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns one {@code swagger-parser} schema into the ten-shape {@link CanonicalSchema} the rest of
 * RESTest works with.
 *
 * <p>A value the document describes as being several shapes at once ({@code allOf}) is combined into
 * one shape here, by {@link AllOfMerger}, so that nothing downstream has to know the document
 * described it in halves. A value declared as a <em>choice</em> between shapes ({@code oneOf},
 * {@code anyOf}), one declared by what it is not ({@code not}), one whose shape is picked by a
 * discriminator property, and a tuple-form array ({@code prefixItems}, where every element has its
 * own declared shape) are all still read as {@link UnsupportedSchema}: a choice cannot be collapsed
 * into one shape the way a combination can, and a tuple is not the "every element looks the same"
 * shape {@link ArraySchema} represents. None of them crashes the parser; each says why in the report.
 */
final class SchemaConverter {

    /**
     * A moment in time written the way the standard for dates on the web requires it: always with
     * seconds, which the shorthand {@code OffsetDateTime} prints leaves out when they are zero.
     */
    private static final DateTimeFormatter WEB_DATE_TIME = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral('T')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .appendOffsetId()
            .toFormatter(Locale.ROOT);

    private SchemaConverter() {
    }

    /**
     * The canonical shape for the given schema, or {@link AnySchema} for {@code null}.
     *
     * @param schema the shape as the parser read it
     * @param components the document's reusable pieces, whose named schemas a combination may refer
     *     to; {@code null} when there are none, in which case a combination that refers to one by
     *     name has nothing to resolve it against and says so
     */
    static CanonicalSchema convert(Schema<?> schema, Components components) {
        return convert(schema, components, new Combination());
    }

    /**
     * @param beingCombined how far this combination has been followed already, so that a shape built
     *     from itself, one nested deeper than anything real, and a document that would take longer to
     *     work through than to test are each reported rather than followed
     */
    private static CanonicalSchema convert(Schema<?> schema, Components components,
            Combination beingCombined) {
        if (schema == null) {
            return AnySchema.of();
        }
        if (schema.get$ref() != null) {
            return new SchemaReference(metadataOf(schema), refName(schema.get$ref()));
        }
        SchemaMetadata metadata = metadataOf(schema);
        if (schema.getEnum() != null && schema.getEnum().size() != metadata.enumeration().size()) {
            // Some value this is allowed to take could not be written down exactly. A default in
            // that state is simply dropped, which is honest because the schema then says it has no
            // default. An enumeration cannot be dropped the same way: a shorter list is not "no
            // list", it is a different and narrower claim about what the API accepts, made by us
            // and not by the document. Saying we could not read it is the only true answer.
            return new UnsupportedSchema(metadata, "one of the values this is allowed to take could "
                    + "not be read, so the list of them would be shorter than the document's");
        }
        Optional<String> unreadableComposition = whatMakesTheCompositionUnreadable(schema);
        if (unreadableComposition.isPresent()) {
            return new UnsupportedSchema(metadata, unreadableComposition.get());
        }
        if (isNonEmpty(schema.getAllOf())) {
            return combine(schema, components, beingCombined);
        }
        return convertPlain(schema, metadata, components, beingCombined);
    }

    /** The canonical shape for a schema that is not built out of others. */
    private static CanonicalSchema convertPlain(Schema<?> schema, SchemaMetadata metadata,
            Components components, Combination beingCombined) {
        if (schema.getPrefixItems() != null && !schema.getPrefixItems().isEmpty()) {
            return new UnsupportedSchema(metadata, "a tuple-form array, where each element has its "
                    + "own declared shape (prefixItems), is not supported yet");
        }
        Set<String> nonNullTypes = nonNullTypes(schema);
        if (nonNullTypes.size() > 1) {
            return new UnsupportedSchema(metadata, "a value declared as one of several plain JSON "
                    + "Schema types (" + String.join(", ", nonNullTypes) + ") is not supported yet");
        }
        String type = effectiveType(schema);
        return switch (type) {
            case "object" -> convertObject(schema, metadata, components, beingCombined);
            case "array" -> convertArray(schema, metadata, components, beingCombined);
            case "string" -> convertString(schema, metadata);
            case "integer" -> convertNumber(schema, metadata, NumberKind.INTEGER);
            case "number" -> convertNumber(schema, metadata, NumberKind.NUMBER);
            case "boolean" -> new BooleanSchema(metadata);
            case "null" -> new NullSchema(metadata);
            case null -> new AnySchema(metadata);
            default -> new UnsupportedSchema(metadata, "an unrecognised schema type: '" + type + "'");
        };
    }

    /**
     * Whether any part of the given schema is, or contains, {@link UnsupportedSchema} - resolving a
     * {@link SchemaReference} against the given named schemas one level at a time, so a body that is
     * only a {@code $ref} to a composed schema is not reported as perfectly fine just because the
     * reference itself is a plain, resolvable name. A name already visited on this walk is treated as
     * not (newly) unsupported rather than resolved again, so a schema that refers to itself, directly
     * or through others, cannot recurse for ever. A name the given schemas do not declare at all is
     * unsupported: there is nothing to read, whether the document forgot to define it or keeps it in
     * another file.
     */
    static boolean hasUnsupportedConstruct(CanonicalSchema schema, Map<String, CanonicalSchema> schemas) {
        return hasUnsupportedConstruct(schema, schemas, new HashSet<>());
    }

    private static boolean hasUnsupportedConstruct(CanonicalSchema schema,
            Map<String, CanonicalSchema> schemas, Set<String> visited) {
        return switch (schema) {
            case UnsupportedSchema u -> true;
            case ObjectSchema o -> o.properties().values().stream()
                    .anyMatch(v -> hasUnsupportedConstruct(v, schemas, visited))
                    || o.additionalProperties().map(v -> hasUnsupportedConstruct(v, schemas, visited))
                            .orElse(false);
            case ArraySchema a -> hasUnsupportedConstruct(a.items(), schemas, visited);
            case SchemaReference r -> {
                if (!visited.add(r.name())) {
                    yield false;
                }
                CanonicalSchema target = schemas.get(r.name());
                // A name nothing declares counts as not understood, which is the honest answer and
                // was not the answer before. A document may point at a shape it never defines, or
                // at one in a second file this tool deliberately does not open; either way there is
                // nothing here to generate from and nothing to hold a reply to. Answering "fine"
                // because the reference itself was well-formed let those operations be tested as
                // though they were fully understood, and said nothing to anybody about the shape
                // that went missing.
                yield target == null || hasUnsupportedConstruct(target, schemas, visited);
            }
            case AnySchema ignored -> false;
            case BooleanSchema ignored -> false;
            case NothingSchema ignored -> false;
            case NullSchema ignored -> false;
            case NumberSchema ignored -> false;
            case StringSchema ignored -> false;
        };
    }

    /**
     * Why this schema's way of being built out of others cannot be read, or nothing when it can.
     *
     * <p>Only one of these is a combination: {@code allOf} says a value has to be every one of
     * several shapes at once, which is a single shape once worked out. The rest describe a value by
     * offering a <em>choice</em> of shapes, or by saying what it must not be, and neither collapses
     * into one shape - so the honest answer is still that we cannot read it, and which of them
     * defeated us is worth naming in the report.
     */
    private static Optional<String> whatMakesTheCompositionUnreadable(Schema<?> schema) {
        if (isNonEmpty(schema.getOneOf())) {
            return Optional.of("a value declared as exactly one of several alternative shapes "
                    + "(oneOf) is not supported yet");
        }
        if (isNonEmpty(schema.getAnyOf())) {
            return Optional.of("a value declared as at least one of several alternative shapes "
                    + "(anyOf) is not supported yet");
        }
        if (schema.getNot() != null) {
            return Optional.of("a value declared by the shape it must not have (not) is not "
                    + "supported yet");
        }
        if (schema.getDiscriminator() != null) {
            return Optional.of("a value whose shape is chosen by a discriminator property is not "
                    + "supported yet");
        }
        return Optional.empty();
    }

    /**
     * The one shape a value must have to be every one of this schema's halves at once.
     *
     * <p>What the schema says in its own right counts as one more half: {@code allOf} is allowed to
     * sit beside a type, properties or an enumeration, and those constrain the value exactly as the
     * listed halves do. Taking it first is also what gives the schema's own description and default
     * priority over any it inherits.
     */
    private static CanonicalSchema combine(Schema<?> schema, Components components,
            Combination beingCombined) {
        List<CanonicalSchema> halves = new ArrayList<>();
        halves.add(convertPlain(schema, metadataOf(schema), components, beingCombined));
        for (Schema<?> half : schema.getAllOf()) {
            halves.add(combineBranch(half, components, beingCombined));
        }
        return AllOfMerger.fold(halves);
    }

    /**
     * One half of a combination. A half that names a shape declared elsewhere has to be looked up
     * and written out in place, because a combination cannot be worked out without knowing what it
     * is combining - which is the one situation where a name is not kept.
     */
    private static CanonicalSchema combineBranch(Schema<?> half, Components components,
            Combination beingCombined) {
        if (half == null || half.get$ref() == null) {
            return convert(half, components, beingCombined);
        }
        String reference = half.get$ref();
        if (!reference.startsWith("#")) {
            // Writing a half out in place means finding it by name, and a name in another document
            // is not this document's name: a local shape that happened to share it would be put in
            // silently. Left unread rather than read as the wrong shape.
            return UnsupportedSchema.of("this is built from a shape in another document ('"
                    + reference + "'), which RESTest does not open");
        }
        if (beingCombined.open.size() >= Combination.DEEPEST) {
            return UnsupportedSchema.of("this is built from shapes nested more than "
                    + Combination.DEEPEST + " deep, which is further than RESTest follows");
        }
        if (beingCombined.remaining-- <= 0) {
            return UnsupportedSchema.of("this document builds shapes out of one another in more "
                    + "combinations than RESTest works through; the rest are left unread");
        }

        String name = refName(reference);
        Schema<?> named = namedSchema(name, components);
        // A half may name a shape that is itself only another name, which code-generated documents
        // produce routinely. Following those costs nothing and reaches the shape that was meant.
        Set<String> alreadyFollowed = new LinkedHashSet<>();
        while (named != null && named.get$ref() != null && named.get$ref().startsWith("#")
                && alreadyFollowed.add(name)) {
            name = refName(named.get$ref());
            named = namedSchema(name, components);
        }
        if (named == null) {
            return UnsupportedSchema.of("this is built from a shape named '" + name + "' that the "
                    + "document does not declare");
        }
        if (named.get$ref() != null) {
            return UnsupportedSchema.of("the shape '" + name + "' is named through a circle of "
                    + "names that never reaches a shape");
        }
        if (!beingCombined.open.add(name)) {
            return UnsupportedSchema.of("the shape '" + name + "' is built from itself, directly or "
                    + "through others, so what a value must look like cannot be worked out");
        }
        try {
            return convert(named, components, beingCombined);
        } finally {
            beingCombined.open.remove(name);
        }
    }

    private static Schema<?> namedSchema(String name, Components components) {
        return components == null || components.getSchemas() == null
                ? null : components.getSchemas().get(name);
    }

    /**
     * How far one combination has been followed, and how much further it may go.
     *
     * <p>Two limits rather than one, because a combination can defeat a reader in two ways. It can
     * nest so deeply that following it exhausts the stack, which would end the whole run rather than
     * cost one operation. And it can branch, so the same shapes are reached along many paths and the
     * work doubles at every level - a document of a few kilobytes able to spend an entire testing
     * budget before the first request is sent. Both answer the same way: say what was not read, and
     * carry on.
     */
    private static final class Combination {

        /** Deeper than any document written by a person, and shallow enough to be safe. */
        private static final int DEEPEST = 64;

        /** The names opened on the way here, so a shape built from itself is caught. */
        private final Set<String> open = new LinkedHashSet<>();

        /** How many more named halves may be looked up before the rest are left unread. */
        private int remaining = 2_000;
    }

    private static boolean isNonEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }

    /**
     * The type declared for this schema: from {@code type} (OAS 3.0) or, when that is absent, from
     * the single non-{@code "null"} member of {@code types} (OAS 3.1's type array). {@code null}
     * when neither declares one - which is a schema that accepts any value, {@link AnySchema}. Not
     * meaningful when {@link #nonNullTypes} has more than one member; callers check that first.
     */
    private static String effectiveType(Schema<?> schema) {
        if (schema.getType() != null) {
            return schema.getType();
        }
        return nonNullTypes(schema).stream().findFirst()
                .orElseGet(() -> describesAnObject(schema) ? "object" : null);
    }

    /**
     * Whether a schema that names no type is nevertheless describing an object, because it carries
     * something only an object could obey: named properties, a list of which of those are required,
     * a rule for the ones not named, or a limit on how many there may be.
     *
     * <p>Leaving the type out is extremely common in real documents, and reading such a schema as
     * "any value at all" threw the properties away: what came back was a schema that says nothing,
     * so the tool would offer a number or a word where the document had carefully described an
     * object with named fields. The parser already fills in the type this way for the other half of
     * the rule - a schema with {@code items} and no type is read as an array - so the two now agree.
     *
     * <p>Strictly, JSON Schema says these keywords only constrain values that happen to be objects
     * and let everything else past, so a document could mean "anything, but if an object, this
     * shape". No document written by a person means that, and reading it that way costs the shape.
     */
    private static boolean describesAnObject(Schema<?> schema) {
        return (schema.getProperties() != null && !schema.getProperties().isEmpty())
                || isNonEmpty(schema.getRequired())
                || schema.getAdditionalProperties() != null
                || schema.getMinProperties() != null
                || schema.getMaxProperties() != null;
    }

    /**
     * The non-{@code "null"} members of OAS 3.1's {@code types} array. A JSON Schema type array can
     * name more than one real type - {@code [string, integer]}, a plain union, distinct from
     * nullability - which none of the ten canonical shapes can represent; more than one member here
     * is the signal for that, checked before {@link #effectiveType} ever has to pick just one.
     */
    private static Set<String> nonNullTypes(Schema<?> schema) {
        if (schema.getTypes() == null) {
            return Set.of();
        }
        return schema.getTypes().stream().filter(candidate -> !"null".equals(candidate))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static ObjectSchema convertObject(Schema<?> schema, SchemaMetadata metadata,
            Components components, Combination beingCombined) {
        Map<String, CanonicalSchema> properties = new LinkedHashMap<>();
        if (schema.getProperties() != null) {
            schema.getProperties().forEach((name, value) ->
                    properties.put(name, convert(value, components, beingCombined)));
        }
        Set<String> required = schema.getRequired() == null
                ? Set.of() : new LinkedHashSet<>(schema.getRequired());
        Optional<CanonicalSchema> additionalProperties = convertAdditionalProperties(
                schema.getAdditionalProperties(), components, beingCombined);
        Optional<Integer> minProperties = Optional.ofNullable(schema.getMinProperties());
        Optional<Integer> maxProperties = Optional.ofNullable(schema.getMaxProperties());
        return new ObjectSchema(metadata, properties, required, additionalProperties, minProperties,
                maxProperties);
    }

    private static Optional<CanonicalSchema> convertAdditionalProperties(Object additionalProperties,
            Components components, Combination beingCombined) {
        if (additionalProperties == null) {
            return Optional.empty();
        }
        if (additionalProperties instanceof Boolean allowed) {
            return allowed ? Optional.empty() : Optional.of(NothingSchema.of());
        }
        if (additionalProperties instanceof Schema<?> nested) {
            return Optional.of(convert(nested, components, beingCombined));
        }
        return Optional.empty();
    }

    private static ArraySchema convertArray(Schema<?> schema, SchemaMetadata metadata,
            Components components, Combination beingCombined) {
        CanonicalSchema items = convert(schema.getItems(), components, beingCombined);
        Optional<Integer> minItems = Optional.ofNullable(schema.getMinItems());
        Optional<Integer> maxItems = Optional.ofNullable(schema.getMaxItems());
        boolean uniqueItems = Boolean.TRUE.equals(schema.getUniqueItems());
        return new ArraySchema(metadata, items, minItems, maxItems, uniqueItems);
    }

    private static StringSchema convertString(Schema<?> schema, SchemaMetadata metadata) {
        Optional<Integer> minLength = Optional.ofNullable(schema.getMinLength());
        Optional<Integer> maxLength = Optional.ofNullable(schema.getMaxLength());
        Optional<String> pattern = Optional.ofNullable(schema.getPattern());
        Optional<String> format = Optional.ofNullable(schema.getFormat());
        return new StringSchema(metadata, minLength, maxLength, pattern, format);
    }

    private static NumberSchema convertNumber(Schema<?> schema, SchemaMetadata metadata,
            NumberKind kind) {
        BigDecimal minimum = schema.getMinimum();
        BigDecimal exclusiveMinimum = schema.getExclusiveMinimumValue();
        if (exclusiveMinimum == null && Boolean.TRUE.equals(schema.getExclusiveMinimum())) {
            // OAS 3.0: exclusiveMinimum is a flag next to `minimum`, not a bound of its own.
            exclusiveMinimum = minimum;
            minimum = null;
        }
        BigDecimal maximum = schema.getMaximum();
        BigDecimal exclusiveMaximum = schema.getExclusiveMaximumValue();
        if (exclusiveMaximum == null && Boolean.TRUE.equals(schema.getExclusiveMaximum())) {
            exclusiveMaximum = maximum;
            maximum = null;
        }
        return new NumberSchema(metadata, kind, Optional.ofNullable(minimum),
                Optional.ofNullable(exclusiveMinimum), Optional.ofNullable(maximum),
                Optional.ofNullable(exclusiveMaximum), Optional.ofNullable(schema.getMultipleOf()),
                Optional.ofNullable(schema.getFormat()));
    }

    private static SchemaMetadata metadataOf(Schema<?> schema) {
        Optional<String> description = Optional.ofNullable(schema.getDescription());
        boolean nullable = Boolean.TRUE.equals(schema.getNullable())
                || (schema.getTypes() != null && schema.getTypes().contains("null"));
        List<JsonValue> enumeration = schema.getEnum() == null
                ? List.of()
                : schema.getEnum().stream()
                        .map(SchemaConverter::toJsonValue)
                        .flatMap(Optional::stream)
                        .toList();
        Optional<JsonValue> defaultValue = schema.getDefault() == null
                ? Optional.empty() : toJsonValue(schema.getDefault());
        boolean deprecated = Boolean.TRUE.equals(schema.getDeprecated());
        SchemaMetadata.Access access = Boolean.TRUE.equals(schema.getReadOnly())
                ? SchemaMetadata.Access.READ_ONLY
                : Boolean.TRUE.equals(schema.getWriteOnly())
                        ? SchemaMetadata.Access.WRITE_ONLY
                        : SchemaMetadata.Access.READ_WRITE;
        return new SchemaMetadata(description, nullable, enumeration, defaultValue, deprecated, access);
    }

    /**
     * The name a {@code $ref} points at: {@code Pet} for {@code #/components/schemas/Pet}, and also
     * for {@code ./other-file.yaml#/components/schemas/Pet} - {@link SchemaReference} resolves by
     * name alone within the one document {@link io.restest.core.model.ApiModel} holds, so a reference
     * into a different file is read the same way a local one is, file part discarded. A multi-file
     * specification where a schema of that name is also declared locally therefore resolves to the
     * local one, not the external one, without anything here able to tell the two apart. Widening
     * {@code SchemaReference} to also carry where a name came from would be the fix; that is a change
     * to the canonical model itself, not something to decide in a converter.
     */
    private static String refName(String ref) {
        Objects.requireNonNull(ref, "ref");
        int lastSlash = ref.lastIndexOf('/');
        return lastSlash < 0 ? ref : ref.substring(lastSlash + 1);
    }

    /**
     * One value the document stated - a {@code default}, or a member of an {@code enum} - as a
     * {@link JsonValue}, or nothing when it cannot be written down exactly as the document meant it.
     *
     * <p>Nothing, rather than something approximate, and that is the whole point. The parser does
     * not hand these back as the text the document wrote: a date arrives as a {@link Date}, a
     * base-64 string as an array of bytes, an object as the parser's own node. Printed the ordinary
     * way, the first becomes {@code Fri Jan 31 00:00:00 CET 2020} - not a date any API accepts, and
     * spelled differently on a machine set up in another country - and the second becomes something
     * like {@code [B@77eca502}, which is a memory address and is different on every single run.
     * Neither is a value any API would take, and the second quietly breaks the promise that one seed
     * and one document make the same requests twice.
     *
     * <p>So each kind is turned back into what the document actually said, and a kind that cannot be
     * is left out. A schema with no default is a schema the tool invents a value for, exactly as it
     * would have anyway; a schema with a made-up default is one that sends rubbish and blames the
     * API for refusing it.
     */
    private static Optional<JsonValue> toJsonValue(Object value) {
        return switch (value) {
            case null -> Optional.of(JsonValue.NULL);
            case Boolean b -> Optional.of(JsonValue.of(b));
            case BigDecimal d -> Optional.of(JsonValue.of(d));
            case Integer i -> Optional.of(JsonValue.of(i.longValue()));
            case Long l -> Optional.of(JsonValue.of(l));
            case Double d -> Optional.of(JsonValue.of(BigDecimal.valueOf(d)));
            case Float f -> Optional.of(JsonValue.of(BigDecimal.valueOf(f)));
            case String s -> Optional.of(JsonValue.of(s));
            // What the document wrote was base-64 text; the parser decoded it on the way in, so
            // encoding it again returns the document's own spelling. This is what a `byte` format
            // means. The parser also hands raw bytes over for a `binary` format, where the document
            // wrote something that JSON cannot carry in the first place; that one is written as
            // base-64 too, for want of any better answer, and a default on such a field is not a
            // thing real documents state.
            case byte[] bytes -> Optional.of(JsonValue.of(Base64.getEncoder().encodeToString(bytes)));
            // An identifier, which the parser recognises and turns into an object of its own. Named
            // here rather than left to the last resort below, because that one reads a value back
            // as JSON and an identifier is not JSON - so without this line a perfectly good default
            // would be dropped.
            case UUID identifier -> Optional.of(JsonValue.of(identifier.toString()));
            // A plain date, which the parser keeps as a moment in time - midnight on that day
            // where this machine is. Read back the same way, which returns the day the document
            // wrote no matter where the machine is. Reading it back at UTC instead looks like the
            // more careful choice and is the wrong one: anywhere east of Greenwich that midnight
            // belongs to the previous day, and the default would quietly shift by one.
            case Date date -> Optional.of(JsonValue.of(
                    date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString()));
            case OffsetDateTime moment -> Optional.of(JsonValue.of(WEB_DATE_TIME.format(moment)));
            case List<?> list -> {
                List<JsonValue> elements = new ArrayList<>();
                for (Object element : list) {
                    Optional<JsonValue> converted = toJsonValue(element);
                    if (converted.isEmpty()) {
                        yield Optional.empty();
                    }
                    elements.add(converted.get());
                }
                yield Optional.of(JsonValue.array(elements));
            }
            case Map<?, ?> map -> {
                Map<String, JsonValue> members = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Optional<JsonValue> converted = toJsonValue(entry.getValue());
                    if (converted.isEmpty()) {
                        yield Optional.empty();
                    }
                    members.put(String.valueOf(entry.getKey()), converted.get());
                }
                yield Optional.of(JsonValue.object(members));
            }
            default -> asTheDocumentWroteIt(value);
        };
    }

    /**
     * A value of a kind this converter does not otherwise know, recovered from the way it prints
     * itself.
     *
     * <p>This is how an object or an array stated as a default reaches us. The parser keeps those
     * as its own document nodes, and such a node prints exactly the JSON the document wrote, so
     * reading that text back gives the value itself instead of a description of it. Recovered
     * through the text rather than by asking the node, because the library that made it belongs to
     * the parser and does not cross out of this module.
     *
     * <p>Anything whose printed form is not JSON is a kind we cannot represent, and produces
     * nothing - which is the same answer this method would give if it were left out altogether, and
     * a better one than a sentence describing a value pretending to be the value.
     */
    private static Optional<JsonValue> asTheDocumentWroteIt(Object value) {
        try {
            return Optional.of(JsonText.read(String.valueOf(value)));
        } catch (JsonException notJson) {
            return Optional.empty();
        }
    }
}

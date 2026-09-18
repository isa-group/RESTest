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

import io.restest.core.json.JsonException;
import io.restest.core.json.JsonValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * Reads a dictionary out of the text of a file.
 *
 * <p>Written in YAML, because a dictionary is the part of this tool somebody is most likely to write
 * by hand and because the specifications it sits beside are written in it too. JSON is a subset of
 * YAML, so a file written that way is read the same way without anybody being told.
 *
 * <pre>{@code
 * version: 1
 * name: fuzzing
 * keyedBy: type
 * values:
 *   any:
 *     - null
 *   string:
 *     - ""
 *     - "   "
 * }</pre>
 *
 * <p>A file this version cannot read is refused by name rather than half-read: a dictionary that
 * silently lost half its values would be worse than one that was never loaded, because nobody would
 * know to go and look. The same goes for a key written twice, which YAML allows and which would
 * otherwise leave the first list quietly replaced by the second.
 */
final class DictionaryDocument {

    /** The version of the file format this build writes and reads. */
    static final long VERSION = 1;

    /** The members a dictionary may have. Anything else is a typo, and a typo is worth saying. */
    private static final List<String> MEMBERS =
            List.of("version", "name", "description", "keyedBy", "values");

    private DictionaryDocument() {
    }

    /**
     * The dictionary a file's text describes.
     *
     * @param text the file's contents
     * @param describedAs where it came from, for saying so when it cannot be read
     * @return the dictionary
     * @throws JsonException if the text is not a dictionary this version can read
     */
    static ValueDictionary read(String text, String describedAs) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(describedAs, "describedAs");
        JsonValue.JsonObject document = asObject(parse(text, describedAs), "a dictionary",
                describedAs);
        // The version is read before anything else is judged. A file written in a later version of
        // the format will have members this build does not know - that is what a later version is -
        // and telling its author they have made a spelling mistake would be the one message that is
        // certainly wrong about it.
        long version = wholeNumber(document, "version", describedAs);
        if (version != VERSION) {
            throw new JsonException(describedAs + " is a dictionary written in version " + version
                    + " of the format, and this version of RESTest reads version " + VERSION);
        }
        rejectAnythingUnrecognised(document, describedAs);

        String name = text(document, "name", describedAs);
        ValueDictionary.Keying keying = keying(text(document, "keyedBy", describedAs), describedAs);

        JsonValue.JsonObject stated = asObject(
                document.member("values").orElseThrow(() -> new JsonException(
                        describedAs + " is a dictionary with no 'values' in it")),
                "the values of a dictionary", describedAs);

        Map<String, List<JsonValue>> values = new LinkedHashMap<>();
        Map<String, Map<String, List<JsonValue>>> perOperation = new LinkedHashMap<>();
        if (keying == ValueDictionary.Keying.OPERATION_AND_PARAMETER) {
            stated.members().forEach((operation, parameters) -> {
                if (ValueDictionary.ANY.equals(operation)) {
                    values.put(operation, listOf(parameters, operation, describedAs));
                    return;
                }
                Map<String, List<JsonValue>> named = new LinkedHashMap<>();
                asObject(parameters, "the parameters of '" + operation + "'", describedAs)
                        .members()
                        .forEach((parameter, held) ->
                                named.put(parameter, listOf(held, parameter, describedAs)));
                perOperation.put(operation, named);
            });
        } else {
            stated.members().forEach((key, held) ->
                    values.put(key, listOf(held, key, describedAs)));
        }
        return new ValueDictionary(name, keying, values, perOperation);
    }

    /**
     * Refuses a file with a member nobody here recognises.
     *
     * <p>Strict on purpose, and easier to be strict now than later. A misspelled {@code keyedBy}
     * would otherwise leave a file that loads without complaint and then quietly does nothing at
     * all - which is the outcome this format goes out of its way to prevent
     * everywhere else. Accepting unknown members can be allowed later without breaking anybody's
     * file; refusing them later cannot.
     */
    private static void rejectAnythingUnrecognised(JsonValue.JsonObject document,
            String describedAs) {
        List<String> unrecognised = document.members().keySet().stream()
                .filter(member -> !MEMBERS.contains(member))
                .toList();
        if (!unrecognised.isEmpty()) {
            throw new JsonException(describedAs + " has " + unrecognised.size()
                    + " thing(s) in it this version does not recognise ("
                    + String.join(", ", unrecognised) + "), which is usually a misspelling - and a "
                    + "misspelled 'keyedBy' would leave a dictionary that loads and then does "
                    + "nothing");
        }
    }

    /**
     * The document a file's text describes, as the values the rest of RESTest speaks in.
     *
     * <p>Read as YAML, which reads JSON too. A key written twice is refused rather than allowed to
     * replace itself quietly, because a list of values is exactly the kind of file where somebody
     * writes {@code string:} twice by accident and never finds out that only the second one is
     * used. Nothing in the file is treated as an instruction to build anything - only the six kinds
     * of value JSON has come out of it.
     */
    private static JsonValue parse(String text, String describedAs) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try {
            DumperOptions writing = new DumperOptions();
            return asValue(new Yaml(new AsWritten(options, describedAs), new Representer(writing),
                    writing, options, new OnlyWhatJsonHas()).load(text), describedAs);
        } catch (YAMLException notYaml) {
            throw new JsonException(describedAs + " could not be read: " + notYaml.getMessage());
        }
    }

    /**
     * Decides what an unquoted word in the file is, and decides it the way JSON would.
     *
     * <p>YAML's older rules turn a surprising number of ordinary words into something else. {@code
     * no}, {@code off} and {@code n} become false and {@code yes}, {@code on} and {@code y} become
     * true, so a list of country codes containing {@code NO} sends {@code false} and a parameter
     * actually called {@code no} files its values under {@code "false"} and never matches anything.
     * Nothing warns you: the file loads, and the values are simply wrong.
     *
     * <p>So only what JSON itself has is recognised - {@code true}, {@code false}, {@code null}, a
     * whole number and a number - and everything else is a piece of text, which is what somebody
     * writing a list of values meant. It also makes the promise that a file written as JSON reads
     * the same way true of a file written as YAML.
     */
    private static final class OnlyWhatJsonHas extends Resolver {

        @Override
        protected void addImplicitResolvers() {
            addImplicitResolver(Tag.BOOL, Pattern.compile("^(?:true|false)$"), "tf");
            addImplicitResolver(Tag.NULL, Pattern.compile("^(?:null)$"), "n");
            addImplicitResolver(Tag.NULL, Pattern.compile("^$"), null);
            addImplicitResolver(Tag.INT, Pattern.compile("^-?(?:0|[1-9][0-9]*)$"), "-0123456789");
            addImplicitResolver(Tag.FLOAT,
                    Pattern.compile("^-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?$"),
                    "-0123456789");
        }
    }

    /**
     * Keeps a number exactly as the file wrote it.
     *
     * <p>Read the ordinary way, a number becomes the nearest value a computer can hold in sixty-four
     * bits, and a list of values for pushing at an API is precisely where somebody writes a number
     * too long for that on purpose. Taking the digits as they were written keeps every one of them.
     */
    private static final class AsWritten extends SafeConstructor {

        AsWritten(LoaderOptions options, String describedAs) {
            super(options);
            yamlConstructors.put(Tag.FLOAT, new AbstractConstruct() {
                @Override
                public Object construct(Node node) {
                    String written = ((ScalarNode) node).getValue();
                    try {
                        return new BigDecimal(written);
                    } catch (NumberFormatException notANumber) {
                        // Reachable only when the file tags a value as a number itself, since
                        // nothing else is read as one. `!!float .inf` is the way in.
                        throw new JsonException(describedAs + " holds '" + written
                                + "' where a number belongs, and that is not a number a request "
                                + "could carry");
                    }
                }
            });
        }
    }

    /** One thing the reader handed back, as the value model the rest of RESTest speaks in. */
    private static JsonValue asValue(Object read, String describedAs) {
        return switch (read) {
            case null -> JsonValue.NULL;
            case Boolean yesOrNo -> JsonValue.of(yesOrNo);
            case Integer whole -> JsonValue.of(whole.longValue());
            case Long whole -> JsonValue.of(whole);
            case java.math.BigInteger whole -> JsonValue.of(new BigDecimal(whole));
            case BigDecimal exact -> JsonValue.of(exact);
            case Double fractional -> JsonValue.of(BigDecimal.valueOf(fractional));
            case Float fractional -> JsonValue.of(BigDecimal.valueOf(fractional));
            case String text -> JsonValue.of(text);
            case List<?> list -> {
                List<JsonValue> elements = new ArrayList<>();
                list.forEach(element -> elements.add(asValue(element, describedAs)));
                yield JsonValue.array(elements);
            }
            case Map<?, ?> map -> {
                Map<String, JsonValue> members = new LinkedHashMap<>();
                map.forEach((key, held) ->
                        members.put(String.valueOf(key), asValue(held, describedAs)));
                yield JsonValue.object(members);
            }
            // A date, a set, a binary blob: YAML has kinds JSON does not, and a value RESTest could
            // not put in a request is not a value worth keeping.
            default -> throw new JsonException(describedAs + " holds a "
                    + read.getClass().getSimpleName() + ", which is not something that can be sent");
        };
    }

    private static List<JsonValue> listOf(JsonValue value, String key, String describedAs) {
        if (value instanceof JsonValue.JsonArray array) {
            return array.elements();
        }
        throw new JsonException(describedAs + " holds something other than a list of values under '"
                + key + "'");
    }

    private static ValueDictionary.Keying keying(String stated, String describedAs) {
        return switch (stated) {
            case "type" -> ValueDictionary.Keying.TYPE;
            case "format" -> ValueDictionary.Keying.FORMAT;
            case "schema" -> ValueDictionary.Keying.SCHEMA;
            case "name" -> ValueDictionary.Keying.NAME;
            case "operationAndParameter" -> ValueDictionary.Keying.OPERATION_AND_PARAMETER;
            default -> throw new JsonException(describedAs + " says its values are keyed by '"
                    + stated + "', which is not one of type, format, schema, name or "
                    + "operationAndParameter");
        };
    }

    private static JsonValue.JsonObject asObject(JsonValue value, String what, String describedAs) {
        if (value instanceof JsonValue.JsonObject object) {
            return object;
        }
        throw new JsonException(describedAs + ": " + what + " is not an object");
    }

    private static String text(JsonValue.JsonObject document, String member, String describedAs) {
        return asText(document.member(member).orElseThrow(() -> new JsonException(
                describedAs + " is a dictionary with no '" + member + "' in it")), member,
                describedAs);
    }

    private static String asText(JsonValue value, String member, String describedAs) {
        if (value instanceof JsonValue.JsonString string) {
            return string.value();
        }
        throw new JsonException(describedAs + "'s '" + member + "' is not text");
    }

    private static long wholeNumber(JsonValue.JsonObject document, String member,
            String describedAs) {
        JsonValue value = document.member(member).orElseThrow(() -> new JsonException(
                describedAs + " is a dictionary with no '" + member + "' in it"));
        if (value instanceof JsonValue.JsonNumber number) {
            try {
                // Exactly, not by rounding: a file declaring version 1.9 is not a version 1 file,
                // and reading it as one is the half-reading this refuses everywhere else.
                return number.value().longValueExact();
            } catch (ArithmeticException notWhole) {
                throw new JsonException(describedAs + "'s '" + member + "' is " + number.value()
                        + ", and a version is a whole number");
            }
        }
        throw new JsonException(describedAs + "'s '" + member + "' is not a number");
    }
}

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
import io.restest.core.json.YamlText;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
 *
 * <p>An operation written down with nothing under it is not one of those mistakes. It says there is
 * nothing to fill in for that operation, and it is read that way rather than costing the file.
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
        JsonValue.JsonObject document = asObject(YamlText.read(text, describedAs), "a dictionary",
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
                orNothingWritten(document.member("values").orElseThrow(() -> new JsonException(
                        describedAs + " is a dictionary with no 'values' in it"))),
                "the values of a dictionary", describedAs);

        Map<String, List<JsonValue>> values = new LinkedHashMap<>();
        Map<String, Map<String, List<JsonValue>>> perOperation = new LinkedHashMap<>();
        if (keying == ValueDictionary.Keying.OPERATION_AND_PARAMETER) {
            stated.members().forEach((operation, parameters) -> {
                if (ValueDictionary.ANY.equals(operation)) {
                    values.put(operation, listOf(parameters, "'" + operation + "'", describedAs));
                    return;
                }
                Map<String, List<JsonValue>> named = new LinkedHashMap<>();
                asObject(orNothingWritten(parameters),
                        "what is written under '" + operation + "'", describedAs)
                        .members()
                        .forEach((parameter, held) -> named.put(parameter, listOf(held,
                                "'" + parameter + "' in '" + operation + "'", describedAs)));
                perOperation.put(operation, named);
            });
        } else {
            stated.members().forEach((key, held) ->
                    values.put(key, listOf(held, "'" + key + "'", describedAs)));
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
     * A heading with nothing under it, read as the empty set of entries it plainly is.
     *
     * <p>YAML reads a key with nothing after it as null rather than as an empty object, so an
     * operation written down with only a comment beneath it - the natural way to say "nothing to
     * fill here" - would otherwise be something that is not an object, and would cost the whole file
     * every value in it. Only where a set of entries belongs: a list of values is a different matter,
     * below.
     */
    private static JsonValue orNothingWritten(JsonValue value) {
        return value instanceof JsonValue.JsonNull ? JsonValue.object(Map.of()) : value;
    }

    /**
     * The list of values written somewhere, or a refusal naming where.
     *
     * @param where the key it was written under, already quoted, with its operation when it has one
     */
    private static List<JsonValue> listOf(JsonValue value, String where, String describedAs) {
        if (value instanceof JsonValue.JsonArray array) {
            return array.elements();
        }
        if (value instanceof JsonValue.JsonNull) {
            // Not read as an empty list, unlike a heading with nothing under it. Here null is a
            // value somebody might mean to send, and guessing between "no values" and "send null"
            // would be wrong half the time without anybody being told.
            throw new JsonException(describedAs + " has nothing under " + where + ", where a list "
                    + "of values belongs: write [] for no values, or [null] to send null");
        }
        throw new JsonException(describedAs + " holds something other than a list of values under "
                + where);
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

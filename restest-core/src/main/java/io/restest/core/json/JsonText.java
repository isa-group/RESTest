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
package io.restest.core.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Turns RESTest's own idea of a JSON value into text, and text back into it.
 *
 * <p>RESTest keeps JSON in types of its own rather than a library's, so that everything built on top
 * of it - a generator, an oracle, a report - works in one vocabulary. That choice stops wherever the
 * text itself is needed: writing it out and reading it back needs a real JSON reader and writer, and
 * the escaping and number rules are exactly the sort of thing that looks easy and is not. So this is
 * a thin adapter over a library that does it properly.
 *
 * <p>Several parts of RESTest need it and none of them can see the others: the file a run is stored
 * in, the report written at the end of a run, the schema location an oracle hands to the validator,
 * and the replies an API sends back that a run learns values from. It therefore sits in the one
 * module they all share, so that this project has exactly one answer to "what does this value look
 * like written down".
 *
 * <p>Those are not all the same kind of text, and the difference matters. What RESTest wrote itself
 * is read back whole, however large the person running it chose to let it be. What an API under
 * test sends back is read under limits, because it is written by the very thing being tested and a
 * reply can be shaped to be expensive to read.
 *
 * <p>Numbers keep their exact written form, so an identifier with twenty digits comes back as itself
 * rather than as the nearest number a computer could hold - and they are written the way people
 * write them. A status code of 200 is stored as {@code 200}: the obvious-looking shortcut here
 * produces {@code 2E+2}, which is the same number and is not what anybody reading a stored run
 * expects to see.
 */
public final class JsonText {

    /**
     * For text RESTest wrote itself. The reader refuses very long pieces of text by default, as a
     * defence against hostile input, and that defence is not what is wanted here: what this reads
     * is a run this tool stored, and the size of a reply body it kept is the user's own setting to
     * make. Left at the default, a run that kept a twenty-megabyte reply would be written happily
     * and then be unreadable for ever.
     */
    private static final JsonFactory FACTORY = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxStringLength(Integer.MAX_VALUE)
                    .maxNumberLength(Integer.MAX_VALUE)
                    .maxNestingDepth(10_000)
                    .build())
            .build();

    /**
     * How deeply a reply from an API under test may be nested before it is refused.
     *
     * <p>Far deeper than any resource anybody describes - the deepest request body in a corpus of
     * fifty real specifications is five levels - and far shallower than the depth at which reading
     * one runs out of room on the stack. The value in between is the whole point: a reply nested
     * ten thousand levels deep costs nothing to send and would otherwise end the thread reading it.
     */
    private static final int AS_DEEP_AS_A_REPLY_MAY_BE = 100;

    /** How long a single number in such a reply may be written, in characters. */
    private static final int LONGEST_NUMBER_IN_A_REPLY = 1_000;

    /** How long a single piece of text in such a reply may be, in characters. */
    private static final int LONGEST_TEXT_IN_A_REPLY = 1_024 * 1_024;

    /**
     * For text an API under test sent back, which is written by the thing being tested.
     *
     * <p>Same reader, tight limits. Reading a value builds it piece by piece and one piece inside
     * another, so a reply that is nothing but ten thousand open brackets - eighteen kilobytes,
     * costing the API nothing - would run the reading thread out of room. Refusing it is the whole
     * of the defence, and it costs nothing real, because nothing this shape is a resource anybody
     * meant to hand back.
     */
    private static final JsonFactory FROM_AN_API = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxStringLength(LONGEST_TEXT_IN_A_REPLY)
                    .maxNumberLength(LONGEST_NUMBER_IN_A_REPLY)
                    .maxNestingDepth(AS_DEEP_AS_A_REPLY_MAY_BE)
                    .build())
            .build();

    private JsonText() {
    }

    /**
     * The value as compact JSON text, on one line.
     *
     * @param value what to write
     * @return the JSON text
     */
    public static String write(JsonValue value) {
        Objects.requireNonNull(value, "value");
        StringWriter text = new StringWriter();
        try (JsonGenerator out = FACTORY.createGenerator(text)) {
            out.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
            writeValue(out, value);
        } catch (IOException e) {
            throw new JsonException("This value could not be written as JSON", e);
        }
        return text.toString();
    }

    /**
     * The value the text describes, and nothing but that value.
     *
     * <p>Text left over after the value ends is refused rather than ignored, and the difference
     * matters more than it looks. One value followed by anything else is not JSON, and the things
     * that produce it are exactly the breakages worth finding: a handler that writes its payload
     * twice, an error page appended after a reply that had already begun, a warning printed into
     * the middle of an answer. Reading only as far as the first value and declaring success would
     * call every one of those a well-formed reply. It would also let a stored run that had been
     * damaged read back as intact, which is the one thing this reader exists to notice.
     *
     * @param text JSON text, as {@link #write} produces
     * @return the value it describes
     * @throws JsonException if the text is not one JSON value, or carries anything after it
     */
    public static JsonValue read(String text) {
        Objects.requireNonNull(text, "text");
        return read(text, FACTORY);
    }

    private static JsonValue read(String text, JsonFactory factory) {
        try (JsonParser in = factory.createParser(text)) {
            if (in.nextToken() == null) {
                throw new JsonException("Empty text where a JSON value was expected");
            }
            JsonValue value = readValue(in);
            if (in.nextToken() != null) {
                throw new JsonException("A JSON value ended and the text carried on, at character "
                        + in.currentLocation().getCharOffset());
            }
            return value;
        } catch (IOException e) {
            throw new JsonException(
                    "Stored JSON could not be read back: " + e.getMessage(), e);
        }
    }

    /**
     * The value the text describes, where the text came back from the API being tested.
     *
     * <p>The same answer {@link #read} gives, under limits {@link #read} deliberately does not
     * apply: how deeply the value may be nested, how long one number may be written, and how long
     * one piece of text may be. A reply exceeding any of them is refused rather than read, which
     * for whoever is asking means learning nothing from that reply - a good deal cheaper than the
     * alternative, since this is read on the one thread that carries a run's announcements to
     * everything listening.
     *
     * @param text the reply, as it arrived
     * @return the value it describes
     * @throws JsonException if it is not one JSON value, or carries anything after it, or is
     *     larger or deeper than a reply is allowed to be
     */
    public static JsonValue readFromAnApi(String text) {
        Objects.requireNonNull(text, "text");
        return read(text, FROM_AN_API);
    }

    /**
     * Checks that the text is one JSON value and nothing else, without building the value.
     *
     * <p>The same question {@link #read} answers, for whoever only wants the answer. Judging a
     * reply against its declared shape starts by asking whether the reply is JSON at all, and doing
     * that with {@code read} built a whole value that was thrown away an instant later - on the one
     * thread that has to keep up with every reply the engine produces, and twice over for every
     * large body, since whatever judges it parses the text again anyway.
     *
     * @param text the text to check
     * @throws JsonException if it is not one JSON value, or carries anything after it
     */
    public static void checkOneValue(String text) {
        Objects.requireNonNull(text, "text");
        try (JsonParser in = FACTORY.createParser(text)) {
            if (in.nextToken() == null) {
                throw new JsonException("Empty text where a JSON value was expected");
            }
            // Walks to the end of this value without keeping any of it: past a whole object or
            // array, and nowhere at all for a plain number or word.
            in.skipChildren();
            if (in.nextToken() != null) {
                throw new JsonException("A JSON value ended and the text carried on, at character "
                        + in.currentLocation().getCharOffset());
            }
        } catch (IOException e) {
            throw new JsonException(
                    "Stored JSON could not be read back: " + e.getMessage(), e);
        }
    }

    private static void writeValue(JsonGenerator out, JsonValue value) throws IOException {
        switch (value) {
            case JsonValue.JsonNull ignored -> out.writeNull();
            case JsonValue.JsonBoolean bool -> out.writeBoolean(bool.value());
            case JsonValue.JsonNumber number -> out.writeNumber(number.value());
            case JsonValue.JsonString string -> out.writeString(string.value());
            case JsonValue.JsonArray array -> {
                out.writeStartArray();
                for (JsonValue element : array.elements()) {
                    writeValue(out, element);
                }
                out.writeEndArray();
            }
            case JsonValue.JsonObject object -> {
                out.writeStartObject();
                for (Map.Entry<String, JsonValue> member : object.members().entrySet()) {
                    out.writeFieldName(member.getKey());
                    writeValue(out, member.getValue());
                }
                out.writeEndObject();
            }
        }
    }

    private static JsonValue readValue(JsonParser in) throws IOException {
        JsonToken token = in.currentToken();
        return switch (token) {
            case VALUE_NULL -> JsonValue.NULL;
            case VALUE_TRUE -> JsonValue.TRUE;
            case VALUE_FALSE -> JsonValue.FALSE;
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT ->
                    JsonValue.of(new BigDecimal(in.getText()));
            case VALUE_STRING -> JsonValue.of(in.getText());
            case START_ARRAY -> readArray(in);
            case START_OBJECT -> readObject(in);
            case null, default -> throw new JsonException(
                    "A JSON value cannot start with " + token);
        };
    }

    private static JsonValue readArray(JsonParser in) throws IOException {
        List<JsonValue> elements = new ArrayList<>();
        while (in.nextToken() != JsonToken.END_ARRAY) {
            elements.add(readValue(in));
        }
        return JsonValue.array(elements);
    }

    private static JsonValue readObject(JsonParser in) throws IOException {
        Map<String, JsonValue> members = new LinkedHashMap<>();
        while (in.nextToken() != JsonToken.END_OBJECT) {
            String name = in.currentName();
            in.nextToken();
            members.put(name, readValue(in));
        }
        return JsonValue.object(members);
    }
}

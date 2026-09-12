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
package io.restest.store;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import io.restest.core.json.JsonValue;
import io.restest.core.store.InteractionStoreException;
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
 * of it - a generator, an oracle, a report - depends on nothing but RESTest. That choice stops at the
 * edge of the disk: writing it out and reading it back needs a real JSON reader and writer, and the
 * escaping and number rules are exactly the sort of thing that looks easy and is not. So this is a
 * thin adapter over a library that does it properly, and it lives here, next to the disk, rather than
 * anywhere a user of RESTest would meet it.
 *
 * <p>Numbers keep their exact written form, so an identifier with twenty digits comes back as itself
 * rather than as the nearest number a computer could hold - and they are written the way people
 * write them. A status code of 200 is stored as {@code 200}: the obvious-looking shortcut here
 * produces {@code 2E+2}, which is the same number and is not what anybody reading a stored run
 * expects to see.
 */
public final class Json {

    /**
     * The reader refuses very long pieces of text by default, as a defence against hostile input.
     * That defence is aimed at data arriving from strangers; here the text is a run this tool wrote
     * itself moments earlier, and the size of a reply body is the user's own setting to make. Left at
     * the default, a run that kept a twenty-megabyte reply would be written happily and then be
     * unreadable for ever.
     */
    private static final JsonFactory FACTORY = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxStringLength(Integer.MAX_VALUE)
                    .maxNumberLength(Integer.MAX_VALUE)
                    .maxNestingDepth(10_000)
                    .build())
            .build();

    private Json() {
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
            throw new InteractionStoreException("This value could not be written as JSON", e);
        }
        return text.toString();
    }

    /**
     * The value the text describes.
     *
     * @param text JSON text, as {@link #write} produces
     * @return the value it describes
     */
    public static JsonValue read(String text) {
        Objects.requireNonNull(text, "text");
        try (JsonParser in = FACTORY.createParser(text)) {
            if (in.nextToken() == null) {
                throw new InteractionStoreException("Empty text where a JSON value was expected");
            }
            return readValue(in);
        } catch (IOException e) {
            throw new InteractionStoreException(
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
            case null, default -> throw new InteractionStoreException(
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

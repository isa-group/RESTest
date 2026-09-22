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
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
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
 * Turns the text of a file somebody wrote by hand into plain values the rest of the tool can read.
 *
 * <p>Two kinds of file are written by hand for RESTest: a list of values to send, and a plan saying
 * where a run's values should come from. Both are YAML, for the same reason - the specifications
 * they sit beside are written in it, and JSON is a subset of YAML, so a file written either way is
 * read the same way without anybody being told.
 *
 * <p>Reading them is the same job twice, and it is a job with two traps in it that are worth having
 * solved in one place rather than two. Both are described below, on the two small classes that
 * avoid them.
 */
final class YamlText {

    private YamlText() {
    }

    /**
     * What a file's text says, as the values the rest of RESTest speaks in.
     *
     * <p>A key written twice is refused rather than allowed to replace itself quietly, because
     * these are exactly the kind of file where somebody writes a heading twice by accident and
     * never finds out that only the second one is used. Nothing in the file is treated as an
     * instruction to build anything - only the six kinds of value JSON has come out of it.
     *
     * @param text the file's contents
     * @param describedAs where it came from, for saying so when it cannot be read
     * @return what it says
     * @throws JsonException if the text is not something this can read
     */
    static JsonValue read(String text, String describedAs) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(describedAs, "describedAs");
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
     * writing one of these meant. It also makes the promise that a file written as JSON reads the
     * same way true of a file written as YAML.
     */
    private static final class OnlyWhatJsonHas extends Resolver {

        @Override
        protected void addImplicitResolvers() {
            addImplicitResolver(Tag.BOOL, Pattern.compile("^(?:true|false)$"), "tf");
            addImplicitResolver(Tag.NULL, Pattern.compile("^(?:null)$"), "n");
            // Registered under no first character on purpose: an empty scalar has none, and the
            // reader consults this list for every scalar as a last resort, which is where it is
            // wanted. Nothing else can match an expression anchored at both ends around nothing.
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

    /** The object a value is, or a refusal naming what was there instead. */
    static JsonValue.JsonObject asObject(JsonValue value, String what, String describedAs) {
        if (value instanceof JsonValue.JsonObject object) {
            return object;
        }
        throw new JsonException(describedAs + ": " + what + " is not an object");
    }

    /** The list a value is, or a refusal naming what was there instead. */
    static List<JsonValue> asList(JsonValue value, String what, String describedAs) {
        if (value instanceof JsonValue.JsonArray array) {
            return array.elements();
        }
        throw new JsonException(describedAs + ": " + what + " is not a list");
    }

    /** The text a value is, or a refusal naming what was there instead. */
    static String asText(JsonValue value, String what, String describedAs) {
        if (value instanceof JsonValue.JsonString string) {
            return string.value();
        }
        throw new JsonException(describedAs + ": " + what + " is not text");
    }

    /**
     * The whole number a value is.
     *
     * <p>Exactly, not by rounding: a file declaring version 1.9 is not a version 1 file, and a
     * share of 24.5 is not a share of 24. Reading either as the nearest whole number is the
     * half-reading these formats refuse everywhere else.
     */
    static long asWholeNumber(JsonValue value, String what, String describedAs) {
        if (value instanceof JsonValue.JsonNumber number) {
            try {
                return number.value().longValueExact();
            } catch (ArithmeticException notWhole) {
                throw new JsonException(describedAs + ": " + what + " is " + number.value()
                        + ", and a whole number belongs there");
            }
        }
        throw new JsonException(describedAs + ": " + what + " is not a number");
    }
}

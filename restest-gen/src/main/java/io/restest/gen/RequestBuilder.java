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

import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.TestCase;
import io.restest.core.json.JsonValue;
import io.restest.core.model.Operation;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.ParameterStyle;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Turns a chosen set of values into an actual request: a URL with the gaps filled in, a query string,
 * headers and cookies.
 *
 * <p>Filling the gaps is less obvious than it looks, because a specification says not only *what* a
 * parameter is but *how* it is written down. A list of tags can travel as {@code tags=a&tags=b} or as
 * {@code tags=a,b}, and which one the API expects is stated in the document, not chosen by us. The two
 * ways this understands are the two that OpenAPI uses unless a document says otherwise, which is the
 * overwhelming majority of real parameters; a document asking for one of the unusual ways is refused
 * by name rather than served a guess that the API would reject for reasons nobody could see.
 *
 * <p>Everything that goes into a URL is percent-encoded - the {@code %20} treatment - so a value
 * containing a space, a slash or a word in a non-Latin alphabet produces a request that means what it
 * says instead of an accidentally different one.
 */
public final class RequestBuilder {

    private RequestBuilder() {
    }

    /**
     * The request that sends these values to this operation.
     *
     * @param operation the operation being tested, which says how each parameter is written down
     * @param testCase the values chosen for it
     * @param baseUrl where the API lives, for example {@code https://example.com/api/v3}
     * @return the request, ready to be sent
     * @throws IllegalArgumentException if the operation writes a parameter down in a way this does not
     *     understand, or if a value is missing for a parameter in the path
     */
    public static HttpRequestRecord build(Operation operation, TestCase testCase, String baseUrl) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(testCase, "testCase");
        Objects.requireNonNull(baseUrl, "baseUrl");

        String path = fillIn(operation, testCase);
        String query = queryString(operation, testCase);
        String url = trimTrailingSlash(baseUrl) + path + (query.isEmpty() ? "" : "?" + query);
        return new HttpRequestRecord(operation.method(), url, headers(operation, testCase),
                Optional.empty());
    }

    /**
     * Whether every parameter of this operation is written down in a way that can be assembled.
     *
     * @param operation the operation
     * @return empty if the request can be built, or what stands in the way
     */
    public static Optional<String> whatCannotBeAssembled(Operation operation) {
        Objects.requireNonNull(operation, "operation");
        for (Parameter parameter : operation.parameters()) {
            if (parameter.isContentSerialised()) {
                return Optional.of("the parameter '" + parameter.name() + "' is written as "
                        + parameter.mediaType().orElseThrow() + " inside the request, which is not "
                        + "assembled yet");
            }
            if (!isUnderstood(parameter.style())) {
                return Optional.of("the parameter '" + parameter.name() + "' is written in the '"
                        + parameter.style() + "' style, which is not assembled yet");
            }
        }
        return Optional.empty();
    }

    private static boolean isUnderstood(ParameterStyle style) {
        return style == ParameterStyle.SIMPLE || style == ParameterStyle.FORM;
    }

    private static String fillIn(Operation operation, TestCase testCase) {
        String path = operation.path();
        for (Parameter parameter : operation.parameters(ParameterLocation.PATH)) {
            ParameterValue value = testCase.parameterValue(parameter.name(), ParameterLocation.PATH)
                    .orElseThrow(() -> new IllegalArgumentException("the path of " + operation.id()
                            + " needs '" + parameter.name() + "' and the test case has no value "
                            + "for it, so no request could be assembled"));
            String written = joined(value.value(), parameter, ",");
            path = path.replace("{" + parameter.name() + "}", encode(written));
        }
        return path;
    }

    private static String queryString(Operation operation, TestCase testCase) {
        StringJoiner query = new StringJoiner("&");
        for (Parameter parameter : operation.parameters(ParameterLocation.QUERY)) {
            testCase.parameterValue(parameter.name(), ParameterLocation.QUERY).ifPresent(value ->
                    asQuery(parameter, value.value()).forEach(query::add));
        }
        return query.toString();
    }

    /**
     * One parameter as the {@code name=value} pieces it contributes to the query string.
     *
     * <p>A list or an object contributes either one piece holding everything, separated by commas, or
     * one piece per element - which is what "exploded" means, and what the specification decides.
     */
    private static List<String> asQuery(Parameter parameter, JsonValue value) {
        List<String> pieces = new ArrayList<>();
        String name = encode(parameter.name());
        switch (value) {
            case JsonValue.JsonArray array when parameter.explode() ->
                    array.elements().forEach(element ->
                            pieces.add(name + "=" + encode(scalar(element))));
            case JsonValue.JsonObject object when parameter.explode() ->
                    object.members().forEach((member, held) ->
                            pieces.add(encode(member) + "=" + encode(scalar(held))));
            default -> pieces.add(name + "=" + encode(joined(value, parameter, ",")));
        }
        return pieces;
    }

    private static List<Header> headers(Operation operation, TestCase testCase) {
        List<Header> headers = new ArrayList<>();
        for (Parameter parameter : operation.parameters(ParameterLocation.HEADER)) {
            testCase.parameterValue(parameter.name(), ParameterLocation.HEADER).ifPresent(value ->
                    headers.add(Header.of(parameter.name(), joined(value.value(), parameter, ","))));
        }

        Map<String, String> cookies = new LinkedHashMap<>();
        for (Parameter parameter : operation.parameters(ParameterLocation.COOKIE)) {
            testCase.parameterValue(parameter.name(), ParameterLocation.COOKIE).ifPresent(value ->
                    cookies.put(parameter.name(), joined(value.value(), parameter, ",")));
        }
        if (!cookies.isEmpty()) {
            StringJoiner jar = new StringJoiner("; ");
            cookies.forEach((name, value) -> jar.add(name + "=" + value));
            headers.add(Header.of("Cookie", jar.toString()));
        }
        return headers;
    }

    /** One value written as a single string, which is what a path, a header and a cookie need. */
    private static String joined(JsonValue value, Parameter parameter, String separator) {
        return switch (value) {
            case JsonValue.JsonArray array -> array.elements().stream()
                    .map(RequestBuilder::scalar)
                    .reduce((a, b) -> a + separator + b)
                    .orElse("");
            case JsonValue.JsonObject object -> {
                StringJoiner written = new StringJoiner(separator);
                String pairing = parameter.explode() ? "=" : separator;
                object.members().forEach((member, held) ->
                        written.add(member + pairing + scalar(held)));
                yield written.toString();
            }
            default -> scalar(value);
        };
    }

    /** One simple value as text: what a person would type, not what JSON would print. */
    private static String scalar(JsonValue value) {
        return switch (value) {
            case JsonValue.JsonString string -> string.value();
            case JsonValue.JsonNumber number -> plain(number.value());
            case JsonValue.JsonBoolean bool -> String.valueOf(bool.value());
            case JsonValue.JsonNull ignored -> "";
            // A list or an object nested inside another one has no agreed way of being written into
            // a URL at all; flattening it loses the nesting, and that is the honest amount of effort
            // to spend on a shape no API can read back.
            case JsonValue.JsonArray array -> array.elements().stream()
                    .map(RequestBuilder::scalar).reduce((a, b) -> a + "," + b).orElse("");
            case JsonValue.JsonObject object -> {
                StringJoiner written = new StringJoiner(",");
                object.members().forEach((member, held) -> written.add(member + "," + scalar(held)));
                yield written.toString();
            }
        };
    }

    private static String plain(BigDecimal number) {
        return number.stripTrailingZeros().toPlainString();
    }

    private static String trimTrailingSlash(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * Percent-encoding, the {@code %20} treatment.
     *
     * <p>Everything except the handful of characters a URL is allowed to contain literally is written
     * as its bytes in hexadecimal. Deliberately strict: encoding a character that did not need it is
     * harmless and means the same thing, while leaving one that did produces a different request from
     * the one intended - a value containing a slash would silently become another path segment.
     */
    private static String encode(String value) {
        StringBuilder encoded = new StringBuilder(value.length());
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char character = (char) (b & 0xFF);
            if (isSafe(character)) {
                encoded.append(character);
            } else {
                encoded.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return encoded.toString();
    }

    private static boolean isSafe(char character) {
        return (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9')
                || character == '-' || character == '.' || character == '_' || character == '~';
    }
}

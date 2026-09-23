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

import io.restest.core.execution.BodyValue;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.Payload;
import io.restest.core.execution.TestCase;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.model.Operation;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.ParameterStyle;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.model.ResponseModel;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 *
 * <p>It also writes the body, for the operations that take one, and says what the request is willing
 * to receive back. A body travels either as JSON or as the fields of a web form, which are the two
 * ways nearly every API in the world accepts one; which of the two, and which exact media type to
 * declare, is read off the document. And every request carries an {@code Accept} header naming the
 * media types the operation's own successful responses declare, so that an API serving more than one
 * - a versioned one, say - is not left guessing what this client can read; where they declare none,
 * the header says the client will take anything.
 */
public final class RequestBuilder {

    /** The media type of a body written as the fields of a web form. */
    private static final String FORM = "application/x-www-form-urlencoded";

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
        String url = base(baseUrl) + path + (query.isEmpty() ? "" : "?" + query);
        Optional<Payload> body = testCase.body().map(RequestBuilder::written);
        return new HttpRequestRecord(operation.method(), url,
                headers(operation, testCase, body), body);
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
            if (written.isEmpty()) {
                // An empty value would close the gap in the path rather than fill it, and
                // /pets/{petId} would quietly become /pets - a request against the collection,
                // judged afterwards against the contract of the single-item operation.
                throw new IllegalArgumentException("the value chosen for '" + parameter.name()
                        + "' in the path of " + operation.id() + " is empty, which would address a "
                        + "different resource instead of filling the gap");
            }
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
        return pairs(parameter.name(), value, parameter.explode());
    }

    /**
     * One named value as the {@code name=value} pieces it contributes, to a query string or to the
     * fields of a form - the two places where the same rules apply.
     */
    private static List<String> pairs(String name, JsonValue value, boolean explode) {
        List<String> pieces = new ArrayList<>();
        String written = encode(name);
        switch (value) {
            case JsonValue.JsonArray array when explode ->
                    array.elements().forEach(element ->
                            pieces.add(written + "=" + encode(scalar(element))));
            case JsonValue.JsonObject object when explode ->
                    object.members().forEach((member, held) ->
                            pieces.add(encode(member) + "=" + encode(scalar(held))));
            default -> pieces.add(written + "=" + encode(joined(value, explode, ",")));
        }
        return pieces;
    }

    private static List<Header> headers(Operation operation, TestCase testCase,
            Optional<Payload> body) {
        List<Header> headers = new ArrayList<>();
        for (Parameter parameter : operation.parameters(ParameterLocation.HEADER)) {
            testCase.parameterValue(parameter.name(), ParameterLocation.HEADER).ifPresent(value ->
                    headers.add(Header.of(parameter.name(), headerValue(parameter.name(),
                            joined(value.value(), parameter, ",")))));
        }
        // Written out rather than left to the HTTP client to add. The client would add the same
        // thing, but only to what goes on the wire - and then the request we recorded, reported and
        // print as a curl command would be missing a header the API actually received.
        body.ifPresent(payload -> addUnlessDeclared(headers, "Content-Type", payload.mediaType()));
        // A document that declares an Accept header of its own has said what it wants sent there,
        // and a value has already been chosen for it above.
        addUnlessDeclared(headers, "Accept", willAccept(operation));

        Map<String, String> cookies = new LinkedHashMap<>();
        for (Parameter parameter : operation.parameters(ParameterLocation.COOKIE)) {
            testCase.parameterValue(parameter.name(), ParameterLocation.COOKIE).ifPresent(value ->
                    // Encoded, because a semicolon or a comma inside one cookie's value would
                    // otherwise read as the start of another cookie.
                    cookies.put(parameter.name(), encode(joined(value.value(), parameter, ","))));
        }
        if (!cookies.isEmpty()) {
            StringJoiner jar = new StringJoiner("; ");
            cookies.forEach((name, value) -> jar.add(name + "=" + value));
            headers.add(Header.of("Cookie", jar.toString()));
        }
        return headers;
    }

    /**
     * One header this class adds itself, unless the document declared a header of that name and a
     * value has already been chosen for it.
     *
     * <p>Checked like any other header value. Both of these are built out of media types the
     * document wrote, and a document is free to write anything at all.
     */
    private static void addUnlessDeclared(List<Header> headers, String name, String value) {
        if (headers.stream().noneMatch(header -> header.name().equalsIgnoreCase(name))) {
            headers.add(Header.of(name, headerValue(name, value)));
        }
    }

    /**
     * What the request says it is willing to receive, from the media types the operation's own
     * successful responses declare.
     *
     * <p>Sending nothing leaves content negotiation to whatever the server does by default, which is
     * usually the right thing and occasionally a 406 against a client that never said what it could
     * read. An API serving a versioned media type - {@code application/vnd.example.v2+json} - is
     * entitled to refuse a request that did not ask for it.
     *
     * <p>Only the successful responses, because an error shape is not what the request is for.
     *
     * <p>All of them, but not equally: the ones RESTest can read back are asked for first and the
     * rest are asked for at a lower quality, which is what HTTP's {@code q} is. This matters more
     * than it looks. Several documents in the corpus offer XML before JSON - the pet shop the smoke
     * run starts is one - and a server that honours the client's order would then answer XML, which
     * nothing on our side can judge against the shape the document declares. The reply would be
     * neither checked nor reported: worse than the default this header was added to improve on. The
     * lower quality still asks for them, so an API that serves nothing else is not refused a
     * reply.
     *
     * <p>An operation that declares no successful media type at all - no success, or a success with
     * nothing in it - is asked for anything, which is what leaving the header out means anyway. It
     * is said out loud because not every server treats a missing header the way the standard does.
     *
     * @return the header's value
     */
    private static String willAccept(Operation operation) {
        Set<String> offered = new LinkedHashSet<>();
        for (ResponseModel response : operation.responses()) {
            if (response.status().startsWith("2")) {
                response.content().keySet().stream()
                        .filter(mediaType -> !cannotTravelInAHeader(mediaType))
                        .forEach(offered::add);
            }
        }
        if (offered.isEmpty()) {
            return "*/*";
        }
        List<String> readable = offered.stream().filter(RequestBuilder::isJson).toList();
        if (readable.isEmpty()) {
            return String.join(", ", offered);
        }
        StringJoiner written = new StringJoiner(", ");
        readable.forEach(written::add);
        offered.stream().filter(mediaType -> !isJson(mediaType))
                .forEach(mediaType -> written.add(mediaType + ";q=0.5"));
        return written.toString();
    }

    /**
     * The media type a body would be sent as, out of the ones the document offers for it.
     *
     * <p>JSON where it is offered, a web form otherwise. Those are the two this writes, and the
     * preference is not a coin toss: an API offering both describes the same resource twice, and
     * JSON is the one that can carry a shape with anything nested inside it.
     *
     * <p>A document that names no exact type but leaves the door open - {@code *&#47;*} or {@code
     * application/*} - is answered with JSON, which is a media type both of those ranges include.
     *
     * @param body what the operation accepts
     * @return the media type to declare and write, or nothing when the body is only offered in ways
     *     this cannot write
     */
    static Optional<String> mediaTypeToSend(RequestBodyModel body) {
        Objects.requireNonNull(body, "body");
        // A media type is written into a header, so one that could not travel in a header is one no
        // request could carry. Refused here rather than thrown at the point of sending, where the
        // operation would already have been reported as one this run is testing.
        List<String> offered = body.mediaTypes().stream()
                .filter(mediaType -> !cannotTravelInAHeader(mediaType))
                .toList();
        Optional<String> json = offered.stream().filter(RequestBuilder::isJson).findFirst();
        if (json.isPresent()) {
            return json.map(RequestBuilder::exactly);
        }
        return offered.stream().filter(RequestBuilder::isForm).findFirst();
    }

    /** Whether a body of this media type is written as the fields of a web form. */
    static boolean isForm(String mediaType) {
        return FORM.equals(mediaType);
    }

    private static boolean isJson(String mediaType) {
        return "application/json".equals(mediaType) || mediaType.endsWith("+json")
                || isARangeIncludingJson(mediaType);
    }

    private static boolean isARangeIncludingJson(String mediaType) {
        return "*/*".equals(mediaType) || "application/*".equals(mediaType);
    }

    /** A range is a promise to accept several things; one of them has to be named on the wire. */
    private static String exactly(String mediaType) {
        return isARangeIncludingJson(mediaType) ? "application/json" : mediaType;
    }

    /**
     * One chosen body, written out as the bytes that travel.
     *
     * <p>A form body is a query string that happens to be in the body rather than in the URL, so it
     * is written by the same rules and with the same limits - including what happens to a value with
     * something nested inside it, which no form encoding agrees on.
     */
    private static Payload written(BodyValue body) {
        String text = isForm(body.mediaType()) ? asForm(body.value()) : JsonText.write(body.value());
        return Payload.of(text.getBytes(StandardCharsets.UTF_8), body.mediaType());
    }

    private static String asForm(JsonValue value) {
        if (!(value instanceof JsonValue.JsonObject object)) {
            // The fields of a form are the members of an object; there is nothing else to name them
            // after. Refused here rather than written as something unnamed, which no API reads.
            throw new IllegalArgumentException("a web form is written as the fields of an object, "
                    + "and this body is not one");
        }
        StringJoiner written = new StringJoiner("&");
        object.members().forEach((name, held) -> pairs(name, held, true).forEach(written::add));
        return written.toString();
    }

    /**
     * Whether a request carrying this value in this place could be assembled at all.
     *
     * <p>Two values cannot be sent, whatever anybody thinks of them. One that writes out as nothing
     * closes a gap in the path rather than filling it, so {@code /owners/{ownerId}} becomes a
     * request for every owner. One carrying a character a header cannot hold - a line break, which
     * ends that header and starts another; a control character; anything outside plain ASCII, which
     * is all HTTP allows a header to be written in - is refused by the engine before it is sent.
     * Either way the whole attempt is thrown away when it is assembled, and an operation whose every
     * attempt is thrown away sends nothing for as long as the run lasts while still being counted
     * among the operations being tested.
     *
     * <p>Whoever is choosing a value can avoid that only by asking the same question this class
     * asks, of the same code, so this answers it by writing the value out rather than by looking at
     * its shape. The two do not agree: a list of one empty word is a list with something in it and
     * writes out as nothing, and a line break may sit inside one member of an object. Whether the
     * value is spread out or written as one piece changes neither answer, so it is asked as though
     * it were written as one piece.
     *
     * @param value the value being considered
     * @param location where in the request it would go
     * @return whether a request could be assembled with it
     */
    static boolean canBeSentFrom(JsonValue value, ParameterLocation location) {
        String written = joined(value, false, ",");
        return switch (location) {
            case PATH -> !written.isEmpty();
            case HEADER -> !cannotTravelInAHeader(written);
            // Both are percent-encoded on the way out, so nothing in them can end anything early.
            case QUERY, COOKIE -> true;
            // A body carries whatever JSON can carry, which is everything this model can express.
            case BODY -> true;
        };
    }

    /** One value written as a single string, which is what a path, a header and a cookie need. */
    private static String joined(JsonValue value, Parameter parameter, String separator) {
        return joined(value, parameter.explode(), separator);
    }

    private static String joined(JsonValue value, boolean explode, String separator) {
        return switch (value) {
            case JsonValue.JsonArray array -> array.elements().stream()
                    .map(RequestBuilder::scalar)
                    .reduce((a, b) -> a + separator + b)
                    .orElse("");
            case JsonValue.JsonObject object -> {
                StringJoiner written = new StringJoiner(separator);
                String pairing = explode ? "=" : separator;
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

    /**
     * The address the API lives at, checked and tidied.
     *
     * <p>A base address carrying a query string of its own - an API key, most often - cannot simply
     * have a path stuck on the end of it: the result would put the path inside the query and address
     * nothing. Refused here, where it can be explained, rather than producing requests that all fail
     * for a reason nobody can see.
     */
    private static String base(String baseUrl) {
        String trimmed = baseUrl.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("a request needs the address the API lives at");
        }
        if (trimmed.indexOf('?') >= 0 || trimmed.indexOf('#') >= 0) {
            throw new IllegalArgumentException("the address '" + baseUrl + "' carries a query "
                    + "string or a fragment of its own, and a path cannot be added to the end of "
                    + "it; give the address up to the path only");
        }
        int end = trimmed.length();
        while (end > 0 && trimmed.charAt(end - 1) == '/') {
            end--;
        }
        return trimmed.substring(0, end);
    }

    /**
     * Refuses a header value that no request could carry.
     *
     * <p>A line break inside a header value ends that header as far as HTTP is concerned and starts
     * whatever follows as another one, so the request that went out would not be the request that
     * was recorded. Anything else outside plain ASCII - a control character, an emoji, a word in a
     * script other than Latin - is refused by the engine, and the request is never sent at all. Both
     * are caught here, where the same rule decides whether a value is worth choosing in the first
     * place, so that a run does not spend its time on requests that cannot leave the machine.
     */
    private static String headerValue(String name, String value) {
        if (cannotTravelInAHeader(value)) {
            throw new IllegalArgumentException("the value for the header '" + name + "' contains a "
                    + "character no header can carry - a line break, a control character or "
                    + "anything outside plain ASCII");
        }
        return value;
    }

    /**
     * Whether this text, written as a header value, could not be sent.
     *
     * <p>The rule is the one the engine applies: a header value is made of printable ASCII, the
     * space and the tab. A line break would split the request in two; everything else outside that
     * range is refused before the request leaves, so choosing such a value buys nothing but a
     * request that is thrown away.
     */
    private static boolean cannotTravelInAHeader(String value) {
        return value.chars().anyMatch(c -> (c < 0x20 && c != '\t') || c > 0x7e);
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

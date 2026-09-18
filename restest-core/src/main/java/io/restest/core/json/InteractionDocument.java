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

import io.restest.core.execution.BodyValue;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionId;
import io.restest.core.execution.InteractionOutcome;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.Payload;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.TestCaseId;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The shape one interaction takes when it is written down, and how it gets back out again.
 *
 * <p>There is deliberately only one of these in the project. A run's stored file and the report
 * written at the end of a run both describe the same attempt, and if each wrote its own version the
 * two would drift, so both come through here. The evidence quoted in a report is therefore the same
 * evidence, byte for byte, as the evidence kept on disk.
 *
 * <p>Everything a run did is stored as a JSON object of this shape - the request as it was sent, the
 * reply as it came back, which test it belonged to and where every value in it came from. Keeping it
 * as JSON rather than spreading it across database columns has two payoffs: the stored run can be
 * read by anything that reads JSON, without RESTest and without knowing its database layout, and a
 * new field in the model does not mean a new column and a migration for every run ever stored.
 *
 * <p>Bodies get the treatment that makes a stored run readable by a person. A body whose bytes are
 * valid text is stored as that text, so a JSON reply in a stored run looks like a JSON reply. A body
 * that is not - an image, a compressed file, a malformed reply - is stored as base64 instead, so
 * nothing is lost or quietly mangled. The two cases are told apart by which name the body is stored
 * under, so nobody has to guess.
 *
 * <p>Times are written the way the rest of the world writes them: an instant as
 * {@code 2026-09-12T16:00:00Z} and a duration as {@code PT0.403S}. Both read plainly and both come
 * back exactly as they went in.
 */
public final class InteractionDocument {

    /** Keep the whole of every body, however long. */
    private static final long EVERYTHING = Long.MAX_VALUE;

    private InteractionDocument() {
    }

    /**
     * The interaction as the JSON object it is stored as.
     *
     * @param interaction what was tried, sent and came back
     * @return the document
     */
    public static JsonValue of(Interaction interaction) {
        return of(interaction, EVERYTHING);
    }

    /**
     * The same, keeping only the beginning of any body longer than the given number of bytes.
     *
     * <p>For somewhere that has to stay small enough to open. A body kept in part says how long the
     * whole thing was, so nobody mistakes our trimming for the API having sent less than it did.
     *
     * <p>Whatever keeps a run for looking at again later should use the other one and keep bodies
     * whole. Judging a reply against what the specification promised needs the bytes that actually
     * arrived: half a JSON object is not a smaller fact, it is a false one, and anything re-judging a
     * trimmed body would report faults that never happened.
     *
     * @param interaction what was tried, sent and came back
     * @param mostBodyBytes how much of any one body to keep
     * @return the document
     */
    public static JsonValue of(Interaction interaction, long mostBodyBytes) {
        Map<String, JsonValue> document = new LinkedHashMap<>();
        document.put("id", JsonValue.of(interaction.id().value()));
        document.put("sentAt", JsonValue.of(interaction.sentAt().toString()));
        document.put("elapsed", JsonValue.of(interaction.elapsed().toString()));
        document.put("testCase", of(interaction.testCase()));
        document.put("request", of(interaction.request(), mostBodyBytes));
        document.put("outcome", of(interaction.outcome(), mostBodyBytes));
        return JsonValue.object(document);
    }

    /**
     * The interaction a stored document describes.
     *
     * <p>A document that is not one - written by a later version of RESTest, or edited by hand into
     * something impossible - is reported as a stored interaction that could not be read, rather than
     * as whatever low-level complaint the mangled part happened to produce.
     *
     * @param value the stored document
     * @return the interaction it describes
     */
    public static Interaction toInteraction(JsonValue value) {
        JsonValue.JsonObject document = object(value, "the interaction");
        try {
            return new Interaction(
                    InteractionId.of(string(document, "id")),
                    toTestCase(member(document, "testCase")),
                    toRequest(member(document, "request")),
                    toOutcome(member(document, "outcome")),
                    Instant.parse(string(document, "sentAt")),
                    Duration.parse(string(document, "elapsed")));
        } catch (JsonException alreadyExplained) {
            throw alreadyExplained;
        } catch (RuntimeException e) {
            throw new JsonException("A stored interaction could not be read back: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    // --- the test case -------------------------------------------------------------------------

    private static JsonValue of(TestCase testCase) {
        Map<String, JsonValue> document = new LinkedHashMap<>();
        document.put("id", JsonValue.of(testCase.id().value()));
        document.put("operation", JsonValue.of(testCase.operation().value()));
        document.put("parameters", JsonValue.array(
                testCase.parameterValues().stream().map(InteractionDocument::of).toList()));
        testCase.body().ifPresent(body -> document.put("body", of(body)));
        return JsonValue.object(document);
    }

    private static TestCase toTestCase(JsonValue value) {
        JsonValue.JsonObject document = object(value, "the test case");
        List<ParameterValue> parameters = array(document, "parameters").stream()
                .map(InteractionDocument::toParameterValue)
                .toList();
        return new TestCase(
                TestCaseId.of(string(document, "id")),
                OperationId.of(string(document, "operation")),
                parameters,
                document.member("body").map(InteractionDocument::toBodyValue));
    }

    private static JsonValue of(ParameterValue parameter) {
        Map<String, JsonValue> document = new LinkedHashMap<>();
        document.put("name", JsonValue.of(parameter.name()));
        document.put("in", JsonValue.of(parameter.location().name()));
        document.put("value", parameter.value());
        document.put("origin", of(parameter.origin()));
        return JsonValue.object(document);
    }

    private static ParameterValue toParameterValue(JsonValue value) {
        JsonValue.JsonObject document = object(value, "a parameter value");
        return new ParameterValue(
                string(document, "name"),
                ParameterLocation.valueOf(string(document, "in")),
                member(document, "value"),
                toOrigin(member(document, "origin")));
    }

    private static JsonValue of(BodyValue body) {
        Map<String, JsonValue> document = new LinkedHashMap<>();
        document.put("mediaType", JsonValue.of(body.mediaType()));
        document.put("value", body.value());
        document.put("origin", of(body.origin()));
        return JsonValue.object(document);
    }

    private static BodyValue toBodyValue(JsonValue value) {
        JsonValue.JsonObject document = object(value, "the body that was sent");
        return new BodyValue(string(document, "mediaType"), member(document, "value"),
                toOrigin(member(document, "origin")));
    }

    /**
     * Where a value came from: the document said so, something invented it, or it was taken out of an
     * earlier reply. The last of those is what makes a run explainable afterwards - it says which
     * request handed this one the identifier it used.
     *
     * <p>A value the document stated also says which of the things a document states it was read
     * from, under {@code stated}. That member is left out rather than guessed at for a run recorded
     * before RESTest kept the three apart, so an older file still reads.
     */
    private static JsonValue of(ValueOrigin origin) {
        Map<String, JsonValue> document = new LinkedHashMap<>();
        switch (origin) {
            case ValueOrigin.Declared declared -> {
                document.put("kind", JsonValue.of("declared"));
                declared.statement().ifPresent(statement -> document.put("stated",
                        JsonValue.of(statement.name().toLowerCase(Locale.ROOT))));
            }
            case ValueOrigin.Generated generated -> {
                document.put("kind", JsonValue.of("generated"));
                document.put("source", JsonValue.of(generated.source()));
            }
            case ValueOrigin.Derived derived -> {
                document.put("kind", JsonValue.of("derived"));
                document.put("from", JsonValue.of(derived.from().value()));
                document.put("description", JsonValue.of(derived.description()));
            }
        }
        return JsonValue.object(document);
    }

    /** Which of the things a document states a stored value was read from. */
    private static ValueOrigin.Declared.Statement toStatement(String stated) {
        return switch (stated) {
            case "default" -> ValueOrigin.Declared.Statement.DEFAULT;
            case "enumeration" -> ValueOrigin.Declared.Statement.ENUMERATION;
            case "example" -> ValueOrigin.Declared.Statement.EXAMPLE;
            default -> throw new JsonException("A value was stated as '" + stated
                    + "', which is not something this version knows");
        };
    }

    private static ValueOrigin toOrigin(JsonValue value) {
        JsonValue.JsonObject document = object(value, "where a value came from");
        String kind = string(document, "kind");
        return switch (kind) {
            case "declared" -> document.member("stated")
                    .map(stated -> (ValueOrigin) ValueOrigin.declared(
                            toStatement(text(stated, "stated"))))
                    .orElse(ValueOrigin.DECLARED);
            case "generated" -> new ValueOrigin.Generated(string(document, "source"));
            case "derived" -> new ValueOrigin.Derived(
                    InteractionId.of(string(document, "from")), string(document, "description"));
            default -> throw new JsonException(
                    "A value came from '" + kind + "', which is not something this version knows");
        };
    }

    // --- the request and the reply -------------------------------------------------------------

    private static JsonValue of(HttpRequestRecord request, long mostBodyBytes) {
        Map<String, JsonValue> document = new LinkedHashMap<>();
        document.put("method", JsonValue.of(request.method().name()));
        document.put("url", JsonValue.of(request.url()));
        document.put("headers", of(request.headers()));
        request.body().ifPresent(body -> document.put("body", of(body, mostBodyBytes)));
        return JsonValue.object(document);
    }

    private static HttpRequestRecord toRequest(JsonValue value) {
        JsonValue.JsonObject document = object(value, "the request");
        HttpMethod method = HttpMethod.named(string(document, "method"))
                .orElseThrow(() -> new JsonException("A request was recorded with the "
                        + "method '" + string(document, "method") + "', which is not one this "
                        + "version knows"));
        return new HttpRequestRecord(method, string(document, "url"),
                toHeaders(document), document.member("body").map(InteractionDocument::toPayload));
    }

    private static JsonValue of(InteractionOutcome outcome, long mostBodyBytes) {
        Map<String, JsonValue> document = new LinkedHashMap<>();
        switch (outcome) {
            case InteractionOutcome.Answered answered -> {
                document.put("kind", JsonValue.of("answered"));
                HttpResponseRecord response = answered.response();
                document.putAll(of(response.statusLine()));
                document.put("headers", of(response.headers()));
                response.body().ifPresent(body ->
                        document.put("body", of(body, mostBodyBytes)));
            }
            case InteractionOutcome.MalformedResponse malformed -> {
                document.put("kind", JsonValue.of("malformed"));
                document.put("reason", JsonValue.of(malformed.reason()));
                malformed.statusLine().ifPresent(line -> document.putAll(of(line)));
                document.put("headers", of(malformed.headers()));
                malformed.partial().ifPresent(body ->
                        document.put("body", of(body, mostBodyBytes)));
            }
            case InteractionOutcome.TransportFailure failure -> {
                document.put("kind", JsonValue.of("failed"));
                document.put("reason", JsonValue.of(failure.reason()));
            }
        }
        return JsonValue.object(document);
    }

    private static InteractionOutcome toOutcome(JsonValue value) {
        JsonValue.JsonObject document = object(value, "how the attempt ended");
        String kind = string(document, "kind");
        Optional<Payload> body = document.member("body").map(InteractionDocument::toPayload);
        return switch (kind) {
            case "answered" -> new InteractionOutcome.Answered(new HttpResponseRecord(
                    toStatusLine(document).orElseThrow(() -> new JsonException(
                            "An answered interaction was recorded without a status code")),
                    toHeaders(document), body));
            case "malformed" -> new InteractionOutcome.MalformedResponse(
                    string(document, "reason"), toStatusLine(document), toHeaders(document), body);
            case "failed" -> new InteractionOutcome.TransportFailure(string(document, "reason"));
            default -> throw new JsonException(
                    "An attempt ended as '" + kind + "', which is not something this version knows");
        };
    }

    private static Map<String, JsonValue> of(StatusLine statusLine) {
        Map<String, JsonValue> document = new LinkedHashMap<>();
        document.put("status", JsonValue.of(statusLine.statusCode()));
        statusLine.reasonPhrase().ifPresent(phrase ->
                document.put("statusText", JsonValue.of(phrase)));
        statusLine.protocolVersion().ifPresent(protocol ->
                document.put("protocol", JsonValue.of(protocol)));
        return document;
    }

    private static Optional<StatusLine> toStatusLine(JsonValue.JsonObject document) {
        return document.member("status").map(status -> new StatusLine(
                number(status, "status").intValueExact(),
                document.member("statusText").map(text -> text(text, "statusText")),
                document.member("protocol").map(protocol -> text(protocol, "protocol"))));
    }

    private static JsonValue of(List<Header> headers) {
        return JsonValue.array(headers.stream()
                .map(InteractionDocument::of)
                .toList());
    }

    private static JsonValue of(Header header) {
        // Written member by member, not built from a map literal: the order of a small map literal
        // is scrambled differently in every run of the program, and a stored run that came out
        // byte-different every time would be impossible to compare with itself.
        Map<String, JsonValue> document = new LinkedHashMap<>();
        document.put("name", JsonValue.of(header.name()));
        document.put("value", JsonValue.of(header.value()));
        return JsonValue.object(document);
    }

    private static List<Header> toHeaders(JsonValue.JsonObject document) {
        List<Header> headers = new ArrayList<>();
        for (JsonValue header : array(document, "headers")) {
            JsonValue.JsonObject one = object(header, "a header");
            headers.add(Header.of(string(one, "name"), string(one, "value")));
        }
        return headers;
    }

    // --- bodies --------------------------------------------------------------------------------

    private static JsonValue of(Payload payload, long mostBodyBytes) {
        Map<String, JsonValue> document = new LinkedHashMap<>();
        document.put("mediaType", JsonValue.of(payload.mediaType()));
        payload = trimmed(payload, mostBodyBytes);
        byte[] content = payload.content();
        asText(content).ifPresentOrElse(
                text -> document.put("text", JsonValue.of(text)),
                () -> document.put("base64",
                        JsonValue.of(Base64.getEncoder().encodeToString(content))));
        payload.wireLength().ifPresent(length ->
                document.put("wireLength", JsonValue.of(length)));
        return JsonValue.object(document);
    }

    private static Payload toPayload(JsonValue value) {
        JsonValue.JsonObject document = object(value, "a body");
        String mediaType = string(document, "mediaType");
        byte[] content = document.member("text")
                .map(text -> text(text, "text").getBytes(StandardCharsets.UTF_8))
                .orElseGet(() -> Base64.getDecoder().decode(string(document, "base64")));
        return new Payload(content, mediaType, document.member("wireLength")
                .map(length -> number(length, "wireLength").longValueExact()));
    }

    /**
     * The body, kept only as far as allowed.
     *
     * <p>Cut on a whole character rather than in the middle of one, so that what is kept is still
     * text and can be read. A body already shorter than the limit is handed back untouched, so
     * nothing is copied for nothing in the ordinary case.
     */
    private static Payload trimmed(Payload payload, long mostBodyBytes) {
        if (payload.size() <= mostBodyBytes) {
            return payload;
        }
        byte[] content = payload.content();
        int keep = (int) mostBodyBytes;
        // Back off to the start of the character that was cut through: UTF-8 continuation bytes all
        // begin 10, so walking back off them lands on the first byte of that character.
        while (keep > 0 && (content[keep] & 0xC0) == 0x80) {
            keep--;
        }
        return Payload.partial(java.util.Arrays.copyOf(content, keep), payload.mediaType(),
                payload.deliveredLength());
    }

    /**
     * The bytes as text, if they really are text.
     *
     * <p>Strictly decoded on purpose: a decoder that quietly replaces the bytes it does not
     * understand would turn "this reply was not valid text" - which is a finding - into a body full
     * of question marks that came back different from how it went in.
     */
    private static Optional<String> asText(byte[] content) {
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return Optional.of(decoded.toString());
        } catch (CharacterCodingException notText) {
            return Optional.empty();
        }
    }

    // --- reading the document ------------------------------------------------------------------

    private static JsonValue.JsonObject object(JsonValue value, String what) {
        if (value instanceof JsonValue.JsonObject object) {
            return object;
        }
        throw new JsonException(
                "A stored interaction is not shaped as it should be: " + what + " is not an object");
    }

    private static JsonValue member(JsonValue.JsonObject document, String name) {
        return document.member(name).orElseThrow(() -> new JsonException(
                "A stored interaction is missing its '" + name + "'"));
    }

    private static String string(JsonValue.JsonObject document, String name) {
        return text(member(document, name), name);
    }

    private static String text(JsonValue value, String name) {
        if (value instanceof JsonValue.JsonString string) {
            return string.value();
        }
        throw new JsonException("A stored interaction's '" + name + "' is not text");
    }

    private static java.math.BigDecimal number(JsonValue value, String name) {
        if (value instanceof JsonValue.JsonNumber number) {
            return number.value();
        }
        throw new JsonException("A stored interaction's '" + name + "' is not a number");
    }

    private static List<JsonValue> array(JsonValue.JsonObject document, String name) {
        JsonValue value = member(document, name);
        if (value instanceof JsonValue.JsonArray array) {
            return array.elements();
        }
        throw new JsonException("A stored interaction's '" + name + "' is not a list");
    }
}

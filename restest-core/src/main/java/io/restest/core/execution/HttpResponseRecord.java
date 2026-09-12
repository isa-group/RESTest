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
package io.restest.core.execution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A well-formed response, as it was actually received: the status line, headers and body verbatim.
 *
 * <p>This is what {@link InteractionOutcome.Answered} carries. A response that broke HTTP framing
 * before it could be read as one is not this type at all - see
 * {@link InteractionOutcome.MalformedResponse}, which is what {@code Answered} does not have to
 * account for.
 *
 * <p>The status code is kept exactly as received, with no range check. A server sending something
 * outside 100-599 is itself a fault - HTTP-semantics oracles report it from M3.2 - and a model that
 * refused to hold the observation could not report the very thing it was sent to catch.
 *
 * @param statusCode the status code received
 * @param reasonPhrase the reason phrase on the status line, when the protocol carries one - HTTP/2
 *     and HTTP/3 do not
 * @param protocolVersion the protocol version the response was received over, as reported by the
 *     engine - {@code "HTTP/1.1"}, {@code "h2"}
 * @param headers the headers received, in wire order, repeats kept
 * @param body the body received, absent when the response carried none
 */
public record HttpResponseRecord(
        int statusCode,
        Optional<String> reasonPhrase,
        Optional<String> protocolVersion,
        List<Header> headers,
        Optional<Payload> body) {

    public HttpResponseRecord {
        Objects.requireNonNull(reasonPhrase, "reasonPhrase");
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(body, "body");
        headers = List.copyOf(headers);
    }

    /** A response with the given status, nothing else recorded. */
    public static HttpResponseRecord of(int statusCode) {
        return new HttpResponseRecord(statusCode, Optional.empty(), Optional.empty(), List.of(),
                Optional.empty());
    }

    /** Every value received under the given header name, compared case-insensitively, in wire order. */
    public List<String> headerValues(String name) {
        return Headers.valuesOf(headers, name);
    }

    /**
     * The status and the received headers' *names*, never a header's value - see
     * {@link HttpRequestRecord#toString()} for why.
     */
    @Override
    public String toString() {
        List<String> names = headers.stream().map(Header::name).toList();
        return "HttpResponseRecord[" + statusCode + reasonPhrase.map(r -> " " + r).orElse("")
                + ", headers=" + names + ", body=" + body.map(Object::toString).orElse("none")
                + "]";
    }
}

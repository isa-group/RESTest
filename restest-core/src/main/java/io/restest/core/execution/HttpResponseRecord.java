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
 * A well-formed HTTP response, exactly as it was received: the status, headers and body, verbatim.
 *
 * <p>This is what {@link InteractionOutcome.Answered} carries: a response that arrived and could be
 * read as valid HTTP. A response so broken that it could not even be parsed as HTTP is a different
 * type entirely - see {@link InteractionOutcome.MalformedResponse}.
 *
 * <p>The status code is kept exactly as received, with no range check. A server sending a status
 * code outside the normal 100-599 range is itself misbehaving, and that is exactly the kind of fact
 * RESTest exists to catch and report, so this type must be able to hold it rather than reject it.
 *
 * @param statusLine the status code and whatever else of the status line the engine reports
 * @param headers the headers received, in wire order, repeats kept
 * @param body the body received, absent when the response carried none
 */
public record HttpResponseRecord(StatusLine statusLine, List<Header> headers,
        Optional<Payload> body) {

    public HttpResponseRecord {
        Objects.requireNonNull(statusLine, "statusLine");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(body, "body");
        headers = List.copyOf(headers);
    }

    /** A response with the given status, nothing else recorded. */
    public static HttpResponseRecord of(int statusCode) {
        return new HttpResponseRecord(StatusLine.of(statusCode), List.of(), Optional.empty());
    }

    /** The status code received - a shortcut for {@code statusLine().statusCode()}. */
    public int statusCode() {
        return statusLine.statusCode();
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
        return "HttpResponseRecord[" + statusLine.statusCode()
                + statusLine.reasonPhrase().map(r -> " " + r).orElse("") + ", headers=" + names
                + ", body=" + body.map(Object::toString).orElse("none") + "]";
    }
}

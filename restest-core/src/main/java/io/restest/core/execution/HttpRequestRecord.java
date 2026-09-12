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

import io.restest.core.model.HttpMethod;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The request as it actually went out: the method, the resolved URL and the exact headers and body -
 * not the operation's template, the values already substituted in.
 *
 * <p>Represents one HTTP exchange. Where the engine follows a redirect, each hop is its own
 * {@link Interaction} with its own request and response - this record does not try to collapse a
 * chain into one, which would leave no way to say which of the two URLs or which of the two sets of
 * headers actually produced a given response.
 *
 * @param method the HTTP method used
 * @param url the exact URL requested, path parameters substituted and the query string appended
 * @param headers the headers sent, in wire order, repeats kept
 * @param body the body sent, absent when the request carried none
 */
public record HttpRequestRecord(HttpMethod method, String url, List<Header> headers,
        Optional<Payload> body) {

    public HttpRequestRecord {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(body, "body");
        if (url.isBlank()) {
            throw new IllegalArgumentException("a request has a URL");
        }
        headers = List.copyOf(headers);
    }

    /** A request with no headers and no body. */
    public static HttpRequestRecord of(HttpMethod method, String url) {
        return new HttpRequestRecord(method, url, List.of(), Optional.empty());
    }

    /** Every value sent under the given header name, compared case-insensitively, in wire order. */
    public List<String> headerValues(String name) {
        return Headers.valuesOf(headers, name);
    }

    /**
     * The method, the URL, the sent headers' *names* and the body's own summary - never a header's
     * value. A record's generated {@code toString} would print every header verbatim, and a header
     * is exactly where an {@code Authorization} bearer token or an API key (M2.6) is carried; a log
     * line or a report that prints an interaction must not repeat one back.
     *
     * <p>This does not solve secret redaction in general - a credential inside a query string or a
     * request body is not caught by it, and doing that properly needs the request's meaning, not
     * just its shape. That is left to the reporting work in M3.6.
     */
    @Override
    public String toString() {
        List<String> names = headers.stream().map(Header::name).toList();
        return "HttpRequestRecord[" + method + " " + url + ", headers=" + names + ", body="
                + body.map(Object::toString).orElse("none") + "]";
    }
}

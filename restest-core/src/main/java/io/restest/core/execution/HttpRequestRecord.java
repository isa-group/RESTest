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
 * A request exactly as it was actually sent to the API: the method, the final URL, and the exact
 * headers and body - with every value already filled in, not the operation's template.
 *
 * <p>This is the request belonging to whichever {@link Interaction} it is part of. If the HTTP
 * client follows a redirect, it may record each hop as its own interaction, in which case this is
 * that one hop's request, or it may fold a whole redirect chain into a single interaction, in which
 * case this is the first request of the chain. Either way, this record always describes one HTTP
 * request, exactly as it was sent - which is also what letting someone reproduce a request by hand
 * (for example as a {@code curl} command) needs to show.
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
     * The method, the URL, the *names* of the headers that were sent, and a summary of the body -
     * but never a header's value. Headers commonly carry credentials such as an {@code Authorization}
     * token or an API key, so a log line or report that prints an interaction must not repeat one
     * back by accident.
     *
     * <p>This does not protect every kind of secret - a credential placed inside a query string or a
     * request body is not caught by this alone.
     */
    @Override
    public String toString() {
        List<String> names = headers.stream().map(Header::name).toList();
        return "HttpRequestRecord[" + method + " " + url + ", headers=" + names + ", body="
                + body.map(Object::toString).orElse("none") + "]";
    }
}

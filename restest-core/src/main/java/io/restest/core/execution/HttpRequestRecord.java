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
}

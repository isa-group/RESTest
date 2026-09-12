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
 * The response as it was actually received: the status code, headers and body verbatim.
 *
 * <p>The status code is kept exactly as received, with no range check. A server sending something
 * outside 100-599 is itself a fault - HTTP-semantics oracles report it from M3.2 - and a model that
 * refused to hold the observation could not report the very thing it was sent to catch.
 *
 * @param statusCode the status code received
 * @param headers the headers received, in wire order, repeats kept
 * @param body the body received, absent when the response carried none
 */
public record HttpResponseRecord(int statusCode, List<Header> headers, Optional<Payload> body) {

    public HttpResponseRecord {
        Objects.requireNonNull(body, "body");
        headers = List.copyOf(headers);
    }

    /** A response with the given status, no headers and no body. */
    public static HttpResponseRecord of(int statusCode) {
        return new HttpResponseRecord(statusCode, List.of(), Optional.empty());
    }

    /** Every value received under the given header name, compared case-insensitively, in wire order. */
    public List<String> headerValues(String name) {
        return Headers.valuesOf(headers, name);
    }
}

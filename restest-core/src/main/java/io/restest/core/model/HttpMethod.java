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
package io.restest.core.model;

import java.util.Locale;
import java.util.Optional;

/**
 * The HTTP methods (GET, POST, and so on) that an API operation can use.
 *
 * <p>These are the eight methods OpenAPI has always allowed, plus {@code QUERY}, added by OpenAPI
 * 3.2 for requests that carry a body without changing anything on the server. RESTest's current
 * scope does not include OpenAPI 3.2 (too recent for a second parser backend to be worth its
 * upkeep), so no operation actually reaching this model declares {@code QUERY} today; it is modeled
 * anyway so this enum does not need to change the day that scope is revisited.
 */
public enum HttpMethod {
    GET,
    PUT,
    POST,
    DELETE,
    OPTIONS,
    HEAD,
    PATCH,
    TRACE,
    /** Added by OpenAPI 3.2: a safe request that carries a body. Not reachable at the current scope. */
    QUERY;

    /**
     * The method the given name stands for, in any case, or empty if it is not one we know.
     *
     * <p>Empty rather than an exception: a document naming a method we do not know is the parser's
     * to report and skip, not a reason to stop reading the document.
     */
    public static Optional<HttpMethod> named(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String upper = name.trim().toUpperCase(Locale.ROOT);
        for (HttpMethod method : values()) {
            if (method.name().equals(upper)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }
}

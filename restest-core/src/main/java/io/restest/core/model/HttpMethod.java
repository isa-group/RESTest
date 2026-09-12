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
 * The HTTP methods an operation can use.
 *
 * <p>The eight OpenAPI has always allowed, plus {@code QUERY}, which OpenAPI 3.2 adds for
 * safe requests that carry a body. The scope in {@code docs/DESIGN.md} covers every 3.x, so a 3.2
 * document using it must land somewhere rather than being skipped.
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
    /** Added by OpenAPI 3.2: a safe request that carries a body. */
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

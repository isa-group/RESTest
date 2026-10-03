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

import java.util.Objects;

/**
 * One header on the wire, as it was actually sent or received: a name and a value, nothing folded
 * together.
 *
 * <p>Kept as a list rather than a map on {@link HttpRequestRecord} and {@link HttpResponseRecord}:
 * HTTP allows a header to repeat ({@code Set-Cookie} routinely does), which a {@code Map<String,
 * String>} cannot represent without silently keeping only one of them.
 *
 * @param name the header's name, in whatever case it was sent or received with
 * @param value the header's value
 */
public record Header(String name, String value) {

    public Header {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a header has a name");
        }
    }

    /** A header with the given name and value. */
    public static Header of(String name, String value) {
        return new Header(name, value);
    }
}

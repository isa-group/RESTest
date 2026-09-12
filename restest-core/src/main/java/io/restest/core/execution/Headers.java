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

/**
 * Case-insensitive lookup over a list of {@link Header}s, shared by {@link HttpRequestRecord} and
 * {@link HttpResponseRecord}.
 *
 * <p>HTTP header names are case-insensitive, the same fact
 * {@link io.restest.core.model.ResponseModel#header(String)} already accounts for on the declared
 * side; this is its counterpart for what was actually observed on the wire.
 *
 * <p>Package-private: {@code io.restest.core.execution} is exported, but this class is not part of
 * its public surface - plumbing shared between two records in the package, not part of the model.
 */
final class Headers {

    private Headers() {
    }

    /** Every value declared under the given name, compared case-insensitively, in wire order. */
    static List<String> valuesOf(List<Header> headers, String name) {
        Objects.requireNonNull(name, "name");
        return headers.stream()
                .filter(header -> header.name().equalsIgnoreCase(name))
                .map(Header::value)
                .toList();
    }
}

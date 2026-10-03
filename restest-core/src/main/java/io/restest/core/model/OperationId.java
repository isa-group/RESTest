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

import java.util.Objects;

/**
 * What one operation is called, everywhere in the tool.
 *
 * <p>Every test case, every stored interaction, every failure report and every per-operation setting
 * keys on this, so it has to exist for every operation and it has to be stable between runs of the
 * same document. A document's own {@code operationId} satisfies neither condition on its own: it is
 * optional, and a large share of real specifications leave it out.
 *
 * <p>So the parser uses the declared identifier when there is one and {@link #synthesised} otherwise,
 * which yields {@code GET /pets/{petId}} - unique, because a method and a path together identify an
 * operation in OpenAPI, and stable, because it is derived from the document rather than from the
 * order things were read in. A counter would have been shorter and would renumber every operation
 * when one is added, invalidating saved settings and making two runs incomparable.
 *
 * <p>A wrapper rather than a bare {@code String} so that an operation identifier cannot be passed
 * where a path, a tag or a test-case identifier is expected.
 */
public record OperationId(String value) {

    public OperationId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("an operation identifier cannot be blank");
        }
    }

    /** The identifier the document declared. */
    public static OperationId of(String value) {
        return new OperationId(value);
    }

    /** The identifier for an operation whose document declared none: {@code GET /pets/{petId}}. */
    public static OperationId synthesised(HttpMethod method, String path) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        return new OperationId(method.name() + " " + path);
    }

    @Override
    public String toString() {
        return value;
    }
}

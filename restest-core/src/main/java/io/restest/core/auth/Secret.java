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
package io.restest.core.auth;

import java.util.Objects;

/**
 * A value the person running the tool handed over to prove who the requests come from - an API key
 * - together with the text written in its place wherever the run writes anything down.
 *
 * <p>The value is only ever read inside this package, where it is put into the requests on their
 * way out and looked for in everything that comes back. Printing one of these, which a log line or
 * a stack trace does without asking, shows the text written in its place and never the value.
 */
final class Secret {

    private final String value;
    private final String mask;

    Secret(String value, String mask) {
        this.value = Objects.requireNonNull(value, "value");
        this.mask = Objects.requireNonNull(mask, "mask");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("a secret has a value");
        }
        if (mask.isEmpty()) {
            throw new IllegalArgumentException("a secret has a text written in its place");
        }
    }

    /** The value itself, for putting into a request and for looking for in what came back. */
    String value() {
        return value;
    }

    /** What is written wherever the value would otherwise have been. */
    String mask() {
        return mask;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Secret secret && value.equals(secret.value)
                && mask.equals(secret.mask);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, mask);
    }

    /** The text written in the value's place, never the value. */
    @Override
    public String toString() {
        return mask;
    }
}

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
import java.util.UUID;

/**
 * Identity for an {@link Interaction}, stable across storage and re-analysis.
 *
 * <p>This is also the value a {@link ValueOrigin.Derived} points at: a stateful value is read out of
 * a specific interaction's response, and by the time it can be constructed at all that interaction
 * already exists (see {@link ValueOrigin.Derived} for why there is no earlier moment the reference
 * would need to be made). Pointing at this identifier, rather than at the {@link TestCaseId} of the
 * step that produced it, is what keeps the dependency unaffected by anything the engine later does
 * with that test case - a retry, a replay, or nothing at all: this identifies the one interaction
 * whose data was actually used, permanently.
 */
public record InteractionId(String value) {

    public InteractionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("an interaction identifier cannot be blank");
        }
    }

    /**
     * A fresh identifier.
     *
     * <p>Random rather than sequential, for the reason {@link TestCaseId#generate()} gives: two
     * runs must be able to generate identifiers concurrently without colliding.
     */
    public static InteractionId generate() {
        return new InteractionId(UUID.randomUUID().toString());
    }

    /** A specific identifier, for an interaction read back from storage or built in a test. */
    public static InteractionId of(String value) {
        return new InteractionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

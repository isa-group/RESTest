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
 * Identity for a {@link TestCase}, stable across generation, storage and replay.
 *
 * <p>A test case is built once by the generator, sent by the engine, persisted by the store and
 * possibly re-examined by {@code restest recheck} long after the run that produced it ended. All
 * four have to mean the same test case, so identity is a value the test case carries, not the
 * object's own identity in memory or its position in a list - neither survives being written to
 * SQLite and read back.
 *
 * <p>A wrapper rather than a bare {@code String}, for the same reason as {@link
 * io.restest.core.model.OperationId}: it cannot be passed by mistake where an operation identifier
 * or an {@link InteractionId} is expected.
 */
public record TestCaseId(String value) {

    public TestCaseId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("a test case identifier cannot be blank");
        }
    }

    /**
     * A fresh identifier.
     *
     * <p>Random rather than sequential: two runs generating test cases at the same time - whether two
     * separate processes or two threads within one - must not race to hand out the same identifier.
     */
    public static TestCaseId generate() {
        return new TestCaseId(UUID.randomUUID().toString());
    }

    /** A specific identifier, for a test case read back from storage or built in a test. */
    public static TestCaseId of(String value) {
        return new TestCaseId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

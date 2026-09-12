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

import io.restest.core.json.JsonValue;
import io.restest.core.model.ParameterLocation;
import java.util.Objects;

/**
 * The value chosen for one parameter, in one {@link TestCase}.
 *
 * <p>Identified by name and location rather than by holding the {@link io.restest.core.model.Parameter}
 * it fills, for the same reason {@link TestCase} holds an {@link io.restest.core.model.OperationId}
 * rather than an {@link io.restest.core.model.Operation}: a test case has to be buildable, storable
 * and replayable without the model instance that produced it still being in memory.
 *
 * @param name the parameter's name, as the operation declares it
 * @param location where it travels
 * @param value what was chosen
 * @param origin where that value came from
 */
public record ParameterValue(String name, ParameterLocation location, JsonValue value,
        ValueOrigin origin) {

    public ParameterValue {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(origin, "origin");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a parameter value has a name");
        }
    }

    /** The value chosen for a parameter, with its origin. */
    public static ParameterValue of(String name, ParameterLocation location, JsonValue value,
            ValueOrigin origin) {
        return new ParameterValue(name, location, value, origin);
    }
}

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
import java.util.Objects;

/**
 * The request body chosen for a {@link TestCase}, when the operation takes one.
 *
 * <p>{@code mediaType} is kept as chosen, not normalised: it is not a lookup key here the way
 * {@link io.restest.core.model.RequestBodyModel#schemaFor(String)}'s argument is, it is the
 * media type this attempt actually used, and whoever judges it against the declared shape
 * normalises at that point.
 *
 * @param mediaType the media type the body was sent as
 * @param value the body's content, before serialisation
 * @param origin where that content came from
 */
public record BodyValue(String mediaType, JsonValue value, ValueOrigin origin) {

    public BodyValue {
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(origin, "origin");
        if (mediaType.isBlank()) {
            throw new IllegalArgumentException("a body value has a media type");
        }
    }
}

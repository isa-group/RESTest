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
 * The request body chosen for one {@link TestCase}, for an operation that takes one.
 *
 * <p>{@code mediaType} is kept exactly as chosen for this attempt, not adjusted in any way; matching
 * it against what the API's specification declares is a separate step, done elsewhere.
 *
 * <p>{@code origin} records where the body as a whole came from, not a separate origin for each of
 * its fields. A body can well have fields that came from different places at once - for example, an
 * identifier read from an earlier response and a name that was freely generated - but this record
 * cannot yet say which field came from where, only that the body as a whole was, say, partly derived
 * from an earlier response. Recording each field's own origin is a more advanced feature this record
 * does not attempt yet.
 *
 * <p>{@code value} only represents bodies that are plain JSON for now. Bodies sent as web forms or as
 * file uploads are not represented by this type yet either.
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

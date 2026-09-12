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
 * <p>{@code origin} is one {@link ValueOrigin} for the whole body, not one per field. A body whose
 * fields come from several places at once - {@code {"id": <derived from the resource just created>,
 * "name": <generated>}}, the ordinary shape of an update in a CRUD sequence - cannot have that
 * distinction recorded here; the store can say the body as a whole was, say, partly derived, but
 * not which field. Recording per-field provenance inside a JSON document is a real design question
 * - it needs an annotated-value shape this record does not have - and it belongs with M2.5, which
 * builds request-body generation, or with M4 once a generator actually produces such bodies. Nothing
 * here should be read as having solved it.
 *
 * <p>{@code value} is a {@link JsonValue}, which is as far as this increment's scope goes: the form
 * and multipart bodies M2.5 adds are not representable yet, and this record is not where that arrives
 * either - it is extended, or given siblings, once M2.5 defines what those bodies need.
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

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
import java.util.Optional;

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
 * <p>{@code value} is the body's content, which is written out as JSON or as the fields of a web
 * form, depending on the media type, when the request is sent. Almost always that is the whole
 * story. The exception is a body sent on purpose as something no value can be written as - nothing
 * at all, JSON cut off halfway, a list inside a list ten thousand times over, or the right content
 * under the wrong media type - to see what an API does with a body it cannot read. Then {@code
 * sentAs} holds the exact text that travels, and {@code value} the content it was made from, so that
 * both what was sent and what it started as can be read afterwards.
 *
 * @param mediaType the media type the body was sent as
 * @param value the body's content, before serialisation; when {@code sentAs} is present, the content
 *     that text was made from
 * @param origin where that content came from
 * @param sentAs the exact text sent, when it is not {@code value} written out; empty text means a
 *     body of no bytes at all
 */
public record BodyValue(String mediaType, JsonValue value, ValueOrigin origin,
        Optional<String> sentAs) {

    public BodyValue {
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(sentAs, "sentAs");
        if (mediaType.isBlank()) {
            throw new IllegalArgumentException("a body value has a media type");
        }
    }

    /**
     * A body sent as its value written out, which is every body but one broken on purpose.
     *
     * @param mediaType the media type the body was sent as
     * @param value the body's content, before serialisation
     * @param origin where that content came from
     */
    public BodyValue(String mediaType, JsonValue value, ValueOrigin origin) {
        this(mediaType, value, origin, Optional.empty());
    }

    /**
     * The same body, sent as this exact text instead of as its value written out.
     *
     * @param mediaType the media type to send it as
     * @param text what travels; empty for a body of no bytes at all
     * @return the body, which still names the content the text was made from and where that came
     *     from
     */
    public BodyValue withTextSent(String mediaType, String text) {
        return new BodyValue(mediaType, value, origin, Optional.of(Objects.requireNonNull(text,
                "text")));
    }
}

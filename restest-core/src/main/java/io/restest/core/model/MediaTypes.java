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

import io.restest.core.internal.Copies;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Media-type names, compared the way HTTP compares them.
 *
 * <p>{@code application/json}, {@code application/json; charset=utf-8} and {@code APPLICATION/JSON}
 * are one media type as far as RFC 9110 is concerned, and documents in the wild write all three.
 * A plain map lookup would treat them as three, so every key and every query goes through
 * {@link #normalise} first: lower-cased, trimmed, and with the parameters after the semicolon
 * dropped.
 *
 * <p>Parameters are dropped rather than compared because they qualify a payload - the charset it
 * uses, the profile it follows - while what the model keys on is which schema applies. Keeping them
 * would split one schema across two keys.
 *
 * <p>Ranges are resolved rather than matched literally, which matters more than it looks.
 * <code>*&#47;*</code> is what a Swagger 2.0 document's default {@code produces} becomes, and it is
 * everywhere in the corpora: a response declared under it, looked up literally by
 * {@code application/json}, would find nothing and an oracle would silently validate no body at all.
 * So a lookup tries the exact type, then {@code type/*}, then <code>*&#47;*</code>, which is the order of
 * specificity HTTP itself uses.
 */
final class MediaTypes {

    private MediaTypes() {
    }

    /** The type and subtype, lower-cased and trimmed, without parameters. */
    static String normalise(String mediaType) {
        Objects.requireNonNull(mediaType, "mediaType");
        int parameters = mediaType.indexOf(';');
        String bare = parameters < 0 ? mediaType : mediaType.substring(0, parameters);
        return bare.trim().toLowerCase(Locale.ROOT);
    }

    /** What the document declares for the given media type: exact, then {@code type/*}, then <code>*&#47;*</code>. */
    static <T> Optional<T> lookup(Map<String, T> declared, String mediaType) {
        String wanted = normalise(mediaType);
        T exact = declared.get(wanted);
        if (exact != null) {
            return Optional.of(exact);
        }
        int slash = wanted.indexOf('/');
        if (slash > 0) {
            T subtypeRange = declared.get(wanted.substring(0, slash) + "/*");
            if (subtypeRange != null) {
                return Optional.of(subtypeRange);
            }
        }
        return Optional.ofNullable(declared.get("*/*"));
    }

    /**
     * Keys normalised on the way in, so that a document writing {@code application/json;
     * charset=utf-8} and a caller asking for {@code application/json} meet.
     *
     * <p>Two keys that normalise to the same media type are a contradiction in the document - two
     * different shapes claimed for one payload - and are rejected rather than one silently winning.
     */
    static <T> Map<String, T> normaliseKeys(Map<String, T> content, String what) {
        Objects.requireNonNull(content, what);
        Map<String, T> normalised = new LinkedHashMap<>();
        content.forEach((mediaType, value) -> {
            String key = normalise(mediaType);
            T previous = normalised.put(key, value);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "two different shapes are declared for media type " + key);
            }
        });
        return Copies.orderedMap(normalised, what);
    }
}

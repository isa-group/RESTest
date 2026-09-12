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
import io.restest.core.schema.CanonicalSchema;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What an operation says it will return under one status key.
 *
 * <p>The key is OpenAPI's, not an integer: a document may answer for exactly {@code 200}, for the
 * whole {@code 2XX} range, or with {@code default} for everything it has not named. Turning all
 * three into an integer would lose the difference, and the difference is what
 * {@link Operation#responseFor(int)} needs in order to pick the most specific match. That method is
 * the only correct way to ask which response applies to a status code, which is why this record
 * offers no shortcut of its own: a plain "does this one cover 200?" would answer yes for
 * {@code default}, and an oracle built on it would validate a successful body against the error
 * shape whenever a document happened to declare {@code default} first.
 *
 * @param status {@code 200}, {@code 2XX} or {@code default}
 * @param content the shape of the body for each media type, keyed by the normalised media type
 * @param headers the headers the response declares, under the names the document wrote
 * @param description what the document says the response means
 */
public record ResponseModel(
        String status,
        Map<String, CanonicalSchema> content,
        Map<String, HeaderModel> headers,
        Optional<String> description) {

    /** {@code 200}, {@code 2XX} or {@code default}, which is all OpenAPI allows. */
    private static final Pattern STATUS = Pattern.compile("[1-5][0-9]{2}|[1-5]XX|default");

    /** The key for everything the document has not named. */
    public static final String DEFAULT = "default";

    public ResponseModel {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(description, "description");
        status = normaliseStatus(status);
        content = MediaTypes.normaliseKeys(content, "content");
        headers = checkedHeaders(headers);
    }

    /** A response under the given status key with a JSON body of the given shape. */
    public static ResponseModel json(String status, CanonicalSchema schema) {
        return new ResponseModel(status, Map.of("application/json", schema), Map.of(),
                Optional.empty());
    }

    /** A response under the given status key that declares no body. */
    public static ResponseModel empty(String status) {
        return new ResponseModel(status, Map.of(), Map.of(), Optional.empty());
    }

    /**
     * The shape of the body for the given media type.
     *
     * <p>Exact match first, then {@code type/*}, then <code>*&#47;*</code>, whatever case and parameters
     * either was written with.
     */
    public Optional<CanonicalSchema> schemaFor(String mediaType) {
        return MediaTypes.lookup(content, mediaType);
    }

    /**
     * The declared header of that name, compared case-insensitively.
     *
     * <p>HTTP header names are case-insensitive, so a document declaring {@code X-Rate-Limit}
     * describes the {@code x-rate-limit} a server actually sends. Comparing them literally would
     * have the header oracle report a header as missing while it sat in the response.
     */
    public Optional<HeaderModel> header(String name) {
        Objects.requireNonNull(name, "name");
        return headers.entrySet().stream()
                .filter(declared -> declared.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /** The range key a status code falls in: {@code 404} is in {@code 4XX}. */
    static String rangeOf(int statusCode) {
        return (statusCode / 100) + "XX";
    }

    private static String normaliseStatus(String status) {
        String trimmed = status.trim();
        String candidate = DEFAULT.equalsIgnoreCase(trimmed)
                ? DEFAULT
                : trimmed.toUpperCase(Locale.ROOT);
        if (!STATUS.matcher(candidate).matches()) {
            throw new IllegalArgumentException("a response is declared for a status code (200), a "
                    + "range (2XX) or 'default', not for '" + status + "'");
        }
        return candidate;
    }

    /**
     * Header names are kept as the document wrote them, so a report reads as the document does, and
     * two names differing only in case are refused: they are one header on the wire, and keeping
     * both would make {@link #header(String)} answer by declaration order rather than by fact.
     */
    private static Map<String, HeaderModel> checkedHeaders(Map<String, HeaderModel> headers) {
        Objects.requireNonNull(headers, "headers");
        Map<String, String> seen = new LinkedHashMap<>();
        headers.keySet().forEach(name -> {
            String previous = seen.put(name.toLowerCase(Locale.ROOT), name);
            if (previous != null) {
                throw new IllegalArgumentException("the response declares '" + previous + "' and '"
                        + name + "', which are one header: HTTP header names are case-insensitive");
            }
        });
        return Copies.orderedMap(headers, "headers");
    }
}

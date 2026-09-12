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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Exact bytes, with their declared media type.
 *
 * <p>This is what "exact wire capture" (M1.3) and "exact request and response payloads" (this
 * increment's own entry in {@code ROADMAP.md}) mean as a type: a request or response body kept
 * byte-for-byte, not re-derived from a parsed model of it. A schema-conformance oracle (M1.6) needs
 * the bytes actually received, not a JSON tree that normalises away whatever made the response
 * malformed in the first place.
 *
 * <p>{@code truncated} and {@code wireLength} exist because ADR-0006 names "configurable
 * response-body truncation" as the store's (M1.4) answer to storage cost - so a stored payload is
 * not always the whole body, and a record silent about that would have an oracle read a cut-off
 * document, fail to parse it, and report a fault the API never committed. {@code truncated} alone
 * would only say "do not trust this"; a {@code Content-Length} conformance oracle (M3.2) needs the
 * actual wire length to compare against, not merely a warning that one is missing, so the two travel
 * together: {@link #partial(byte[], String, long)} always sets both.
 *
 * <p>This is the first record in {@code restest-core} holding a mutable component - not the case the
 * {@code TODO(M1.1b)} on {@code ArchitectureRules.noStaticMutableState} was left for, since that gap
 * is about {@code static final} fields and this is an instance field, but the same discipline
 * applies: no static analysis of a compact constructor can tell "copies the array" from "keeps the
 * reference", so it is enforced by convention and by the test below, not mechanically. The
 * convention: clone on the way in, clone on the way out, and override {@code equals}/{@code
 * hashCode} by hand - the ones a record generates for an array component compare references, which
 * is silently wrong for two payloads with identical content built from two different arrays.
 *
 * @param content the exact bytes actually retained, defensively copied on construction and on every
 *     read - not necessarily the whole body; see {@link #truncated()}
 * @param mediaType the media type these bytes were declared or observed under, kept as written
 * @param truncated whether {@code content} is less than what was actually on the wire
 * @param wireLength how many bytes were actually on the wire, when known - present whenever
 *     {@code truncated} is true, and possibly present otherwise too, when the source confirmed the
 *     full length independently of what was retained
 */
public record Payload(byte[] content, String mediaType, boolean truncated,
        Optional<Long> wireLength) {

    public Payload {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(wireLength, "wireLength");
        if (mediaType.isBlank()) {
            throw new IllegalArgumentException("a payload has a media type");
        }
        content = content.clone();
    }

    /** A payload of the given bytes and media type, not truncated. */
    public static Payload of(byte[] content, String mediaType) {
        return new Payload(content, mediaType, false, Optional.empty());
    }

    /** An empty payload of the given media type, for a body that was declared but sent empty. */
    public static Payload empty(String mediaType) {
        return new Payload(new byte[0], mediaType, false, Optional.empty());
    }

    /** A payload holding the given text, encoded as UTF-8. */
    public static Payload text(String text, String mediaType) {
        Objects.requireNonNull(text, "text");
        return new Payload(text.getBytes(StandardCharsets.UTF_8), mediaType, false,
                Optional.empty());
    }

    /**
     * A payload holding only a prefix of a body that was larger, with the actual wire length an
     * oracle needs to judge the truncation against.
     */
    public static Payload partial(byte[] retained, String mediaType, long wireLength) {
        return new Payload(retained, mediaType, true, Optional.of(wireLength));
    }

    /**
     * A defensive copy of the exact bytes.
     *
     * <p>A record's generated accessor would hand back the field itself; overridden here so that a
     * caller mutating the array it received cannot reach into this payload or into another one built
     * from the same source array.
     */
    @Override
    public byte[] content() {
        return content.clone();
    }

    /** How many bytes this payload holds - the retained length, not necessarily the wire length. */
    public int size() {
        return content.length;
    }

    /**
     * Equal when the bytes are equal element-by-element, and the media type, truncation flag and
     * wire length match.
     *
     * <p>Overridden because a record's generated {@code equals} compares an array component by
     * reference ({@code Object.equals}), not by content, which would make two payloads built from
     * separately-allocated but identical bytes unequal - silently wrong for a store or a test that
     * expects value semantics.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof Payload payload
                && Arrays.equals(content, payload.content)
                && mediaType.equals(payload.mediaType)
                && truncated == payload.truncated
                && wireLength.equals(payload.wireLength);
    }

    /** Consistent with the overridden {@link #equals(Object)}. */
    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(content), mediaType, truncated, wireLength);
    }

    /**
     * A size, a media type and, when it applies, a note that this is not the whole body - not the
     * bytes themselves, which may be large or binary and are the one thing about this record not fit
     * to print.
     */
    @Override
    public String toString() {
        String size = truncated
                ? content.length + " of " + wireLength.map(String::valueOf).orElse("?") + " bytes"
                : content.length + " bytes";
        return "Payload[" + size + (truncated ? " (truncated)" : "") + ", " + mediaType + "]";
    }
}

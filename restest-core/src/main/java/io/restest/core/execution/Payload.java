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
 * The exact bytes of a request or response body, together with the media type (such as
 * {@code application/json}) they were sent or received under.
 *
 * <p>When RESTest sends a request to an API or reads its response, it keeps the body exactly as it
 * was on the wire, byte for byte - it does not reinterpret it into some parsed form first. This
 * matters because checking whether a response matches what the API's specification promises needs
 * the bytes actually received, not a version that has already been cleaned up and might hide the
 * very problem being looked for.
 *
 * <p>{@code content} and {@code wireLength} always describe the same version of the body - typically
 * already decoded and reassembled by the underlying HTTP client. What matters is that the two are
 * never mixed: a header stating the encoded size cannot be meaningfully compared against a decoded
 * body's length.
 *
 * <p>{@code wireLength} exists because RESTest is meant to be able to keep only part of a very
 * large body, to save space, rather than being forced to keep everything or nothing. When that
 * happens, {@code content} holds less than the API actually sent, and this field records how much
 * was truly delivered - so that a stored body being shorter than expected is never mistaken for the
 * API itself having sent a broken response. That is a separate
 * concern from whether the API's own {@code Content-Length} header matches what it actually sent;
 * that comparison is made using the headers kept alongside this payload, not through this field.
 * {@link #truncated()} answers only "did our own storage cut this short". Bytes that simply stopped
 * arriving, with nothing to compare them against, are recorded as {@code content} with no
 * {@code wireLength} at all: everything that was kept, with no claim about whether more was coming.
 *
 * <p>Code comparing what an API declared against what it delivered should use
 * {@link #deliveredLength()}, not {@link #size()}. {@code size()} is only how many bytes this record
 * itself is holding, which can be smaller than what was truly delivered when storage has trimmed it;
 * comparing that number against a declared length would unfairly blame the API for our own storage
 * choice. {@code deliveredLength()} accounts for that, and is identical to {@code size()} when
 * nothing was trimmed.
 *
 * <p>This is the first place in this module where a value, once built, still contains an array
 * (the raw bytes) that could in principle be changed after the fact from outside. That is guarded
 * against by convention: the bytes are copied on the way in and on the way out, and equality is
 * checked by comparing the bytes themselves rather than by reference - see the overrides below.
 *
 * @param content the exact bytes actually retained, defensively copied on construction and on every
 *     read - not necessarily the whole body; see {@link #wireLength()} and {@link #truncated()}
 * @param mediaType the media type these bytes were declared or observed under, kept as written
 * @param wireLength how many bytes were actually on the wire, when known. Never less than
 *     {@code content.length}: a shorter claim would say bytes were retained that were never sent
 */
public record Payload(byte[] content, String mediaType, Optional<Long> wireLength) {

    /**
     * The media type to declare for a partial or unknown body when nothing said what it was meant
     * to be - RFC 2046's standard name for "unknown binary content," not a guess at a
     * {@code Content-Type} the source may never have sent.
     */
    public static final String UNKNOWN_MEDIA_TYPE = "application/octet-stream";

    public Payload {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(wireLength, "wireLength");
        if (mediaType.isBlank()) {
            throw new IllegalArgumentException("a payload has a media type");
        }
        if (wireLength.isPresent() && wireLength.get() < content.length) {
            throw new IllegalArgumentException("wireLength (" + wireLength.get() + ") cannot be "
                    + "less than the retained content (" + content.length + " bytes)");
        }
        content = content.clone();
    }

    /** A payload of the given bytes and media type, not truncated. */
    public static Payload of(byte[] content, String mediaType) {
        return new Payload(content, mediaType, Optional.empty());
    }

    /** An empty payload of the given media type, for a body that was declared but sent empty. */
    public static Payload empty(String mediaType) {
        return new Payload(new byte[0], mediaType, Optional.empty());
    }

    /** A payload holding the given text, encoded as UTF-8. */
    public static Payload text(String text, String mediaType) {
        Objects.requireNonNull(text, "text");
        return new Payload(text.getBytes(StandardCharsets.UTF_8), mediaType, Optional.empty());
    }

    /**
     * A payload holding only a prefix of a body that was larger, with the wire length an oracle
     * needs to judge the truncation against.
     */
    public static Payload partial(byte[] retained, String mediaType, long wireLength) {
        if (wireLength <= retained.length) {
            throw new IllegalArgumentException("a partial payload's wireLength (" + wireLength
                    + ") must exceed the retained bytes (" + retained.length + "); use of(...) if "
                    + "nothing was actually dropped");
        }
        return new Payload(retained, mediaType, Optional.of(wireLength));
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
     * How many bytes the source actually delivered: {@link #wireLength()} when it is known, the
     * retained length otherwise. This, not {@link #size()}, is what a declared length like
     * {@code Content-Length} should be compared against - {@code size()} understates delivery
     * whenever this payload was itself truncated by our own storage.
     */
    public long deliveredLength() {
        return wireLength.orElse((long) content.length);
    }

    /** Whether {@code content} is less than what was actually on the wire. */
    public boolean truncated() {
        return wireLength.map(length -> length > content.length).orElse(false);
    }

    /**
     * Equal when the bytes are equal element-by-element, and the media type and wire length match.
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
                && wireLength.equals(payload.wireLength);
    }

    /** Consistent with the overridden {@link #equals(Object)}. */
    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(content), mediaType, wireLength);
    }

    /**
     * A size, a media type and, when it applies, a note that this is not the whole body - not the
     * bytes themselves, which may be large or binary and are the one thing about this record not fit
     * to print.
     */
    @Override
    public String toString() {
        String size = truncated()
                ? content.length + " of " + wireLength.orElseThrow() + " bytes"
                : content.length + " bytes";
        return "Payload[" + size + (truncated() ? " (truncated)" : "") + ", " + mediaType + "]";
    }
}

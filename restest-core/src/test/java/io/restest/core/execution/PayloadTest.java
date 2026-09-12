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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PayloadTest {

    @Test
    @DisplayName("changing the array a payload was built from does not change the payload")
    void a_payload_copies_what_it_is_given() {
        byte[] source = {1, 2, 3};

        Payload payload = new Payload(source, "application/octet-stream", Optional.empty());
        source[0] = 99;

        assertThat(payload.content()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("changing the array a payload hands back does not change the payload")
    void a_payload_hands_back_a_copy() {
        Payload payload = new Payload(new byte[] {1, 2, 3}, "application/octet-stream",
                Optional.empty());

        payload.content()[0] = 99;

        assertThat(payload.content()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("two payloads with equal bytes from different arrays are equal")
    void equality_is_by_content_not_by_reference() {
        Payload first = new Payload(new byte[] {1, 2, 3}, "application/json", Optional.empty());
        Payload second = new Payload(new byte[] {1, 2, 3}, "application/json", Optional.empty());

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    @DisplayName("payloads with different content, media type or wire length are not equal")
    void inequality_is_detected_in_every_component() {
        Payload reference = new Payload(new byte[] {1, 2, 3}, "application/json", Optional.empty());

        assertThat(reference).isNotEqualTo(
                new Payload(new byte[] {1, 2, 4}, "application/json", Optional.empty()));
        assertThat(reference).isNotEqualTo(
                new Payload(new byte[] {1, 2, 3}, "text/plain", Optional.empty()));
        assertThat(reference).isNotEqualTo(
                new Payload(new byte[] {1, 2, 3}, "application/json", Optional.of(10L)));
        assertThat(reference).isNotEqualTo("not a payload");
    }

    @Test
    @DisplayName("an empty payload holds zero bytes")
    void an_empty_payload_holds_nothing() {
        Payload empty = Payload.empty("application/json");

        assertThat(empty.content()).isEmpty();
        assertThat(empty.size()).isZero();
        assertThat(empty.truncated()).isFalse();
    }

    @Test
    @DisplayName("a text payload is encoded as UTF-8")
    void a_text_payload_is_utf8() {
        Payload payload = Payload.text("café", "text/plain");

        assertThat(payload.content()).isEqualTo("café".getBytes(StandardCharsets.UTF_8));
        assertThat(payload.size()).isEqualTo("café".getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    @DisplayName("of(), empty() and text() build a payload that is not truncated")
    void factories_build_untruncated_payloads() {
        assertThat(Payload.of(new byte[] {1}, "application/json").truncated()).isFalse();
        assertThat(Payload.empty("application/json").truncated()).isFalse();
        assertThat(Payload.text("x", "text/plain").truncated()).isFalse();
    }

    @Test
    @DisplayName("a partial payload is truncated, and carries the wire length an oracle needs")
    void a_partial_payload_says_so() {
        Payload partial = Payload.partial(new byte[] {1, 2, 3}, "application/json", 10L);

        assertThat(partial.truncated()).isTrue();
        assertThat(partial.wireLength()).contains(10L);
        assertThat(partial).hasToString("Payload[3 of 10 bytes (truncated), application/json]");
    }

    @Test
    @DisplayName("a partial payload's wire length must actually exceed what was retained")
    void a_partial_payload_must_be_genuinely_shorter() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Payload.partial(new byte[] {1, 2, 3}, "application/json", 3L))
                .withMessageContaining("must exceed the retained bytes");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Payload.partial(new byte[] {1, 2, 3}, "application/json", 2L));
    }

    @Test
    @DisplayName("a wire length shorter than the retained bytes is refused by the canonical constructor too")
    void a_contradictory_wire_length_is_refused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Payload(new byte[] {1, 2, 3}, "application/json",
                        Optional.of(2L)))
                .withMessageContaining("cannot be less than the retained content");
    }

    @Test
    @DisplayName("a wire length equal to the retained bytes is accepted, and is not truncated")
    void a_confirmed_complete_length_is_not_truncated() {
        Payload confirmed = new Payload(new byte[] {1, 2, 3}, "application/json",
                Optional.of(3L));

        assertThat(confirmed.truncated()).isFalse();
        assertThat(confirmed.wireLength()).contains(3L);
    }

    @Test
    @DisplayName("toString prints a size and a media type, not the bytes themselves")
    void to_string_does_not_dump_the_bytes() {
        Payload payload = new Payload(new byte[] {1, 2, 3}, "application/json", Optional.empty());

        assertThat(payload).hasToString("Payload[3 bytes, application/json]");
    }

    @Test
    @DisplayName("a payload must declare a media type")
    void a_media_type_is_required() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Payload(new byte[0], null, Optional.empty()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Payload(new byte[0], " ", Optional.empty()));
    }

    @Test
    @DisplayName("a null wire length is refused rather than treated as absent")
    void a_null_wire_length_is_refused() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Payload(new byte[0], "application/json", null));
    }

    @Test
    @DisplayName("UNKNOWN_MEDIA_TYPE names the convention for bytes nothing declared a type for")
    void unknown_media_type_is_a_named_constant() {
        assertThat(Payload.UNKNOWN_MEDIA_TYPE).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("deliveredLength() is the retained size when nothing else was truncated")
    void delivered_length_defaults_to_the_retained_size() {
        Payload payload = Payload.of(new byte[] {1, 2, 3}, "application/json");

        assertThat(payload.deliveredLength()).isEqualTo(3L);
    }

    @Test
    @DisplayName("deliveredLength(), not size(), stays correct when the store truncated the body")
    void delivered_length_accounts_for_store_truncation() {
        // The API genuinely delivered all 900 bytes it declared via Content-Length - nothing is
        // wrong with this response - but our own store (M1.4) only retained the first 100. size()
        // would understate delivery and make an oracle blame the API for our retention policy;
        // deliveredLength() reports the confirmed original length instead.
        Payload storeTruncated = Payload.partial(new byte[100], Payload.UNKNOWN_MEDIA_TYPE, 900L);

        assertThat(storeTruncated.size()).isEqualTo(100);
        assertThat(storeTruncated.deliveredLength()).isEqualTo(900L);
    }
}

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

        Payload payload = new Payload(source, "application/octet-stream", false, Optional.empty());
        source[0] = 99;

        assertThat(payload.content()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("changing the array a payload hands back does not change the payload")
    void a_payload_hands_back_a_copy() {
        Payload payload = new Payload(new byte[] {1, 2, 3}, "application/octet-stream", false, Optional.empty());

        payload.content()[0] = 99;

        assertThat(payload.content()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("two payloads with equal bytes from different arrays are equal")
    void equality_is_by_content_not_by_reference() {
        Payload first = new Payload(new byte[] {1, 2, 3}, "application/json", false, Optional.empty());
        Payload second = new Payload(new byte[] {1, 2, 3}, "application/json", false, Optional.empty());

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    @DisplayName("payloads with different content, or a different media type, are not equal")
    void inequality_is_detected_in_both_components() {
        Payload reference = new Payload(new byte[] {1, 2, 3}, "application/json", false,
                Optional.empty());

        assertThat(reference).isNotEqualTo(new Payload(new byte[] {1, 2, 4}, "application/json",
                false, Optional.empty()));
        assertThat(reference).isNotEqualTo(new Payload(new byte[] {1, 2, 3}, "text/plain", false,
                Optional.empty()));
        assertThat(reference).isNotEqualTo(new Payload(new byte[] {1, 2, 3}, "application/json",
                true, Optional.of(10L)));
        assertThat(reference).isNotEqualTo(new Payload(new byte[] {1, 2, 3}, "application/json",
                false, Optional.of(3L)));
        assertThat(reference).isNotEqualTo("not a payload");
    }

    @Test
    @DisplayName("an empty payload holds zero bytes")
    void an_empty_payload_holds_nothing() {
        Payload empty = Payload.empty("application/json");

        assertThat(empty.content()).isEmpty();
        assertThat(empty.size()).isZero();
    }

    @Test
    @DisplayName("a text payload is encoded as UTF-8")
    void a_text_payload_is_utf8() {
        Payload payload = Payload.text("café", "text/plain");

        assertThat(payload.content()).isEqualTo("café".getBytes(StandardCharsets.UTF_8));
        assertThat(payload.size()).isEqualTo("café".getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    @DisplayName("toString prints a size and a media type, not the bytes themselves")
    void to_string_does_not_dump_the_bytes() {
        Payload payload = new Payload(new byte[] {1, 2, 3}, "application/json", false,
                Optional.empty());

        assertThat(payload).hasToString("Payload[3 bytes, application/json]");
    }

    @Test
    @DisplayName("a truncated payload says so, and carries the wire length an oracle needs")
    void a_truncated_payload_says_so() {
        Payload truncated = Payload.partial(new byte[] {1, 2, 3}, "application/json", 10L);

        assertThat(truncated.truncated()).isTrue();
        assertThat(truncated.wireLength()).contains(10L);
        assertThat(truncated).hasToString("Payload[3 of 10 bytes (truncated), application/json]");
    }

    @Test
    @DisplayName("of(), empty() and text() build a payload that is not truncated")
    void factories_build_untruncated_payloads() {
        assertThat(Payload.of(new byte[] {1}, "application/json").truncated()).isFalse();
        assertThat(Payload.empty("application/json").truncated()).isFalse();
        assertThat(Payload.text("x", "text/plain").truncated()).isFalse();
    }

    @Test
    @DisplayName("a payload must declare a media type")
    void a_media_type_is_required() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Payload(new byte[0], null, false, Optional.empty()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Payload(new byte[0], " ", false, Optional.empty()));
    }
}

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

import io.restest.core.json.JsonValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BodyValueTest {

    @Test
    @DisplayName("a body value carries its media type, value and origin")
    void a_body_value_is_a_plain_tuple() {
        BodyValue body = new BodyValue("application/json", JsonValue.of("x"),
                ValueOrigin.DECLARED);

        assertThat(body.mediaType()).isEqualTo("application/json");
        assertThat(body.value()).isEqualTo(JsonValue.of("x"));
        assertThat(body.origin()).isEqualTo(ValueOrigin.DECLARED);
    }

    @Test
    @DisplayName("a body value must have a media type")
    void a_media_type_is_required() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new BodyValue(" ", JsonValue.of("x"), ValueOrigin.DECLARED));
    }
}

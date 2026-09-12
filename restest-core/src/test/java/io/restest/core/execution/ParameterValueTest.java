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
import io.restest.core.model.ParameterLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParameterValueTest {

    @Test
    @DisplayName("a parameter value carries its name, location, value and origin")
    void a_parameter_value_is_a_plain_tuple() {
        ParameterValue value = ParameterValue.of("limit", ParameterLocation.QUERY,
                JsonValue.of(10L), ValueOrigin.DECLARED);

        assertThat(value.name()).isEqualTo("limit");
        assertThat(value.location()).isEqualTo(ParameterLocation.QUERY);
        assertThat(value.value()).isEqualTo(JsonValue.of(10L));
        assertThat(value.origin()).isEqualTo(ValueOrigin.DECLARED);
    }

    @Test
    @DisplayName("a parameter value must have a name")
    void a_name_is_required() {
        assertThatIllegalArgumentException().isThrownBy(() -> ParameterValue.of(" ",
                ParameterLocation.QUERY, JsonValue.of(1L), ValueOrigin.DECLARED));
    }
}

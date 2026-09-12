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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.StringSchema;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParameterTest {

    @Test
    @DisplayName("a path parameter is required even when the document forgot to say so")
    void a_path_parameter_is_always_required() {
        Parameter declaredOptional = Parameter.of("petId", ParameterLocation.PATH, false,
                NumberSchema.of(NumberKind.INTEGER));

        assertThat(declaredOptional.required()).isTrue();
    }

    @Test
    @DisplayName("a query parameter is optional unless the document says otherwise")
    void other_locations_are_taken_at_their_word() {
        assertThat(Parameter.of("limit", ParameterLocation.QUERY, false, StringSchema.of())
                .required()).isFalse();
        assertThat(Parameter.of("limit", ParameterLocation.QUERY, true, StringSchema.of())
                .required()).isTrue();
    }

    @Test
    @DisplayName("a parameter that names no style gets the one the format defines for its location")
    void style_defaults_follow_the_format() {
        assertThat(Parameter.of("petId", ParameterLocation.PATH, true, StringSchema.of()).style())
                .isEqualTo(ParameterStyle.SIMPLE);
        assertThat(Parameter.of("tags", ParameterLocation.QUERY, false, StringSchema.of()).style())
                .isEqualTo(ParameterStyle.FORM);
        assertThat(Parameter.of("X-Trace", ParameterLocation.HEADER, false, StringSchema.of())
                .style()).isEqualTo(ParameterStyle.SIMPLE);
        assertThat(Parameter.of("session", ParameterLocation.COOKIE, false, StringSchema.of())
                .style()).isEqualTo(ParameterStyle.FORM);
    }

    @Test
    @DisplayName("only the form style expands a composite value by default")
    void explode_defaults_follow_the_format() {
        assertThat(Parameter.of("tags", ParameterLocation.QUERY, false, StringSchema.of())
                .explode()).isTrue();
        assertThat(Parameter.of("petId", ParameterLocation.PATH, true, StringSchema.of())
                .explode()).isFalse();
    }

    @Test
    @DisplayName("a style the document chose is kept, not overwritten by the default")
    void a_declared_style_survives() {
        Parameter deepObject = new Parameter("filter", ParameterLocation.QUERY, false,
                ObjectSchema.of(Map.of("status", StringSchema.of())),
                ParameterStyle.DEEP_OBJECT, true, java.util.Optional.empty(),
                java.util.Optional.of("a structured filter"));

        assertThat(deepObject.style()).isEqualTo(ParameterStyle.DEEP_OBJECT);
        assertThat(deepObject.explode()).isTrue();
        assertThat(deepObject.description()).contains("a structured filter");
        assertThat(ParameterStyle.DEEP_OBJECT.explodesByDefault()).isFalse();
    }

    @Test
    @DisplayName("a parameter without a name is refused")
    void a_parameter_has_a_name() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> Parameter.of("  ", ParameterLocation.QUERY, false, StringSchema.of()));
    }

    @Test
    @DisplayName("a parameter declared with a media type instead of a style keeps it")
    void a_content_serialised_parameter_is_representable() {
        Parameter filter = Parameter.ofContent("filter", ParameterLocation.QUERY, false,
                ObjectSchema.of(Map.of("status", StringSchema.of())),
                "APPLICATION/JSON; charset=utf-8");

        assertThat(filter.isContentSerialised()).isTrue();
        assertThat(filter.mediaType())
                .describedAs("normalised like every other media type in the model")
                .contains("application/json");
    }

    @Test
    @DisplayName("a parameter declared with a style is not content-serialised")
    void a_styled_parameter_carries_no_media_type() {
        assertThat(Parameter.of("limit", ParameterLocation.QUERY, false, StringSchema.of())
                .isContentSerialised()).isFalse();
    }
}

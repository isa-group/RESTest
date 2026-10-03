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
package io.restest.core.gen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.restest.core.json.JsonValue;
import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.StringSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The question put to whoever might know a good value for one parameter. */
class ValueRequestTest {

    private static final OperationId OPERATION = OperationId.of("GET /owners/{ownerId}");

    @Test
    @DisplayName("a parameter the document offers no sample for carries none")
    void a_question_without_samples() {
        ValueRequest request =
                ValueRequest.of(OPERATION, "ownerId", ParameterLocation.PATH, StringSchema.of());

        assertThat(request.examples()).isEmpty();
    }

    @Test
    @DisplayName("looking up a shape the document named keeps the parameter's own sample values")
    void samples_follow_the_same_value_into_a_named_shape() {
        ValueRequest request = ValueRequest.of(OPERATION, "owner", ParameterLocation.QUERY,
                StringSchema.of(), List.of(JsonValue.of("Davis")));

        assertThat(request.about(ObjectSchema.of(Map.of())).examples())
                .describedAs("still the same value, so what the document said about it still holds")
                .containsExactly(JsonValue.of("Davis"));
    }

    @Test
    @DisplayName("stepping into one piece of a shape leaves the whole value's sample behind")
    void samples_do_not_follow_into_a_property() {
        ValueRequest request = ValueRequest.of(OPERATION, "owner", ParameterLocation.QUERY,
                ObjectSchema.of(Map.of("firstName", StringSchema.of())),
                List.of(JsonValue.of("Davis")));

        assertThat(request.about("firstName", StringSchema.of()).examples())
                .describedAs("a sample owner is not a sample of the owner's first name")
                .isEmpty();
    }

    @Test
    @DisplayName("a parameter is in the one place it is, so its name and the way down to it are "
            + "the same thing")
    void a_parameter_is_its_own_path() {
        ValueRequest request =
                ValueRequest.of(OPERATION, "ownerId", ParameterLocation.PATH, StringSchema.of());

        assertThat(request.path()).isEqualTo("ownerId");
    }

    @Test
    @DisplayName("stepping into a piece of a value keeps the name of that piece and remembers the "
            + "way down to it")
    void stepping_in_remembers_the_way_down() {
        ValueRequest body = ValueRequest.of(OPERATION, "body", ParameterLocation.BODY,
                ObjectSchema.of(Map.of()));

        ValueRequest email = body.about("owner", ObjectSchema.of(Map.of()))
                .about("email", StringSchema.of());

        assertThat(email.name())
                .describedAs("the name alone is what a list of good e-mail addresses matches on")
                .isEqualTo("email");
        assertThat(email.path())
                .describedAs("the way down is what somebody who means this e-mail and no other "
                        + "writes")
                .isEqualTo("body.owner.email");
    }

    @Test
    @DisplayName("an element of a list is a piece of the list rather than a piece of the value the "
            + "list belongs to, and its path says so")
    void an_element_of_a_list_says_it_is_one() {
        ValueRequest body = ValueRequest.of(OPERATION, "body", ParameterLocation.BODY,
                ObjectSchema.of(Map.of()));

        ValueRequest label = body.about("tags", ObjectSchema.of(Map.of()))
                .aboutAPieceOf(ObjectSchema.of(Map.of()))
                .about("label", StringSchema.of());

        assertThat(label.path())
                .describedAs("there is no one element a value could be meant for, so a path names "
                        + "them all")
                .isEqualTo("body.tags[].label");
    }

    @Test
    @DisplayName("a value is always asked for by the name of a parameter")
    void a_question_needs_a_name() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                ValueRequest.of(OPERATION, " ", ParameterLocation.QUERY, StringSchema.of()));
    }
}

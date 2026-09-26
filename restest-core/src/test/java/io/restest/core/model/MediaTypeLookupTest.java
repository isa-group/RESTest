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

import io.restest.core.json.JsonValue;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.StringSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Media types are compared the way HTTP compares them, in both directions: what the document wrote
 * and what a caller asks for.
 */
class MediaTypeLookupTest {

    private static final CanonicalSchema PET = ObjectSchema.of(Map.of("name", StringSchema.of()));

    @Test
    @DisplayName("a body declared as application/json is found when asked for with a charset")
    void parameters_do_not_hide_a_media_type() {
        RequestBodyModel body = RequestBodyModel.json(PET, true);

        assertThat(body.schemaFor("application/json; charset=utf-8")).contains(PET);
    }

    @Test
    @DisplayName("a body declared in capitals is found when asked for in lower case")
    void case_does_not_hide_a_media_type() {
        RequestBodyModel body = RequestBodyModel.ofShapes(true, Map.of("APPLICATION/JSON", PET));

        assertThat(body.schemaFor("application/json")).contains(PET);
        assertThat(body.mediaTypes()).containsExactly("application/json");
    }

    @Test
    @DisplayName("a body declared under */* is found when asked for by an exact media type")
    void a_wildcard_range_is_resolved() {
        RequestBodyModel body = RequestBodyModel.ofShapes(true, Map.of("*/*", PET));

        assertThat(body.schemaFor("application/json")).contains(PET);
        assertThat(body.schemaFor("text/plain")).contains(PET);
    }

    @Test
    @DisplayName("a subtype range is preferred to the fully wild one, as HTTP prefers it")
    void the_most_specific_range_wins() {
        CanonicalSchema exact = ObjectSchema.of(Map.of("exact", StringSchema.of()));
        Map<String, CanonicalSchema> content = new LinkedHashMap<>();
        content.put("*/*", StringSchema.of());
        content.put("application/*", PET);
        content.put("application/json", exact);
        RequestBodyModel body = RequestBodyModel.ofShapes(true, content);

        assertThat(body.schemaFor("application/json")).contains(exact);
        assertThat(body.schemaFor("application/xml")).contains(PET);
        assertThat(body.schemaFor("text/plain")).contains(StringSchema.of());
    }

    @Test
    @DisplayName("a media type with no subtype resolves to the fully wild range or to nothing")
    void a_malformed_media_type_does_not_throw() {
        RequestBodyModel wild = RequestBodyModel.ofShapes(true, Map.of("*/*", PET));
        RequestBodyModel exact = RequestBodyModel.json(PET, true);

        assertThat(wild.schemaFor("nonsense")).contains(PET);
        assertThat(exact.schemaFor("nonsense")).isEmpty();
    }

    @Test
    @DisplayName("a response declared under a range is resolved the same way")
    void responses_resolve_ranges_too() {
        ResponseModel response = new ResponseModel("200", Map.of("*/*", PET), Map.of(),
                Optional.empty());

        assertThat(response.schemaFor("application/json; charset=utf-8")).contains(PET);
    }

    @Test
    @DisplayName("a body that must be sent but declares no media type could never be sent")
    void an_unsendable_required_body_is_refused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RequestBodyModel.ofShapes(true, Map.of()));

        assertThat(RequestBodyModel.ofShapes(false, Map.of()).mediaTypes()).isEmpty();
    }

    @Test
    @DisplayName("two shapes claimed for one media type are refused rather than one winning silently")
    void a_contradictory_document_is_refused() {
        Map<String, CanonicalSchema> content = new LinkedHashMap<>();
        content.put("application/json", PET);
        content.put("application/json; charset=utf-8", StringSchema.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> RequestBodyModel.ofShapes(true, content))
                .withMessageContaining("application/json");
    }

    @Test
    @DisplayName("a response body is looked up the same way")
    void responses_normalise_too() {
        ResponseModel response = ResponseModel.json("200", PET);

        assertThat(response.schemaFor("Application/JSON")).contains(PET);
        assertThat(response.schemaFor("application/xml")).isEmpty();
    }

    @Test
    @DisplayName("a response claiming two shapes for one media type is refused as well")
    void a_contradictory_response_is_refused() {
        Map<String, CanonicalSchema> content = new LinkedHashMap<>();
        content.put("application/json", PET);
        content.put("APPLICATION/JSON ", StringSchema.of());

        assertThatIllegalArgumentException().isThrownBy(
                () -> new ResponseModel("200", content, Map.of(), Optional.empty()));
    }

    @Test
    @DisplayName("one shape declared under two spellings of one media type is kept, not refused")
    void the_same_shape_twice_is_not_a_contradiction() {
        Map<String, CanonicalSchema> content = new LinkedHashMap<>();
        content.put("application/json", PET);
        content.put("application/json;charset=UTF-8", PET);

        ResponseModel response = new ResponseModel("200", content, Map.of(), Optional.empty());

        assertThat(response.schemaFor("application/json")).contains(PET);
    }

    @Test
    @DisplayName("an operation can be asked whether it must be sent with a body")
    void a_required_body_is_visible_on_the_operation() {
        Operation withBody = Operation.of(HttpMethod.POST, "/pets")
                .withRequestBody(RequestBodyModel.json(PET, true));
        Operation withOptionalBody = Operation.of(HttpMethod.POST, "/pets")
                .withRequestBody(RequestBodyModel.json(PET, false));

        assertThat(withBody.requiresBody()).isTrue();
        assertThat(withOptionalBody.requiresBody()).isFalse();
        assertThat(Operation.of(HttpMethod.GET, "/pets").requiresBody()).isFalse();
    }

    @Test
    @DisplayName("the samples written beside a media type are found with its shape")
    void samples_travel_with_the_shape_they_belong_to() {
        JsonValue sample = JsonValue.object(Map.of("name", JsonValue.of("Bobby")));
        RequestBodyModel body = new RequestBodyModel(true,
                Map.of("application/json", new BodyContent(PET, List.of(sample))),
                Optional.empty());

        assertThat(body.contentFor("application/json; charset=utf-8").orElseThrow().examples())
                .containsExactly(sample);
        assertThat(body.schemaFor("application/json")).contains(PET);
    }

    @Test
    @DisplayName("a shape declared with no sample beside it has none")
    void a_shape_may_stand_alone() {
        assertThat(RequestBodyModel.json(PET, true).contentFor("application/json").orElseThrow()
                .examples()).isEmpty();
    }
}

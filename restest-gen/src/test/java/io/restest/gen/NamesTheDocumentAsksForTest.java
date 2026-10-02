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
package io.restest.gen;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which names a request can ask a value for, worked out from the document.
 *
 * <p>The memory of what an API returned keeps a value only under a name some request asks for, so
 * a name missed here is a value thrown away, and a name found here that nothing asks for is room
 * taken for nothing. These tests are about finding every place a request names, however it is
 * written down.
 */
class NamesTheDocumentAsksForTest {

    @Test
    @DisplayName("a parameter is asked for by its name, and so is anything inside it")
    void parameters_and_what_is_inside_them() {
        NamesTheDocumentAsksFor names = NamesTheDocumentAsksFor.in(anApi(Operation.of(
                HttpMethod.GET, "/pets/{petId}", List.of(
                        Parameter.of("petId", ParameterLocation.PATH, true, StringSchema.of()),
                        Parameter.of("filter", ParameterLocation.QUERY, false,
                                ObjectSchema.of(Map.of("colour", StringSchema.of())))))));

        assertThat(names.answeredBy("petId")).containsExactly("petId");
        assertThat(names.answeredBy("filter")).containsExactly("filter");
        assertThat(names.answeredBy("colour")).containsExactly("colour");
        assertThat(names.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("a body is asked for as a whole, and by every name inside it however deep")
    void the_body_and_everything_in_it() {
        ObjectSchema owner = ObjectSchema.of(Map.of(
                "address", ObjectSchema.of(Map.of("town", StringSchema.of())),
                "pets", ArraySchema.of(ObjectSchema.of(Map.of("petName", StringSchema.of()))),
                "contact", ChoiceSchema.of(List.of(
                        ObjectSchema.of(Map.of("email", StringSchema.of())),
                        ObjectSchema.of(Map.of("phone", StringSchema.of())))),
                "labels", new ObjectSchema(SchemaMetadata.none(), Map.of(), Set.of(),
                        Optional.of(ObjectSchema.of(Map.of("text", StringSchema.of()))),
                        Optional.empty(), Optional.empty())));
        NamesTheDocumentAsksFor names = NamesTheDocumentAsksFor.in(anApi(
                Operation.of(HttpMethod.POST, "/owners")
                        .withRequestBody(RequestBodyModel.json(owner, true))));

        assertThat(List.of("body", "address", "town", "pets", "petName", "contact", "email",
                "phone", "labels", "text"))
                .allSatisfy(name -> assertThat(names.answeredBy(name))
                        .describedAs(name).containsExactly(name));
        assertThat(names.size())
                .describedAs("a member of a map has no name of its own, only what is inside it")
                .isEqualTo(10);
    }

    @Test
    @DisplayName("a shape that contains itself is read once, and the reading ends")
    void a_shape_that_contains_itself() {
        ObjectSchema person = ObjectSchema.of(Map.of(
                "name", StringSchema.of(),
                "parent", SchemaReference.to("Person"),
                "children", ArraySchema.of(SchemaReference.to("Person"))));
        ApiModel model = anApi(Operation.of(HttpMethod.POST, "/people")
                        .withRequestBody(RequestBodyModel.json(SchemaReference.to("Person"), true)))
                .withSchemas(Map.of("Person", person));

        NamesTheDocumentAsksFor names = NamesTheDocumentAsksFor.in(model);

        assertThat(names.answeredBy("parent")).containsExactly("parent");
        assertThat(names.answeredBy("children")).containsExactly("children");
        assertThat(names.answeredBy("name")).containsExactly("name");
        assertThat(names.size()).isEqualTo(4);
    }

    @Test
    @DisplayName("a body on a GET adds nothing, since no such body is ever sent")
    void a_body_never_sent_adds_nothing() {
        NamesTheDocumentAsksFor names = NamesTheDocumentAsksFor.in(anApi(
                Operation.of(HttpMethod.GET, "/airports")
                        .withRequestBody(RequestBodyModel.json(
                                ObjectSchema.of(Map.of("pageSize", StringSchema.of())), true))));

        assertThat(names.answeredBy("body")).isEmpty();
        assertThat(names.answeredBy("pageSize")).isEmpty();
    }

    @Test
    @DisplayName("a name answers only itself, written exactly as the document writes it")
    void a_name_answers_only_itself() {
        NamesTheDocumentAsksFor names = NamesTheDocumentAsksFor.in(anApi(
                Operation.of(HttpMethod.POST, "/user/refresh-token")
                        .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of(
                                "refreshToken", StringSchema.of(),
                                "count", NumberSchema.of(NumberKind.INTEGER))), true))));

        assertThat(names.answeredBy("refreshToken")).containsExactly("refreshToken");
        assertThat(names.answeredBy("RefreshToken")).isEmpty();
        assertThat(names.answeredBy("token")).isEmpty();
        assertThat(names.answeredBy("org.springframework.boot.autoconfigure.data.mongo"
                + ".MongoDataAutoConfiguration")).isEmpty();
    }

    @Test
    @DisplayName("a name pointing at a shape the document never declares ends the reading there")
    void a_shape_that_is_not_there() {
        NamesTheDocumentAsksFor names = NamesTheDocumentAsksFor.in(anApi(
                Operation.of(HttpMethod.POST, "/owners")
                        .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of(
                                "name", StringSchema.of(),
                                "pet", SchemaReference.to("Missing"))), true))));

        assertThat(names.answeredBy("name")).containsExactly("name");
        assertThat(names.answeredBy("pet")).containsExactly("pet");
    }

    @Test
    @DisplayName("a document nested far deeper than any other does not end the run")
    void a_document_built_to_go_down_for_ever() {
        io.restest.core.schema.CanonicalSchema deep = StringSchema.of();
        for (int level = 0; level < 5_000; level++) {
            deep = ObjectSchema.of(Map.of("level" + level, deep));
        }
        NamesTheDocumentAsksFor names = NamesTheDocumentAsksFor.in(anApi(
                Operation.of(HttpMethod.POST, "/deep")
                        .withRequestBody(RequestBodyModel.json(deep, true))));

        assertThat(names.answeredBy("level4999")).containsExactly("level4999");
        assertThat(names.answeredBy("level0"))
                .describedAs("read only so far down")
                .isEmpty();
    }

    @Test
    @DisplayName("a property the API only ever sends back is asked for by no request")
    void a_property_only_ever_returned() {
        ObjectSchema pet = ObjectSchema.of(Map.of(
                "name", StringSchema.of(),
                "id", new StringSchema(SchemaMetadata.none().withAccess(
                        SchemaMetadata.Access.READ_ONLY), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty())));
        NamesTheDocumentAsksFor names = NamesTheDocumentAsksFor.in(anApi(
                Operation.of(HttpMethod.POST, "/pets")
                        .withRequestBody(RequestBodyModel.json(pet, true))));

        assertThat(names.answeredBy("name")).containsExactly("name");
        assertThat(names.answeredBy("id")).isEmpty();
    }

    private static ApiModel anApi(Operation operation) {
        return ApiModel.of("an API", "1", List.of(operation));
    }
}

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
package io.restest.spec;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.json.JsonValue;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.model.SpecificationIssue;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter.StyleEnum;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Operation-level conversion, exercised through hand-built {@code swagger-parser} objects rather
 * than documents, for the same reason as {@link SchemaConverterTest}: fast, and independent of any
 * fixture's exact text.
 */
class OperationConverterTest {

    @Test
    @DisplayName("an operation's own parameter overrides a path-level one of the same name and location")
    void operation_parameters_override_path_level_ones() {
        Schema<Object> pathLevelSchema = new Schema<>();
        pathLevelSchema.setType("string");
        io.swagger.v3.oas.models.parameters.Parameter pathLevel =
                new io.swagger.v3.oas.models.parameters.Parameter()
                        .name("id").in("query").schema(pathLevelSchema);

        Schema<Object> operationLevelSchema = new Schema<>();
        operationLevelSchema.setType("integer");
        io.swagger.v3.oas.models.parameters.Parameter operationLevel =
                new io.swagger.v3.oas.models.parameters.Parameter()
                        .name("id").in("query").schema(operationLevelSchema);

        PathItem pathItem = new PathItem().parameters(List.of(pathLevel));
        pathItem.setGet(new io.swagger.v3.oas.models.Operation()
                .operationId("getWidget").parameters(List.of(operationLevel)));

        OpenAPI api = apiWithPath("/widgets", pathItem);

        Operation operation = onlyOperation(api);

        assertThat(operation.parameters()).hasSize(1);
        Parameter parameter = operation.parameter("id", ParameterLocation.QUERY).orElseThrow();
        assertThat(parameter.schema()).isEqualTo(io.restest.core.schema.NumberSchema.of(
                io.restest.core.schema.NumberKind.INTEGER));
    }

    @Test
    @DisplayName("a duplicate parameter in the same location skips the operation, not the document")
    void a_duplicate_parameter_skips_only_its_operation() {
        io.swagger.v3.oas.models.parameters.Parameter first =
                new io.swagger.v3.oas.models.parameters.Parameter()
                        .name("id").in("query").schema(new Schema<>().type("string"));
        io.swagger.v3.oas.models.parameters.Parameter second =
                new io.swagger.v3.oas.models.parameters.Parameter()
                        .name("id").in("query").schema(new Schema<>().type("string"));

        PathItem pathItem = new PathItem();
        pathItem.setGet(new io.swagger.v3.oas.models.Operation()
                .operationId("getWidget").parameters(List.of(first, second)));

        OpenAPI api = apiWithPath("/widgets", pathItem);
        OperationConverter.Result result = OperationConverter.convert(api, java.util.Map.of());

        assertThat(result.operations()).isEmpty();
        assertThat(result.issues()).hasSize(1);
        SpecificationIssue issue = result.issues().get(0);
        assertThat(issue.skipsAnOperation()).isTrue();
        assertThat(issue.operation()).contains(OperationId.of("getWidget"));
    }

    @Test
    @DisplayName("a path template with nothing to fill one of its variables skips the operation")
    void an_unfillable_template_skips_the_operation() {
        PathItem pathItem = new PathItem();
        pathItem.setGet(new io.swagger.v3.oas.models.Operation().operationId("getWidget"));

        OpenAPI api = apiWithPath("/widgets/{widgetId}", pathItem);
        OperationConverter.Result result = OperationConverter.convert(api, java.util.Map.of());

        assertThat(result.operations()).isEmpty();
        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().get(0).skipsAnOperation()).isTrue();
    }

    @Test
    @DisplayName("a required request body declaring no media type skips the operation")
    void a_required_body_with_no_content_skips_the_operation() {
        PathItem pathItem = new PathItem();
        pathItem.setPost(new io.swagger.v3.oas.models.Operation().operationId("createWidget")
                .requestBody(new RequestBody().required(true)));

        OpenAPI api = apiWithPath("/widgets", pathItem);
        OperationConverter.Result result = OperationConverter.convert(api, java.util.Map.of());

        assertThat(result.operations()).isEmpty();
        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().get(0).skipsAnOperation()).isTrue();
    }

    @Test
    @DisplayName("two operations sharing one operationId keep both operations, the second renamed")
    void a_duplicate_operation_id_is_disambiguated_not_dropped() {
        PathItem widgets = new PathItem();
        widgets.setGet(new io.swagger.v3.oas.models.Operation().operationId("list"));
        PathItem gadgets = new PathItem();
        gadgets.setGet(new io.swagger.v3.oas.models.Operation().operationId("list"));

        Paths paths = new Paths();
        paths.addPathItem("/widgets", widgets);
        paths.addPathItem("/gadgets", gadgets);
        OpenAPI api = new OpenAPI().paths(paths);

        OperationConverter.Result result = OperationConverter.convert(api, java.util.Map.of());

        assertThat(result.operations()).hasSize(2);
        assertThat(result.operations().stream().map(Operation::id).distinct()).hasSize(2);
        assertThat(result.operations().stream().map(Operation::id))
                .contains(OperationId.of("list"));
        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().get(0).effect())
                .isEqualTo(SpecificationIssue.Effect.DEGRADED);
    }

    @Test
    @DisplayName("a media-type-serialised parameter is read by its content, not a style")
    void a_content_serialised_parameter_carries_its_media_type() {
        Schema<Object> filterSchema = new Schema<>();
        filterSchema.setType("object");
        MediaType mediaType = new MediaType().schema(filterSchema);
        Content content = new Content();
        content.addMediaType("application/json", mediaType);
        io.swagger.v3.oas.models.parameters.Parameter parameter =
                new io.swagger.v3.oas.models.parameters.Parameter()
                        .name("filter").in("query").content(content);

        PathItem pathItem = new PathItem();
        pathItem.setGet(new io.swagger.v3.oas.models.Operation()
                .operationId("search").parameters(List.of(parameter)));

        Operation operation = onlyOperation(apiWithPath("/widgets", pathItem));

        Parameter converted = operation.parameter("filter", ParameterLocation.QUERY).orElseThrow();
        assertThat(converted.isContentSerialised()).isTrue();
        assertThat(converted.mediaType()).contains("application/json");
    }

    @Test
    @DisplayName("a declared style is translated to its RESTest equivalent")
    void a_declared_style_is_translated() {
        io.swagger.v3.oas.models.parameters.Parameter parameter =
                new io.swagger.v3.oas.models.parameters.Parameter()
                        .name("tags").in("query").schema(new Schema<>().type("string"))
                        .style(StyleEnum.PIPEDELIMITED);

        PathItem pathItem = new PathItem();
        pathItem.setGet(new io.swagger.v3.oas.models.Operation()
                .operationId("list").parameters(List.of(parameter)));

        Operation operation = onlyOperation(apiWithPath("/widgets", pathItem));

        assertThat(operation.parameter("tags", ParameterLocation.QUERY).orElseThrow().style())
                .isEqualTo(io.restest.core.model.ParameterStyle.PIPE_DELIMITED);
    }

    @Test
    @DisplayName("a parameter declaring no style at all falls back to the location's default")
    void no_declared_style_falls_back_to_the_locations_default() {
        io.swagger.v3.oas.models.parameters.Parameter parameter =
                new io.swagger.v3.oas.models.parameters.Parameter()
                        .name("tags").in("query").schema(new Schema<>().type("string"));

        PathItem pathItem = new PathItem();
        pathItem.setGet(new io.swagger.v3.oas.models.Operation()
                .operationId("list").parameters(List.of(parameter)));

        Operation operation = onlyOperation(apiWithPath("/widgets", pathItem));

        assertThat(operation.parameter("tags", ParameterLocation.QUERY).orElseThrow().style())
                .isEqualTo(io.restest.core.model.ParameterStyle.defaultFor(ParameterLocation.QUERY));
    }

    @Test
    @DisplayName("an operation that fails in a way nobody foresaw costs itself, and says whose fault it is")
    void an_unforeseen_failure_skips_one_operation_and_blames_the_tool() {
        Paths paths = new Paths();
        paths.addPathItem("/fine", new PathItem().get(new io.swagger.v3.oas.models.Operation()
                .operationId("fine")));
        paths.addPathItem("/broken", new PathItem().get(new io.swagger.v3.oas.models.Operation()
                .operationId("broken")
                .addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter()
                        .name("q").in("query").schema(new ExplodesWhenRead()))));
        paths.addPathItem("/alsoFine", new PathItem().get(new io.swagger.v3.oas.models.Operation()
                .operationId("alsoFine")));

        OperationConverter.Result result = OperationConverter.convert(
                new OpenAPI().paths(paths), java.util.Map.of());

        // The two either side are the point. Before the catch was widened, one operation failing in
        // a way this code did not expect escaped to the parser's last-resort handler, which answers
        // with a model holding no operations at all - so a single accident cost every other
        // operation in the document.
        assertThat(result.operations()).extracting(operation -> operation.id().value())
                .containsExactly("fine", "alsoFine");
        assertThat(result.issues()).anySatisfy(issue -> {
            assertThat(issue.effect()).isEqualTo(SpecificationIssue.Effect.OPERATION_SKIPPED);
            assertThat(issue.operation()).isPresent();
            // Worded so that nobody goes looking in the document for a fault that is ours.
            assertThat(issue.message()).contains("fault of the tool");
        });
    }

    @Test
    @DisplayName("a parameter's own sample values are read, in both the spellings a document uses")
    void a_parameters_own_samples_are_read() {
        io.swagger.v3.oas.models.parameters.Parameter single =
                new io.swagger.v3.oas.models.parameters.Parameter()
                        .name("cluster_id").in("path").required(true).example("cluster-1")
                        .schema(new Schema<>().type("string"));
        io.swagger.v3.oas.models.parameters.Parameter named =
                new io.swagger.v3.oas.models.parameters.Parameter()
                        .name("status").in("query")
                        .schema(new Schema<>().type("string"))
                        .examples(new java.util.LinkedHashMap<>(java.util.Map.of()));
        named.getExamples().put("first", new Example().value("sold"));
        named.getExamples().put("second", new Example().value("pending"));

        Operation operation = onlyOperation(apiWithPath("/clusters/{cluster_id}",
                new PathItem().get(new io.swagger.v3.oas.models.Operation()
                        .operationId("getCluster")
                        .parameters(List.of(single, named)))));

        assertThat(operation.parameter("cluster_id", ParameterLocation.PATH).orElseThrow()
                .examples()).containsExactly(JsonValue.of("cluster-1"));
        assertThat(operation.parameter("status", ParameterLocation.QUERY).orElseThrow()
                .examples())
                .describedAs("named samples are offered in the order the document wrote them")
                .containsExactly(JsonValue.of("sold"), JsonValue.of("pending"));
    }

    @Test
    @DisplayName("a named sample kept among the document's reusable pieces is followed")
    void a_sample_declared_once_and_named_is_followed() {
        io.swagger.v3.oas.models.parameters.Parameter parameter =
                new io.swagger.v3.oas.models.parameters.Parameter()
                        .name("status").in("query").schema(new Schema<>().type("string"))
                        .examples(new java.util.LinkedHashMap<>());
        parameter.getExamples().put("theUsualOne",
                new Example().$ref("#/components/examples/Sold"));
        OpenAPI api = apiWithPath("/pets", new PathItem().get(
                new io.swagger.v3.oas.models.Operation().operationId("listPets")
                        .parameters(List.of(parameter))));
        api.setComponents(new io.swagger.v3.oas.models.Components()
                .addExamples("Sold", new Example().value("sold")));

        OperationConverter.Result result = OperationConverter.convert(api, java.util.Map.of());

        assertThat(result.operations().get(0).parameter("status", ParameterLocation.QUERY)
                .orElseThrow().examples()).containsExactly(JsonValue.of("sold"));
    }

    @Test
    @DisplayName("a named sample that points at nothing, or at itself, costs the parameter nothing")
    void a_sample_pointing_nowhere_is_read_without_crashing() {
        io.swagger.v3.oas.models.parameters.Parameter parameter =
                new io.swagger.v3.oas.models.parameters.Parameter()
                        .name("status").in("query").schema(new Schema<>().type("string"))
                        .examples(new java.util.LinkedHashMap<>());
        parameter.getExamples().put("missing",
                new Example().$ref("#/components/examples/NeverDeclared"));
        parameter.getExamples().put("itself", new Example().$ref("#/components/examples/Loop"));
        parameter.getExamples().put("here", new Example().value("sold"));
        OpenAPI api = apiWithPath("/pets", new PathItem().get(
                new io.swagger.v3.oas.models.Operation().operationId("listPets")
                        .parameters(List.of(parameter))));
        api.setComponents(new io.swagger.v3.oas.models.Components()
                .addExamples("Loop", new Example().$ref("#/components/examples/Loop")));

        OperationConverter.Result result = OperationConverter.convert(api, java.util.Map.of());

        assertThat(result.operations().get(0).parameter("status", ParameterLocation.QUERY)
                .orElseThrow().examples())
                .describedAs("a document that points at nothing costs a suggestion, not the run")
                .containsExactly(JsonValue.of("sold"));
    }

    @Test
    @DisplayName("a sample that is only a web address is left alone, and costs the parameter nothing")
    void a_sample_kept_elsewhere_on_the_web_is_not_fetched() {
        io.swagger.v3.oas.models.parameters.Parameter parameter =
                new io.swagger.v3.oas.models.parameters.Parameter()
                        .name("status").in("query").schema(new Schema<>().type("string"))
                        .examples(new java.util.LinkedHashMap<>());
        parameter.getExamples().put("elsewhere",
                new Example().externalValue("https://example.com/sold.json"));
        parameter.getExamples().put("here", new Example().value("sold"));

        Operation operation = onlyOperation(apiWithPath("/pets", new PathItem().get(
                new io.swagger.v3.oas.models.Operation().operationId("listPets")
                        .parameters(List.of(parameter)))));

        assertThat(operation.parameter("status", ParameterLocation.QUERY).orElseThrow().examples())
                .describedAs("reading a document must not depend on the network being there")
                .containsExactly(JsonValue.of("sold"));
    }

    @Test
    @DisplayName("a parameter offering no sample offers none, rather than offering the word null")
    void a_parameter_without_a_sample_offers_none() {
        Operation operation = onlyOperation(apiWithPath("/pets/{id}", new PathItem().get(
                new io.swagger.v3.oas.models.Operation().operationId("getPet")
                        .parameters(List.of(new io.swagger.v3.oas.models.parameters.Parameter()
                                .name("id").in("path").required(true)
                                .schema(new Schema<>().type("string")))))));

        assertThat(operation.parameter("id", ParameterLocation.PATH).orElseThrow().examples())
                .isEmpty();
    }

    /**
     * A schema that throws something other than the kinds this converter expects, standing in for
     * whatever a real document one day does that nothing here thought to guard against.
     */
    private static final class ExplodesWhenRead extends Schema<Object> {

        @Override
        public String getType() {
            throw new IllegalStateException("nobody saw this coming");
        }
    }

    @Test
    @DisplayName("the sample bodies written beside a media type are read, in both spellings")
    void the_samples_a_body_declares_are_read() {
        MediaType json = new MediaType()
                .schema(new Schema<>().type("object"))
                .example(java.util.Map.of("name", "Bobby"));
        MediaType form = new MediaType().schema(new Schema<>().type("object"))
                .examples(new java.util.LinkedHashMap<>());
        form.getExamples().put("first", new Example().value(java.util.Map.of("name", "Alba")));
        form.getExamples().put("second", new Example().value(java.util.Map.of("name", "Nube")));
        Content content = new Content();
        content.addMediaType("application/json", json);
        content.addMediaType("application/x-www-form-urlencoded", form);

        Operation operation = onlyOperation(apiWithPath("/pets",
                new PathItem().post(new io.swagger.v3.oas.models.Operation()
                        .operationId("addPet")
                        .requestBody(new RequestBody().required(true).content(content)))));

        RequestBodyModel body = operation.requestBody().orElseThrow();
        assertThat(body.contentFor("application/json").orElseThrow().examples())
                .containsExactly(JsonValue.object(java.util.Map.of("name", JsonValue.of("Bobby"))));
        assertThat(body.contentFor("application/x-www-form-urlencoded").orElseThrow().examples())
                .describedAs("named samples in the order the document wrote them")
                .containsExactly(JsonValue.object(java.util.Map.of("name", JsonValue.of("Alba"))),
                        JsonValue.object(java.util.Map.of("name", JsonValue.of("Nube"))));
    }

    @Test
    @DisplayName("a sample body kept among the document's reusable pieces is followed")
    void a_sample_body_declared_once_and_named_is_followed() {
        MediaType json = new MediaType().schema(new Schema<>().type("object"))
                .examples(new java.util.LinkedHashMap<>());
        json.getExamples().put("theUsualOne", new Example().$ref("#/components/examples/NewPet"));
        Content content = new Content();
        content.addMediaType("application/json", json);
        OpenAPI api = apiWithPath("/pets", new PathItem().post(
                new io.swagger.v3.oas.models.Operation().operationId("addPet")
                        .requestBody(new RequestBody().required(true).content(content))));
        api.setComponents(new io.swagger.v3.oas.models.Components()
                .addExamples("NewPet", new Example().value(java.util.Map.of("name", "Bobby"))));

        OperationConverter.Result result = OperationConverter.convert(api, java.util.Map.of());

        assertThat(result.operations().get(0).requestBody().orElseThrow()
                .contentFor("application/json").orElseThrow().examples())
                .containsExactly(JsonValue.object(java.util.Map.of("name", JsonValue.of("Bobby"))));
    }

    @Test
    @DisplayName("a sample only a web address points at is left out, and costs the body nothing")
    void a_sample_body_behind_a_web_address_is_left_out() {
        MediaType json = new MediaType().schema(new Schema<>().type("object"))
                .examples(new java.util.LinkedHashMap<>());
        json.getExamples().put("elsewhere",
                new Example().externalValue("https://example.com/pet.json"));
        Content content = new Content();
        content.addMediaType("application/json", json);

        Operation operation = onlyOperation(apiWithPath("/pets",
                new PathItem().post(new io.swagger.v3.oas.models.Operation()
                        .operationId("addPet")
                        .requestBody(new RequestBody().required(true).content(content)))));

        assertThat(operation.requestBody().orElseThrow().contentFor("application/json")
                .orElseThrow().examples()).isEmpty();
    }

    private static OpenAPI apiWithPath(String path, PathItem pathItem) {
        Paths paths = new Paths();
        paths.addPathItem(path, pathItem);
        return new OpenAPI().paths(paths);
    }

    private static Operation onlyOperation(OpenAPI api) {
        OperationConverter.Result result = OperationConverter.convert(api, java.util.Map.of());
        assertThat(result.operations()).hasSize(1);
        assertThat(result.issues()).isEmpty();
        return result.operations().get(0);
    }
}

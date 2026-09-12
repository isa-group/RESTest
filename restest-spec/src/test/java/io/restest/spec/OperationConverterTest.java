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

import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.SpecificationIssue;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
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

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

import io.restest.core.model.HeaderModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.ParameterStyle;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.model.ResponseModel;
import io.restest.core.model.Server;
import io.restest.core.model.ServerVariable;
import io.restest.core.model.SpecificationIssue;
import io.restest.core.schema.CanonicalSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Turns the paths and operations of a {@code swagger-parser} document into {@link Operation}s.
 *
 * <p>An operation that cannot be represented at all - an unfillable path template, a parameter
 * declared twice in the same location, a body the document marks required but describes no shape
 * for - is skipped, never allowed to stop the rest of the document from being read: {@link Operation}
 * and {@link RequestBodyModel} already refuse to construct those shapes by throwing, which this class
 * catches and turns into a {@link SpecificationIssue}. Two operations that declare the same
 * {@code operationId} are not skipped - both stay testable, with the second renamed and the renaming
 * reported, because {@link io.restest.core.model.ApiModel} requires every identifier to be unique.
 *
 * <p>A parameter, request body, response or header may itself be a {@code $ref} to a reusable
 * definition under {@code components}, and that definition may in turn be a {@code $ref} to another
 * one - common for a shared "not found" response or a pagination parameter, and confirmed, the hard
 * way, against real corpus documents that reliably crashed an earlier version of this class.
 * {@link #resolveChain} follows the whole chain, one lookup into {@code components} at a time; a
 * chain that does not resolve, or that loops back on a name already seen, is dropped with a
 * {@link SpecificationIssue#degraded degraded} issue rather than a {@code NullPointerException} or an
 * infinite loop. A document construct this class does not otherwise guard against - a name missing
 * where the format requires one, a server with no URL or an unusable variable - is degraded or
 * skipped the same way, never left to throw past the one operation, server entry or named schema it
 * belongs to.
 */
final class OperationConverter {

    private OperationConverter() {
    }

    /**
     * Every operation and issue this document's paths yield.
     *
     * @param schemas the document's named schemas, already converted - used only to tell whether an
     *     operation's data includes something {@link SchemaConverter} could not fully read, without
     *     resolving each reference a second time
     */
    static Result convert(OpenAPI api, Map<String, CanonicalSchema> schemas) {
        List<Operation> operations = new ArrayList<>();
        List<SpecificationIssue> issues = new ArrayList<>();
        Paths paths = api.getPaths();
        Components components = api.getComponents();
        if (paths != null) {
            paths.forEach((path, pathItem) ->
                    convertPathItem(path, pathItem, components, schemas, operations, issues));
        }
        disambiguateOperationIds(operations, issues);
        return new Result(operations, issues);
    }

    /**
     * The API-level, path-level or operation-level servers a document declares, converted in order.
     * A server declaring no URL at all describes nothing a request could be sent to, and a server
     * variable that is itself malformed (no default value, or a default outside its own enumerated
     * values) is one {@link io.restest.core.model.ServerVariable} refuses to construct; either drops
     * only that one server entry, never the rest of the list or whatever operation was reading it.
     */
    static List<Server> convertServers(List<io.swagger.v3.oas.models.servers.Server> servers) {
        if (servers == null) {
            return List.of();
        }
        List<Server> converted = new ArrayList<>();
        for (io.swagger.v3.oas.models.servers.Server server : servers) {
            if (server == null || server.getUrl() == null || server.getUrl().isBlank()) {
                continue;
            }
            try {
                Map<String, ServerVariable> variables = new LinkedHashMap<>();
                if (server.getVariables() != null) {
                    server.getVariables().forEach((name, variable) -> variables.put(name,
                            new ServerVariable(variable.getDefault(),
                                    variable.getEnum() == null
                                            ? List.of() : List.copyOf(variable.getEnum()),
                                    Optional.ofNullable(variable.getDescription()))));
                }
                converted.add(new Server(server.getUrl(), variables,
                        Optional.ofNullable(server.getDescription())));
            } catch (IllegalArgumentException ignored) {
                // A server this malformed describes nothing a request could be sent to either;
                // dropped the same way one declaring no URL at all already is.
            }
        }
        return converted;
    }

    private static void convertPathItem(String path, PathItem pathItem, Components components,
            Map<String, CanonicalSchema> schemas, List<Operation> operations,
            List<SpecificationIssue> issues) {
        if (pathItem == null) {
            return;
        }
        if (pathItem.get$ref() != null) {
            issues.add(SpecificationIssue.document("paths." + path,
                    "a path item that is itself a reference to another document is not supported yet"));
            return;
        }
        List<io.swagger.v3.oas.models.parameters.Parameter> pathParameters = pathItem.getParameters();
        List<Server> pathServers = convertServers(pathItem.getServers());
        pathItem.readOperationsMap().forEach((method, swaggerOperation) -> {
            String location = "paths." + path + "." + method.name().toLowerCase(Locale.ROOT);
            Optional<HttpMethod> httpMethod = HttpMethod.named(method.name());
            if (httpMethod.isEmpty()) {
                issues.add(SpecificationIssue.skipped(location,
                        OperationId.of(method.name() + " " + path),
                        "an HTTP method RESTest does not recognise: '" + method.name() + "'"));
                return;
            }
            OperationId id = declaredOrSynthesised(swaggerOperation, httpMethod.get(), path);
            try {
                Operation operation = buildOperation(httpMethod.get(), path, id, pathParameters,
                        pathServers, swaggerOperation, components, location, issues);
                operations.add(operation);
                if (hasUnsupportedConstruct(operation, schemas)) {
                    issues.add(SpecificationIssue.degraded(location, id, "this operation's data "
                            + "includes a shape RESTest does not fully understand yet"));
                }
            } catch (IllegalArgumentException e) {
                issues.add(SpecificationIssue.skipped(location, id,
                        "the operation could not be represented: " + message(e)));
            }
        });
    }

    private static Operation buildOperation(HttpMethod method, String path, OperationId id,
            List<io.swagger.v3.oas.models.parameters.Parameter> pathParameters,
            List<Server> pathServers, io.swagger.v3.oas.models.Operation swaggerOperation,
            Components components, String location, List<SpecificationIssue> issues) {
        List<Parameter> parameters = mergeParameters(pathParameters, swaggerOperation.getParameters(),
                components).stream()
                .flatMap(resolved -> convertParameter(resolved, id, location, issues).stream())
                .toList();
        List<Server> operationServers = convertServers(swaggerOperation.getServers());
        List<Server> servers = operationServers.isEmpty() ? pathServers : operationServers;
        Optional<RequestBodyModel> requestBody = convertRequestBody(swaggerOperation.getRequestBody(),
                components, id, location, issues);
        List<ResponseModel> responses = convertResponses(swaggerOperation.getResponses(), components,
                id, location, issues);
        List<String> tags = swaggerOperation.getTags() == null
                ? List.of() : List.copyOf(swaggerOperation.getTags());
        Optional<String> summary = Optional.ofNullable(swaggerOperation.getSummary());
        Optional<String> description = Optional.ofNullable(swaggerOperation.getDescription());
        boolean deprecated = Boolean.TRUE.equals(swaggerOperation.getDeprecated());
        return new Operation(id, method, path, servers, parameters, requestBody, responses, tags,
                summary, description, deprecated);
    }

    /**
     * Path-level parameters with the operation's own layered on top, operation winning when both
     * declare the same name in the same location - the precedence OpenAPI itself defines, regardless
     * of whether either side is itself a {@code $ref} to a shared parameter (a path-level pagination
     * parameter overridden by one operation's own is an ordinary document, not an edge case).
     *
     * <p>Only a path-level parameter can be shadowed this way. Two operation-level parameters
     * resolving to the same name and location are not merged into one here - that is a genuine mistake
     * in the document, not an override, and {@link Operation}'s own constructor is what must see it
     * and refuse to build, so this method has to let both through rather than quietly picking one.
     *
     * <p>Every parameter is resolved here, once, so the override check compares what a parameter
     * actually is rather than how it was spelled.
     */
    private static List<io.swagger.v3.oas.models.parameters.Parameter> mergeParameters(
            List<io.swagger.v3.oas.models.parameters.Parameter> pathParameters,
            List<io.swagger.v3.oas.models.parameters.Parameter> operationParameters,
            Components components) {
        List<io.swagger.v3.oas.models.parameters.Parameter> ownResolved =
                (operationParameters == null ? List.<io.swagger.v3.oas.models.parameters.Parameter>of()
                        : operationParameters).stream()
                        .map(parameter -> resolveParameter(parameter, components))
                        .toList();

        Set<String> overriddenByOperation = new HashSet<>();
        ownResolved.forEach(parameter -> {
            if (parameter != null) {
                overriddenByOperation.add(parameterKey(parameter));
            }
        });

        List<io.swagger.v3.oas.models.parameters.Parameter> merged = new ArrayList<>();
        if (pathParameters != null) {
            for (io.swagger.v3.oas.models.parameters.Parameter raw : pathParameters) {
                io.swagger.v3.oas.models.parameters.Parameter resolved =
                        resolveParameter(raw, components);
                if (resolved != null && overriddenByOperation.contains(parameterKey(resolved))) {
                    continue;
                }
                merged.add(resolved);
            }
        }
        merged.addAll(ownResolved);
        return merged;
    }

    private static String parameterKey(io.swagger.v3.oas.models.parameters.Parameter parameter) {
        return parameter.getIn() + " " + parameter.getName();
    }

    /**
     * The parameter itself, or - if it is a {@code $ref}, however many hops deep - the definition the
     * chain ends at.
     */
    private static io.swagger.v3.oas.models.parameters.Parameter resolveParameter(
            io.swagger.v3.oas.models.parameters.Parameter parameter, Components components) {
        Map<String, io.swagger.v3.oas.models.parameters.Parameter> pool =
                components == null ? null : components.getParameters();
        return resolveChain(parameter, io.swagger.v3.oas.models.parameters.Parameter::get$ref,
                pool);
    }

    /**
     * The converted parameter, or empty if it was a reference that never resolved, or declared no
     * name to convert it under - reported once, here, rather than letting either reach field access,
     * or {@link Parameter}'s own constructor, further down.
     */
    private static Optional<Parameter> convertParameter(
            io.swagger.v3.oas.models.parameters.Parameter parameter, OperationId operationId,
            String location, List<SpecificationIssue> issues) {
        if (parameter == null) {
            issues.add(SpecificationIssue.degraded(location, operationId,
                    "a parameter reference does not resolve to anything the document declares"));
            return Optional.empty();
        }
        if (parameter.getName() == null || parameter.getName().isBlank()) {
            issues.add(SpecificationIssue.degraded(location, operationId,
                    "a parameter declares no name to send it under"));
            return Optional.empty();
        }
        ParameterLocation parameterLocation = parameterLocation(parameter.getIn());
        boolean required = Boolean.TRUE.equals(parameter.getRequired());
        Optional<String> description = Optional.ofNullable(parameter.getDescription());
        if (parameter.getContent() != null && !parameter.getContent().isEmpty()) {
            Map.Entry<String, io.swagger.v3.oas.models.media.MediaType> entry =
                    parameter.getContent().entrySet().iterator().next();
            CanonicalSchema schema = SchemaConverter.convert(entry.getValue().getSchema());
            return Optional.of(new Parameter(parameter.getName(), parameterLocation, required, schema,
                    ParameterStyle.defaultFor(parameterLocation), false, Optional.of(entry.getKey()),
                    description));
        }
        CanonicalSchema schema = SchemaConverter.convert(parameter.getSchema());
        ParameterStyle style = parameterStyle(parameter.getStyle(), parameterLocation);
        boolean explode = parameter.getExplode() != null
                ? parameter.getExplode() : style.explodesByDefault();
        return Optional.of(new Parameter(parameter.getName(), parameterLocation, required, schema,
                style, explode, Optional.empty(), description));
    }

    /**
     * OpenAPI 2.0's {@code in: body} and {@code in: formData} - the two locations {@link
     * ParameterLocation} deliberately has no case for - never reach here: {@code swagger-parser}
     * converts a 2.0 document to a 3.0 one, request body and all, before this class ever sees it
     * (confirmed empirically against the corpus). A location string outside the four this format
     * actually defines, or absent entirely, should not occur; defaulting to {@code QUERY} keeps the
     * parameter testable rather than losing it, on the rare chance it does.
     */
    private static ParameterLocation parameterLocation(String in) {
        return switch (in) {
            case "path" -> ParameterLocation.PATH;
            case "header" -> ParameterLocation.HEADER;
            case "cookie" -> ParameterLocation.COOKIE;
            case null, default -> ParameterLocation.QUERY;
        };
    }

    private static ParameterStyle parameterStyle(
            io.swagger.v3.oas.models.parameters.Parameter.StyleEnum style,
            ParameterLocation location) {
        if (style == null) {
            return ParameterStyle.defaultFor(location);
        }
        return switch (style) {
            case MATRIX -> ParameterStyle.MATRIX;
            case LABEL -> ParameterStyle.LABEL;
            case FORM -> ParameterStyle.FORM;
            case SIMPLE -> ParameterStyle.SIMPLE;
            case SPACEDELIMITED -> ParameterStyle.SPACE_DELIMITED;
            case PIPEDELIMITED -> ParameterStyle.PIPE_DELIMITED;
            case DEEPOBJECT -> ParameterStyle.DEEP_OBJECT;
        };
    }

    private static Optional<RequestBodyModel> convertRequestBody(RequestBody requestBody,
            Components components, OperationId operationId, String location,
            List<SpecificationIssue> issues) {
        if (requestBody == null) {
            return Optional.empty();
        }
        boolean wasReference = requestBody.get$ref() != null;
        RequestBody resolved = resolveRequestBody(requestBody, components);
        if (resolved == null) {
            if (wasReference) {
                issues.add(SpecificationIssue.degraded(location, operationId,
                        "a request body reference does not resolve to anything the document declares"));
            }
            return Optional.empty();
        }
        boolean required = Boolean.TRUE.equals(resolved.getRequired());
        Map<String, CanonicalSchema> content = convertContent(resolved.getContent());
        return Optional.of(new RequestBodyModel(required, content,
                Optional.ofNullable(resolved.getDescription())));
    }

    /**
     * The request body itself, or - if it is a {@code $ref}, however many hops deep - the definition
     * the chain ends at.
     */
    private static RequestBody resolveRequestBody(RequestBody requestBody, Components components) {
        Map<String, RequestBody> pool = components == null ? null : components.getRequestBodies();
        return resolveChain(requestBody, RequestBody::get$ref, pool);
    }

    private static List<ResponseModel> convertResponses(ApiResponses responses, Components components,
            OperationId operationId, String location, List<SpecificationIssue> issues) {
        if (responses == null) {
            return List.of();
        }
        List<ResponseModel> converted = new ArrayList<>();
        responses.forEach((status, response) -> {
            boolean wasReference = response != null && response.get$ref() != null;
            ApiResponse resolved = resolveResponse(response, components);
            if (resolved == null) {
                if (wasReference) {
                    issues.add(SpecificationIssue.degraded(location, operationId, "the response '"
                            + status + "' is a reference that does not resolve to anything the "
                            + "document declares"));
                }
                return;
            }
            Map<String, CanonicalSchema> content = convertContent(resolved.getContent());
            Map<String, HeaderModel> headers = convertHeaders(resolved.getHeaders(), components,
                    operationId, location, issues);
            try {
                converted.add(new ResponseModel(status, content, headers,
                        Optional.ofNullable(resolved.getDescription())));
            } catch (IllegalArgumentException e) {
                // A malformed response - most often an unusable status key - costs only itself: the
                // operation is still testable against whatever it does answer correctly.
                issues.add(SpecificationIssue.degraded(location, operationId,
                        "the response '" + status + "' could not be represented: " + message(e)));
            }
        });
        return converted;
    }

    /**
     * The response itself, or - if it is a {@code $ref}, however many hops deep - the definition the
     * chain ends at.
     */
    private static ApiResponse resolveResponse(ApiResponse response, Components components) {
        Map<String, ApiResponse> pool = components == null ? null : components.getResponses();
        return resolveChain(response, ApiResponse::get$ref, pool);
    }

    private static Map<String, HeaderModel> convertHeaders(Map<String, Header> headers,
            Components components, OperationId operationId, String location,
            List<SpecificationIssue> issues) {
        if (headers == null) {
            return Map.of();
        }
        Map<String, HeaderModel> converted = new LinkedHashMap<>();
        headers.forEach((name, header) -> {
            boolean wasReference = header != null && header.get$ref() != null;
            Header resolved = resolveHeader(header, components);
            if (resolved == null) {
                if (wasReference) {
                    issues.add(SpecificationIssue.degraded(location, operationId, "the header '" + name
                            + "' is a reference that does not resolve to anything the document "
                            + "declares"));
                }
                return;
            }
            converted.put(name, new HeaderModel(Boolean.TRUE.equals(resolved.getRequired()),
                    SchemaConverter.convert(resolved.getSchema()),
                    Optional.ofNullable(resolved.getDescription())));
        });
        return converted;
    }

    /**
     * The header itself, or - if it is a {@code $ref}, however many hops deep - the definition the
     * chain ends at.
     */
    private static Header resolveHeader(Header header, Components components) {
        Map<String, Header> pool = components == null ? null : components.getHeaders();
        return resolveChain(header, Header::get$ref, pool);
    }

    /**
     * Follows a {@code $ref} chain to whatever it ultimately names, one lookup at a time: the given
     * value if it is not a reference at all, {@code null} if any link is missing from {@code pool} or
     * the chain revisits a name already seen (a cycle, which a well-formed document should never
     * contain, but nothing here assumes it does not).
     */
    private static <T> T resolveChain(T value, Function<T, String> ref, Map<String, T> pool) {
        Set<String> visited = new HashSet<>();
        T current = value;
        while (current != null) {
            String reference = ref.apply(current);
            if (reference == null) {
                return current;
            }
            if (!visited.add(reference) || pool == null) {
                return null;
            }
            current = pool.get(refName(reference));
        }
        return null;
    }

    private static Map<String, CanonicalSchema> convertContent(Content content) {
        if (content == null) {
            return Map.of();
        }
        Map<String, CanonicalSchema> converted = new LinkedHashMap<>();
        content.forEach((mediaType, value) -> converted.put(mediaType,
                SchemaConverter.convert(value.getSchema())));
        return converted;
    }

    /** The name a {@code $ref} points at: {@code Pet} for {@code #/components/parameters/Pet}. */
    private static String refName(String ref) {
        int lastSlash = ref.lastIndexOf('/');
        return lastSlash < 0 ? ref : ref.substring(lastSlash + 1);
    }

    private static OperationId declaredOrSynthesised(io.swagger.v3.oas.models.Operation swaggerOperation,
            HttpMethod method, String path) {
        String declared = swaggerOperation.getOperationId();
        return declared != null && !declared.isBlank()
                ? OperationId.of(declared) : OperationId.synthesised(method, path);
    }

    /**
     * Two operations sharing one {@code operationId} is a contradiction {@link
     * io.restest.core.model.ApiModel} refuses to hold; the second (and any further collision) is
     * renamed to its synthesised, always-unique identifier, and the renaming is reported so a reader
     * knows why an operation's identifier does not match what the document declared.
     */
    private static void disambiguateOperationIds(List<Operation> operations,
            List<SpecificationIssue> issues) {
        Set<OperationId> seen = new LinkedHashSet<>();
        for (int i = 0; i < operations.size(); i++) {
            Operation operation = operations.get(i);
            if (seen.add(operation.id())) {
                continue;
            }
            OperationId unique = OperationId.synthesised(operation.method(), operation.path());
            int suffix = 2;
            while (!seen.add(unique)) {
                unique = OperationId.of(operation.method() + " " + operation.path() + " (" + suffix + ")");
                suffix++;
            }
            operations.set(i, operation.withId(unique));
            String location = "paths." + operation.path() + "."
                    + operation.method().name().toLowerCase(Locale.ROOT);
            issues.add(SpecificationIssue.degraded(location, unique, "the document declares this "
                    + "operation's identifier more than once; renamed to stay unique"));
        }
    }

    private static boolean hasUnsupportedConstruct(Operation operation,
            Map<String, CanonicalSchema> schemas) {
        for (Parameter parameter : operation.parameters()) {
            if (SchemaConverter.hasUnsupportedConstruct(parameter.schema(), schemas)) {
                return true;
            }
        }
        if (operation.requestBody().isPresent()
                && operation.requestBody().get().content().values().stream()
                        .anyMatch(schema -> SchemaConverter.hasUnsupportedConstruct(schema, schemas))) {
            return true;
        }
        for (ResponseModel response : operation.responses()) {
            if (response.content().values().stream()
                    .anyMatch(schema -> SchemaConverter.hasUnsupportedConstruct(schema, schemas))) {
                return true;
            }
            for (HeaderModel header : response.headers().values()) {
                if (SchemaConverter.hasUnsupportedConstruct(header.schema(), schemas)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** An exception's own message, or its class name when it declined to leave one. */
    private static String message(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    /** Everything reading a document's paths produces: the operations, and what could not be read. */
    record Result(List<Operation> operations, List<SpecificationIssue> issues) {
    }
}

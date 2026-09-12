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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * One thing an API can be asked to do: a method, a path, what it takes and what it answers.
 *
 * <p>This is the unit everything downstream works in. The scheduler picks one, the generator fills
 * in its parameters and body, the engine sends it, an oracle judges the answer against
 * {@link #responses()}, and the report names it by {@link #id()}.
 *
 * @param id what this operation is called throughout the tool
 * @param method the HTTP method
 * @param path the path template as the document wrote it, {@code /pets/{petId}}, with the braces
 *     still in it. Substitution happens when a request is built, so that the template survives in
 *     reports and a reader can see which operation a request came from
 * @param servers the servers this operation overrides the API's with, empty when it does not.
 *     OpenAPI allows the override on a path item and on an operation, and APIs that serve uploads
 *     or a gateway-fronted subset from another host use it; without somewhere to keep it, every
 *     request for such an operation would go to the wrong origin and nothing would say so
 * @param parameters the inputs, in declaration order
 * @param requestBody what the operation accepts in the body, if anything
 * @param responses what the document says it answers, in declaration order
 * @param tags the document's own grouping, kept because reports group by it
 * @param summary the document's one-line description
 * @param description the document's longer description
 * @param deprecated whether the document marks the operation as on its way out
 */
public record Operation(
        OperationId id,
        HttpMethod method,
        String path,
        List<Server> servers,
        List<Parameter> parameters,
        Optional<RequestBodyModel> requestBody,
        List<ResponseModel> responses,
        List<String> tags,
        Optional<String> summary,
        Optional<String> description,
        boolean deprecated) {

    public Operation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(requestBody, "requestBody");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(description, "description");
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("a path starts with '/': " + path);
        }
        servers = List.copyOf(servers);
        parameters = List.copyOf(parameters);
        responses = List.copyOf(responses);
        tags = List.copyOf(tags);
        rejectDuplicateParameters(parameters);
        rejectUnfillableTemplate(path, parameters);
    }

    /** The variables a path template carries: {@code petId} in {@code /pets/{petId}}. */
    private static final Pattern TEMPLATE_VARIABLE =
            Pattern.compile("\\{([^{}/]+)}");

    /**
     * OpenAPI identifies a parameter by its name and its location, so two with both the same are
     * one parameter declared twice - and since {@code parameters} is a list rather than a map,
     * nothing in the document format prevents it.
     *
     * <p>Refused here for the reason {@link ApiModel} refuses duplicate operation identifiers:
     * {@link #parameter(String, ParameterLocation)} would answer with whichever was declared first,
     * so a generator could send a value of one shape while an oracle judged it against the other,
     * and nothing would say so.
     */
    private static void rejectDuplicateParameters(List<Parameter> parameters) {
        Set<String> seen = new HashSet<>();
        for (Parameter parameter : parameters) {
            if (!seen.add(parameter.location() + " " + parameter.name())) {
                throw new IllegalArgumentException("the parameter '" + parameter.name()
                        + "' is declared twice in " + parameter.location());
            }
        }
    }

    /**
     * A template variable with no parameter to fill it describes a request nobody can assemble:
     * the engine would put {@code /pets/%7BpetId%7D} on the wire, the API would answer 404, and an
     * oracle would report a fault against an API that behaved correctly.
     *
     * <p>The converse is left alone deliberately. A path parameter naming no template variable is
     * junk in the document, but the request can still be assembled and sent, so it is read
     * charitably rather than costing the operation - the same judgement as a path parameter the
     * document forgot to mark required.
     *
     * <p>Path-level parameters, which OpenAPI lets a document declare once for every operation
     * under a path, have to be merged into the operation before it is constructed. That is the
     * parser's job from M1.2, and getting it wrong fails loudly here rather than producing requests
     * with braces in them.
     */
    private static void rejectUnfillableTemplate(String path, List<Parameter> parameters) {
        Set<String> declared = parameters.stream()
                .filter(parameter -> parameter.location() == ParameterLocation.PATH)
                .map(Parameter::name)
                .collect(Collectors.toSet());
        Matcher variables = TEMPLATE_VARIABLE.matcher(path);
        while (variables.find()) {
            String variable = variables.group(1);
            if (!declared.contains(variable)) {
                throw new IllegalArgumentException("the path template '" + path + "' has no "
                        + "parameter to fill '" + variable + "', so no request could be assembled");
            }
        }
    }

    /**
     * An operation with a synthesised identifier, taking no parameters.
     *
     * <p>For a templated path, use {@link #of(HttpMethod, String, List)}: an operation whose
     * template has nothing to fill it cannot be constructed, so there is no order of {@code with…}
     * calls that reaches a valid one from here.
     */
    public static Operation of(HttpMethod method, String path) {
        return of(method, path, List.of());
    }

    /** An operation with a synthesised identifier, taking the given parameters. */
    public static Operation of(HttpMethod method, String path, List<Parameter> parameters) {
        return new Operation(OperationId.synthesised(method, path), method, path, List.of(),
                parameters, Optional.empty(), List.of(), List.of(), Optional.empty(),
                Optional.empty(), false);
    }

    /** The same operation, taking the given parameters. */
    public Operation withParameters(List<Parameter> value) {
        return new Operation(id, method, path, servers, value, requestBody, responses, tags,
                summary, description, deprecated);
    }

    /** The same operation, accepting the given request body. */
    public Operation withRequestBody(RequestBodyModel value) {
        return new Operation(id, method, path, servers, parameters,
                Optional.of(Objects.requireNonNull(value, "value")), responses, tags, summary,
                description, deprecated);
    }

    /** The same operation, answering with the given responses. */
    public Operation withResponses(List<ResponseModel> value) {
        return new Operation(id, method, path, servers, parameters, requestBody, value, tags,
                summary, description, deprecated);
    }

    /** The same operation, served from the given servers rather than the API's. */
    public Operation withServers(List<Server> value) {
        return new Operation(id, method, path, value, parameters, requestBody, responses, tags,
                summary, description, deprecated);
    }

    /** The same operation under the given identifier, as the document declared it. */
    public Operation withId(OperationId value) {
        return new Operation(Objects.requireNonNull(value, "value"), method, path, servers,
                parameters, requestBody, responses, tags, summary, description, deprecated);
    }

    /**
     * What the document says this operation answers with for the given status code.
     *
     * <p>Most specific wins, which is what OpenAPI prescribes: an exact {@code 404} before the
     * {@code 4XX} range, and the range before {@code default}. Getting that order wrong would have an
     * oracle validate a 404 body against the shape declared for every other error, so the precedence
     * lives here rather than being left to each caller to remember.
     *
     * <p>Empty when the document declares nothing that covers the code - itself a finding, and one
     * the status-conformance oracle reports from M3.1.
     */
    public Optional<ResponseModel> responseFor(int statusCode) {
        return firstDeclaring(String.valueOf(statusCode))
                .or(() -> firstDeclaring(ResponseModel.rangeOf(statusCode)))
                .or(() -> firstDeclaring(ResponseModel.DEFAULT));
    }

    /** The parameters that travel in the given location, in declaration order. */
    public List<Parameter> parameters(ParameterLocation location) {
        Objects.requireNonNull(location, "location");
        return parameters.stream().filter(parameter -> parameter.location() == location).toList();
    }

    /** The named parameter in the given location, if the operation takes one. */
    public Optional<Parameter> parameter(String name, ParameterLocation location) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        return parameters.stream()
                .filter(parameter -> parameter.location() == location
                        && parameter.name().equals(name))
                .findFirst();
    }

    /** Whether the operation cannot be sent without a body. */
    public boolean requiresBody() {
        return requestBody.map(RequestBodyModel::required).orElse(false);
    }

    private Optional<ResponseModel> firstDeclaring(String status) {
        return responses.stream().filter(response -> response.status().equals(status)).findFirst();
    }
}

package com.atlassian.oai.validator.interaction;

import com.atlassian.oai.validator.model.ApiOperation;
import com.atlassian.oai.validator.model.NormalisedPath;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.parameter.ParameterValidators;
import com.atlassian.oai.validator.report.MessageResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.schema.SchemaValidator;
import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.MediaType;
import io.swagger.models.Swagger;
import io.swagger.models.auth.ApiKeyAuthDefinition;
import io.swagger.models.auth.In;
import io.swagger.models.auth.SecuritySchemeDefinition;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;

import javax.annotation.Nonnull;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.atlassian.oai.validator.report.ValidationReport.empty;
import static java.util.Objects.requireNonNull;

/**
 * Validate a request against a given API operation.
 */
public class RequestValidator {

    private final SchemaValidator schemaValidator;
    private final ParameterValidators parameterValidators;
    private final MessageResolver messages;
    private final Swagger swaggerDefinition;

    /**
     * Construct a new request validator with the given schema validator.
     *
     * @param schemaValidator The schema validator to use when validating request bodies
     * @param messages The message resolver to use
     */
    public RequestValidator(@Nonnull final SchemaValidator schemaValidator,
                            @Nonnull final MessageResolver messages,
                            @Nonnull final Swagger swaggerDefinition) {
        this.schemaValidator = requireNonNull(schemaValidator, "A schema validator is required");
        this.parameterValidators = new ParameterValidators(schemaValidator, messages);
        this.messages = requireNonNull(messages, "A message resolver is required");
        this.swaggerDefinition = requireNonNull(swaggerDefinition, "A swagger definition required");
    }

    /**
     * Validate the request against the given API operation
     *
     * @param request The request to validate
     * @param apiOperation The operation to validate the request against
     *
     * @return A validation report containing validation errors
     */
    @Nonnull
    public ValidationReport validateRequest(@Nonnull final Request request,
                                            @Nonnull final ApiOperation apiOperation) {
        requireNonNull(request, "A request is required");
        requireNonNull(apiOperation, "An API operation is required");

        return  validateSecurity(request, apiOperation)
                .merge(validateContentType(request, apiOperation))
                .merge(validateAccepts(request, apiOperation))
                .merge(validateHeaders(request, apiOperation))
                .merge(validatePathParameters(apiOperation))
                .merge(validateRequestBody(request.getBody(), apiOperation))
                .merge(validateQueryParameters(request, apiOperation));
    }

    @Nonnull
    private ValidationReport validateSecurity(@Nonnull final Request request,
                                              @Nonnull final ApiOperation apiOperation) {
        final List<Map<String, List<String>>> securityRequired = apiOperation.getOperation().getSecurity();

        if (null != securityRequired && !securityRequired.isEmpty()) {
            final Map<String, SecuritySchemeDefinition> filtered = new HashMap<>();
            for (Map.Entry<String, SecuritySchemeDefinition> s: swaggerDefinition.getSecurityDefinitions().entrySet()) {
                securityRequired.stream().filter(item -> item.containsKey(s.getKey())).forEach(item -> filtered.put(s.getKey(), s.getValue()));
            }

            return filtered.entrySet().stream().map(e -> validateSingleSecurityParameter(request, e.getValue()))
                    .reduce(empty(), ValidationReport::merge);
        }
        return empty();
    }

    @Nonnull
    private ValidationReport validateSingleSecurityParameter(@Nonnull final Request request,
                                                             @Nonnull final SecuritySchemeDefinition securitySchemeDefinition) {
        switch (securitySchemeDefinition.getType()) {
            case "apiKey" :
                final ApiKeyAuthDefinition apiKeyAuthDefinition = (ApiKeyAuthDefinition) securitySchemeDefinition;
                final In in = apiKeyAuthDefinition.getIn();
                switch (in.toValue()) {
                    case "header":
                        return checkApiKeyAuthorizationByHeader(request, apiKeyAuthDefinition);
                    case "query" :
                        return checkApiKeyAuthorizationByQueryParameter(request, apiKeyAuthDefinition);
                    default:
                        return empty();
                }
            default:
                return empty();
        }
    }

    @Nonnull
    private ValidationReport checkApiKeyAuthorizationByQueryParameter(@Nonnull final Request request,
                                                                      @Nonnull final ApiKeyAuthDefinition apiKeyAuthDefinition) {
        final Optional<String> authQueryParam = request.getQueryParameterValues(apiKeyAuthDefinition.getName()).stream().findFirst();
        if (!authQueryParam.isPresent()) {
            return ValidationReport.singleton(messages.get("validation.request.security.missing", request.getMethod(), request.getPath()));
        }
        return empty();
    }

    @Nonnull
    private ValidationReport checkApiKeyAuthorizationByHeader(@Nonnull final Request request,
                                                              @Nonnull final ApiKeyAuthDefinition apiKeyAuthDefinition) {

        if (!request.getHeaderValue(apiKeyAuthDefinition.getName()).isPresent()) {
            return ValidationReport.singleton(
                 messages.get("validation.request.security.missing",
                         request.getMethod(), request.getPath())
            );
        }
        return empty();
    }

    @Nonnull
    private ValidationReport validateContentType(@Nonnull final Request request,
                                                 @Nonnull final ApiOperation apiOperation) {
        return validateMediaTypes(request,
                "Content-Type",
                getConsumes(apiOperation),
                "validation.request.contentType.invalid",
                "validation.request.contentType.notAllowed");
    }

    @Nonnull
    private ValidationReport validateAccepts(@Nonnull final Request request,
                                             @Nonnull final ApiOperation apiOperation) {
        return validateMediaTypes(request,
                "Accept",
                getProduces(apiOperation),
                "validation.request.accept.invalid",
                "validation.request.accept.notAllowed");
    }

    @Nonnull
    private ValidationReport validateMediaTypes(@Nonnull final Request request,
                                                @Nonnull final String headerName,
                                                @Nonnull final Collection<String> specMediaTypes,
                                                @Nonnull final String invalidTypeKey,
                                                @Nonnull final String notAllowedKey) {

        final Collection<String> requestHeaderValues = request.getHeaderValues(headerName);
        if (requestHeaderValues.isEmpty()) {
            return empty();
        }

        final List<MediaType> requestMediaTypes = new ArrayList<>();
        for (final String requestHeaderValue : requestHeaderValues) {
            try {
                requestMediaTypes.add(MediaType.parse(requestHeaderValue));
            } catch (final IllegalArgumentException e) {
                return ValidationReport.singleton(messages.get(invalidTypeKey, requestHeaderValue));
            }
        }

        if (specMediaTypes.isEmpty()) {
            return empty();
        }

        return specMediaTypes
                .stream()
                .map(MediaType::parse)
                .filter(specType ->
                        requestMediaTypes.stream()
                                .anyMatch(requestType ->
                                        specType.withoutParameters().is(requestType.withoutParameters())
                                )
                )
                .findFirst()
                .map(m -> empty())
                .orElse(ValidationReport.singleton(messages.get(notAllowedKey, requestHeaderValues, specMediaTypes)));
    }

    @Nonnull
    private Collection<String> getConsumes(@Nonnull final ApiOperation apiOperation) {
        // Operation-specific 'consumes' overrides global consumes entries
        if (apiOperation.getOperation().getConsumes() == null) {
            return swaggerDefinition.getConsumes() == null ? Collections.emptyList() : swaggerDefinition.getConsumes();
        }
        return apiOperation.getOperation().getConsumes();
    }

    @Nonnull
    private Collection<String> getProduces(@Nonnull final ApiOperation apiOperation) {
        // Operation-specific 'produces' overrides global produces entries
        if (apiOperation.getOperation().getProduces() == null) {
            return swaggerDefinition.getProduces() == null ? Collections.emptyList() : swaggerDefinition.getProduces();
        }
        return apiOperation.getOperation().getProduces();
    }

    @Nonnull
    private ValidationReport validateRequestBody(@Nonnull final Optional<String> requestBody,
                                                 @Nonnull final ApiOperation apiOperation) {

        if (isFormData(requestBody, apiOperation)) {
            return validateForm(requestBody, apiOperation);
        }
        return validateBody(requestBody, apiOperation);
    }

    @Nonnull
    private ValidationReport validateForm(@Nonnull final Optional<String> requestBody,
                                          @Nonnull final ApiOperation apiOperation) {

        final Multimap<String, String> formData = parseFormData(requestBody.get());
        return apiOperation.getOperation().getParameters()
                .stream()
                .filter(RequestValidator::isFormDataParam)
                .flatMap(parameter ->
                        prepareFormDataForParameter(formData, parameter).stream()
                                .map(value -> parameterValidators.validate(value, parameter))
                )
                .reduce(empty(), ValidationReport::merge);
    }

    @Nonnull
    private Collection<String> prepareFormDataForParameter(@Nonnull final Multimap<String, String> formData,
                                                           @Nonnull final Parameter parameter) {
        final Collection<String> parameterValues = formData.get(parameter.getName());
        return parameterValues.isEmpty() ? Collections.singletonList(null) : parameterValues;
    }

    @Nonnull
    private ValidationReport validateBody(@Nonnull final Optional<String> requestBody,
                                          @Nonnull final ApiOperation apiOperation) {
        final Optional<Parameter> bodyParameter = apiOperation.getOperation().getParameters()
                .stream()
                .filter(RequestValidator::isBodyParam)
                .findFirst();

        if (requestBody.isPresent() && !requestBody.get().isEmpty() && !bodyParameter.isPresent()) {
            return ValidationReport.singleton(
                    messages.get("validation.request.body.unexpected",
                            apiOperation.getMethod(), apiOperation.getApiPath().original())
            );
        }

        if (!bodyParameter.isPresent()) {
            return empty();
        }

        if (!requestBody.isPresent() || requestBody.get().isEmpty()) {
            if (bodyParameter.get().getRequired()) {
                return ValidationReport.singleton(
                        messages.get("validation.request.body.missing",
                                apiOperation.getMethod(), apiOperation.getApiPath().original())
                );
            }
            return empty();
        }

        return schemaValidator.validate(requestBody.get(), ((BodyParameter) bodyParameter.get()).getSchema());
    }

    @Nonnull
    private ValidationReport validatePathParameters(@Nonnull final ApiOperation apiOperation) {

        ValidationReport validationReport = empty();
        final NormalisedPath requestPath = apiOperation.getRequestPath();
        for (int i = 0; i < apiOperation.getApiPath().numberOfParts(); i++) {
            if (!apiOperation.getApiPath().hasParams(i)) {
                continue;
            }

            final ValidationReport pathPartValidation = apiOperation
                    .getApiPath()
                    .paramValues(i, requestPath.part(i))
                    .entrySet()
                    .stream()
                    .map((param) -> validatePathParameter(apiOperation, param.getKey(), param.getValue()))
                    .reduce(empty(), ValidationReport::merge);

            validationReport = validationReport.merge(pathPartValidation);
        }
        return validationReport;
    }

    @Nonnull
    private ValidationReport validatePathParameter(@Nonnull final ApiOperation apiOperation,
                                                   @Nonnull final String paramName,
                                                   @Nonnull final Optional<String> paramValue) {
        return apiOperation.getOperation().getParameters()
                .stream()
                .filter(RequestValidator::isPathParam)
                .filter(p -> p.getName().equalsIgnoreCase(paramName))
                .findFirst()
                .map(p -> parameterValidators.validate(paramValue.orElse(null), p))
                .orElse(empty());
    }

    @Nonnull
    private ValidationReport validateQueryParameters(@Nonnull final Request request,
                                                     @Nonnull final ApiOperation apiOperation) {
        return apiOperation
                .getOperation()
                .getParameters()
                .stream()
                .filter(RequestValidator::isQueryParam)
                .map(p -> validateParameter(
                        apiOperation, p,
                        request.getQueryParameterValues(p.getName()),
                        "validation.request.parameter.query.missing")
                )
                .reduce(empty(), ValidationReport::merge);
    }

    @Nonnull
    private ValidationReport validateHeaders(@Nonnull final Request request,
                                             @Nonnull final ApiOperation apiOperation) {
        return apiOperation
                .getOperation()
                .getParameters()
                .stream()
                .filter(RequestValidator::isHeaderParam)
                .map(p -> validateParameter(
                        apiOperation, p,
                        request.getHeaderValues(p.getName()),
                        "validation.request.parameter.header.missing")
                )
                .reduce(empty(), ValidationReport::merge);
    }

    @Nonnull
    private ValidationReport validateParameter(@Nonnull final ApiOperation apiOperation,
                                               @Nonnull final Parameter parameter,
                                               @Nonnull final Collection<String> parameterValues,
                                               @Nonnull final String missingKey) {

        if (parameterValues.isEmpty() && parameter.getRequired()) {
            return ValidationReport.singleton(
                    messages.get(missingKey, parameter.getName(), apiOperation.getApiPath().original())
            );
        }

        return parameterValues
                .stream()
                .map((v) -> parameterValidators.validate(v, parameter))
                .reduce(empty(), ValidationReport::merge);
    }

    @Nonnull
    private boolean isFormData(@Nonnull final Optional<String> requestBody,
                               @Nonnull final ApiOperation apiOperation) {
        final List<String> consumes = apiOperation.getOperation().getConsumes();
        return null != consumes && !consumes.isEmpty() &&
                consumes.stream().anyMatch(p -> p.equals(MediaType.FORM_DATA.toString()))
                && requestBody.isPresent();
    }

    @Nonnull
    private Multimap<String, String> parseFormData(@Nonnull final String formData) {
        final Multimap<String, String> params = ArrayListMultimap.create();
        final String[] pairs = formData.split("&");
        try {
            for (String pair : pairs) {
                final String[] fields = pair.split("=");
                final String name = URLDecoder.decode(fields[0], Charsets.UTF_8.name());
                final String value = (fields.length > 1) ? URLDecoder.decode(fields[1], Charsets.UTF_8.name()) : null;
                params.put(name, value);
            }
        } catch (final UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
        return params;
    }

    private static boolean isBodyParam(final Parameter p) {
        return isParam(p, "body");
    }

    private static boolean isPathParam(final Parameter p) {
        return isParam(p, "path");
    }

    private static boolean isQueryParam(final Parameter p) {
        return isParam(p, "query");
    }

    private static boolean isHeaderParam(final Parameter p) {
        return isParam(p, "header");
    }

    private static boolean isFormDataParam(final Parameter p) {
        return isParam(p, "formData");
    }

    private static boolean isParam(final Parameter p, final String type) {
        return p != null && p.getIn() != null && p.getIn().equalsIgnoreCase(type);
    }

}

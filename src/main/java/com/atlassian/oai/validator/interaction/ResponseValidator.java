package com.atlassian.oai.validator.interaction;

import com.atlassian.oai.validator.model.ApiOperation;
import com.atlassian.oai.validator.model.Response;
import com.atlassian.oai.validator.report.MessageResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.schema.SchemaValidator;
import com.google.common.net.MediaType;
import io.swagger.models.Swagger;
import io.swagger.models.properties.Property;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Validate a response against an API operation
 */
public class ResponseValidator {

    private final SchemaValidator schemaValidator;
    private final MessageResolver messages;
    private final Swagger swaggerDefinition;

    /**
     * Construct a new response validator with the given schema validator.
     *
     * @param schemaValidator The schema validator to use when validating response bodies
     * @param messages The message resolver to use
     */
    public ResponseValidator(@Nonnull final SchemaValidator schemaValidator,
                             @Nonnull final MessageResolver messages,
                             @Nonnull final Swagger swaggerDefinition) {
        this.schemaValidator = requireNonNull(schemaValidator, "A schema validator is required");
        this.messages = requireNonNull(messages, "A message resolver is required");
        this.swaggerDefinition = requireNonNull(swaggerDefinition, "A swagger definition is required");
    }

    /**
     * Validate the given response against the API operation.
     *
     * @param response The response to validate
     * @param apiOperation The API operation to validate the response against
     *
     * @return A validation report containing validation errors
     */
    @Nonnull
    public ValidationReport validateResponse(@Nonnull final Response response, @Nonnull final ApiOperation apiOperation) {
        requireNonNull(response, "A response is required");
        requireNonNull(apiOperation, "An API operation is required");

        final io.swagger.models.Response apiResponse = getApiResponse(response, apiOperation);
        if (apiResponse == null) {
            return ValidationReport.singleton(
                    messages.get("validation.response.status.unknown",
                            response.getStatus(), apiOperation.getApiPath().original())
            );
        }

        return validateResponseBody(response, apiResponse, apiOperation)
                .merge(validateContentType(response, apiOperation))
                .merge(validateHeaders(response, apiResponse, apiOperation));
    }

    @Nullable
    private io.swagger.models.Response getApiResponse(@Nonnull final Response response,
                                                      @Nonnull final ApiOperation apiOperation) {
        final io.swagger.models.Response apiResponse =
                apiOperation.getOperation().getResponses().get(Integer.toString(response.getStatus()));
        if (apiResponse == null) {
            return apiOperation.getOperation().getResponses().get("default"); // try the default response
        }
        return apiResponse;
    }

    @Nonnull
    private ValidationReport validateResponseBody(@Nonnull final Response response,
                                                  @Nonnull final io.swagger.models.Response apiResponse,
                                                  @Nonnull final ApiOperation apiOperation) {
        if (apiResponse.getSchema() == null) {
            return ValidationReport.empty();
        }

        if (!response.getBody().isPresent() || response.getBody().get().isEmpty()) {
            return ValidationReport.singleton(
                    messages.get("validation.response.body.missing",
                            apiOperation.getMethod(), apiOperation.getApiPath().original())
            );
        }

        return schemaValidator.validate(response.getBody().get(), apiResponse.getSchema());
    }

    @Nonnull
    private ValidationReport validateContentType(@Nonnull final Response response,
                                                 @Nonnull final ApiOperation apiOperation) {

        final Optional<String> requestHeader = response.getHeaderValue("Content-Type");
        if (!requestHeader.isPresent()) {
            return ValidationReport.empty();
        }

        final MediaType requestMediaType;
        try {
            requestMediaType = MediaType.parse(requestHeader.get());
        } catch (final IllegalArgumentException e) {
            return ValidationReport.singleton(messages.get("validation.response.contentType.invalid", requestHeader.get()));
        }

        final Collection<String> produces = getProduces(apiOperation);
        if (produces.isEmpty()) {
            return ValidationReport.empty();
        }

        final boolean contentTypeMatchesProduces = produces.stream()
                        .map(MediaType::parse)
                        .anyMatch(m -> m.withoutParameters().is(requestMediaType.withoutParameters()));
        if (!contentTypeMatchesProduces) {
            return ValidationReport.singleton(messages.get("validation.response.contentType.notAllowed", requestHeader.get(), produces));
        }

        return ValidationReport.empty();
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
    private ValidationReport validateHeaders(@Nonnull final Response response,
                                             @Nonnull final io.swagger.models.Response apiResponse,
                                             @Nonnull final ApiOperation apiOperation) {

        final Map<String, Property> apiHeaders = apiResponse.getHeaders();
        if (apiHeaders == null || apiHeaders.isEmpty()) {
            return ValidationReport.empty();
        }

        return apiHeaders.entrySet()
                .stream()
                .map(h -> validateHeader(apiOperation, h.getValue(), response.getHeaderValues(h.getKey())))
                .reduce(ValidationReport.empty(), ValidationReport::merge);

    }

    @Nonnull
    private ValidationReport validateHeader(@Nonnull final ApiOperation apiOperation,
                                            @Nonnull final Property property,
                                            @Nonnull final Collection<String> propertyValues) {

        if (propertyValues.isEmpty() && (property.getRequired() || Boolean.FALSE == property.getAllowEmptyValue())) {
            return ValidationReport.singleton(
                    messages.get("validation.response.header.missing",
                            property.getName(), apiOperation.getApiPath().original())
            );
        }

        return propertyValues
                .stream()
                .map((v) -> schemaValidator.validate(v, property))
                .reduce(ValidationReport.empty(), ValidationReport::merge);
    }
}

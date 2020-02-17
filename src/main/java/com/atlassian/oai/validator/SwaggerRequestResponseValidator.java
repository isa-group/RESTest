package com.atlassian.oai.validator;

import com.atlassian.oai.validator.interaction.ApiOperationResolver;
import com.atlassian.oai.validator.interaction.RequestValidator;
import com.atlassian.oai.validator.interaction.ResponseValidator;
import com.atlassian.oai.validator.model.ApiOperation;
import com.atlassian.oai.validator.model.ApiOperationMatch;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.Response;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.MessageResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.schema.SchemaValidator;
import com.atlassian.oai.validator.whitelist.ValidationErrorsWhitelist;
import io.swagger.models.Swagger;
import io.swagger.models.auth.AuthorizationValue;
import io.swagger.parser.SwaggerParser;
import io.swagger.parser.util.SwaggerDeserializationResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Validates a HTTP request/response pair with a Swagger/OpenAPI specification.
 * <p>
 * Validation errors are provided in a @{@link ValidationReport} that can be used to inspect the failures.
 * <p>
 * New instances should be created via the {@link SwaggerRequestResponseValidator#createFor(String)} method.
 *
 * @see #createFor(String)
 */
public class SwaggerRequestResponseValidator {

    private final MessageResolver messages;

    private final ApiOperationResolver apiOperationResolver;
    private final RequestValidator requestValidator;
    private final ResponseValidator responseValidator;
    private final ValidationErrorsWhitelist whitelist;

    /**
     * Create a new instance using the Swagger JSON specification at the given location OR actual swagger JSON String payload.
     * <p>
     * The URL can be an absolute HTTP/HTTPS URL, a File URL or a classpath location (without the classpath: scheme).
     * <p>
     * For example:
     * <pre>
     *     // Create from a publicly hosted HTTP location
     *     .createFor("http://api.myservice.com/swagger.json")
     *
     *     // Create from a file on the local filesystem
     *     .createFor("file://Users/myuser/tmp/swagger.json");
     *
     *     // Create from a classpath resource in the /api package
     *     .createFor("/api/swagger.json");
     *
     *     // Create from a swagger JSON payload
     *     .createFor("{\"swagger\": \"2.0\", ...}")
     * </pre>
     *
     * @param swaggerJsonUrlOrPayload The location of the Swagger JSON specification to use in the validator.
     * @return A new builder instance to use for creating configuring {@link SwaggerRequestResponseValidator} instances.
     */
    public static Builder createFor(@Nonnull final String swaggerJsonUrlOrPayload) {
        return new Builder().withSwaggerJsonUrl(swaggerJsonUrlOrPayload);
    }

    /**
     * Construct a new validator for the specification at the given URL.
     * @param swaggerJsonUrlOrPayload   The location of the Swagger JSON specification to use in this validator.
     * @param basePathOverride (Optional) override for the base path defined in the Swagger specification.
     * @param messages         The message resolver to use for resolving validation messages.
     * @param whitelist        The validation errors whitelist.
     */
    private SwaggerRequestResponseValidator(@Nonnull final String swaggerJsonUrlOrPayload,
                                            @Nullable final String basePathOverride,
                                            @Nonnull final MessageResolver messages,
                                            @Nonnull final ValidationErrorsWhitelist whitelist) {
        this(swaggerJsonUrlOrPayload, basePathOverride, messages, whitelist, null);
    }

    /**
     * Construct a new validator for the specification at the given URL with authentication data.
     * @param swaggerJsonUrlOrPayload   The location of the Swagger JSON specification to use in this validator.
     * @param basePathOverride (Optional) override for the base path defined in the Swagger specification.
     * @param messages         The message resolver to use for resolving validation messages.
     * @param whitelist        The validation errors whitelist.
     * @param authData         (Optional) A List of authentication data to add to swagger spec retrieval request.
     */
    private SwaggerRequestResponseValidator(@Nonnull final String swaggerJsonUrlOrPayload,
                                            @Nullable final String basePathOverride,
                                            @Nonnull final MessageResolver messages,
                                            @Nonnull final ValidationErrorsWhitelist whitelist,
                                            @Nullable final List<AuthorizationValue> authData) {
        requireNonNull(swaggerJsonUrlOrPayload, "A Swagger JSON URL or payload is required");

        final SwaggerDeserializationResult swaggerParseResult =
            swaggerJsonUrlOrPayload.startsWith("{") ?
                new SwaggerParser().readWithInfo(swaggerJsonUrlOrPayload) :
                new SwaggerParser().readWithInfo(swaggerJsonUrlOrPayload, authData, true);
        final Swagger api = swaggerParseResult.getSwagger();
        if (api == null) {
            throw new IllegalArgumentException(
                format("Unable to load API descriptor from provided %s:\n\t%s",
                    swaggerJsonUrlOrPayload, swaggerParseResult.getMessages().toString().replace("\n", "\n\t")));
        }
        this.messages = messages;
        this.apiOperationResolver = new ApiOperationResolver(api, basePathOverride);
        final SchemaValidator schemaValidator = new SchemaValidator(api, messages);
        this.requestValidator = new RequestValidator(schemaValidator, messages, api);
        this.responseValidator = new ResponseValidator(schemaValidator, messages, api);
        this.whitelist = whitelist;
    }

    /**
     * Validate the given request/response against the API.
     * <p>
     * See class docs for more information on the validation performed.
     *
     * @param request  The request to validate (required)
     * @param response The response to validate (required)
     * @return The outcome of the validation
     */
    @Nonnull
    public ValidationReport validate(@Nonnull final Request request, @Nonnull final Response response) {
        requireNonNull(request, "A request is required");
        requireNonNull(response, "A response is required");

        return validateOnApiOperation(
            request.getPath(),
            request.getMethod(),
            apiOperation -> requestValidator.validateRequest(request, apiOperation)
                .merge(responseValidator.validateResponse(response, apiOperation)),
            (apiOperation, report) -> withWhitelistApplied(report, apiOperation, request, response));
    }

    /**
     * Validate ONLY the given response against the API.
     * <p>
     * See class docs for more information on the validation performed.
     *
     * @param request  The request to validate (required)
     * @param response The response to validate (required)
     * @return The outcome of the validation
     */
    @Nonnull
    public ValidationReport validateOnlyResponse(@Nonnull final Request request, @Nonnull final Response response) {
        requireNonNull(request, "A request is required");
        requireNonNull(response, "A response is required");

        return validateOnApiOperation(
                request.getPath(),
                request.getMethod(),
                apiOperation -> requestValidator.notValidateRequest(request, apiOperation)
                        .merge(responseValidator.validateResponse(response, apiOperation)),
                (apiOperation, report) -> withWhitelistApplied(report, apiOperation, request, response));
    }

    /**
     * Validate the given request against the API.
     * <p>
     * See class docs for more information on the validation performed.
     *
     * @param request The request to validate (required)
     * @return The outcome of the request validation
     */
    @Nonnull
    public ValidationReport validateRequest(@Nonnull final Request request) {
        requireNonNull(request, "A request is required");

        return validateOnApiOperation(
            request.getPath(),
            request.getMethod(),
            apiOperation -> requestValidator.validateRequest(request, apiOperation),
            (apiOperation, report) -> withWhitelistApplied(report, apiOperation, request, null));
    }

    /**
     * Validate the given response against the API.
     * <p>
     * See class docs for more information on the validation performed.
     *
     * @param path     The request path (required)
     * @param method   The request method (required)
     * @param response The response to validate (required)
     * @return The outcome of the response validation
     */
    @Nonnull
    public ValidationReport validateResponse(@Nonnull final String path, @Nonnull final Request.Method method,
                                             @Nonnull final Response response) {
        requireNonNull(path, "A path is required");
        requireNonNull(method, "A method is required");
        requireNonNull(response, "A response is required");

        return validateOnApiOperation(
            path,
            method,
            apiOperation -> responseValidator.validateResponse(response, apiOperation),
            (apiOperation, report) -> withWhitelistApplied(report, apiOperation, null, response));
    }

    private ValidationReport validateOnApiOperation(@Nonnull final String path,
                                                    @Nonnull final Request.Method method,
                                                    @Nonnull final Function<ApiOperation, ValidationReport> validationFunction,
                                                    @Nonnull final BiFunction<ApiOperation, ValidationReport, ValidationReport> whitelistingFunction) {
        final ApiOperationMatch apiOperationMatch = apiOperationResolver.findApiOperation(path, method);
        if (!apiOperationMatch.isPathFound()) {
            return whitelistingFunction.apply(null, ValidationReport.singleton(
                messages.get("validation.request.path.missing", path)));
        }

        if (!apiOperationMatch.isOperationAllowed()) {
            return whitelistingFunction.apply(null, ValidationReport.singleton(
                messages.get("validation.request.operation.notAllowed", method, path)));
        }

        final ApiOperation apiOperation = apiOperationMatch.getApiOperation();
        return validationFunction
            .andThen(report -> whitelistingFunction.apply(apiOperation, report))
            .apply(apiOperation);
    }

    private ValidationReport withWhitelistApplied(final ValidationReport report,
                                                  @Nullable final ApiOperation operation,
                                                  @Nullable final Request request,
                                                  @Nullable final Response response) {
        return ValidationReport.from(
            report.getMessages().stream()
                .map(message -> whitelist
                    .whitelistedBy(message, operation, request, response)
                    .map(rule -> message
                        .withLevel(ValidationReport.Level.IGNORE)
                        .withAdditionalInfo("Whitelisted by: " + rule))
                    .orElse(message))
                .collect(Collectors.toList()));
    }

    /**
     * A builder used to createFor configured instances of the {@link SwaggerRequestResponseValidator}.
     */
    public static class Builder {
        private String swaggerJsonUrlOrPayload = "";
        private String basePathOverride;
        private LevelResolver levelResolver = LevelResolver.defaultResolver();
        private List<AuthorizationValue> authData;
        private ValidationErrorsWhitelist whitelist = ValidationErrorsWhitelist.create();

        /**
         * The location of the Swagger JSON specification to use in the validator.
         * <p>
         * The URL can be an absolute HTTP/HTTPS URL, a File URL or a classpath location (without the classpath: scheme).
         * <p>
         * For example:
         * <pre>
         *     // Create from a publicly hosted HTTP location
         *     .withSwaggerJsonUrl("http://api.myservice.com/swagger.json")
         *
         *     // Create from a file on the local filesystem
         *     .withSwaggerJsonUrl("file://Users/myuser/tmp/swagger.json");
         *
         *     // Create from a classpath resource in the /api package
         *     .withSwaggerJsonUrl("/api/swagger.json");
         * </pre>
         *
         * @param swaggerJsonUrlOrPayload The location of the Swagger JSON specification to use in the validator.
         * @return this builder instance.
         */
        public Builder withSwaggerJsonUrl(final String swaggerJsonUrlOrPayload) {
            this.swaggerJsonUrlOrPayload = swaggerJsonUrlOrPayload;
            return this;
        }

        /**
         * An optional basepath override to override the one defined in the Swagger spec.
         * <p>
         * This can be useful if e.g. your Swagger specification has been created for a public URL but you are validating
         * requests against an internal URL where the URL paths differ.
         *
         * @param basePathOverride An optional basepath override to override the one defined in the Swagger spec.
         * @return this builder instance.
         */
        public Builder withBasePathOverride(final String basePathOverride) {
            this.basePathOverride = basePathOverride;
            return this;
        }

        /**
         * The resolver to use for resolving the level of validation messages (ERROR, WARN, IGNORE etc.).
         * <p>
         * This can be used to get fine-grained control over validation behaviour
         * (e.g. what level to emit message at, which validations to ignore etc.).
         * <p>
         * If not provided, a default resolver will be used that resolves all message to ERROR.
         *
         * @param levelResolver The resolver to use for resolving validation message levels.
         * @return this builder instance.
         */
        public Builder withLevelResolver(final LevelResolver levelResolver) {
            this.levelResolver = levelResolver;
            return this;
        }

        /**
         * A whitelist for error messages. Whitelisted error messages will still be returned, but their level will be
         * changed to IGNORE and additional information about whitelisting will be added.
         *
         * @param whitelist The whitelist to use.
         * @return this builder instance
         */
        public Builder withWhitelist(final ValidationErrorsWhitelist whitelist) {
            this.whitelist = whitelist;
            return this;
        }

        /**
         * An optional key value header to add to the Swagger spec retrieval request.
         * <p>
         * This is necessary if e.g. your Swagger specification is retrieved from a remote host and the path to retrieve is secured by an api key in the request header.
         *
         * @param key A key name to add as request header key.
         * @param value (Optional) A value to add as request header value for the given key.
         * @return this builder instance.
         */
        public Builder withAuthHeaderData(final String key, final String value) {
            requireNonNull(key, "A key for the auth header is required");

            this.authData = Arrays.asList(new AuthorizationValue(key, value, "header"));
            return this;
        }

        /**
         * Build a configured {@link SwaggerRequestResponseValidator} instance with the values collected in this builder.
         *
         * @return The configured {@link SwaggerRequestResponseValidator} instance.
         */
        public SwaggerRequestResponseValidator build() {
            return new SwaggerRequestResponseValidator(swaggerJsonUrlOrPayload, basePathOverride, new MessageResolver(levelResolver), whitelist, authData);
        }
    }
}

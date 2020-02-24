package com.atlassian.oai.validator.model;

import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;

import javax.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * A container representing a single API operation.
 * <p>
 * This includes the path, method and operation components from the OAI spec object. Used as a
 * convenience to hold related information in one place.
 */
public class ApiOperation {
    private final ApiPath apiPath;
    private final NormalisedPath requestPath;
    private final HttpMethod method;
    private final Operation operation;

    public ApiOperation(@Nonnull final ApiPath apiPath,
                        @Nonnull final NormalisedPath requestPath,
                        @Nonnull final HttpMethod method,
                        @Nonnull final Operation operation) {

        this.apiPath = requireNonNull(apiPath, "A path string is required");
        this.requestPath = requireNonNull(requestPath, "An api path is required");
        this.method = requireNonNull(method, "A request method is required");
        this.operation = requireNonNull(operation, "A operation object is required");
    }

    /**
     * @return The path the operation is on
     */
    @Nonnull
    public ApiPath getApiPath() {
        return apiPath;
    }

    /**
     * @return The normalised path from original request
     */
    @Nonnull
    public NormalisedPath getRequestPath() {
        return requestPath;
    }

    /**
     * @return The method the operation is on
     */
    @Nonnull
    public HttpMethod getMethod() {
        return method;
    }

    /**
     * @return The operation object from the OAI specification
     */
    @Nonnull
    public Operation getOperation() {
        return operation;
    }
}

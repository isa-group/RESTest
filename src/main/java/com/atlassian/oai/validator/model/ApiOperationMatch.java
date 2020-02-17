package com.atlassian.oai.validator.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * After trying to find the best matching {@link ApiOperation} this model contains the information
 * if the path is defined, the operation is allowed and if so the {@link ApiOperation} itself, too.
 */
public class ApiOperationMatch {

    /**
     * The searched path is not defined in the API definition.
     */
    public static ApiOperationMatch MISSING_PATH = new ApiOperationMatch(false, false, null);

    /**
     * The searched path is defined in the API definition, but the HTTP method is not allowed.
     */
    public static ApiOperationMatch NOT_ALLOWED_OPERATION = new ApiOperationMatch(true, false, null);

    private final boolean pathFound;
    private final boolean operationAllowed;
    private final ApiOperation apiOperation;

    /**
     * If and only if a {@link ApiOperation} was found this {@link ApiOperationMatch} contains it.
     *
     * @param apiOperation the found, matching {@link ApiOperation}
     */
    public ApiOperationMatch(@Nonnull final ApiOperation apiOperation) {
        this(true, true, apiOperation);
    }

    private ApiOperationMatch(final boolean pathFound, final boolean operationAllowed, @Nullable final ApiOperation apiOperation) {
        this.pathFound = pathFound;
        this.operationAllowed = operationAllowed;
        if (pathFound && operationAllowed) {
            requireNonNull(apiOperation, "A path string is required");
        }
        this.apiOperation = apiOperation;
    }

    /**
     * @return if a matching path was found
     */
    public boolean isPathFound() {
        return pathFound;
    }

    /**
     * @return if the operation is allowed on the matched path
     */
    public boolean isOperationAllowed() {
        return operationAllowed;
    }

    /**
     * @return the {@link ApiOperation} on the matched path and operation
     */
    public ApiOperation getApiOperation() {
        if (!pathFound || !operationAllowed) {
            throw new IllegalStateException("No API operation found.");
        }
        return apiOperation;
    }
}

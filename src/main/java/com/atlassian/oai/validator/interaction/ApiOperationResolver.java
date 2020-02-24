package com.atlassian.oai.validator.interaction;

import com.atlassian.oai.validator.model.ApiOperation;
import com.atlassian.oai.validator.model.ApiOperationMatch;
import com.atlassian.oai.validator.model.ApiPath;
import com.atlassian.oai.validator.model.ApiPathImpl;
import com.atlassian.oai.validator.model.NormalisedPath;
import com.atlassian.oai.validator.model.NormalisedPathImpl;
import com.atlassian.oai.validator.model.Request;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Swagger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * Component responsible for matching an incoming request path + method with an operation defined in the OAI spec.
 */
public class ApiOperationResolver {

    private final String apiPrefix;

    private final Map<Integer, List<ApiPath>> apiPathsGroupedByNumberOfParts;
    private final Table<String, HttpMethod, Operation> operations;

    /**
     * A utility for finding the best fitting API path.
     *
     * @param api              the Swagger API definition
     * @param basePathOverride (Optional) override for the base path defined in the Swagger specification.
     */
    public ApiOperationResolver(@Nonnull final Swagger api, @Nullable final String basePathOverride) {
        this.apiPrefix = ofNullable(basePathOverride).orElse(api.getBasePath());
        final Map<String, Path> apiPaths = ofNullable(api.getPaths()).orElse(emptyMap());

        // normalise all API paths and group them by their number of parts
        this.apiPathsGroupedByNumberOfParts = apiPaths.keySet().stream()
                .map(p -> new ApiPathImpl(p, apiPrefix))
                .collect(groupingBy(NormalisedPath::numberOfParts));

        // create a operation mapping for the API path and HTTP method
        this.operations = HashBasedTable.create();
        apiPaths.forEach((pathKey, apiPath) ->
                apiPath.getOperationMap().forEach((httpMethod, operation) ->
                        operations.put(pathKey, httpMethod, operation))
        );
    }

    /**
     * Tries to find the best fitting API path matching the given path and request method.
     *
     * @param path   the requests path to find in API definition
     * @param method the {@link Request.Method} for the request
     * @return a {@link ApiOperationMatch} containing the information if the path is defined, the operation
     * is allowed and having the necessary {@link ApiOperation} if applicable
     */
    @Nonnull
    public ApiOperationMatch findApiOperation(@Nonnull final String path, @Nonnull final Request.Method method) {

        // try to find possible matching paths regardless of HTTP method
        final NormalisedPath requestPath = new NormalisedPathImpl(path, apiPrefix);
        final List<ApiPath> possibleMatches = apiPathsGroupedByNumberOfParts
                .getOrDefault(requestPath.numberOfParts(), emptyList()).stream()
                .filter(p -> p.matches(requestPath))
                .collect(toList());

        if (possibleMatches.isEmpty()) {
            return ApiOperationMatch.MISSING_PATH;
        }

        // try to find the operation which fits the HTTP method
        final HttpMethod httpMethod = HttpMethod.valueOf(method.name());
        final Optional<ApiPath> pathOpt = possibleMatches.stream()
                .filter(apiPath -> operations.contains(apiPath.original(), httpMethod))
                .findFirst(); // if exists there can only be one path matching the path and method - overlapping paths+methods are not allowed

        return pathOpt
                .map(apiPath -> new ApiOperationMatch(new ApiOperation(apiPath, requestPath, httpMethod,
                        operations.get(apiPath.original(), httpMethod))))
                .orElse(ApiOperationMatch.NOT_ALLOWED_OPERATION);
    }

}

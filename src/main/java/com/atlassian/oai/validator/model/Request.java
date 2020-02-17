package com.atlassian.oai.validator.model;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation-agnostic representation of a HTTP request
 */
public interface Request {

    /**
     * Supported HTTP request methods
     */
    enum Method {
        GET,
        POST,
        PUT,
        PATCH,
        DELETE,
        HEAD,
        OPTIONS,
        TRACE
    }

    /**
     * @return the request path
     */
    @Nonnull
    String getPath();

    /**
     * @return the HTTP request method ("GET", "PUT" etc.)
     */
    @Nonnull
    Method getMethod();

    /**
     * @return the request body
     */
    @Nonnull
    Optional<String> getBody();

    /**
     * @return the collection of query parameter names present on this request
     */
    @Nonnull
    Collection<String> getQueryParameters();

    /**
     * Get the collection of query parameter values for the query param with the given name.
     *
     * @param name The name of the parameter to retrieve. If not differently specified this name is case insensitive.
     *
     * @return The query parameter values for that param; or empty list
     */
    @Nonnull
    Collection<String> getQueryParameterValues(String name);

    /**
     * Get the collection of headers on this request.
     * <p>
     * Result will include key and all values (in the case of multiple headers with the same key)
     *
     * @return The map of <code>key-&gt;values</code> for the headers associated with this request.
     */
    @Nonnull
    Map<String, Collection<String>> getHeaders();

    /**
     * Get the collection of header values for the header param with the given name.
     *
     * @param name The (case insensitive) name of the parameter to retrieve
     *
     * @return The header values for that param; or empty list
     */
    @Nonnull
    Collection<String> getHeaderValues(String name);

    /**
     * Get the first of header value for the header param with the given name (if any exist).
     *
     * @param name The (case insensitive) name of the parameter to retrieve
     *
     * @return The first header value for that param (if it exists)
     */
    @Nonnull
    default Optional<String> getHeaderValue(final String name) {
        return getHeaderValues(name).stream().findFirst();
    }

}

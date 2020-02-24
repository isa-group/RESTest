package com.atlassian.oai.validator.model;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Optional;

/**
 * Implementation-agnostic representation of a HTTP response
 */
public interface Response {

    /**
     * @return The response status code
     */
    int getStatus();

    /**
     * @return The response body, if there is one.
     */
    @Nonnull
    Optional<String> getBody();

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

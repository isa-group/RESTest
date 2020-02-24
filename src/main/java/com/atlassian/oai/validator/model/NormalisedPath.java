package com.atlassian.oai.validator.model;

import javax.annotation.Nonnull;

/**
 * A normalised representation of an API path.
 * <p>
 * Normalised paths are devoid of path prefixes and contain a normalised starting/ending
 * slash to make comparisons easier.
 */
public interface NormalisedPath {

    /**
     * @return The number of path parts from the normalised path
     */
    int numberOfParts();

    /**
     * @return The path part at the given index
     * @throws IndexOutOfBoundsException if the provided index is not a valid index
     */
    @Nonnull
    String part(int index);

    /**
     * @return The original, un-normalised path string
     */
    @Nonnull
    String original();

    /**
     * @return The normalised path string, with prefixes removed and a standard treatment for leading/trailing slashes.
     */
    @Nonnull
    String normalised();
}

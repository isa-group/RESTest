package com.atlassian.oai.validator.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.prependIfMissing;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

public class NormalisedPathImpl implements NormalisedPath {

    private final List<String> pathParts;
    private final String original;
    private final String normalised;

    public NormalisedPathImpl(@Nonnull final String path, @Nullable final String apiPrefix) {
        original = requireNonNull(path, "A path is required");
        normalised = normalise(apiPrefix, path);

        // We have normalized to start with a leading "/"; this will result in an empty path element
        pathParts = unmodifiableList(asList(normalised.substring(1).split("/")));
    }

    @Override
    public int numberOfParts() {
        return pathParts.size();
    }

    @Override
    @Nonnull
    public String part(final int index) {
        return pathParts.get(index);
    }

    @Override
    @Nonnull
    public String original() {
        return original;
    }

    @Override
    @Nonnull
    public String normalised() {
        return normalised;
    }

    private static String normalise(@Nullable final String prefix, @Nonnull final String requestPath) {
        final String trimmedPath = trimPrefix(normaliseToLeadingSlash(prefix), normaliseToLeadingSlash(requestPath));
        return normaliseToLeadingSlash(trimmedPath);
    }

    private static String trimPrefix(@Nullable final String apiPrefix, @Nonnull final String requestPath) {
        if (apiPrefix == null || !requestPath.startsWith(apiPrefix)) {
            return requestPath;
        }
        return requestPath.substring(apiPrefix.length());
    }

    private static String normaliseToLeadingSlash(@Nullable final String pathPart) {
        return prependIfMissing(trimToEmpty(pathPart), "/");
    }

}

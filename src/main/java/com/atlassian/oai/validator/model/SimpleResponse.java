package com.atlassian.oai.validator.model;

import com.google.common.collect.Multimap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

/**
 * Simple immutable {@link Response} implementation
 */
public class SimpleResponse implements Response {

    private final int status;
    private final Map<String, Collection<String>> headers;
    private final Optional<String> body;

    private SimpleResponse(final int status,
                           @Nonnull final Map<String, Collection<String>> headers,
                           @Nullable final String body) {
        this.status = status;
        this.headers = requireNonNull(headers);
        this.body = Optional.ofNullable(body);
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Nonnull
    @Override
    public Optional<String> getBody() {
        return body;
    }

    @Nonnull
    @Override
    public Collection<String> getHeaderValues(final String name) {
        return SimpleRequest.getFromMapOrEmptyList(headers, name);
    }

    /**
     * A builder for constructing new {@link SimpleResponse} instances.
     */
    public static class Builder {

        private final int status;
        private final Multimap<String, String> headers;
        private String body;

        /**
         * Creates a {@link Builder} with the given HTTP status code.
         *
         * @param status the responses HTTP status code
         * @return a prepared {@link Builder}
         */
        public static Builder status(final int status) {
            return new Builder(status);
        }

        /**
         * A convenience method for creating a {@link Builder} with
         * the HTTP status code 200.
         *
         * @return a prepared {@link Builder}
         */
        public static Builder ok() {
            return new Builder(200);
        }

        /**
         * A convenience method for creating a {@link Builder} with
         * the HTTP status code 204.
         *
         * @return a prepared {@link Builder}
         */
        public static Builder noContent() {
            return new Builder(204);
        }

        /**
         * A convenience method for creating a {@link Builder} with
         * the HTTP status code 400.
         *
         * @return a prepared {@link Builder}
         */
        public static Builder badRequest() {
            return new Builder(400);
        }

        /**
         * A convenience method for creating a {@link Builder} with
         * the HTTP status code 404.
         *
         * @return a prepared {@link Builder}
         */
        public static Builder notFound() {
            return new Builder(404);
        }

        /**
         * A convenience method for creating a {@link Builder} with
         * the HTTP status code 500.
         *
         * @return a prepared {@link Builder}
         */
        public static Builder serverError() {
            return new Builder(500);
        }

        /**
         * Creates a {@link Builder} with the given HTTP status code.
         *
         * @param status the responses HTTP status code
         */
        public Builder(final int status) {
            this.status = status;
            this.headers = SimpleRequest.Builder.multimapBuilder(false /* header are always case insensitive */);
        }

        /**
         * Adds a response body to this builder.
         *
         * @param body the response body
         * @return this builder
         */
        public Builder withBody(final String body) {
            this.body = body;
            return this;
        }

        /**
         * Adds a response header to this builder. If there was already a header with this
         * name the values will be added.
         * <p>
         * Headers are treated case insensitive.
         *
         * @param name   the header name
         * @param values the values for this header
         * @return this builder
         */
        public Builder withHeader(final String name, final List<String> values) {
            // available but not set headers are considered as empty
            SimpleRequest.Builder.putValuesToMapOrDefault(headers, name, values, "");
            return this;
        }

        /**
         * Adds a response header to this builder. If there was already a header with this
         * name the values will be added.
         * <p>
         * Headers are treated case insensitive.
         *
         * @param name   the header name
         * @param values the values for this header
         * @return this builder
         */
        public Builder withHeader(final String name, final String... values) {
            return withHeader(name, asList(values));
        }

        /**
         * Builds a {@link SimpleResponse} out of this builder.
         *
         * @return the build {@link SimpleResponse}
         */
        public SimpleResponse build() {
            return new SimpleResponse(status, headers.asMap(), body);
        }
    }
}

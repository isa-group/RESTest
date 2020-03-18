package com.atlassian.oai.validator.model;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

/**
 * Simple immutable {@link Request} implementation.
 * <p>
 * New instances should be constructed with a {@link Builder}.
 */
public class SimpleRequest implements Request {

    private final Method method;
    private final String path;
    private final Map<String, Collection<String>> headers;
    private final Map<String, Collection<String>> queryParams;
    private final Optional<String> requestBody;

    private SimpleRequest(@Nonnull final Method method,
                          @Nonnull final String path,
                          @Nonnull final Map<String, Collection<String>> headers,
                          @Nonnull final Map<String, Collection<String>> queryParams,
                          @Nullable final String body) {
        this.method = requireNonNull(method, "A method is required");
        this.path = requireNonNull(path, "A request path is required");
        this.queryParams = requireNonNull(queryParams);
        this.headers = requireNonNull(headers);
        this.requestBody = Optional.ofNullable(body);
    }

    public SimpleRequest(@Nonnull final String method,
                          @Nonnull final String path,
                          @Nonnull final Map<String, String> headers,
                          @Nonnull final Map<String, String> queryParams,
                          @Nullable final String body) {
        Method method1;
        switch (method) {
            case "GET":
                method1 = Request.Method.GET;
                break;
            case "POST":
                method1 = Request.Method.POST;
                break;
            case "PUT":
                method1 = Request.Method.PUT;
                break;
            case "PATCH":
                method1 = Request.Method.PATCH;
                break;
            case "DELETE":
                method1 = Request.Method.DELETE;
                break;
            case "HEAD":
                method1 = Request.Method.HEAD;
                break;
            case "OPTIONS":
                method1 = Request.Method.OPTIONS;
                break;
            case "TRACE":
                method1 = Request.Method.TRACE;
                break;
            default:
                throw new IllegalArgumentException("Invalid request method");
        }

        Map<String, Collection<String>> queryParams2 = new HashMap<>();
        queryParams.forEach((k, v) -> {
            Collection<String> values = new ArrayList<>();
            values.add(v);
            queryParams2.put(k, values);
        });

        Map<String, Collection<String>> headers2 = new HashMap<>();
        headers.forEach((k, v) -> {
            Collection<String> values = new ArrayList<>();
            values.add(v);
            headers2.put(k, values);
        });

        this.method = requireNonNull(method1, "A method is required");
        this.path = requireNonNull(path, "A request path is required");
        this.queryParams = requireNonNull(queryParams2);
        this.headers = requireNonNull(headers2);
        this.requestBody = Optional.ofNullable(body);
    }

    @Nonnull
    @Override
    public String getPath() {
        return path;
    }

    @Nonnull
    @Override
    public Method getMethod() {
        return method;
    }

    @Nonnull
    @Override
    public Optional<String> getBody() {
        return requestBody;
    }

    @Override
    @Nonnull
    public Collection<String> getQueryParameterValues(final String name) {
        return getFromMapOrEmptyList(queryParams, name);
    }

    @Override
    @Nonnull
    public Collection<String> getQueryParameters() {
        return Collections.unmodifiableCollection(queryParams.keySet());
    }

    @Nonnull
    @Override
    public Map<String, Collection<String>> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    @Nonnull
    @Override
    public Collection<String> getHeaderValues(final String name) {
        return getFromMapOrEmptyList(headers, name);
    }

    static Collection<String> getFromMapOrEmptyList(final Map<String, Collection<String>> map, final String name) {
        if (name == null || !map.containsKey(name)) {
            return emptyList();
        }

        return map.get(name).stream().filter(Objects::nonNull)
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }

    /**
     * A builder for constructing new {@link SimpleRequest} instances.
     */
    public static class Builder {

        private final Method method;
        private final String path;
        private final Multimap<String, String> headers;
        private final Multimap<String, String> queryParams;
        private String body;

        /**
         * A convenience method for creating a {@link Builder} with
         * HTTP method GET and the given path.
         *
         * @param path the requests path
         * @return a prepared {@link Builder}
         */
        public static Builder get(final String path) {
            return new Builder(Method.GET, path);
        }

        /**
         * A convenience method for creating a {@link Builder} with
         * HTTP method PUT and the given path.
         *
         * @param path the requests path
         * @return a prepared {@link Builder}
         */
        public static Builder put(final String path) {
            return new Builder(Method.PUT, path);
        }

        /**
         * A convenience method for creating a {@link Builder} with
         * HTTP method POST and the given path.
         *
         * @param path the requests path
         * @return a prepared {@link Builder}
         */
        public static Builder post(final String path) {
            return new Builder(Method.POST, path);
        }

        /**
         * A convenience method for creating a {@link Builder} with
         * HTTP method DELETE and the given path.
         *
         * @param path the requests path
         * @return a prepared {@link Builder}
         */
        public static Builder delete(final String path) {
            return new Builder(Method.DELETE, path);
        }

        /**
         * A convenience method for creating a {@link Builder} with
         * HTTP method PATCH and the given path.
         *
         * @param path the requests path
         * @return a prepared {@link Builder}
         */
        public static Builder patch(final String path) {
            return new Builder(Method.PATCH, path);
        }

        /**
         * A convenience method for creating a {@link Builder} with
         * HTTP method HEAD and the given path.
         *
         * @param path the requests path
         * @return a prepared {@link Builder}
         */
        public static Builder head(final String path) {
            return new Builder(Method.HEAD, path);
        }

        /**
         * A convenience method for creating a {@link Builder} with
         * HTTP method OPTIONS and the given path.
         *
         * @param path the requests path
         * @return a prepared {@link Builder}
         */
        public static Builder options(final String path) {
            return new Builder(Method.OPTIONS, path);
        }

        /**
         * A convenience method for creating a {@link Builder} with
         * HTTP method TRACE and the given path.
         *
         * @param path the requests path
         * @return a prepared {@link Builder}
         */
        public static Builder trace(final String path) {
            return new Builder(Method.TRACE, path);
        }

        /**
         * Creates a {@link Builder} with the given HTTP method and path.
         *
         * @param method the HTTP method
         * @param path   the requests path
         */
        public Builder(final String method, final String path) {
            this(method, path, false);
        }

        /**
         * Creates a {@link Builder} with the given HTTP {@link Method} and path.
         *
         * @param method the HTTP method
         * @param path   the requests path
         */
        public Builder(final Method method, final String path) {
            this(method, path, false);
        }

        /**
         * Creates a {@link Builder} with the given HTTP method and path including
         * the specification if the query parameters are handled case sensitive or not.
         *
         * @param method                       the HTTP method
         * @param path                         the requests path
         * @param queryParametersCaseSensitive flag if the query parameters are handled case sensitive or not
         */
        public Builder(final String method, final String path, final boolean queryParametersCaseSensitive) {
            this(Method.valueOf(requireNonNull(method, "A method is required").toUpperCase()),
                    path, queryParametersCaseSensitive);
        }

        /**
         * Creates a {@link Builder} with the given HTTP {@link Method} and path including
         * the specification if the query parameters are handled case sensitive or not.
         *
         * @param method                       the HTTP method
         * @param path                         the requests path
         * @param queryParametersCaseSensitive flag if the query parameters are handled case sensitive or not
         */
        public Builder(final Method method, final String path, final boolean queryParametersCaseSensitive) {
            this.method = requireNonNull(method, "A method is required");
            this.path = requireNonNull(path, "A path is required");

            this.headers = multimapBuilder(false /* header are always case insensitive */);
            this.queryParams = multimapBuilder(queryParametersCaseSensitive);
        }

        /**
         * Adds a request body to this builder.
         *
         * @param body the request body
         * @return this builder
         */
        public Builder withBody(final String body) {
            this.body = body;
            return this;
        }

        /**
         * Adds a request header to this builder. If there was already a header with this
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
            putValuesToMapOrDefault(headers, name, values, "");
            return this;
        }

        /**
         * Adds a request header to this builder. If there was already a header with this
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
         * Adds a query parameter to this request builder. If there was already a query
         * parameter with this name the values will be added.
         * <p>
         * The case sensitivity can be set by this builder's
         * {@linkplain Builder#Builder(Method, String, boolean)} constructor.
         *
         * @param name   the header name
         * @param values the values for this header
         * @return this builder
         */
        public Builder withQueryParam(final String name, final List<String> values) {
            // available but not set query parameters are considered as available but with no value
            putValuesToMapOrDefault(queryParams, name, values, null);
            return this;
        }

        /**
         * Adds a query parameter to this request builder. If there was already a query
         * parameter with this name the values will be added.
         * <p>
         * The case sensitivity can be set by this builder's
         * {@linkplain Builder#Builder(String, String, boolean)} constructor.
         *
         * @param name   the header name
         * @param values the values for this header
         * @return this builder
         */
        public Builder withQueryParam(final String name, final String... values) {
            return withQueryParam(name, asList(values));
        }

        /**
         * Builds a {@link SimpleRequest} out of this builder.
         *
         * @return the build {@link SimpleRequest}
         */
        public SimpleRequest build() {
            return new SimpleRequest(method, path, headers.asMap(), queryParams.asMap(), body);
        }

        static Multimap<String, String> multimapBuilder(final boolean caseSensitive) {
            return caseSensitive ? MultimapBuilder.hashKeys().arrayListValues().build() :
                    MultimapBuilder.treeKeys(String.CASE_INSENSITIVE_ORDER).arrayListValues().build();
        }

        static void putValuesToMapOrDefault(final Multimap<String, String> map, final String name,
                                            final List<String> values, final String defaultIfNotSet) {
            if (values == null || values.isEmpty()) {
                map.put(name, defaultIfNotSet);
            } else {
                map.putAll(name, values);
            }
        }
    }
}

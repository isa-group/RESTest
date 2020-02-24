package com.atlassian.oai.validator.restassured;

import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import io.restassured.specification.FilterableRequestSpecification;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class RestAssuredRequest implements Request {

    private final Request delegate;

    /**
     * @deprecated Use: {@link RestAssuredRequest#of(FilterableRequestSpecification)}
     */
    @Deprecated
    public RestAssuredRequest(@Nonnull final FilterableRequestSpecification originalRequest) {
        this.delegate = RestAssuredRequest.of(originalRequest);
    }

    @Nonnull
    @Override
    public String getPath() {
        return delegate.getPath();
    }

    @Nonnull
    @Override
    public Method getMethod() {
        return delegate.getMethod();
    }

    @Nonnull
    @Override
    public Optional<String> getBody() {
        return delegate.getBody();
    }

    @Nonnull
    @Override
    public Collection<String> getQueryParameters() {
        return delegate.getQueryParameters();
    }

    @Nonnull
    @Override
    public Collection<String> getQueryParameterValues(final String name) {
        return delegate.getQueryParameterValues(name);
    }

    @Nonnull
    @Override
    public Map<String, Collection<String>> getHeaders() {
        return delegate.getHeaders();
    }

    @Nonnull
    @Override
    public Collection<String> getHeaderValues(final String name) {
        return delegate.getHeaderValues(name);
    }

    /**
     * Builds a {@link Request} for the swagger validator out of the
     * original {@link FilterableRequestSpecification}.
     *
     * @param originalRequest the original {@link FilterableRequestSpecification}
     */
    @Nonnull
    public static Request of(@Nonnull final FilterableRequestSpecification originalRequest) {
        requireNonNull(originalRequest, "An original request is required");
        final SimpleRequest.Builder builder =
                new SimpleRequest.Builder(originalRequest.getMethod(), originalRequest.getDerivedPath())
                        .withBody(originalRequest.getBody());
        if (originalRequest.getHeaders() != null) {
            originalRequest.getHeaders().forEach(header -> builder.withHeader(header.getName(), header.getValue()));
        }
        // the query params seems wrongly typed - they can contain either a list of strings or a string
        new HashMap<String, Object>(originalRequest.getQueryParams())
                .forEach((key, value) -> {
                    if (value instanceof List) {
                        builder.withQueryParam(key, (List) value);
                    } else if (value instanceof String) {
                        builder.withQueryParam(key, (String) value);
                    }
                });
        if ("GET".equalsIgnoreCase(originalRequest.getMethod())) {
            originalRequest.getRequestParams().forEach((key, value) -> {
                builder.withQueryParam(key, value);
            });
        }
        return builder.build();
    }
}

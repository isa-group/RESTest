package com.atlassian.oai.validator.restassured;

import com.atlassian.oai.validator.model.Response;
import com.atlassian.oai.validator.model.SimpleResponse;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class RestAssuredResponse implements Response {

    private final Response delegate;

    /**
     * @deprecated Use: {@link RestAssuredResponse#of(io.restassured.response.Response)}
     */
    @Deprecated
    public RestAssuredResponse(@Nonnull final io.restassured.response.Response originalResponse) {
        this.delegate = RestAssuredResponse.of(originalResponse);
    }

    @Override
    public int getStatus() {
        return delegate.getStatus();
    }

    @Nonnull
    @Override
    public Optional<String> getBody() {
        return delegate.getBody();
    }

    @Nonnull
    @Override
    public Collection<String> getHeaderValues(final String name) {
        return delegate.getHeaderValues(name);
    }

    /**
     * Builds a {@link Response} for the Swagger validator out of the
     * original {@link io.restassured.response.Response}.
     *
     * @param originalResponse the original {@link io.restassured.response.Response}
     */
    @Nonnull
    public static Response of(@Nonnull final io.restassured.response.Response originalResponse) {
        requireNonNull(originalResponse, "An original response is required");
        final SimpleResponse.Builder builder = new SimpleResponse.Builder(originalResponse.getStatusCode())
                .withBody(originalResponse.getBody().asString());
        if (originalResponse.getHeaders() != null) {
            originalResponse.getHeaders().forEach(header -> builder.withHeader(header.getName(), header.getValue()));
        }
        return builder.build();
    }
}

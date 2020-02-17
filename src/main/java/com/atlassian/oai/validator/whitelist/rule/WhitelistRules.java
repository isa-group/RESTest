package com.atlassian.oai.validator.whitelist.rule;

import com.atlassian.oai.validator.model.ApiOperation;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.Response;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.whitelist.StatusType;
import com.google.common.collect.ImmutableList;
import io.swagger.models.HttpMethod;

import java.util.Collections;
import java.util.regex.Pattern;

/**
 * Static factory methods for creating {@link WhitelistRule} instances.
 */
public final class WhitelistRules {

    private WhitelistRules() {
    }

    /**
     * Creates a rule that matches if all given rules match.
     */
    public static WhitelistRule allOf(final WhitelistRule... rules) {
        return new AndWhitelistRule(ImmutableList.copyOf(rules));
    }

    /**
     * Creates a rule that matches if any of the given rules match.
     */
    public static WhitelistRule anyOf(final WhitelistRule... rules) {
        return new OrWhitelistRule(ImmutableList.copyOf(rules));
    }

    /**
     * Matches if the given entity (identified by name) is sent in the request or returned in the response,
     * as specified in the spec (actual json payload are not inspected).
     */
    public static WhitelistRule entityIs(final String entityName) {
        return new IsEntityWhitelistRule(entityName);
    }

    /**
     * Matches all error messages with the given key.
     */
    public static WhitelistRule messageHasKey(final String key) {
        return new PrintableWhitelistRule(
            "Message with key: '" + key + "'",
            (message, operation, request, response) -> key.equalsIgnoreCase(message.getKey()));
    }

    /**
     * Matches messages that contain the given regular expression in their text.
     */
    public static WhitelistRule messageContains(final String regexp) {
        return new PrintableWhitelistRule(
            "Message contains: '" + regexp + "'",
            (message, operation, request, response) -> regexpContain(message.getMessage(), regexp));
    }

    /**
     * Matches operations that contain the given regular expression in their API path.
     * <p>
     *     The tested path does not have parameters materialized, but is taken from the API
     * definition, e.g. "/store/order/{orderId}".
     */
    public static WhitelistRule pathContains(final String regexp) {
        return new PrintableWhitelistRule(
            "Api path contains: '" + regexp + "'",
            (message, operation, request, response) -> operation != null &&
                regexpContain(operation.getApiPath().normalised(), regexp));
    }

    /**
     * Matches all request errors.
     */
    public static WhitelistRule isRequest() {
        return new PrintableWhitelistRule(
            "Is request",
            (message, operation, request, response) -> request != null);
    }

    /**
     * Matches all response errors.
     */
    public static WhitelistRule isResponse() {
        return new PrintableWhitelistRule(
            "Is response",
            (message, operation, request, response) -> response != null);
    }

    /**
     * Matches all responses with the given status.
     */
    public static WhitelistRule responseStatusIs(final int status) {
        return new PrintableWhitelistRule(
            "Response status is " + status,
            (message, operation, request, response) -> response != null && response.getStatus() == status);
    }

    /**
     * Matches all responses with the given status type.
     */
    public static WhitelistRule responseStatusTypeIs(final StatusType statusType) {
        return new PrintableWhitelistRule(
            "Response status is " + statusType,
            (message, operation, request, response) -> response != null && statusType.matches(response.getStatus()));
    }

    /**
     * Matches all operations with the given method. Both request and response errors can be matched with this.
     */
    public static WhitelistRule methodIs(final HttpMethod method) {
        return new PrintableWhitelistRule(
            "Method is " + method,
            (message, operation, request, response) -> operation != null && operation.getMethod() == method);
    }

    /**
     * Matches requests or responses whose at least one of the given header's values contain the given regular expression.
     * Each header value is inspected separately, and the rule will match if any value matches the expression.
     */
    public static WhitelistRule headerContains(final String header, final String regexp) {
        return new PrintableWhitelistRule(
            "Header '" + header + "' contains '" + regexp + "'",
            new RequestOrResponseWhitelistRule() {
                @Override
                public boolean matches(final ValidationReport.Message message, final ApiOperation operation, final Request request) {
                    return request.getHeaders()
                        .getOrDefault(header, Collections.emptyList())
                        .stream()
                        .anyMatch(value -> regexpContain(value, regexp));
                }

                @Override
                public boolean matches(final ValidationReport.Message message, final ApiOperation operation, final Response response) {
                    return response.getHeaderValues(header)
                        .stream()
                        .anyMatch(value -> regexpContain(value, regexp));
                }
            });
    }

    private static boolean regexpContain(final String value, final String regexp) {
        return Pattern.compile(regexp, Pattern.CASE_INSENSITIVE).matcher(value).find();
    }
}

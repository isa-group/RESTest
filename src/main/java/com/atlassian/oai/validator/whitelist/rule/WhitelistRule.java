package com.atlassian.oai.validator.whitelist.rule;

import com.atlassian.oai.validator.model.ApiOperation;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.Response;
import com.atlassian.oai.validator.report.ValidationReport.Message;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;

/**
 * A rule for matching validation messages. Matched errors are whitelisted (ignored) and don't fail validation.
 */
public interface WhitelistRule {

    boolean matches(final Message message, @Nullable final ApiOperation operation, @Nullable final Request request, @Nullable final Response response);

    /**
     * Creates a new rule that matches only if this and that matches.
     */
    default WhitelistRule and(final WhitelistRule rule) {
        return new AndWhitelistRule(ImmutableList.of(this, rule));
    }

    /**
     * Creates a new rule that matches if this or that matches.
     */
    default WhitelistRule or(final WhitelistRule rule) {
        return new OrWhitelistRule(ImmutableList.of(this, rule));
    }

    /**
     * Negates the result of this rule.
     */
    default WhitelistRule not() {
        return new PrintableWhitelistRule(
            "Not " + this,
            (message, operation, request, response) -> !this.matches(message, operation, request, response)
        );
    }
}

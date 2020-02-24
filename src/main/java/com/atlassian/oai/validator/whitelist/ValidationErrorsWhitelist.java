package com.atlassian.oai.validator.whitelist;

import com.atlassian.oai.validator.model.ApiOperation;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.Response;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.whitelist.rule.WhitelistRule;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ValidationErrorsWhitelist {

    private final List<NamedWhitelistRule> rules;

    /**
     * Creates an empty validation whitelist. Start with this method when creating a new whitelist from scratch.
     */
    public static ValidationErrorsWhitelist create() {
        return new ValidationErrorsWhitelist(Collections.emptyList());
    }

    /**
     * Creates a new whitelist with all rules of this and with a new rule.
     *
     * @param title A human-readable name of the new rule.
     * @param rule A new rule to be added.
     * @return A new instance with the added rule.
     */
    public ValidationErrorsWhitelist withRule(final String title, final WhitelistRule rule) {
        return new ValidationErrorsWhitelist(
                ImmutableList.<NamedWhitelistRule>builder()
                        .addAll(rules)
                        .add(new NamedWhitelistRule(title, rule))
                        .build());
    }

    /**
     * Returns a whitelist rule that is applicable for the given parameters.
     * If a non-empty value is returned then the error message should be whitelisted.
     *
     * @param message report message that can be whitelisted
     * @param operation validated api operation
     * @param request validated request
     * @param response validated response
     * @return a rule that matches the arguments, or empty
     */
    public Optional<NamedWhitelistRule> whitelistedBy(final ValidationReport.Message message,
                                                      @Nullable final ApiOperation operation,
                                                      @Nullable final Request request,
                                                      @Nullable final Response response) {
        return rules.stream()
                .filter(rule -> rule.getRule().matches(message, operation, request, response))
                .findFirst();
    }

    private ValidationErrorsWhitelist(final Iterable<NamedWhitelistRule> rules) {
        this.rules = ImmutableList.copyOf(rules);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ValidationErrorsWhitelist that = (ValidationErrorsWhitelist) o;

        return Objects.equals(this.rules, that.rules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rules);
    }

    @Override
    public String toString() {
        return rules.toString();
    }
}

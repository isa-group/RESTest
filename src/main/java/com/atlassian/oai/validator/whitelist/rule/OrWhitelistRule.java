package com.atlassian.oai.validator.whitelist.rule;

import com.atlassian.oai.validator.model.ApiOperation;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.Response;
import com.atlassian.oai.validator.report.ValidationReport;

import java.util.List;

import static java.util.stream.Collectors.joining;

class OrWhitelistRule implements WhitelistRule {
    private final List<WhitelistRule> rules;

    public OrWhitelistRule(final List<WhitelistRule> rules) {
        this.rules = rules;
    }

    @Override
    public boolean matches(final ValidationReport.Message message, final ApiOperation operation, final Request request, final Response response) {
        return rules.stream().anyMatch(r -> r.matches(message, operation, request, response));
    }

    @Override
    public String toString() {
        return rules.stream().map(Object::toString).collect(joining(" OR ", "(", ")"));
    }
}


package com.atlassian.oai.validator.whitelist.rule;

import com.atlassian.oai.validator.model.ApiOperation;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.Response;
import com.atlassian.oai.validator.report.ValidationReport.Message;

import javax.annotation.Nullable;

/**
 * A utility interface making whitelist rules that operate on either request or response data easier to implement.
 */
interface RequestOrResponseWhitelistRule extends WhitelistRule {
    default boolean matches(final Message message, final ApiOperation operation, @Nullable final Request request, @Nullable final Response response) {
        return request != null && matches(message, operation, request) ||
                response != null && matches(message, operation, response);
    }

    boolean matches(final Message message, final ApiOperation operation, final Request request);

    boolean matches(final Message message, final ApiOperation operation, final Response response);
}

package com.atlassian.oai.validator.parameter.format;

import com.atlassian.oai.validator.report.MessageResolver;
import com.atlassian.oai.validator.report.ValidationReport;

import java.net.URI;
import java.net.URISyntaxException;

import static com.atlassian.oai.validator.report.ValidationReport.empty;
import static com.atlassian.oai.validator.report.ValidationReport.singleton;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class URIFormatValidator implements FormatValidator<String> {

    private static final String MESSAGE_KEY = "validation.request.parameter.string.uri.invalid";

    private final MessageResolver messages;

    public URIFormatValidator(final MessageResolver messages) {
        this.messages = requireNonNull(messages, "A message resolver is required");
    }

    @Override
    public boolean supports(final String format) {
        return "uri".equalsIgnoreCase(format);
    }

    @Override
    public ValidationReport validate(final String value) {
        if (isBlank(value)) {
            return singleton(messages.get(MESSAGE_KEY, value));
        }
        try {
            new URI(value);
        } catch (final URISyntaxException ignored) {
            return singleton(messages.get(MESSAGE_KEY, value));
        }
        return empty();
    }
}

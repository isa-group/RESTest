package com.atlassian.oai.validator.parameter.format;

import com.atlassian.oai.validator.report.MessageResolver;
import com.atlassian.oai.validator.report.ValidationReport;

import java.util.UUID;

import static com.atlassian.oai.validator.report.ValidationReport.empty;
import static com.atlassian.oai.validator.report.ValidationReport.singleton;
import static java.util.Objects.requireNonNull;

public class UUIDFormatValidator implements FormatValidator<String> {

    private static final String MESSAGE_KEY = "validation.request.parameter.string.uuid.invalid";

    private final MessageResolver messages;

    public UUIDFormatValidator(final MessageResolver messages) {
        this.messages = requireNonNull(messages, "A message resolver is required");
    }

    @Override
    public boolean supports(final String format) {
        return "uuid".equalsIgnoreCase(format);
    }

    @Override
    public ValidationReport validate(final String value) {
        try {
            UUID.fromString(value);
        } catch (final IllegalArgumentException ignored) {
            return singleton(messages.get(MESSAGE_KEY, value));
        }
        return empty();
    }
}

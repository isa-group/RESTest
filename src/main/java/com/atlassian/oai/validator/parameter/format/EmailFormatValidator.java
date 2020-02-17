package com.atlassian.oai.validator.parameter.format;

import com.atlassian.oai.validator.report.MessageResolver;
import com.atlassian.oai.validator.report.ValidationReport;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import static com.atlassian.oai.validator.report.ValidationReport.empty;
import static com.atlassian.oai.validator.report.ValidationReport.singleton;
import static java.util.Objects.requireNonNull;

public class EmailFormatValidator implements FormatValidator<String> {

    private static final String MESSAGE_KEY = "validation.request.parameter.string.email.invalid";

    private final MessageResolver messages;

    public EmailFormatValidator(final MessageResolver messages) {
        this.messages = requireNonNull(messages, "A message resolver is required");
    }

    @Override
    public boolean supports(final String format) {
        return "email".equalsIgnoreCase(format);
    }

    @Override
    public ValidationReport validate(final String value) {
        try {
            new InternetAddress(value, true);
        } catch (final AddressException ignored) {
            return singleton(messages.get(MESSAGE_KEY, value));
        }
        return empty();
    }
}

package com.atlassian.oai.validator.parameter.format;

import com.atlassian.oai.validator.report.MessageResolver;
import com.atlassian.oai.validator.report.ValidationReport;

import javax.annotation.Nonnull;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public abstract class BaseDateFormatValidator implements FormatValidator<String> {

    private final MessageResolver messages;

    protected BaseDateFormatValidator(final MessageResolver messages) {
        this.messages = messages;
    }

    @Override
    public ValidationReport validate(@Nonnull final String value) {
        final DateTimeFormatter dateFormatter = getFormatter();
        try {
            dateFormatter.parse(value);
        } catch (final DateTimeParseException e) {
            return ValidationReport.singleton(messages.get("validation.request.parameter.string." + getMessageKey() + ".invalid", value));
        }
        return ValidationReport.empty();
    }

    protected abstract String getMessageKey();

    protected abstract DateTimeFormatter getFormatter();
}

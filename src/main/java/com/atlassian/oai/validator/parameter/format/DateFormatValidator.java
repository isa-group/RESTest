package com.atlassian.oai.validator.parameter.format;

import com.atlassian.oai.validator.report.MessageResolver;

import java.time.format.DateTimeFormatter;

public class DateFormatValidator extends BaseDateFormatValidator {

    public DateFormatValidator(final MessageResolver messages) {
        super(messages);
    }

    @Override
    protected String getMessageKey() {
        return "date";
    }

    @Override
    public boolean supports(final String format) {
        return format.equals("date");
    }

    @Override
    protected DateTimeFormatter getFormatter() {
        return DateTimeFormatter.ISO_DATE;
    }
}

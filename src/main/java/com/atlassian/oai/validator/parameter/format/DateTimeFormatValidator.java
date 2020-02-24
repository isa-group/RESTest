package com.atlassian.oai.validator.parameter.format;

import com.atlassian.oai.validator.report.MessageResolver;

import java.time.format.DateTimeFormatter;

public class DateTimeFormatValidator extends BaseDateFormatValidator {

    public DateTimeFormatValidator(final MessageResolver messages) {
        super(messages);
    }

    @Override
    protected String getMessageKey() {
        return "dateTime";
    }

    @Override
    public boolean supports(final String format) {
        return format.equals("date-time");
    }

    @Override
    protected DateTimeFormatter getFormatter() {
        return CustomDateTimeFormatter.getRFC3339Formatter();
    }
}

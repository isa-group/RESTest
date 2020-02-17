package com.atlassian.oai.validator.parameter.format;

import com.atlassian.oai.validator.report.ValidationReport;

public class NoOpStringFormatValidator implements FormatValidator<String> {

    @Override
    public boolean supports(final String format) {
        return false;
    }

    @Override
    public ValidationReport validate(final String value) {
        return ValidationReport.empty();
    }
}

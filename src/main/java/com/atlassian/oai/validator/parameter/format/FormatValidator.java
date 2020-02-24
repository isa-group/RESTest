package com.atlassian.oai.validator.parameter.format;

import com.atlassian.oai.validator.report.ValidationReport;

public interface FormatValidator<T> {

    boolean supports(String format);

    ValidationReport validate(T value);
}

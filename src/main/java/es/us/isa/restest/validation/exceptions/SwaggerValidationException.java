package es.us.isa.restest.validation.exceptions;

import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.report.ValidationReportFormatter;

public class SwaggerValidationException extends RuntimeException {
    public SwaggerValidationException(final ValidationReport report) {
        super(ValidationReportFormatter.format(report));
    }
}

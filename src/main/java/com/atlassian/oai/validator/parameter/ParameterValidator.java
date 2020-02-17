package com.atlassian.oai.validator.parameter;

import com.atlassian.oai.validator.report.ValidationReport;
import io.swagger.models.parameters.Parameter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface ParameterValidator {

    @Nonnull
    String supportedParameterType();

    boolean supports(@Nullable Parameter p);

    @Nonnull
    ValidationReport validate(@Nullable String value, @Nullable Parameter p);

}

package com.atlassian.oai.validator.parameter;

import com.atlassian.oai.validator.report.MessageResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.schema.SchemaValidator;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.SerializableParameter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

public final class ParameterValidators {

    private final ArrayParameterValidator arrayValidator;
    private final MessageResolver messages;
    private final List<ParameterValidator> validators;

    /**
     * Create a new validators object with the given schema validator. If none is provided a default (empty) schema
     * validator will be used and no <code>ref</code> validation will be performed.
     *
     * @param schemaValidator The schema validator to use. If not provided a default (empty) validator will be used.
     * @param messages The message resolver to use.
     */
    public ParameterValidators(@Nullable final SchemaValidator schemaValidator, @Nonnull final MessageResolver messages) {
        this.arrayValidator = new ArrayParameterValidator(schemaValidator, messages);
        this.messages = requireNonNull(messages);
        this.validators = asList(
                new StringParameterValidator(messages),
                new NumberParameterValidator(messages),
                new IntegerParameterValidator(messages)
        );
    }

    public ValidationReport validate(final String value, @Nonnull final Parameter parameter) {
        requireNonNull(parameter);

        if ((parameter instanceof SerializableParameter) &&
                ((SerializableParameter) parameter).getType().equalsIgnoreCase("array")) {
            return arrayValidator.validate(value, parameter);
        }

        return validators.stream()
                .filter(v -> v.supports(parameter))
                .map(v -> v.validate(value, parameter))
                .reduce(ValidationReport.empty(), ValidationReport::merge);
    }

}

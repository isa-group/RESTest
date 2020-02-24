package com.atlassian.oai.validator.parameter;

import com.atlassian.oai.validator.report.MessageResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import io.swagger.models.parameters.SerializableParameter;

import javax.annotation.Nonnull;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static com.google.common.base.MoreObjects.firstNonNull;

public abstract class BaseNumericParameterValidator extends BaseParameterValidator {

    public BaseNumericParameterValidator(@Nonnull final MessageResolver messages) {
        super(messages);
    }

    @Override
    protected ValidationReport doValidate(@Nonnull final String value,
                                          @Nonnull final SerializableParameter parameter) {
        final double doubleValue;
        try {
            doubleValue = getNumericValue(value, parameter).doubleValue();
        } catch (final NumberFormatException e) {
            return failFormatValidation(value, parameter, parameter.getFormat());
        }

        return Stream.of(
            validateMinimum(parameter, doubleValue),
            validateMaximum(parameter, doubleValue),
            validateMultipleOf(parameter, doubleValue)
        ).reduce(ValidationReport.empty(), ValidationReport::merge);
    }

    private ValidationReport failFormatValidation(
        final String value,
        final SerializableParameter parameter,
        final String format) {
        return ValidationReport.singleton(messages.get("validation.request.parameter.invalidFormat",
            value, parameter.getName(), supportedParameterType(), format)
        );
    }

    private ValidationReport validateMultipleOf(final SerializableParameter parameter,
                                                final Double value) {

        final Number multipleOf = parameter.getMultipleOf();
        final Double doubleMultipleOf = multipleOf != null ? multipleOf.doubleValue() : null;
        if (doubleMultipleOf != null && (value % doubleMultipleOf != 0d)) {
            return ValidationReport.singleton(messages.get("validation.request.parameter.number.multipleOf",
                value, parameter.getName(), multipleOf)
            );
        }
        return ValidationReport.empty();
    }

    private ValidationReport validateMinimum(final SerializableParameter parameter,
                                             final Double value) {
        final BigDecimal minimum = parameter.getMinimum();
        final boolean exclusiveMinimum = firstNonNull(parameter.isExclusiveMinimum(), false);

        if (parameter.getMinimum() != null) {
            if (exclusiveMinimum && value <= minimum.doubleValue()) {
                return ValidationReport.singleton(messages.get("validation.request.parameter.number.belowExclusiveMin",
                    value, parameter.getName(), minimum)
                );
            } else if (!exclusiveMinimum && value < minimum.doubleValue()) {
                return ValidationReport.singleton(messages.get("validation.request.parameter.number.belowMin",
                    value, parameter.getName(), minimum)
                );
            }
        }
        return ValidationReport.empty();
    }

    private ValidationReport validateMaximum(final SerializableParameter parameter,
                                             final Double value) {
        final BigDecimal maximum = parameter.getMaximum();
        final boolean exclusiveMaximum = firstNonNull(parameter.isExclusiveMaximum(), false);

        if (parameter.getMaximum() != null) {
            if (exclusiveMaximum && value >= maximum.doubleValue()) {
                return ValidationReport.singleton(messages.get("validation.request.parameter.number.aboveExclusiveMax",
                    value, parameter.getName(), maximum)
                );
            } else if (!exclusiveMaximum && value > maximum.doubleValue()) {
                return ValidationReport.singleton(messages.get("validation.request.parameter.number.aboveMax",
                    value, parameter.getName(), maximum)
                );
            }
        }
        return ValidationReport.empty();
    }

    protected abstract Number getNumericValue(String value, SerializableParameter parameter) throws NumberFormatException;
}

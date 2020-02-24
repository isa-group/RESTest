package com.atlassian.oai.validator.parameter;

import com.atlassian.oai.validator.report.MessageResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.schema.SchemaValidator;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.SerializableParameter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * A validator for array parameters.
 * <p>
 * This is a special-case validator as it needs to handle single and collection types for validation.
 */
public class ArrayParameterValidator extends BaseParameterValidator {

    public static final String ARRAY_PARAMETER_TYPE = "array";

    private final SchemaValidator schemaValidator;

    private enum CollectionFormat {
        CSV(","),
        SSV(" "),
        TSV("\t"),
        PIPES("\\|"),
        MULTI(null);

        final String separator;
        CollectionFormat(final String separator) {
            this.separator = separator;
        }

        Collection<String> split(final String value) {
            if (separator == null) {
                return Collections.singleton(value);
            }
            return Arrays.asList(value.split(separator));
        }

        @Nonnull
        static CollectionFormat from(@Nonnull final SerializableParameter parameter) {
            requireNonNull(parameter, "A parameter is required");

            return Optional.ofNullable(parameter.getCollectionFormat())
                    .map(String::toUpperCase)
                    .map(CollectionFormat::valueOf)
                    .orElse(CSV);
        }
    }

    public ArrayParameterValidator(@Nullable final SchemaValidator schemaValidator,
                                   @Nonnull final MessageResolver messages) {
        super(messages);
        this.schemaValidator = schemaValidator == null ? new SchemaValidator(messages) : schemaValidator;
    }

    @Nonnull
    @Override
    public String supportedParameterType() {
        return ARRAY_PARAMETER_TYPE;
    }

    @Override
    @Nonnull
    public ValidationReport validate(@Nullable final String value, @Nullable final Parameter p) {
        if (!supports(p)) {
            return ValidationReport.empty();
        }

        final SerializableParameter parameter = (SerializableParameter) p;

        if (parameter.getRequired() && (value == null || value.trim().isEmpty())) {
            return ValidationReport.singleton(messages.get("validation.request.parameter.missing", parameter.getName()));
        }

        if (value == null || value.trim().isEmpty()) {
            return ValidationReport.empty();
        }

        return doValidate(value, parameter);
    }

    public ValidationReport validate(@Nullable final Collection<String> values, @Nullable final Parameter p) {
        if (p == null) {
            return ValidationReport.empty();
        }

        final SerializableParameter parameter = (SerializableParameter) p;
        if (parameter.getRequired() && (values == null || values.isEmpty())) {
            return ValidationReport.singleton(messages.get("validation.request.parameter.missing", parameter.getName()));
        }

        if (values == null) {
            return ValidationReport.empty();
        }

        if (!parameter.getCollectionFormat().equalsIgnoreCase(CollectionFormat.MULTI.name())) {
            return ValidationReport.singleton(messages.get("validation.request.parameter.collection.invalidFormat",
                    p.getName(), parameter.getCollectionFormat(), "multi")
            );
        }

        return doValidate(values, parameter);
    }

    @Override
    protected ValidationReport doValidate(@Nonnull final String value,
                                          @Nonnull final SerializableParameter parameter) {

        return doValidate(CollectionFormat.from(parameter).split(value), parameter);
    }

    private ValidationReport doValidate(@Nonnull final Collection<String> values,
                                        @Nonnull final SerializableParameter parameter) {

        final ValidationReport report = Stream.of(
                validateMaxItems(values, parameter),
                validateMinItems(values, parameter),
                validateUniqueItems(values, parameter)
        ).reduce(ValidationReport.empty(), ValidationReport::merge);

        if (parameter.getEnum() != null && !parameter.getEnum().isEmpty()) {
            final Set<String> enumValues = new HashSet<>(parameter.getEnum());
            return values.stream()
                    .filter(v -> !enumValues.contains(v))
                    .map(v -> ValidationReport.singleton(messages.get("validation.request.parameter.enum.invalid",
                            v, parameter.getName(), parameter.getEnum())
                    ))
                    .reduce(report, ValidationReport::merge);
        }

        return values.stream()
                .map(v -> schemaValidator.validate(v, parameter.getItems()))
                .reduce(report, ValidationReport::merge);
    }

    private ValidationReport validateUniqueItems(final @Nonnull Collection<String> values, final @Nonnull SerializableParameter parameter) {
        if (Boolean.TRUE.equals(parameter.isUniqueItems()) &&
            values.stream().distinct().count() != values.size()) {
            return ValidationReport.singleton(messages.get("validation.request.parameter.collection.duplicateItems",
                parameter.getName())
            );
        }
        return ValidationReport.empty();
    }

    private ValidationReport validateMinItems(final @Nonnull Collection<String> values, final @Nonnull SerializableParameter parameter) {
        if (parameter.getMinItems() != null && values.size() < parameter.getMinItems()) {
            return ValidationReport.singleton(messages.get("validation.request.parameter.collection.tooFewItems",
                parameter.getName(), parameter.getMinItems(), values.size())
            );
        }
        return ValidationReport.empty();
    }

    private ValidationReport validateMaxItems(final @Nonnull Collection<String> values, final @Nonnull SerializableParameter parameter) {
        if (parameter.getMaxItems() != null && values.size() > parameter.getMaxItems()) {
            return ValidationReport.singleton(messages.get("validation.request.parameter.collection.tooManyItems",
                parameter.getName(), parameter.getMaxItems(), values.size())
            );
        }
        return ValidationReport.empty();
    }
}

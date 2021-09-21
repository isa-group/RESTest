package es.us.isa.restest.specification;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Auxiliary class to store multiple commonly used properties of an OpenAPI parameter
 */
public class ParameterFeatures {
    String name;                    // Parameter name
    String in;                      // query, path, etc.
    String type;                    // string, integer, etc.
    String format;                  // If parameter is string, its format (email, uuid, etc.)
    String pattern;                 // If parameter is string and regex is included
    List<String> enumValues;        // Set of possible value if parameter is an enum
    BigDecimal min;                 // If parameter is number, min value allowed
    BigDecimal max;                 // If parameter is number, max value allowed
    Integer minLength;              // If parameter is string, minLength allowed
    Integer maxLength;              // If parameter is string, maxLength value allowed
    Boolean required;               // 'true' if the parameter needs to be in the response

    public ParameterFeatures(Parameter p) {
        name = p.getName();
        in = p.getIn();
        type = p.getSchema().getType();
        format = p.getSchema().getFormat();
        pattern = p.getSchema().getPattern();
        enumValues = p.getSchema().getEnum();
        if ("array".equals(type))
            enumValues = (List<String>) ((ArraySchema)p.getSchema()).getItems().getEnum();
        min = p.getSchema().getMinimum();
        max = p.getSchema().getMaximum();
        minLength = p.getSchema().getMinLength();
        maxLength = p.getSchema().getMaxLength();
        required = p.getRequired() == null? Boolean.FALSE : p.getRequired();
    }

    public ParameterFeatures(String name, String in, Boolean required) {
        this.name = name;
        this.in = in;
        this.required = required;
    }

    public ParameterFeatures(String name, Schema s, Boolean required) {
        this.name = name;
        type = s.getType();
        format = s.getFormat();
        pattern = s.getPattern();
        enumValues = s.getEnum();
        if ("array".equals(type))
            enumValues = (List<String>) ((ArraySchema)s).getItems().getEnum();
        min = s.getMinimum();
        max = s.getMaximum();
        minLength = s.getMinLength();
        maxLength = s.getMaxLength();

        in = "formData";
        this.required = required;

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIn() {
        return in;
    }

    public void setIn(String in) {
        this.in = in;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public List<String> getEnumValues() {
        return enumValues;
    }

    public void setEnumValues(List<String> enumValues) {
        this.enumValues = enumValues;
    }

    public BigDecimal getMin() {
        return min;
    }

    public void setMin(BigDecimal min) {
        this.min = min;
    }

    public BigDecimal getMax() {
        return max;
    }

    public void setMax(BigDecimal max) {
        this.max = max;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParameterFeatures that = (ParameterFeatures) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(in, that.in) &&
                Objects.equals(type, that.type) &&
                Objects.equals(format, that.format) &&
                Objects.equals(pattern, that.pattern) &&
                Objects.equals(enumValues, that.enumValues) &&
                Objects.equals(min, that.min) &&
                Objects.equals(max, that.max) &&
                Objects.equals(minLength, that.minLength) &&
                Objects.equals(maxLength, that.maxLength) &&
                Objects.equals(required, that.required);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, in, type, format, pattern, enumValues, min, max, minLength, maxLength, required);
    }
}

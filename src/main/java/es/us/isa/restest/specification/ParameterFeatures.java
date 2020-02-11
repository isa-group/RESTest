package es.us.isa.restest.specification;

import io.swagger.models.parameters.AbstractSerializableParameter;
import io.swagger.models.parameters.Parameter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Auxiliary class to store multiple commonly used properties of a Swagger parameter
 */
public class ParameterFeatures {
    String name;                    // Parameter name
    String in;                      // query, path, etc.
    String type;                    // string, integer, etc.
    String format;                  // If parameter is string, its format (email, uuid, etc.)
    List<String> enumValues;        // Set of possible value if parameter is an enum
    BigDecimal min;                 // If parameter is number, min value allowed
    BigDecimal max;                 // If parameter is number, max value allowed
    Integer minLength;              // If parameter is string, minLength allowed
    Integer maxLength;              // If parameter is string, maxLength value allowed

    public ParameterFeatures(Parameter p) {
        name = p.getName();
        in = p.getIn();
        type = ((AbstractSerializableParameter)p).getType();
        format = ((AbstractSerializableParameter) p).getFormat();
        enumValues = ((AbstractSerializableParameter)p).getEnum();
        min = ((AbstractSerializableParameter) p).getMinimum();
        max = ((AbstractSerializableParameter) p).getMaximum();
        minLength = ((AbstractSerializableParameter) p).getMinLength();
        maxLength = ((AbstractSerializableParameter) p).getMaxLength();
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
}

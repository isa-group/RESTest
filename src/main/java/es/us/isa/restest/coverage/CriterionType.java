package es.us.isa.restest.coverage;

import java.util.Arrays;
import java.util.List;

/**
 * All possible criterion types that can be covered, both for inputs and outputs.
 */
public enum CriterionType {
    PATH,
    OPERATION,
    PARAMETER,
    PARAMETER_VALUE,
    PARAMETER_CONDITION,
    OPERATIONS_FLOW,
    INPUT_CONTENT_TYPE,
    AUTHENTICATION,
    STATUS_CODE,
    STATUS_CODE_CLASS,
    RESPONSE_BODY_PROPERTIES,
    OUTPUT_CONTENT_TYPE;

    public static List<CriterionType> getTypes(String type) {
        List<CriterionType> types;

        if (type == null) {
            types = Arrays.asList(PATH, OPERATION, PARAMETER, PARAMETER_VALUE, PARAMETER_CONDITION, OPERATIONS_FLOW,
                    INPUT_CONTENT_TYPE, AUTHENTICATION, STATUS_CODE, STATUS_CODE_CLASS, RESPONSE_BODY_PROPERTIES,
                    OUTPUT_CONTENT_TYPE);
        } else if (type.equals("input")) {
            types = Arrays.asList(PATH, OPERATION, PARAMETER, PARAMETER_VALUE, PARAMETER_CONDITION, OPERATIONS_FLOW,
                    INPUT_CONTENT_TYPE, AUTHENTICATION);
        } else if (type.equals("output")) {
            types = Arrays.asList(STATUS_CODE, STATUS_CODE_CLASS, RESPONSE_BODY_PROPERTIES, OUTPUT_CONTENT_TYPE);
        } else {
            throw new IllegalArgumentException("Illegal criterion type: " + type + ". Can only be 'input', 'output' or null.");
        }

        return types;
    }
}
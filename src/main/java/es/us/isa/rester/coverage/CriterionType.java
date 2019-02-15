package es.us.isa.rester.coverage;

/**
 * All possible criterion types that can be covered, both for inputs and outputs.
 */
public enum CriterionType {
    PATH, OPERATION, PARAMETER, PARAMETER_VALUE,
    PARAMETER_CONDITION, OPERATIONS_FLOW, 
    INPUT_CONTENT_TYPE, AUTHENTICATION, STATUS_CODE,
    STATUS_CODE_CLASS, RESPONSE_BODY_PROPERTIES,
    OUTPUT_CONTENT_TYPE
}
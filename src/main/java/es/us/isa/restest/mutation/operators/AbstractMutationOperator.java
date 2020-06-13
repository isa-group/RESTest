package es.us.isa.restest.mutation.operators;

import es.us.isa.restest.specification.ParameterFeatures;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.v3.oas.models.Operation;

/**
 * @author Alberto Martin-Lopez
 */
public abstract class AbstractMutationOperator {



    /**
     * To be overridden by concrete mutation operator
     * @param tc
     * @param specOperation
     * @return
     */
    public static Boolean mutate(TestCase tc, Operation specOperation) {
        throw new UnsupportedOperationException("Method not supported on this mutation operator");
    }

    /**
     * To be overridden by concrete mutation operator
     * @param tc
     * @return
     */
    public static Boolean mutate(TestCase tc) {
        throw new UnsupportedOperationException("Method not supported on this mutation operator");
    }

    /**
     * Receives a test case, a parameter and a value and sets the parameter to that value.
     * @param tc
     * @param p
     * @param value
     */
    protected static void setParameterToValue(TestCase tc, ParameterFeatures p, String value) {
        switch (p.getIn()) {
            case "query":
                tc.addQueryParameter(p.getName(), value);
                break;
            case "header":
                tc.addHeaderParameter(p.getName(), value);
                break;
            case "path":
                tc.addPathParameter(p.getName(), value);
                break;
            case "formData":
                tc.addFormParameter(p.getName(), value);
                break;
            case "body":
                tc.setBodyParameter(value);
                break;
            default:
                throw new IllegalArgumentException("Parameter type '" + p.getIn() + "' not supported.");
        }
    }

    /**
     * Receives a test case and a parameter and removes the parameter from the test case.
     * @param tc
     * @param p
     */
    protected static void removeParameter(TestCase tc, ParameterFeatures p) {
        switch (p.getIn()) {
            case "query":
                tc.removeQueryParameter(p.getName());
                break;
            case "header":
                tc.removeHeaderParameter(p.getName());
                break;
            case "path":
                tc.removePathParameter(p.getName());
                break;
            case "formData":
                tc.removeFormParameter(p.getName());
                break;
            case "body":
                tc.setBodyParameter(null);
                break;
            default:
                throw new IllegalArgumentException("Parameter type '" + p.getIn() + "' not supported.");
        }
    }
}

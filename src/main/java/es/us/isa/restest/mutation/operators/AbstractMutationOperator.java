package es.us.isa.restest.mutation.operators;

import es.us.isa.restest.specification.OpenAPIParameter;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.v3.oas.models.Operation;

/**
 * @author Alberto Martin-Lopez
 */
public abstract class AbstractMutationOperator {

    // To be overridden by concrete mutation operator
    public static String mutate(TestCase tc, Operation specOperation) {
        throw new UnsupportedOperationException("Method not supported on this mutation operator");
    }

    // To be overridden by concrete mutation operator
    public static String mutate(TestCase tc) {
        throw new UnsupportedOperationException("Method not supported on this mutation operator");
    }


    // To be overridden by concrete mutation operator
    public static String mutate(TestCase tc, OpenAPIParameter param) {
        throw new UnsupportedOperationException("Method not supported on this mutation operator");
    }
}

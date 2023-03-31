package es.us.isa.restest.mutation.operators.invalidvalue;

import es.us.isa.restest.specification.OpenAPIParameter;
import es.us.isa.restest.testcases.TestCase;

/**
 * Mutate a boolean parameter by assigning it an invalid value, i.e., a string,
 * an integer or a double.
 *
 * @author Alberto Martin-Lopez
 */
public class BooleanToInvalid extends AbstractToInvalidOperator {

    private static final String[] mutations = {
            REPLACE_WITH_INT,
            REPLACE_WITH_NUMBER,
            REPLACE_WITH_STRING
    };

    public static String mutate(TestCase tc, OpenAPIParameter param) {
        return mutate(tc, param, mutations);
    }
}

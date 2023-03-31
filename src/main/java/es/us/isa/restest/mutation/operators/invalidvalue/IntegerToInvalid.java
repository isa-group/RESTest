package es.us.isa.restest.mutation.operators.invalidvalue;

import es.us.isa.restest.specification.OpenAPIParameter;
import es.us.isa.restest.testcases.TestCase;

/**
 * Mutate an integer parameter by assigning it a string, a boolean, a double, or
 * violating either a max or min constraint.
 *
 * @author Alberto Martin-Lopez
 */
public class IntegerToInvalid extends AbstractToInvalidOperator {

    private static final String[] mutations = {
            VIOLATE_MAX_CONSTRAINT,
            VIOLATE_MIN_CONSTRAINT,
            REPLACE_WITH_NUMBER,
            REPLACE_WITH_STRING,
            REPLACE_WITH_BOOL
    };

    public static String mutate(TestCase tc, OpenAPIParameter param) {
        return mutate(tc, param, mutations);
    }
}

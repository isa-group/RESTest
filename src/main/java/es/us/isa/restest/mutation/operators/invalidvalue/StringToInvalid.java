package es.us.isa.restest.mutation.operators.invalidvalue;

import es.us.isa.restest.specification.OpenAPIParameter;
import es.us.isa.restest.testcases.TestCase;

/**
 * Mutate a string parameter by assigning it a string, a boolean, or violating either
 * a max or min constraint.
 *
 * @author Alberto Martin-Lopez
 */
public class StringToInvalid extends AbstractToInvalidOperator {

    private static final String[] mutations = {
            VIOLATE_FORMAT_CONSTRAINT,
            VIOLATE_MAX_LENGTH_CONSTRAINT,
            VIOLATE_MIN_LENGTH_CONSTRAINT
    };

    public static String mutate(TestCase tc, OpenAPIParameter param) {
        return mutate(tc, param, mutations);
    }
}

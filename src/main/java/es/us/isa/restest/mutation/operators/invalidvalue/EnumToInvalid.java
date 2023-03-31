package es.us.isa.restest.mutation.operators.invalidvalue;

import es.us.isa.restest.specification.OpenAPIParameter;
import es.us.isa.restest.testcases.TestCase;

/**
 * Mutate an enum parameter by assigning it an out-of-range value, either a string,
 * boolean or integer.
 *
 * @author Alberto Martin-Lopez
 */
public class EnumToInvalid extends AbstractToInvalidOperator {

    private static final String[] mutations= {
            REPLACE_WITH_INT,
            REPLACE_WITH_NUMBER,
            REPLACE_WITH_STRING,
            REPLACE_WITH_BOOL
    };

    public static String mutate(TestCase tc, OpenAPIParameter param) {
        return mutate(tc, param, mutations);
    }
}

package es.us.isa.restest.mutation.operators.invalidvalue;

import es.us.isa.restest.mutation.operators.AbstractMutationOperator;
import es.us.isa.restest.mutation.operators.RemoveRequiredParameter;
import es.us.isa.restest.specification.ParameterFeatures;
import es.us.isa.restest.testcases.TestCase;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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

    public static String mutate(TestCase tc, ParameterFeatures param) {
        return mutate(tc, param, mutations);
    }
}

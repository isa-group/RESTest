package es.us.isa.restest.mutation.operators.invalidvalue;

import es.us.isa.restest.mutation.operators.AbstractMutationOperator;
import es.us.isa.restest.specification.ParameterFeatures;
import es.us.isa.restest.testcases.TestCase;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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

    public static String mutate(TestCase tc, ParameterFeatures param) {
        return mutate(tc, param, mutations);
    }
}

package es.us.isa.restest.mutation.operators.invalidvalue;

import es.us.isa.restest.mutation.operators.AbstractMutationOperator;
import es.us.isa.restest.specification.ParameterFeatures;
import es.us.isa.restest.testcases.TestCase;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static es.us.isa.restest.mutation.operators.invalidvalue.InvalidParameterValue.INTEGER_TYPE;
import static es.us.isa.restest.mutation.operators.invalidvalue.InvalidParameterValue.NUMBER_TYPE;

/**
 * Mutate a number parameter by assigning it a string, a boolean, or violating either
 * a max or min constraint.
 *
 * @author Alberto Martin-Lopez
 */
public class NumberToInvalid extends AbstractToInvalidOperator {

    private static final String[] mutations = {
            VIOLATE_MAX_CONSTRAINT,
            VIOLATE_MIN_CONSTRAINT,
            REPLACE_WITH_STRING,
            REPLACE_WITH_BOOL
    };

    public static String mutate(TestCase tc, ParameterFeatures param) {
        return mutate(tc, param, mutations);
    }
}

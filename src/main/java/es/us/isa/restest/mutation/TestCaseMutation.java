package es.us.isa.restest.mutation;

import es.us.isa.restest.mutation.operators.InvalidParameterValue;
import es.us.isa.restest.mutation.operators.RemoveRequiredParameter;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.v3.oas.models.Operation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Set of static methods to mutate a test case, e.g. to make it faulty.
 *
 * @author Alberto Martin-Lopez
 */
public class TestCaseMutation {

    private TestCaseMutation() {
        //Utility class
    }

    private static final int N_FAULTY_MUTATIONS = 2;

    /**
     * Given a valid (nominal) test case, if possible, mutate it and convert it into an invalid (faulty) test case.
     * Set of possible mutations:
     * <ol>
     *     <li>Remove required parameter.</li>
     *     <li>Change type of parameter value (e.g. use a string for an integer parameter).</li>
     *     <li>Violate a constraint of a parameter (e.g. use an integer value higher than the maximum.</li>
     *     <li>TODO: Body with invalid structure (only applicable to create or update operations).</li>
     *     <li>TODO: Violate inter-parameter dependencies.</li>
     * </ol>
     * @param nominalTestCase Original valid test case. NOTE: If the mutation is applied, the original
     *                        {@code nominalTestCase} object will not be preserved, it should be cloned
     *                        before calling this method.
     * @return True if the test case turned faulty, false otherwise
     */
    public static Boolean makeTestCaseFaulty(TestCase nominalTestCase, Operation specOperation) {
        List<Boolean> mutationsTried = new ArrayList<>(Arrays.asList(new Boolean[N_FAULTY_MUTATIONS])); // In case one mutation cannot be applied, try others
        Collections.fill(mutationsTried, Boolean.FALSE);
        float randomVal;

        while (mutationsTried.contains(Boolean.FALSE)) {
            randomVal = ThreadLocalRandom.current().nextFloat();
            if (!mutationsTried.get(0) && randomVal < 1f / N_FAULTY_MUTATIONS) { // Remove required parameter
                if (RemoveRequiredParameter.mutate(nominalTestCase, specOperation)) // If mutation was applied
                    return true; // Stop loop
                else
                    mutationsTried.set(0, true); // This mutation cannot be applied, do not try again
            } else if (!mutationsTried.get(1) && randomVal < 2f / N_FAULTY_MUTATIONS) {
                if (InvalidParameterValue.mutate(nominalTestCase, specOperation)) // If mutation was applied
                    return true; // Stop loop
                else
                    mutationsTried.set(1, true); // This mutation cannot be applied, do not try again
            } else {
                // TODO: Support other faulty test cases: Body with invalid structure, and violation of inter-parameter dependencies
            }
        }

        return false; // If all mutations have been tried without success, the test case was not mutated, return false
    }
}

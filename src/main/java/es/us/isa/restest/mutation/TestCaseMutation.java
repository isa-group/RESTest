package es.us.isa.restest.mutation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import es.us.isa.restest.mutation.operators.InvalidParameterValue;
import es.us.isa.restest.mutation.operators.RemoveRequiredParameter;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.v3.oas.models.Operation;

/**
 * This class implements method for mutating a test case based on a set of mutation operators.
 *
 * @author Alberto Martin-Lopez
 */
public class TestCaseMutation {

	private static String[] mutationOperators= {"INVALID_VALUE", "REMOVE_REQUIRED_PARAMETER"};
	

    /**
     * Given a valid (nominal) test case, if possible, mutate it and convert it into an invalid (faulty) test case.
     * Set of possible mutations:
     * <ol>
     *     <li>Remove required parameter.</li>
     *     <li>Change type of parameter value (e.g. use a string for an integer parameter).</li>
     *     <li>Violate a constraint of a parameter (e.g. use an integer value higher than the maximum.</li>
     * </ol>
     * @param nominalTestCase Original valid test case. NOTE: If the mutation is applied, the original
     *                        {@code nominalTestCase} object will not be preserved, it should be cloned
     *                        before calling this method.
     * @return True if the test case turned faulty, false otherwise
     */
	public static boolean mutate(TestCase nominalTestCase, Operation specOperation) {
		boolean mutationApplied = false;
		
		// Shuffle list of operators
		List<String> operators = Arrays.asList(mutationOperators);
		Collections.shuffle(operators);
		
		int index = 0;
		while (index<operators.size() && !mutationApplied) {
			switch(operators.get(index)) {
				case "INVALID_VALUE":
					mutationApplied = InvalidParameterValue.mutate(nominalTestCase, specOperation);
					break;
				case "REMOVE_REQUIRED_PARAMETER":
					mutationApplied = RemoveRequiredParameter.mutate(nominalTestCase, specOperation);
					break;
				default:
			}
			index++;		
		}
		
		return mutationApplied;
	}
	
}

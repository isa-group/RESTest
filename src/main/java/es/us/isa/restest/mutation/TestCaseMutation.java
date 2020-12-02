package es.us.isa.restest.mutation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import es.us.isa.restest.mutation.operators.invalidvalue.InvalidParameterValue;
import es.us.isa.restest.mutation.operators.RemoveRequiredParameter;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.v3.oas.models.Operation;

/**
 * This class implements method for mutating a test case based on a set of mutation operators.
 *
 * @author Alberto Martin-Lopez
 */
public class TestCaseMutation {

	private static final String INVALID_VALUE = "INVALID_VALUE";
	private static final String REMOVE_REQUIRED_PARAMETER = "REMOVE_REQUIRED_PARAMETER";
	private static final String[] mutationOperators= {INVALID_VALUE, REMOVE_REQUIRED_PARAMETER};
	

    /**
     * Given a valid (nominal) test case, if possible, mutate it and convert it into an invalid (faulty) test case.
     * Set of possible mutations:
     * <ol>
     *     <li>Remove required parameter.</li>
     *     <li>Change type of parameter value (e.g. use a string for an integer parameter).</li>
     *     <li>Violate a constraint of a parameter (e.g. use an integer value higher than the maximum.</li>
     * </ol>
     * @param testCase Original valid test case. NOTE: If the mutation is applied, the original
     *                        {@code nominalTestCase} object will not be preserved, it should be cloned
     *                        before calling this method.
	 * @param specOperation OpenAPI operation related to the test case.
     * @return a string indicating the mutation operator applied, empty if none.
     */
	public static String mutate(TestCase testCase, Operation specOperation) {
		String mutationApplied = "";

		// Shuffle list of operators
		List<String> operators = Arrays.asList(mutationOperators);
		Collections.shuffle(operators);
		
		int index = 0;
		while (index<operators.size() && mutationApplied.equals("")) {
			switch(operators.get(index)) {
				case INVALID_VALUE:
					mutationApplied = InvalidParameterValue.mutate(testCase, specOperation);
					break;
				case REMOVE_REQUIRED_PARAMETER:
					mutationApplied = RemoveRequiredParameter.mutate(testCase, specOperation);
					break;
				default:
			}
			index++;		
		}
		
		return mutationApplied;
	}
	
}

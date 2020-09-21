package es.us.isa.restest.searchbased.operators;

import es.us.isa.restest.testcases.TestCase;
import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;

public class RemoveTestCaseMutation extends AbstractMutationOperator {

	public RemoveTestCaseMutation(double mutationProbability, PseudoRandomGenerator randomGenerator) {
		super(mutationProbability, randomGenerator);		
	}

	@Override
	protected void doMutation(double mutationProbability, RestfulAPITestSuiteSolution solution) {
		// If we are solving a fixed suite size problem, we perform no mutation 
		if(solution.getProblem().getFixedTestSuiteSize()!=null)
			return;
		// If the size of the suite is not maximal
		if (solution.getVariables().size()>solution.getProblem().getMinTestSuiteSize() 
				&& getRandomGenerator().nextDouble() <= mutationProbability) {
			// We remove one of the test cases of the suite chosen randomly
			int removedTestCaseIndex = getRandomGenerator().nextInt(0, solution.getVariables().size()-1);
			TestCase removedTestCase = solution.getVariable(removedTestCaseIndex);
			solution.removeVariable(removedTestCaseIndex);
			solution.removeTestResult(removedTestCase.getId());
		}
	}

}

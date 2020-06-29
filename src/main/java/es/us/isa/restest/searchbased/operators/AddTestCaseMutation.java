package es.us.isa.restest.searchbased.operators;

import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;

public class AddTestCaseMutation extends AbstractAPITestCaseMutationOperator {

	public AddTestCaseMutation(double mutationProbability, PseudoRandomGenerator randomGenerator) {
		super(mutationProbability, randomGenerator);
	}

	@Override
	protected void doMutation(double mutationProbability, RestfulAPITestSuiteSolution solution) {
		// If we are solving a fixed suite size problem, we perform no mutation 
		if(solution.getProblem().getFixedTestSuiteSize()!=null)
			return;
		// If the size of the suite is not maximal
		if(solution.getVariables().size()<solution.getProblem().getMaxTestSuiteSize()) {
			// We add a random test case to the suite:
			solution.getVariables().add(solution.getProblem().createRandomTestCase());
		}
	}
		

}

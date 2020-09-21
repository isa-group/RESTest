package es.us.isa.restest.searchbased.operators;

import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;

import static es.us.isa.restest.searchbased.operators.Utils.resetTestResult;

public class ResourceChangeMutation extends AbstractMutationOperator {



public ResourceChangeMutation(double mutationProbability, PseudoRandomGenerator randomGenerator) {
		super(mutationProbability, randomGenerator);		
	}

@Override
protected void doMutation(double mutationProbability, RestfulAPITestSuiteSolution solution) {
	for (TestCase testCase : solution.getVariables()) {
		if (getRandomGenerator().nextDouble() <= mutationProbability) {
			doMutation(testCase,solution);
			resetTestResult(testCase.getId(), solution); // The test case changed, reset test result
		}
	}
	
}

private void doMutation(TestCase testCase, RestfulAPITestSuiteSolution solution) {
	
	
}

}
package es.us.isa.restest.searchbased.operators;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

import es.us.isa.restest.searchbased.AbstractSearchBasedTest;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteGenerationProblem;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import io.qameta.allure.junit4.DisplayName;

public class RemoveTestCaseMutationTest extends AbstractSearchBasedTest{
	@Test
	@DisplayName("With a mutation probability of 0 there is no changes due to mutation")
	public void executeWithNoProbabilityTest() {
		for(RestfulAPITestSuiteGenerationProblem problem:createTestProblems()) {
			// Arrangment | fixture
			RestfulAPITestSuiteSolution solution = problem.createSolution();
			RestfulAPITestSuiteSolution expectedResult = solution.copy();

			RemoveTestCaseMutation operator = new RemoveTestCaseMutation(0.0, JMetalRandom.getInstance().getRandomGenerator());

			// Act (SUT invocation)
			RestfulAPITestSuiteSolution result = operator.execute(solution.copy());

			// Assert: There is no changes due to the mutation:
			assertTrue(result.equals(expectedResult));
		}
	}

	@Test
	@DisplayName("With a mutation probability of 1 the suite has lost one test case (or its size was minimal)")
	public void executeWithFullProbabilityTest() {
		for(RestfulAPITestSuiteGenerationProblem problem:createTestProblems()) {
			// Arrangment | fixture
			RestfulAPITestSuiteSolution solution = problem.createSolution();			

			RemoveTestCaseMutation operator = new RemoveTestCaseMutation(1.0, JMetalRandom.getInstance().getRandomGenerator());

			// Act (SUT invocation)
			RestfulAPITestSuiteSolution result = operator.execute(solution.copy());

			// Assert: Either there is changes due to the mutation or the test suite size was minimal:
			assertFalse(result.equals(solution) && solution.getVariables().size()>solution.getProblem().getMinTestSuiteSize());
			// Assert: Either the mutated suite has one additional test case or the test size was minimal:
			assertTrue(result.getVariables().size()==solution.getVariables().size()-1 || solution.getVariables().size()==solution.getProblem().getMinTestSuiteSize());
			// Assert: The original suite contains all the test cases of the mutated tests suite
			assertTrue(solution.getVariables().containsAll(result.getVariables()));
			
		}
	}
}

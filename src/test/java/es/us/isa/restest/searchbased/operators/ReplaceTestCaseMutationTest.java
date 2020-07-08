package es.us.isa.restest.searchbased.operators;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

import es.us.isa.restest.searchbased.AbstractSearchBasedTest;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteGenerationProblem;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import io.qameta.allure.junit4.DisplayName;

public class ReplaceTestCaseMutationTest extends AbstractSearchBasedTest {
	@Test
	@DisplayName("With a mutation probability of 0 there is no changes due to mutation")
	public void executeWithNoProbabilityTest() {
		for(RestfulAPITestSuiteGenerationProblem problem:createTestProblems()) {
			// Arrangement / Fixture		
			RestfulAPITestSuiteSolution solution= problem.createSolution();
			RestfulAPITestSuiteSolution expectedResult=solution.copy(); 
		
			ReplaceTestCaseMutation operator=new  ReplaceTestCaseMutation(0.0, JMetalRandom.getInstance().getRandomGenerator());
		
			// Act (SUT invocation)		
			RestfulAPITestSuiteSolution result=operator.execute(solution.copy());
		
			// Assert: There is no changes due to the mutation:
			assertTrue(result.equals(expectedResult));
		}
	}
	
	@Test
	@DisplayName("With a mutation probability of 1 the suite has lost one test case (or its size was minimal)")
	public void executeWithFullProbabilityTest() {
		for(RestfulAPITestSuiteGenerationProblem problem:createTestProblems()) {
			// Arrangement / Fixture		
			RestfulAPITestSuiteSolution solution= problem.createSolution();
			RestfulAPITestSuiteSolution expectedResult=solution.copy(); 
		
			ReplaceTestCaseMutation operator=new  ReplaceTestCaseMutation(1.0, JMetalRandom.getInstance().getRandomGenerator());
		
			// Act (SUT invocation)		
			RestfulAPITestSuiteSolution result=operator.execute(solution.copy());
		
			// Assert: There is changes due to the mutation:
			assertFalse(result.equals(expectedResult));
			// Assert: The size of the mutated suite is the same as of the original suite:
			assertTrue(result.getVariables().size()==solution.getVariables().size());
		}
	}

}

package es.us.isa.restest.searchbased.operators;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

import es.us.isa.restest.searchbased.AbstractSearchBasedTest;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteGenerationProblem;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;
import io.qameta.allure.junit4.DisplayName;

public class ParameterAdditionMutationTest extends AbstractSearchBasedTest {
	
	@Test
	@DisplayName("With a mutation probability of 0 there is no changes due to mutation")
	public void executeWithNoProbabilityTest() {
		for(RestfulAPITestSuiteGenerationProblem problem:createTestProblems()){		
			// Arrangment | fixture
			RestfulAPITestSuiteSolution solution= problem.createSolution();
			RestfulAPITestSuiteSolution expectedResult=solution.copy(); 
		
			AddParameterMutation operator=new  AddParameterMutation(0.0, JMetalRandom.getInstance().getRandomGenerator());
		
			// Act (SUT invocation)		
			RestfulAPITestSuiteSolution result=operator.execute(solution.copy());
		
			// Assert: There is no changes due to the mutation:
			result.equals(expectedResult);
		}
	}
	
	@Test
	@DisplayName("With a mutation probability of 1 all the test cases have additional parameters (or are full)")
	public void executeWithFullProbabilityTest() {
	
		List<RestfulAPITestSuiteGenerationProblem> problems=createTestProblems();		
		for(RestfulAPITestSuiteGenerationProblem problem:problems) {
			// Arrangement:
			RestfulAPITestSuiteSolution solution= problem.createSolution();		 		
			AddParameterMutation operator=new  AddParameterMutation(1.0, JMetalRandom.getInstance().getRandomGenerator());
		
			// Act (SUT invocation)		
			RestfulAPITestSuiteSolution result=operator.execute(solution.copy());
		
			// Assert: There are additional parameters on each testcase (or they are not modified if they have all the parameters)
			TestCase originalTestCase;
			TestCase mutatedTestCase;
			for(int i=0;i<result.getVariables().size();i++) {
				originalTestCase=solution.getVariable(i);
				mutatedTestCase=result.getVariable(i);
				// Path parameters:
				assertTrue(mutatedTestCase.getPathParameters().keySet().containsAll(originalTestCase.getPathParameters().keySet()));
				assertTrue(mutatedTestCase.getPathParameters().entrySet().size()>=originalTestCase.getPathParameters().entrySet().size());
				// Query parameters:
				assertTrue(mutatedTestCase.getQueryParameters().entrySet().containsAll(originalTestCase.getQueryParameters().entrySet()));
				assertTrue(mutatedTestCase.getQueryParameters().entrySet().size()>=originalTestCase.getQueryParameters().entrySet().size());
				// Header parameters:
				assertTrue(mutatedTestCase.getHeaderParameters().entrySet().containsAll(originalTestCase.getHeaderParameters().entrySet()));
				assertTrue(mutatedTestCase.getHeaderParameters().entrySet().size()>=originalTestCase.getHeaderParameters().entrySet().size());
				// TODO: Body parameters testing.
		}
		}
	}
}

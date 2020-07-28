package es.us.isa.restest.searchbased.operators;

import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

import es.us.isa.restest.searchbased.AbstractSearchBasedTest;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteGenerationProblem;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.searchbased.SearchBasedTestSuiteGenerator;
import es.us.isa.restest.searchbased.objectivefunction.SuiteSize;
import es.us.isa.restest.testcases.TestCase;
import io.qameta.allure.junit4.DisplayName;

public class ParameterRemovalMutationTest extends AbstractSearchBasedTest {
	@Test
	@DisplayName("With a mutation probability of 0 there is no changes due to mutation")
	public void executeWithNoProbabilityTest() {
		for(RestfulAPITestSuiteGenerationProblem problem:createTestProblems()) {
			// Arrangement / Fixture		
			RestfulAPITestSuiteSolution solution= problem.createSolution();
			RestfulAPITestSuiteSolution expectedResult=solution.copy(); 
		
			RemoveParameterMutation operator=new  RemoveParameterMutation(0.0, JMetalRandom.getInstance().getRandomGenerator());
		
			// Act (SUT invocation)		
			RestfulAPITestSuiteSolution result=operator.execute(solution.copy());
		
			// Assert: There is no changes due to the mutation:
			result.equals(expectedResult);
		}
	}
	
	@Test
	@DisplayName("With a mutation probability of 1 all the test cases have less parameters (or are empty)")
	public void executeWithFullProbabilityTest() {
		for(RestfulAPITestSuiteGenerationProblem problem:createTestProblems()) {
			// Arrangement / Fixture		
			RestfulAPITestSuiteSolution solution= problem.createSolution();		 
		
			RemoveParameterMutation operator=new  RemoveParameterMutation(1.0, JMetalRandom.getInstance().getRandomGenerator(),true);
		
			// Act (SUT invocation)		
			RestfulAPITestSuiteSolution result=operator.execute(solution.copy());
		
			// Assert: There are additional parameters on each testcase (or they are not modified if they have all the parameters)
			TestCase originalTestCase;
			TestCase mutatedTestCase;
			for(int i=0;i<result.getVariables().size();i++) {
				originalTestCase=solution.getVariable(i);
				mutatedTestCase=result.getVariable(i);
				// 	Path parameters:
				assertTrue(originalTestCase.getPathParameters().entrySet().containsAll(mutatedTestCase.getPathParameters().entrySet()));
				assertTrue(mutatedTestCase.getPathParameters().entrySet().size()<originalTestCase.getPathParameters().entrySet().size() 
						|| originalTestCase.getPathParameters().size()==0);
				// Query parameters:
				assertTrue(originalTestCase.getQueryParameters().entrySet().containsAll(mutatedTestCase.getQueryParameters().entrySet()));
				assertTrue(mutatedTestCase.getQueryParameters().entrySet().size()<originalTestCase.getQueryParameters().entrySet().size() 
						|| originalTestCase.getQueryParameters().size()==0);
				// Header parameters:
				assertTrue(originalTestCase.getHeaderParameters().entrySet().containsAll(mutatedTestCase.getHeaderParameters().entrySet()));
				assertTrue(mutatedTestCase.getHeaderParameters().entrySet().size()<originalTestCase.getHeaderParameters().entrySet().size() 
						|| originalTestCase.getHeaderParameters().size()==0);
				// TODO: Body parameters testing.
			}
		}
	}
}

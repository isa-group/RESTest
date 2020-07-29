package es.us.isa.restest.searchbased.objectivefunction;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import es.us.isa.restest.searchbased.AbstractSearchBasedTest;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteGenerationProblem;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.searchbased.objectivefunction.RestfulAPITestingObjectiveFunction.ObjectiveFunctionType;
import io.qameta.allure.junit4.DisplayName;

public class ValidTestRatioTest extends AbstractSearchBasedTest{

	@Test
	@DisplayName("For the bikewise api, since it has no constraints the ratio of valid is 1")
	public void evaluateTest() {
		for(RestfulAPITestSuiteGenerationProblem problem:createTestProblems()) {
			if(problem.getName().equals("Bikewise API v2")) {				
				RestfulAPITestSuiteSolution sol=problem.createSolution();
				ValidTestsRatio sut=new ValidTestsRatio(ObjectiveFunctionType.MINIMIZATION);
				Double expectedValue=1.0;
				assertEquals(sut.evaluate(sol),expectedValue);
			}
		}
	}
}

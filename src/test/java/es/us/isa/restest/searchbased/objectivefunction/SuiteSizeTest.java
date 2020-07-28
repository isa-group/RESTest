package es.us.isa.restest.searchbased.objectivefunction;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import es.us.isa.restest.searchbased.AbstractSearchBasedTest;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteGenerationProblem;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import io.qameta.allure.junit4.DisplayName;

public class SuiteSizeTest extends AbstractSearchBasedTest {
	@Test
	@DisplayName("In problems with max and min test suite sizes of 2.0, the size obj. func. returns 2.0")
	public void evaluateTest() {
		for(RestfulAPITestSuiteGenerationProblem problem:createTestProblems()) {
			// Arrangement:
			RestfulAPITestSuiteSolution solution=problem.createSolution();
			SuiteSize sut=new SuiteSize();
			
			// Act:
			Double value=sut.evaluate(solution);
			// Assert:
			Double expectedValue=4.0;
			assertEquals(value,expectedValue);
		}
	}
}

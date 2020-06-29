package es.us.isa.restest.searchbased.operators;

import java.util.List;

import org.junit.Test;

import es.us.isa.restest.searchbased.AbstractSearchBasedTest;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteGenerationProblem;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import io.qameta.allure.junit4.DisplayName;

public class UniformTestCaseCrossoverTest extends AbstractSearchBasedTest {

	@Test
	@DisplayName("With a crossover probability of 0 there is no changes due to crossover")
	public void executeTest() {
		for(RestfulAPITestSuiteGenerationProblem problem:createTestProblems()) {
			// Arrange | Fixture:
			RestfulAPITestSuiteSolution parent1=problem.createSolution();
			RestfulAPITestSuiteSolution parent2=problem.createSolution();
			List<RestfulAPITestSuiteSolution> parents=List.of(parent1,parent2);
			UniformTestCaseCrossover sut=new UniformTestCaseCrossover(0.0);
			// Act (SUT invocation):
			List<RestfulAPITestSuiteSolution> offspring=sut.execute(parents);			
			// Assert:
			parent1.equals(offspring.get(0));
			parent2.equals(offspring.get(1));
		}
	}
		
	@Test
	@DisplayName("With a crossover probability of 1 there is no changes due to crossover (but offspring order is exchanged)")
	public void executeTest2() {
		for(RestfulAPITestSuiteGenerationProblem problem:createTestProblems()) {
			// Arrange | Fixture:
			RestfulAPITestSuiteSolution parent1=problem.createSolution();
			RestfulAPITestSuiteSolution parent2=problem.createSolution();
			List<RestfulAPITestSuiteSolution> parents=List.of(parent1,parent2);
			UniformTestCaseCrossover sut=new UniformTestCaseCrossover(1.0);
			// Act (SUT invocation):
			List<RestfulAPITestSuiteSolution> offspring=sut.execute(parents);			
			// Assert:
			parent1.equals(offspring.get(1));
			parent2.equals(offspring.get(0));
		}
	}
	
}

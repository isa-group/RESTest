package es.us.isa.restest.searchbased.algorithms;

import java.util.Comparator;
import java.util.List;

import es.us.isa.restest.searchbased.SearchBasedTestSuiteGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.uma.jmetal.operator.CrossoverOperator;
import org.uma.jmetal.operator.MutationOperator;
import org.uma.jmetal.operator.SelectionOperator;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.util.evaluator.SolutionListEvaluator;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteGenerationProblem;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.searchbased.terminationcriteria.TerminationCriterion;

public class NSGAII extends org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAII<RestfulAPITestSuiteSolution> implements SearchBasedAlgorithm {

	private static final Logger logger = LogManager.getLogger(NSGAII.class.getName());
	
	public NSGAII(RestfulAPITestSuiteGenerationProblem problem, 
			int populationSize,int matingPoolSize, int offspringPopulationSize,
			CrossoverOperator<RestfulAPITestSuiteSolution> crossoverOperator,
			MutationOperator<RestfulAPITestSuiteSolution> mutationOperator,
			SelectionOperator<List<RestfulAPITestSuiteSolution>, RestfulAPITestSuiteSolution> selectionOperator,
			SolutionListEvaluator<RestfulAPITestSuiteSolution> evaluator,
			TerminationCriterion terminationCriterion) {
		super(problem, 1, populationSize, matingPoolSize, offspringPopulationSize, crossoverOperator,
				mutationOperator, selectionOperator, evaluator);
		this.problem=problem;
		this.terminationCriterion=terminationCriterion;
	}

	private TerminationCriterion terminationCriterion;
	private RestfulAPITestSuiteGenerationProblem problem;
	
	@Override
	public long getEvaluations() {
		return evaluations;
	}

	@Override
	public RestfulAPITestSuiteGenerationProblem getProblem() {
		return problem;
	}
	
	@Override protected boolean isStoppingConditionReached() {
		logger.info("Checking whether stopping criterion has been reached...");
	    return terminationCriterion.test(this);
	  }

	@Override
	public List<RestfulAPITestSuiteSolution> currentSolutions() {
		return getResult();
	}
}

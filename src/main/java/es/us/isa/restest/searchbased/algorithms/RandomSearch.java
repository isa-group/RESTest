package es.us.isa.restest.searchbased.algorithms;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteGenerationProblem;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.searchbased.terminationcriteria.TerminationCriterion;

import java.util.List;

import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.solution.Solution;
import org.uma.jmetal.util.archive.impl.NonDominatedSolutionListArchive;

public class RandomSearch implements SearchBasedAlgorithm {

	private TerminationCriterion terminationCriterion;
	private RestfulAPITestSuiteGenerationProblem problem;
	private long evaluations;
	NonDominatedSolutionListArchive<RestfulAPITestSuiteSolution> nonDominatedArchive;

	public RandomSearch(RestfulAPITestSuiteGenerationProblem problem, TerminationCriterion tc) {
		this.terminationCriterion = tc;
		this.problem = problem;
		nonDominatedArchive = new NonDominatedSolutionListArchive<>();
	}

	@Override
	public void run() {
		evaluations = 0;
		RestfulAPITestSuiteSolution newSolution;
		while (!terminationCriterion.test(this)) {
			newSolution = problem.createSolution();
			problem.evaluate(newSolution);
			evaluations++;
			nonDominatedArchive.add(newSolution);
		}
	}

	

	@Override
	public List<RestfulAPITestSuiteSolution> getResult() {
		return nonDominatedArchive.getSolutionList();
	}

	@Override
	public String getName() {
		return "RS";
	}

	@Override
	public String getDescription() {
		return "Multi-objective random search algorithm";
	}

	@Override
	public long getEvaluations() {
		return evaluations;
	}

	@Override
	public RestfulAPITestSuiteGenerationProblem getProblem() {
		return problem;
	}

	@Override
	public List<RestfulAPITestSuiteSolution> currentSolutions() {
		return nonDominatedArchive.getSolutionList();
	}
}

package es.us.isa.restest.searchbased.algorithms;

import java.util.List;

import org.uma.jmetal.algorithm.Algorithm;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteGenerationProblem;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;

public interface SearchBasedAlgorithm extends Algorithm<List<RestfulAPITestSuiteSolution>>{
	public long getEvaluations();
	public RestfulAPITestSuiteGenerationProblem getProblem();
	public List<RestfulAPITestSuiteSolution> currentSolutions();
}

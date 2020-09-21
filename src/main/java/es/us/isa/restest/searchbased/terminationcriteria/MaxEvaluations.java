package es.us.isa.restest.searchbased.terminationcriteria;

import es.us.isa.restest.searchbased.SearchBasedTestSuiteGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAII;
import org.uma.jmetal.algorithm.multiobjective.randomsearch.RandomSearch;

import es.us.isa.restest.searchbased.algorithms.SearchBasedAlgorithm;

public class MaxEvaluations implements TerminationCriterion {

	private int maxEvaluations;
	private static final Logger logger = LogManager.getLogger(MaxEvaluations.class.getName());
	
	public MaxEvaluations(int maxEvaluations) {
		this.maxEvaluations=maxEvaluations;
	}
	
	@Override
	public boolean test(SearchBasedAlgorithm t) {
		logger.info("Stopping criterion state: " + t.getEvaluations() + " / " + maxEvaluations);
		return t.getEvaluations()>=maxEvaluations;
	}

}

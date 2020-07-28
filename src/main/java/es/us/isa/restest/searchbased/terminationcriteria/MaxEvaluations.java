package es.us.isa.restest.searchbased.terminationcriteria;

import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAII;
import org.uma.jmetal.algorithm.multiobjective.randomsearch.RandomSearch;

import es.us.isa.restest.searchbased.algorithms.SearchBasedAlgorithm;

public class MaxEvaluations implements TerminationCriterion {

	private int maxEvaluations;	
	
	public MaxEvaluations(int maxEvaluations) {		
	}
	
	@Override
	public boolean test(SearchBasedAlgorithm t) {
		return t.getEvaluations()>=maxEvaluations;
	}

}

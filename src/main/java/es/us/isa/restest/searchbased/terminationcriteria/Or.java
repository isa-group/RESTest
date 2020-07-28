package es.us.isa.restest.searchbased.terminationcriteria;

import es.us.isa.restest.searchbased.algorithms.SearchBasedAlgorithm;

public class Or implements TerminationCriterion {

	TerminationCriterion clause1,clause2;
	
	public Or(TerminationCriterion clause1,TerminationCriterion clause2) {
		this.clause1=clause1;
		this.clause2=clause2;
	}
	
	public static Or of(TerminationCriterion clause1,TerminationCriterion clause2) {
		return new Or(clause1,clause2);
	}
	
	public static Or or(TerminationCriterion clause1,TerminationCriterion clause2) {
		return new Or(clause1,clause2);
	}

	@Override
	public boolean test(SearchBasedAlgorithm t) {		
		return clause1.test(t) || clause2.test(t);
	}
}

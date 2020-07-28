package es.us.isa.restest.searchbased.terminationcriteria;

import es.us.isa.restest.searchbased.algorithms.SearchBasedAlgorithm;

public class MaxExecutedRequests implements TerminationCriterion {

	private long maxRequestsToBeExecuted;
	
	public MaxExecutedRequests(long maxRequestsToBeExecuted) {
		this.maxRequestsToBeExecuted=maxRequestsToBeExecuted;
	}
	
	@Override
	public boolean test(SearchBasedAlgorithm t) {		
		return t.getProblem().getTestCasesExecuted()>=maxRequestsToBeExecuted;
	}

}

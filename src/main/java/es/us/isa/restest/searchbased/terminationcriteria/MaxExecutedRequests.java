package es.us.isa.restest.searchbased.terminationcriteria;

import es.us.isa.restest.searchbased.algorithms.SearchBasedAlgorithm;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MaxExecutedRequests implements TerminationCriterion {

	private long maxRequestsToBeExecuted;
	private static final Logger logger = LogManager.getLogger(MaxExecutedRequests.class.getName());

	public MaxExecutedRequests(long maxRequestsToBeExecuted) {
		this.maxRequestsToBeExecuted=maxRequestsToBeExecuted;
	}
	
	@Override
	public boolean test(SearchBasedAlgorithm t) {
		logger.info("Stopping criterion state: " + t.getProblem().getTestCasesExecuted() + " / " + maxRequestsToBeExecuted);
		return t.getProblem().getTestCasesExecuted()>=maxRequestsToBeExecuted;
	}

}

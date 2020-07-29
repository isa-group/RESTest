package es.us.isa.restest.searchbased.terminationcriteria;

import java.util.ArrayList;
import java.util.List;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.searchbased.algorithms.SearchBasedAlgorithm;
import es.us.isa.restest.testcases.TestResult;

public class ResponseCodesObtained implements TerminationCriterion {

	private List<Integer> requiredResponseCodes=null;

	public ResponseCodesObtained(List<Integer> codes) {
		this.requiredResponseCodes=new ArrayList<>(codes);
	}
	
	@Override
	public boolean test(SearchBasedAlgorithm t) {
		boolean result=true;
		for(int i=0;i<requiredResponseCodes.size() && result;i++) {
			result=containsResponseCodes(requiredResponseCodes.get(i),t.getResult());
		}
		return result;
	}

	private boolean containsResponseCodes(Integer code, List<RestfulAPITestSuiteSolution> solutions) {
		boolean result=false;
		for(RestfulAPITestSuiteSolution sol:solutions) {
			for(TestResult testResult:sol.getTestResults()) {
					if(code.equals(Integer.parseInt(testResult.getStatusCode())))
							return true;
			}
		}
		return result;
	}

}

package es.us.isa.restest.searchbased.objectivefunction;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import org.javatuples.Triplet;

import java.util.ArrayList;
import java.util.List;

/**
 * This function maximizes the total number of different failures uncovered by the
 * test suite. Each test case that fails and has a unique response error message
 * counts as 1. If two test cases return the same error message but they belong
 * to different API operations or have different status codes, both count.
 *
 * @author Alberto Martin-Lopez
 */
public class UniqueFailures extends RestfulAPITestingObjectiveFunction {

    public UniqueFailures() {
        super(RestfulAPITestingObjectiveFunction.ObjectiveFunctionType.MAXIMIZATION,true,true);
    }

    @Override
    public Double evaluate(RestfulAPITestSuiteSolution solution) {
        List<Triplet<String, String, String>> failures = new ArrayList<>(); // operationId, statusCode, responseBody
        for (TestCase testCase: solution.getVariables()) {
            TestResult testResult = solution.getTestResult(testCase.getId());
            if (testResult.getPassed() != null && !testResult.getPassed()) {
                Triplet<String, String, String> failure = Triplet.with(testCase.getOperationId(), testResult.getStatusCode(), testResult.getResponseBody());
                if (!failures.contains(failure))
                    failures.add(failure);
            }
        }

        return (double) failures.size();
    }
}

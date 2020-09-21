package es.us.isa.restest.searchbased.objectivefunction;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import org.javatuples.Triplet;

import java.util.ArrayList;
import java.util.List;

/**
 * This function maximizes the total number of different failures uncovered by the
 * test suite. Two different modes are possible:<br>
 *     1.- Count all failures whose response body is unique.<br>
 *     2.- Count failures whose response body is different enough from the others
 *         (similarityThreshold).<br>
 *
 * @author Alberto Martin-Lopez
 */
public class UniqueFailures extends RestfulAPITestingObjectiveFunction {

    private double similarityThreshold = 1; // Used to compare how different response bodies are
    private SimilarityMeter similarityMeter = null;

    // Mode 1
    public UniqueFailures() {
        super(RestfulAPITestingObjectiveFunction.ObjectiveFunctionType.MAXIMIZATION,true,true);
    }

    // Mode 2
    public UniqueFailures(SimilarityMeter.METRIC similarityMetric, double similarityThreshold) {
        super(RestfulAPITestingObjectiveFunction.ObjectiveFunctionType.MAXIMIZATION,true,true);
        similarityMeter = new SimilarityMeter(similarityMetric);
        assert(similarityThreshold < 1);
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public Double evaluate(RestfulAPITestSuiteSolution solution) {
        double numOfFailures = getFailures(solution).size();
        logEvaluation(numOfFailures);
        return numOfFailures;
    }

    public List<Triplet<String, String, String>> getFailures(RestfulAPITestSuiteSolution solution) {
        List<Triplet<String, String, String>> failures = new ArrayList<>(); // operationId, statusCode, responseBody
        for (TestCase testCase: solution.getVariables()) {
            TestResult testResult = solution.getTestResult(testCase.getId());
            if (testResult.getPassed() != null && !testResult.getPassed()) {
                Triplet<String, String, String> failure = Triplet.with(testCase.getOperationId(), testResult.getStatusCode(), testResult.getResponseBody());
                if ((similarityThreshold == 1 && !failures.contains(failure))   // Mode 1: If similarity is disabled, just add all failures whose body is unique
                        ||                                                      // OR
                    (similarityThreshold < 1 &&                                 // Mode 2: if similarity is enabled
                        failures.stream().noneMatch(f ->
                        (f.getValue0().equals(failure.getValue0()) &&               // add failures in a new operation with a new status code
                        f.getValue1().equals(failure.getValue1()))
                            &&                                                      // OR in same operation/status code, but different enough
                        similarityMeter.apply(f.getValue2(), failure.getValue2()) > similarityThreshold)))
                    failures.add(failure);
            }
        }

        return failures;
    }
}

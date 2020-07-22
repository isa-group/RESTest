package es.us.isa.restest.searchbased.objectivefunction;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import org.apache.commons.text.similarity.JaccardSimilarity;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.javatuples.Pair;
import org.javatuples.Triplet;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * This function maximizes the total number of different failures uncovered by the
 * test suite. Each test case that fails and has a unique response error message
 * counts as 1. If two test cases return the same error message but they belong
 * to different API operations or have different status codes, both count.
 *
 * @author Alberto Martin-Lopez
 */
public class UniqueFailures extends RestfulAPITestingObjectiveFunction {

    private double similarityThreshold = 1; // Used to compare how different response bodies are
    private JaroWinklerSimilarity similarityMetric;
//    private JaccardSimilarity similarityMetric;

    public UniqueFailures() {
        super(RestfulAPITestingObjectiveFunction.ObjectiveFunctionType.MAXIMIZATION,true,true);
    }

    public UniqueFailures(double similarityThreshold) {
        super(RestfulAPITestingObjectiveFunction.ObjectiveFunctionType.MAXIMIZATION,true,true);
        assert(similarityThreshold < 1);
        this.similarityThreshold = similarityThreshold;
        this.similarityMetric = new JaroWinklerSimilarity();
//        this.similarityMetric = new JaccardSimilarity();
    }

    @Override
    public Double evaluate(RestfulAPITestSuiteSolution solution) {
        List<Triplet<String, String, String>> failures = new ArrayList<>(); // operationId, statusCode, responseBody
        for (TestCase testCase: solution.getVariables()) {
            TestResult testResult = solution.getTestResult(testCase.getId());
            if (testResult.getPassed() != null && !testResult.getPassed()) {
                Triplet<String, String, String> failure = Triplet.with(testCase.getOperationId(), testResult.getStatusCode(), testResult.getResponseBody());
                if ((similarityThreshold == 1 && !failures.contains(failure))   // If similarity is disabled, just add all failures whose body is unique
                        ||                                                      // OR
                    (similarityThreshold < 1 &&                                 // if similarity is enabled
                        failures.stream().noneMatch(f ->
                        (f.getValue0().equals(failure.getValue0()) &&               // add failures in a new operation with a new status code
                        f.getValue1().equals(failure.getValue1()))
                                &&                                                  // OR in same operation/status code, but different enough
                        similarityMetric.apply(f.getValue2(), failure.getValue2()) > similarityThreshold)))
                    failures.add(failure);
            }
        }

        return (double) failures.size();
    }
}

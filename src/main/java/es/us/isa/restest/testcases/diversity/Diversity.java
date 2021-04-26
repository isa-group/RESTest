package es.us.isa.restest.testcases.diversity;

import es.us.isa.restest.testcases.TestCase;

import java.util.List;

/**
 * This function maximizes the globalDiversity of a specific property of several
 * elements. Only one mode is possible:<br>
 *     1.- Diversity among inputs (HTTP requests).<br>
 *
 * globalDiversity is computed as follows: 1) the number of unique pairs
 * operationId-statusCode is counted, and this number is added to globalDiversity;
 * 2) for those pairs whose operationId is the same, diversity (1 - similarity) is
 * computed and added to globalDiversity; 3) globalDiversity is divided by the total
 * number of elements.
 *
 * @author Alberto Martin-Lopez
 */
public class Diversity {

    private SimilarityMeter similarityMeter;
    private boolean normalize; // Diversity measured in [0,1], to avoid bias due to test suite size

    public Diversity(SimilarityMeter.METRIC similarityMetric, boolean normalize) {
        similarityMeter = new SimilarityMeter(similarityMetric);
        this.normalize = normalize;
    }

    public Double evaluate(List<TestCase> testCases) {
        double globalDiversity = 0;
        for (int i=0; i < testCases.size(); i++) {
            TestCase testCase_i = testCases.get(i);
            for (int j=i+1; j < testCases.size(); j++) {
                TestCase testCase_j = testCases.get(j);
                if (testCase_i.getOperationId().equals(testCase_j.getOperationId()))
                    globalDiversity += 1 - similarityMeter.apply(testCase_i.getFlatRepresentation(), testCase_j.getFlatRepresentation());
                else
                    globalDiversity += 1; // Distinct operations count +1 each
            }
        }
        if (normalize)
            globalDiversity /= ((double) (testCases.size() * (testCases.size() - 1)) / 2);

        return globalDiversity;
    }

    public Double evaluate(List<TestCase> testCases, TestCase testCase) {
        double maxSimilarity = 0;
        for (TestCase testCase_i: testCases) {
            if (testCase_i.getOperationId().equals(testCase.getOperationId()))
                maxSimilarity = Math.max(maxSimilarity, similarityMeter.apply(testCase.getFlatRepresentation(), testCase_i.getFlatRepresentation()));
        }
        return 1 - maxSimilarity;
    }

}

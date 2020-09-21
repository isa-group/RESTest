package es.us.isa.restest.searchbased.objectivefunction;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import org.javatuples.Pair;
import org.javatuples.Triplet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This function maximizes the globalDiversity of a specific property of several
 * elements. Three modes are possible:<br>
 *     1.- Diversity among inputs (HTTP requests).<br>
 *     2.- Diversity among outputs (HTTP responses).<br>
 *     3.- Diversity among failures (based on the response body).<br>
 *
 * globalDiversity is computed as follows: 1) the number of unique pairs
 * operationId-statusCode is counted, and this number is added to globalDiversity;
 * 2) for those pairs whose operationId is the same, diversity (1 - similarity) is
 * computed and added to globalDiversity; 3) globalDiversity is divided by the total
 * number of elements.
 *
 * @author Alberto Martin-Lopez
 */
public class Diversity extends RestfulAPITestingObjectiveFunction {

    private ELEMENT elementType = null;
    private SimilarityMeter similarityMeter = null;

    public Diversity(SimilarityMeter.METRIC similarityMetric, ELEMENT element) {
        super(RestfulAPITestingObjectiveFunction.ObjectiveFunctionType.MAXIMIZATION,element != ELEMENT.INPUT,element == ELEMENT.FAILURE);
        elementType = element;
        similarityMeter = new SimilarityMeter(similarityMetric);
    }

    @Override
    public Double evaluate(RestfulAPITestSuiteSolution solution) {
        double globalDiversity = 0;
        switch (elementType){
            case FAILURE:
                List<Triplet<String, String, String>> failures = new UniqueFailures().getFailures(solution); // operationId, statusCode, responseBody
//                globalDiversity += failures.stream().map(f -> Pair.with(f.getValue0(), f.getValue1())).distinct().count(); // Unique operationId-statusCodes count +1 each
                for (int i=0; i < failures.size(); i++) {
                    Triplet<String, String, String> failure_i = failures.get(i);
                    for (int j=i+1; j < failures.size(); j++) {
                        Triplet<String, String, String> failure_j = failures.get(j);
                        if (failure_i.getValue0().equals(failure_j.getValue0()) && failure_i.getValue1().equals(failure_j.getValue1()))
                            globalDiversity += 1 - similarityMeter.apply(failure_i.getValue2(), failure_j.getValue2());
                        else
                            globalDiversity += 1; // Distinct pairs operationId-statusCode count +1 each
                    }
                }
                break;
            case INPUT:
                globalDiversity += solution.getVariables().stream().map(TestCase::getOperationId).distinct().count();
                for (int i=0; i < solution.getVariables().size(); i++) {
                    TestCase testCase_i = solution.getVariables().get(i);
                    for (int j=i+1; j < solution.getVariables().size(); j++) {
                        TestCase testCase_j = solution.getVariables().get(j);
                        if (testCase_i.getOperationId().equals(testCase_j.getOperationId())) {
                            String testCaseRepresentation_i = getTestCaseRepresentation(testCase_i);
                            String testCaseRepresentation_j = getTestCaseRepresentation(testCase_j);
                            globalDiversity += 1 - similarityMeter.apply(testCaseRepresentation_i, testCaseRepresentation_j);
                        } else
                            globalDiversity += 1; // Distinct operations count +1 each
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("The element type " + elementType + " is not supported");
        }

        logEvaluation(globalDiversity);
        return globalDiversity;
    }

    private String getTestCaseRepresentation(TestCase tc) {
        StringBuilder tcRepresentation = new StringBuilder(300);

        tcRepresentation.append(tc.getMethod().toString()); // Method

        String path = tc.getPath(); // Path
        for(String pathParameter : tc.getPathParameters().keySet())
            path=path.replace("{"+pathParameter+"}",tc.getPathParameters().get(pathParameter));
        tcRepresentation.append(path);

        tcRepresentation.append(tc.getInputFormat()); // Content type

        List<String> queryParameters = new ArrayList<>(tc.getQueryParameters().keySet());  // Query parameters
        Collections.sort(queryParameters);
        for(String queryParameter : queryParameters)
            tcRepresentation.append(queryParameter).append(tc.getQueryParameters().get(queryParameter));

        List<String> headerParameters = new ArrayList<>(tc.getHeaderParameters().keySet());  // Header parameters
        Collections.sort(headerParameters);
        for(String headerParameter : headerParameters)
            tcRepresentation.append(headerParameter).append(tc.getHeaderParameters().get(headerParameter));

        List<String> formDataParameters = new ArrayList<>(tc.getFormParameters().keySet());  // FormData parameters
        Collections.sort(formDataParameters);
        for(String formDataParameter : formDataParameters)
            tcRepresentation.append(formDataParameter).append(tc.getFormParameters().get(formDataParameter));

        if (tc.getBodyParameter() != null) // Body
            tcRepresentation.append(tc.getBodyParameter());

        return  tcRepresentation.toString();
    }

    public enum ELEMENT {
        INPUT,
        OUTPUT,
        FAILURE
    }
}

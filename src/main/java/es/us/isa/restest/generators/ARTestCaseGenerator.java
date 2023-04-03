package es.us.isa.restest.generators;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.diversity.Diversity;
import es.us.isa.restest.testcases.diversity.SimilarityMeter;
import es.us.isa.restest.util.RESTestException;
import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * @author José Ramón Fernández
 */

public class ARTestCaseGenerator extends ConstraintBasedTestCaseGenerator {

    private Diversity diversity;
    private Integer numberOfCandidates = 100;
    private List<TestCase> testCases;


    public ARTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
        super(spec, conf, nTests);
        testCases = new ArrayList<>();
    }

    @Override
    public TestCase generateNextTestCase(Operation testOperation, String faultyReason) throws RESTestException {
        Pair<TestCase, Double> bestResult = Pair.with(generateTestCase(testOperation, faultyReason), .0);

        if (!testCases.isEmpty()) {
            for (int i = 0; i < numberOfCandidates-1; i++) {
                TestCase tc = generateTestCase(testOperation, faultyReason);
                if (tc != null) {
                    Double minDistance = diversity.evaluate(testCases, tc);
                    if (minDistance > bestResult.getValue1())
                        bestResult = Pair.with(tc, minDistance);
                }
            }
        }

        TestCase test = bestResult.getValue0();

        if (test != null) {
            testCases.add(test);
            if (test.getFaulty() != null) {
                if (test.getFaulty() && faultyReason.equals(INTER_PARAMETER_DEPENDENCY)) {
                    nFaultyTestDueToDependencyViolations++;
                } else if (test.getFaulty() && faultyReason.equals(INDIVIDUAL_PARAMETER_CONSTRAINT)) {
                    nFaultyTestsDueToIndividualConstraint++;
                }
            }
        }

        return test;
    }

    private TestCase generateTestCase(Operation testOperation, String faultyReason) throws RESTestException {
        TestCase test;
        switch (faultyReason) {
            case "none":
                test = generateValidTestCase(testOperation);
                break;
            case INTER_PARAMETER_DEPENDENCY:
                test = generateFaultyTestCaseDueToViolatedDependencies(testOperation);
                break;
            case INDIVIDUAL_PARAMETER_CONSTRAINT:
                test = generateValidTestCase(testOperation);
                makeTestCaseFaultyDueToIndividualConstraints(test, testOperation);
                break;
            default:
                throw new IllegalArgumentException("The faulty reason '" + faultyReason + "' is not supported.");
        }

        return test;
    }

    public void setDiversity(String similarityMetric) {
        SimilarityMeter.METRIC metric = SimilarityMeter.METRIC.valueOf(similarityMetric);
        this.diversity = new Diversity(metric, true);
    }

    public void setNumberOfCandidates(Integer numberOfCandidates) {
        this.numberOfCandidates = numberOfCandidates;
    }
}

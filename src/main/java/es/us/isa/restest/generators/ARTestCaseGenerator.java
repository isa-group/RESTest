package es.us.isa.restest.generators;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.objectfunction.Diversity;
import es.us.isa.restest.testcases.objectfunction.SimilarityMeter;
import es.us.isa.restest.util.RESTestException;
import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.List;

public class ARTestCaseGenerator extends ConstraintBasedTestCaseGenerator {

    private Diversity diversity;
    private Integer numberOfCandidates = 100;
    private List<TestCase> testCases;


    public ARTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
        super(spec, conf, nTests);
        testCases = new ArrayList<>();
    }

    public TestCase generateNextTestCase(Operation testOperation, String faultyReason) throws RESTestException {
        TestCase test;

        switch (faultyReason) {
            case "none":
                test = generateARValidTestCase(testOperation);
                break;

            case INTER_PARAMETER_DEPENDENCY:
                test = generateARFaultyTestCaseDueToViolatedDependencies(testOperation);
                if (test.getFaulty())
                    nFaultyTestDueToDependencyViolations++;
                break;

            case INDIVIDUAL_PARAMETER_CONSTRAINT:
                test = generateARFaultyTestCaseDueToIndividualConstraints(testOperation);
                if (test != null)
                    nFaultyTestsDueToIndividualConstraint++;
                break;
            default:
                throw new IllegalArgumentException("The faulty reason '" + faultyReason + "' is not supported.");
        }

        if(test != null) {
            testCases.add(test);
        }

        return test;
    }

    private TestCase generateARValidTestCase(Operation testOperation) throws RESTestException {
        Pair<TestCase, Double> bestResult = Pair.with(null, 0.);

        if(testCases.isEmpty()) {
            bestResult = bestResult.setAt0(generateValidTestCase(testOperation));
        } else {
            for(int i = 0; i < getNumberOfCandidates(); i++) {
                List<TestCase> tcs = new ArrayList<>(testCases);
                TestCase tc = generateValidTestCase(testOperation);
                tcs.add(0, tc);
                Double globalDiversity = getDiversity().evaluate(tcs);

                if(globalDiversity > bestResult.getValue1()) {
                    bestResult = Pair.with(tc, globalDiversity);
                }
            }
        }
        return bestResult.getValue0();
    }

    private TestCase generateARFaultyTestCaseDueToViolatedDependencies(Operation testOperation) throws RESTestException {
        Pair<TestCase, Double> bestResult = Pair.with(null, 0.);

        if(testCases.isEmpty()) {
            bestResult = bestResult.setAt0(generateFaultyTestCaseDueToViolatedDependencies(testOperation));
        } else {
            for(int i = 0; i < getNumberOfCandidates(); i++) {
                List<TestCase> tcs = new ArrayList<>(testCases);
                TestCase tc = generateFaultyTestCaseDueToViolatedDependencies(testOperation);
                tcs.add(0, tc);
                Double globalDiversity = getDiversity().evaluate(tcs);

                if(globalDiversity > bestResult.getValue1()) {
                    bestResult = Pair.with(tc, globalDiversity);
                }
            }
        }
        return bestResult.getValue0();
    }

    private TestCase generateARFaultyTestCaseDueToIndividualConstraints(Operation testOperation) throws RESTestException {
        Pair<TestCase, Double> bestResult = Pair.with(null, 0.);

        if(testCases.isEmpty()) {
            bestResult = bestResult.setAt0(generateFaultyTestCaseDueToIndividualConstraints(testOperation));
        } else {
            for(int i = 0; i < getNumberOfCandidates(); i++) {
                List<TestCase> tcs = new ArrayList<>(testCases);
                TestCase tc = generateFaultyTestCaseDueToIndividualConstraints(testOperation);
                if(tc != null) {
                    tcs.add(0, tc);
                    Double globalDiversity = getDiversity().evaluate(tcs);

                    if(globalDiversity > bestResult.getValue1()) {
                        bestResult = Pair.with(tc, globalDiversity);
                    }
                }
            }
        }
        return bestResult.getValue0();
    }

    public Diversity getDiversity() {
        return diversity;
    }

    public void setDiversity(String similarityMetric) {
        SimilarityMeter.METRIC metric = SimilarityMeter.METRIC.valueOf(similarityMetric);
        this.diversity = new Diversity(metric, true);
    }

    public Integer getNumberOfCandidates() {
        return numberOfCandidates;
    }

    public void setNumberOfCandidates(Integer numberOfCandidates) {
        this.numberOfCandidates = numberOfCandidates;
    }
}

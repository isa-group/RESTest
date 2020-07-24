package es.us.isa.restest.searchbased.objectivefunction;

import es.us.isa.restest.searchbased.AbstractSearchBasedTest;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteGenerationProblem;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import es.us.isa.restest.util.IDGenerator;
import io.swagger.v3.oas.models.PathItem;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static es.us.isa.restest.searchbased.objectivefunction.SimilarityMeter.METRIC.*;
import static org.junit.Assert.assertEquals;

public class UniqueFailuresTest extends AbstractSearchBasedTest {

    @Test
    public void onlyOneUniqueFailureTest() {
        RestfulAPITestSuiteGenerationProblem problem = createTestProblems().get(0);
        // Leave testConf with only one operation:
        problem.getConfig().getTestConfiguration().setOperations(Collections.singletonList(problem.getConfig().getTestConfiguration().getOperations().get(0)));

        RestfulAPITestSuiteSolution solution = new RestfulAPITestSuiteSolution(problem);
        for (TestCase tc: solution.getVariables()) {
            solution.setTestResult(tc.getId(), new TestResult(tc.getId(), "500", "Same body", "application/json", false));
        }

        UniqueFailures objFunc = new UniqueFailures();
        Double expectedValue=1.0;
        assertEquals("All failures are the same, the function should return 1", expectedValue, objFunc.evaluate(solution));
    }

    @Test
    public void onlyOneUniqueFailurePerOperationPerStatusCodeTest() {
        RestfulAPITestSuiteGenerationProblem problem = createTestProblems().get(0);

        RestfulAPITestSuiteSolution solution = new RestfulAPITestSuiteSolution(problem);
        // Add custom TCs and TRs to solution (2 per operation)
        for (int i=0; i<testSuiteSize; i++)
            solution.removeVariable(0);
        problem.getConfig().getTestConfiguration().getOperations().forEach(op -> {
            String testId = IDGenerator.generateId();
            solution.addVariable(new TestCase(testId, false, op.getOperationId(), op.getTestPath(), PathItem.HttpMethod.GET));
            solution.setTestResult(testId, new TestResult(testId, "500", "Same body", "application/json", false));

            testId = IDGenerator.generateId();
            solution.addVariable(new TestCase(testId, false, op.getOperationId(), op.getTestPath(), PathItem.HttpMethod.GET));
            solution.setTestResult(testId, new TestResult(testId, "400", "Same body", "application/json", false));
        });

        UniqueFailures objFunc = new UniqueFailures();
        Double expectedValue=8.0;
        assertEquals("All failures are the same, but from different operations/ status codes, the function should return 8", expectedValue, objFunc.evaluate(solution));
    }

    @Test
    public void allUniqueFailuresTest() {
        RestfulAPITestSuiteGenerationProblem problem = createTestProblems().get(0);
        // Leave testConf with only one operation:
        problem.getConfig().getTestConfiguration().setOperations(Collections.singletonList(problem.getConfig().getTestConfiguration().getOperations().get(0)));

        RestfulAPITestSuiteSolution solution = new RestfulAPITestSuiteSolution(problem);
        for (TestCase tc: solution.getVariables()) {
            solution.setTestResult(tc.getId(), new TestResult(tc.getId(), "500", tc.getId(), "application/json", false));
        }

        UniqueFailures objFunc = new UniqueFailures();
        Double expectedValue=4.0;
        assertEquals("All failures are different, the function should return 4", expectedValue, objFunc.evaluate(solution));
    }

    @Test
    public void onlyOneUniqueFailureWithThresholdTest() {
        RestfulAPITestSuiteGenerationProblem problem = createTestProblems().get(0);
        // Leave testConf with only one operation:
        problem.getConfig().getTestConfiguration().setOperations(Collections.singletonList(problem.getConfig().getTestConfiguration().getOperations().get(0)));

        RestfulAPITestSuiteSolution solution = new RestfulAPITestSuiteSolution(problem);
        for (TestCase tc: solution.getVariables()) {
            solution.setTestResult(tc.getId(), new TestResult(tc.getId(), "500", "Same body", "application/json", false));
        }

        UniqueFailures objFunc = new UniqueFailures(JACCARD, 0.9);
        Double expectedValue=1.0;
        assertEquals("All failures are the same, the function should return 1", expectedValue, objFunc.evaluate(solution));
    }

    @Test
    public void allDifferentButAlmostEqualFailuresWithThresholdTest() {
        RestfulAPITestSuiteGenerationProblem problem = createTestProblems().get(0);
        // Leave testConf with only one operation:
        problem.getConfig().getTestConfiguration().setOperations(Collections.singletonList(problem.getConfig().getTestConfiguration().getOperations().get(0)));

        RestfulAPITestSuiteSolution solution = new RestfulAPITestSuiteSolution(problem);
        for (int i=0; i<solution.getVariables().size(); i++) {
            TestCase tc = solution.getVariable(i);
            solution.setTestResult(tc.getId(), new TestResult(tc.getId(), "500", "Same body Same body Same body Same body Same body Same body "+tc.getId().charAt(tc.getId().length()-i-1), "application/json", false));
        }

        UniqueFailures objFunc = new UniqueFailures(LEVENSHTEIN, 0.9);
        Double expectedValue=1.0;
        assertEquals("All failures are almost the same, the function should return 1", expectedValue, objFunc.evaluate(solution));
    }

    @Test
    public void allVeryDifferentFailuresWithThresholdTest() {
        RestfulAPITestSuiteGenerationProblem problem = createTestProblems().get(0);
        // Leave testConf with only one operation:
        problem.getConfig().getTestConfiguration().setOperations(Collections.singletonList(problem.getConfig().getTestConfiguration().getOperations().get(0)));

        RestfulAPITestSuiteSolution solution = new RestfulAPITestSuiteSolution(problem);
        String[] bodies = {"abcdef", "ghijkl", "mnopqr", "stuvwx"};
        for (int i=0; i<solution.getVariables().size(); i++) {
            TestCase tc = solution.getVariable(i);
            solution.setTestResult(tc.getId(), new TestResult(tc.getId(), "500", bodies[i], "application/json", false));
        }

        UniqueFailures objFunc = new UniqueFailures(JARO_WINKLER, 0.1);
        Double expectedValue=4.0;
        assertEquals("All failures are very different, the function should return 4", expectedValue, objFunc.evaluate(solution));
    }

    @Test
    public void noFailuresTest() {
        RestfulAPITestSuiteGenerationProblem problem = createTestProblems().get(0);

        RestfulAPITestSuiteSolution solution = new RestfulAPITestSuiteSolution(problem);
        for (TestCase tc: solution.getVariables()) {
            solution.setTestResult(tc.getId(), new TestResult(tc.getId(), "200", "Same body", "application/json", true));
        }

        UniqueFailures objFunc = new UniqueFailures();
        Double expectedValue=0.0;
        assertEquals("There are no failures, the function should return 0", expectedValue, objFunc.evaluate(solution));
    }
}

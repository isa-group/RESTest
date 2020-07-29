package es.us.isa.restest.searchbased.operators;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;

import java.util.List;

import static es.us.isa.restest.testcases.TestCase.checkFulfillsDependencies;
import static es.us.isa.restest.testcases.TestCase.getFaultyReasons;

/**
 * This class contains common utilities to all mutation operators
 */
public class Utils {

    static void updateTestCaseFaultyReason(RestfulAPITestSuiteSolution solution, TestCase testCase) {
        if (testCase.getEnableOracles()) {
            List<String> faultyReasons = getFaultyReasons(testCase, solution.getProblem().getTestCaseGenerators().get(testCase.getOperationId()).getValidator());
            if (!faultyReasons.isEmpty()) {
                testCase.setFaultyReason(String.join(", ", faultyReasons));
                testCase.setFaulty(true);
            } else if (checkFulfillsDependencies(testCase, solution.getProblem().getTestCaseGenerators().get(testCase.getOperationId()).getIdlReasoner())) {
                testCase.setFaultyReason("inter_parameter_dependency");
                testCase.setFulfillsDependencies(false);
                testCase.setFaulty(true);
            } else {
                testCase.setFaultyReason("none");
                testCase.setFulfillsDependencies(true);
                testCase.setFaulty(false);
            }
        }
    }

    static void resetTestResult(String testCaseId, RestfulAPITestSuiteSolution solution) {
        solution.setTestResult(testCaseId, null);
    }
}

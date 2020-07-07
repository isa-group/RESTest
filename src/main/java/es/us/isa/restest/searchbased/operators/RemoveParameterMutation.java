/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.operators;

import es.us.isa.restest.specification.ParameterFeatures;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;

import java.util.Optional;

import static es.us.isa.restest.searchbased.operators.Utils.resetTestResult;
import static es.us.isa.restest.searchbased.operators.Utils.updateTestCaseFaultyReason;

/**
 *
 * @author japar
 */
public class RemoveParameterMutation extends AbstractAPITestCaseMutationOperator {
    
    public RemoveParameterMutation(double mutationProbability, PseudoRandomGenerator randomGenerator) {
        super(mutationProbability, randomGenerator);
    }
    
    @Override
    protected void doMutation(double mutationProbability, RestfulAPITestSuiteSolution solution) {
        for (TestCase testCase : solution.getVariables()) {
            mutationApplied = false;
            for (ParameterFeatures param : getAllPresentParameters(testCase)) {
                if (getRandomGenerator().nextDouble() <= mutationProbability) {                    
                    doMutation(param, testCase);
                    if (!mutationApplied) mutationApplied = true;
                }
            }

            if (mutationApplied) {
                updateTestCaseFaultyReason(solution, testCase);
                resetTestResult(testCase.getId(), solution); // The test case changed, reset test result
            }
        }
    }
    
    private void doMutation(ParameterFeatures param, TestCase testCase) {
        testCase.removeParameter(param);
    }
    
}

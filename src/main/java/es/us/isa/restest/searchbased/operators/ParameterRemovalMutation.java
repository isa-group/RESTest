/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.operators;

import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;

/**
 *
 * @author japar
 */
public class ParameterRemovalMutation extends AbstractAPITestCaseMutationOperator {
    
    public ParameterRemovalMutation(double mutationProbability, PseudoRandomGenerator randomGenerator) {
        super(mutationProbability, randomGenerator);
    }
    
    @Override
    protected void doMutation(double mutationProbability, RestfulAPITestSuiteSolution solution) {
        for (TestCase testCase : solution.getVariables()) {            
            for (String paramName : getAllPresentParameters(testCase)) {
                if (getRandomGenerator().nextDouble() <= mutationProbability) {                    
                    doMutation(paramName, testCase, solution);
                    resetTestResult(testCase.getId(), solution); // The test case changed, reset test result
                }
            }
        }
    }
    
    private void doMutation(String paramName, TestCase testCase, RestfulAPITestSuiteSolution solution) {
        testCase.removePathParameter(paramName);
        testCase.removeQueryParameter(paramName);
        testCase.removeHeaderParameter(paramName);
        testCase.removeFormParameter(paramName);
    }
    
}

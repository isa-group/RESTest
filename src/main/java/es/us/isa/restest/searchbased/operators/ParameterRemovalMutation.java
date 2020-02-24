/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.operators;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;
import java.util.Collection;
import org.uma.jmetal.operator.MutationOperator;
import org.uma.jmetal.util.pseudorandom.RandomGenerator;

/**
 *
 * @author japar
 */
public class ParameterRemovalMutation extends AbstractAPITestCaseMutationOperator {
    
    public ParameterRemovalMutation(double mutationProbability, RandomGenerator<Double> randomGenerator) {
        super(mutationProbability, randomGenerator);
    }
    
    @Override
    protected void doMutation(double mutationProbability, RestfulAPITestSuiteSolution solution) {
        for (TestCase testCase : solution.getVariables()) {            
            for (String paramName : getAllPresentParameters(testCase)) {
                if (getRandomGenerator().getRandomValue() <= mutationProbability) {                    
                    doMutation(paramName, testCase, solution);
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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.operators;

import java.util.Collection;

import es.us.isa.restest.specification.ParameterFeatures;
import org.javatuples.Pair;
import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;

import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.SpecificationVisitor;
import io.swagger.models.Operation;
import io.swagger.models.parameters.Parameter;

/**
 *
 * This mutation operation changes the value associated to a parameter
 * previously present in the test case. If the test case does not have any
 * parameter set, the operator will add one randomly.
 *
 * @author japarejo
 */
public class RandomParameterValueMutation extends AbstractAPITestCaseMutationOperator {

    private ParameterAdditionMutation parameterAdditionOperator;

    public RandomParameterValueMutation(double mutationProbability, PseudoRandomGenerator randomGenerator) {
        super(mutationProbability, randomGenerator);
        parameterAdditionOperator = new ParameterAdditionMutation(mutationProbability, randomGenerator);
    }

    @Override
    protected void doMutation(double probability, RestfulAPITestSuiteSolution solution) {
        for (TestCase testCase : solution.getVariables()) {
            Collection<ParameterFeatures> parameters = getAllPresentParameters(testCase);
            if (parameters.isEmpty()) {
                parameterAdditionOperator.doMutation(probability, solution);
            } else {
                for (ParameterFeatures param : parameters) {
                    if (getRandomGenerator().nextDouble() <= probability) {                        
                        doMutation(param, testCase, solution);
                        resetTestResult(testCase.getId(), solution); // The test case changed, reset test result
                    }
                }
            }
        }
    }

    private void doMutation(ParameterFeatures paramFeatures, TestCase testCase, RestfulAPITestSuiteSolution solution) {
        ITestDataGenerator generator = solution.getProblem().getRandomTestCaseGenerator().getGenerators().get(Pair.with(paramFeatures.getName(), paramFeatures.getIn()));
        testCase.addParameter(paramFeatures, generator.nextValueAsString());
    }
}

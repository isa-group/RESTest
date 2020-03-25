/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.operators;

import java.util.Collection;

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
            Collection<String> parameters = getAllPresentParameters(testCase);
            if (parameters.isEmpty()) {
                parameterAdditionOperator.doMutation(probability, solution);
            } else {
                for (String paramName : parameters) {
                    if (getRandomGenerator().nextDouble() <= probability) {                        
                        doMutation(paramName, testCase, solution);
                    }
                }
            }
        }
    }

    private void doMutation(String confParam, TestCase testCase, RestfulAPITestSuiteSolution solution) {
        ITestDataGenerator generator = solution.getProblem().getGenerators().get(confParam);
        Operation specOperation = SpecificationVisitor.findOperation(solution.getProblem().getOperationUnderTest().getOperationId(), solution.getProblem().getApiUnderTest());
        Parameter specParameter = SpecificationVisitor.findParameter(specOperation, confParam);
        switch (specParameter.getIn()) {
            case "header":
                testCase.addHeaderParameter(confParam, generator.nextValueAsString());
                break;
            case "query":
                testCase.addQueryParameter(confParam, generator.nextValueAsString());
                break;
            case "path":
                testCase.addPathParameter(confParam, generator.nextValueAsString());
                break;
            case "body":
                testCase.setBodyParameter(generator.nextValueAsString());
                break;
            default:
                throw new IllegalArgumentException("Parameter type not supported: " + specParameter.getIn());
        }
    }        
}

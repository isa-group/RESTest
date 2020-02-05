/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.operators;

import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.SpecificationVisitor;
import io.swagger.models.Operation;
import io.swagger.models.parameters.Parameter;
import java.util.List;
import org.uma.jmetal.operator.MutationOperator;
import org.uma.jmetal.util.pseudorandom.RandomGenerator;

/**
 *
 * @author japarejo
 */
public class RandomParameterValueMutation implements MutationOperator<RestfulAPITestSuiteSolution> {

    private double mutationProbability;
    private RandomGenerator<Double> randomGenerator;

    /* Getter */
    public double getMutationProbability() {
        return mutationProbability;
    }

    /* Setters */
    public void setMutationProbability(double mutationProbability) {
        this.mutationProbability = mutationProbability;
    }

    @Override
    public RestfulAPITestSuiteSolution execute(RestfulAPITestSuiteSolution solution) {
        assert (solution != null);

        doMutation(mutationProbability, solution);
        return solution;
    }

    public void doMutation(double probability, RestfulAPITestSuiteSolution solution) {
        List<TestParameter> parameters = solution.getProblem().getParameters();
        for (TestCase testCase : solution.getVariables()) {
            for (int i = 0; i < parameters.size(); i++) {
                TestParameter param = parameters.get(i);
                if (randomGenerator.getRandomValue() <= probability) {
                    doMutation(param, i, testCase, solution);
                }
            }
        }

    }

    private void doMutation(TestParameter confParam, int index, TestCase testCase, RestfulAPITestSuiteSolution solution) {
        ITestDataGenerator generator = solution.getProblem().getGenerators().get(confParam.getName());
        Operation specOperation = SpecificationVisitor.findOperation(solution.getProblem().getOperationUnderTest().getOperationId(), solution.getProblem().getApiUnderTest());
        Parameter specParameter = SpecificationVisitor.findParameter(specOperation, confParam.getName());
        switch (specParameter.getIn()) {
            case "header":
                testCase.addHeaderParameter(confParam.getName(), generator.nextValueAsString());
                break;
            case "query":
                testCase.addQueryParameter(confParam.getName(), generator.nextValueAsString());
                break;
            case "path":
                testCase.addPathParameter(confParam.getName(), generator.nextValueAsString());
                break;
            case "body":
                testCase.setBodyParameter(generator.nextValueAsString());
                break;
            default:
                throw new IllegalArgumentException("Parameter type not supported: " + specParameter.getIn());
        }
    }

}

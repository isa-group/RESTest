/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.operators;

import java.util.Collection;

import es.us.isa.restest.specification.ParameterFeatures;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;
import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;

import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;

import static es.us.isa.restest.searchbased.operators.Utils.resetTestResult;
import static es.us.isa.restest.searchbased.operators.Utils.updateTestCaseFaultyReason;

/**
 *
 * This mutation operation changes the value associated to a parameter
 * previously present in the test case. If the test case does not have any
 * parameter set, the operator will add one randomly.
 *
 * @author japarejo
 */
public class RandomParameterValueMutation extends AbstractMutationOperator {

    private static final Logger logger = LogManager.getLogger(RandomParameterValueMutation.class.getName());

    private AddParameterMutation parameterAdditionOperator;

    public RandomParameterValueMutation(double mutationProbability, PseudoRandomGenerator randomGenerator) {
        super(mutationProbability, randomGenerator);
        parameterAdditionOperator = new AddParameterMutation(mutationProbability, randomGenerator);
    }

    @Override
    protected void doMutation(double probability, RestfulAPITestSuiteSolution solution) {
        for (TestCase testCase : solution.getVariables()) {
            mutationApplied = false;
            Collection<ParameterFeatures> parameters = getAllPresentParameters(testCase);
            if (parameters.isEmpty()) {
                parameterAdditionOperator.doMutation(probability, solution);
            } else {
                for (ParameterFeatures param : parameters) {
                    if (getRandomGenerator().nextDouble() <= probability) {                        
                        doMutation(param, testCase, solution);
                        if (!mutationApplied) mutationApplied = true;
                    }
                }
            }

            if (mutationApplied) {
                logger.info("Mutation probability fulfilled! Parameter value changed in test case.");
                updateTestCaseFaultyReason(solution, testCase);
                resetTestResult(testCase.getId(), solution); // The test case changed, reset test result
            }
        }
    }

    private void doMutation(ParameterFeatures paramFeatures, TestCase testCase, RestfulAPITestSuiteSolution solution) {
        ITestDataGenerator generator = solution.getProblem().getTestCaseGenerators().get(testCase.getOperationId()).getGenerators().get(Pair.with(paramFeatures.getName(), paramFeatures.getIn()));
        testCase.addParameter(paramFeatures, generator.nextValueAsString());
    }
}

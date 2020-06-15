/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.operators;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.specification.ParameterFeatures;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;

import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.SpecificationVisitor;

/**
 *
 * @author japar
 */
public class ParameterAdditionMutation extends AbstractAPITestCaseMutationOperator {

    public ParameterAdditionMutation(double mutationProbability, PseudoRandomGenerator randomGenerator) {
        super(mutationProbability, randomGenerator);
    }
        
    @Override
    protected void doMutation(double mutationProbability, RestfulAPITestSuiteSolution solution) {
        for (TestCase testCase : solution.getVariables()) {            
            for (ParameterFeatures paramFeatures : getNonPresentParameters(testCase,solution)) {
                if (getRandomGenerator().nextDouble() <= mutationProbability) {                    
                    doMutation(paramFeatures, testCase, solution);
                    resetTestResult(testCase.getId(), solution); // The test case changed, reset test result
                }
            }
        }
    }

    protected Collection<ParameterFeatures> getNonPresentParameters(TestCase testCase, RestfulAPITestSuiteSolution solution) {
    	es.us.isa.restest.configuration.pojos.Operation operation = solution.getProblem().getOperationUnderTest();
    	if(operation==null) {
            for(es.us.isa.restest.configuration.pojos.Operation op:solution.getProblem().getConfig().getOperations())
                if(testCase.getOperationId().equals(op.getOperationId())) {
                    operation=op;
                    break;
                }
    	}
        Collection<ParameterFeatures> presentParams=getAllPresentParameters(testCase);
        Set<ParameterFeatures> result=new HashSet<>();
        for (TestParameter param: operation.getTestParameters()) {
            ParameterFeatures paramFeatures = new ParameterFeatures(param.getName(), param.getIn(), null);
            if (presentParams.contains(paramFeatures))
                result.add(paramFeatures);
        }
        return result;
    }

    private void doMutation(ParameterFeatures paramFeatures, TestCase testCase, RestfulAPITestSuiteSolution solution) {
        ITestDataGenerator generator = solution.getProblem().getRandomTestCaseGenerator().getGenerators().get(paramFeatures.getName());
        testCase.addParameter(paramFeatures, generator.nextValueAsString());
    }
    
}

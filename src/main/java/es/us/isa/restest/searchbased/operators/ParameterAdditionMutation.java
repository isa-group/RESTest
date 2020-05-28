/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.operators;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;

import es.us.isa.restest.configuration.pojos.TestPath;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.SpecificationVisitor;
import io.swagger.models.Operation;
import io.swagger.models.parameters.Parameter;

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
            for (String paramName : getNonPresentParameters(testCase,solution)) {
                if (getRandomGenerator().nextDouble() <= mutationProbability) {                    
                    doMutation(paramName, testCase, solution);
                }
            }
        }
    }

    protected Collection<String> getNonPresentParameters(TestCase testCase, RestfulAPITestSuiteSolution solution) {
    	es.us.isa.restest.configuration.pojos.Operation operation = solution.getProblem().getOperationUnderTest();
    	if(operation==null) {
    		for(TestPath path:solution.getProblem().getConfig().getTestPaths()) {
    			for(es.us.isa.restest.configuration.pojos.Operation op:path.getOperations())
    				if(testCase.getOperationId().equals(op.getOperationId()))
    				{
    					operation=op;
    					break;
    				}
    		}
    		
    	}
        Operation specOperation = SpecificationVisitor.findOperation(testCase.getOperationId(), solution.getProblem().getApiUnderTest());
        Collection<String> presentParams=getAllPresentParameters(testCase);
        Set<String> result=new HashSet<>();
        for(Parameter param:specOperation.getParameters()){
            if(!presentParams.contains(param.getName()))
                result.add(param.getName());
        }
        return result;
    }

    private void doMutation(String paramName, TestCase testCase, RestfulAPITestSuiteSolution solution) {
        
        Operation specOperation = SpecificationVisitor.findOperation(solution.getProblem().getOperationUnderTest().getOperationId(), solution.getProblem().getApiUnderTest());
        Parameter specParameter = SpecificationVisitor.findParameter(specOperation, paramName);
        ITestDataGenerator generator = solution.getProblem().getRandomTestCaseGenerator().getGenerators().get(paramName);
        switch (specParameter.getIn()) {
            case "header":
                testCase.addHeaderParameter(paramName, generator.nextValueAsString());
                break;
            case "query":
                testCase.addQueryParameter(paramName, generator.nextValueAsString());
                break;
            case "path":
                testCase.addPathParameter(paramName, generator.nextValueAsString());
                break;
            case "body":
                testCase.setBodyParameter(generator.nextValueAsString());
                break;
            default:
                throw new IllegalArgumentException("Parameter type not supported: " + specParameter.getIn());
        }
    }
    
}

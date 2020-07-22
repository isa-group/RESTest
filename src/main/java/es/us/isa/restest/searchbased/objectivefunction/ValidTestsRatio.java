/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.objectivefunction;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;

/**
 *
 * @author japar
 */
public class ValidTestsRatio extends RestfulAPITestingObjectiveFunction {

	public ValidTestsRatio(ObjectiveFunctionType type) {
		super(type,false,true);
	}
	
    @Override
    public Double evaluate(RestfulAPITestSuiteSolution solution) {
        double validTestCases=0.0;
        double invalidTestCases=0.0;
        for(TestCase testCase:solution.getVariables()){
            if(testCase.getFaulty())
                invalidTestCases++;
            else
                validTestCases++;
        }
        if(invalidTestCases==0)
            return Double.MAX_VALUE;
        else
            return validTestCases/invalidTestCases;
    }
    
}

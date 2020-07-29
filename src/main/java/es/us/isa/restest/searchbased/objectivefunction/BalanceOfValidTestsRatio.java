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
 * @author japarejo
 */
public class BalanceOfValidTestsRatio extends RestfulAPITestingObjectiveFunction {	

	public static final double DEFAULT_TARGET_RATIO = 0.8;
	
	private double targetRatio;
	
	public BalanceOfValidTestsRatio() {
		this(DEFAULT_TARGET_RATIO);		
	}		
	
	public BalanceOfValidTestsRatio (double targetRatio) {
		super(ObjectiveFunctionType.MAXIMIZATION,false,true);
		this.targetRatio=targetRatio;
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
            return 1.0;
        else
            return Math.abs(targetRatio-(double)(validTestCases/invalidTestCases));
    }

	public double getTargetRatio() {
		return targetRatio;
	}
	
    
}

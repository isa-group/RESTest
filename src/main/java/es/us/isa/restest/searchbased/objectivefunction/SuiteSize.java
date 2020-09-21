/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.objectivefunction;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;

/**
 *
 * @author japarejo
 */
public class SuiteSize extends RestfulAPITestingObjectiveFunction{

    public SuiteSize() {
		super(ObjectiveFunctionType.MINIMIZATION,false,false);
	}

	@Override
    public Double evaluate(RestfulAPITestSuiteSolution solution) {
        logEvaluation(solution.getNumberOfVariables());
        return (double)solution.getNumberOfVariables();
    }
    
}

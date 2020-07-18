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
public abstract class RestfulAPITestingObjectiveFunction {
    ObjectiveFunctionType type;
    boolean requiresTestExecution;
    boolean requiresOracles;
    
    public RestfulAPITestingObjectiveFunction(ObjectiveFunctionType type) {
    	this(type,true,true);
    }

    public RestfulAPITestingObjectiveFunction(ObjectiveFunctionType type, boolean requiresTestExecution, boolean requiresOracles) {
    	this.type=type;
    	this.requiresTestExecution =requiresTestExecution;
    	this.requiresOracles = requiresOracles;
	}    
    
    public abstract Double evaluate(RestfulAPITestSuiteSolution solution);
    
	public String toString() {
    	return type + " of " + this.getClass().getSimpleName();
    }
    
    public enum ObjectiveFunctionType{MAXIMIZATION,MINIMIZATION};
    
    public boolean isRequiresTestExecution() {
		return requiresTestExecution;
	}

    public void setRequiresTestExecution(boolean requiresTestExecution) {
        this.requiresTestExecution = requiresTestExecution;
    }

    public boolean isRequiresOracles() {
        return requiresOracles;
    }

    public void setRequiresOracles(boolean requiresOracles) {
        this.requiresOracles = requiresOracles;
    }

    public ObjectiveFunctionType getType() {
		return type;
	}
}

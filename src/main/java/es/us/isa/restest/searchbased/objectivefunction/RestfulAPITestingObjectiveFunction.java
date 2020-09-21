/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.objectivefunction;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.searchbased.terminationcriteria.MaxEvaluations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author japarejo
 */
public abstract class RestfulAPITestingObjectiveFunction {

    private final Logger logger;
    ObjectiveFunctionType type;
    boolean requiresTestExecution;
    boolean requiresTestOracles;
    
    public RestfulAPITestingObjectiveFunction(ObjectiveFunctionType type) {
    	this(type,true,true);
    }

    public RestfulAPITestingObjectiveFunction(ObjectiveFunctionType type, boolean requiresTestExecution, boolean requiresTestOracles) {
        logger = LogManager.getLogger(this.getClass().getName());
    	this.type=type;
    	this.requiresTestExecution =requiresTestExecution;
    	this.requiresTestOracles = requiresTestOracles;
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

    public boolean isRequiresTestOracles() {
        return requiresTestOracles;
    }

    public void setRequiresTestOracles(boolean requiresTestOracles) {
        this.requiresTestOracles = requiresTestOracles;
    }

    public ObjectiveFunctionType getType() {
		return type;
	}

	public void logEvaluation(double value) {
        logger.info("Evaluating fitness function: " + value);
    }
}

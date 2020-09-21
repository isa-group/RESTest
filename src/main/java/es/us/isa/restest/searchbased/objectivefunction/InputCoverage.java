/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.objectivefunction;

import es.us.isa.restest.coverage.CoverageGatherer;
import es.us.isa.restest.coverage.CoverageMeter;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;

/**
 *
 * @author japarejo
 */
public class InputCoverage extends RestfulAPITestingObjectiveFunction{

    CoverageMeter coverageMeter=null;
    public InputCoverage() {
		super(ObjectiveFunctionType.MAXIMIZATION,false,false);
	}
    @Override
    public Double evaluate(RestfulAPITestSuiteSolution solution) {
        if(coverageMeter==null)
            initCoverageMeter(solution);
        coverageMeter.resetCoverage();
        coverageMeter.setTestSuite(solution.getVariables());
        //coverageMeter.setTestResults(solution.getTestResults());
        double coveredInputElements=(double)coverageMeter.getCoveredInputElements();
        double totalInputElements=(double)coverageMeter.getAllInputElements();
        logEvaluation(coveredInputElements/totalInputElements);
        return coveredInputElements/totalInputElements;
    }
    
    private void initCoverageMeter(RestfulAPITestSuiteSolution solution){
        coverageMeter=new CoverageMeter(new CoverageGatherer(solution.getProblem().getApiUnderTest()));        
    }
    
}

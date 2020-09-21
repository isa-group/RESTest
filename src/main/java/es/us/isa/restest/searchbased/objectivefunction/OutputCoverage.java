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
public class OutputCoverage extends RestfulAPITestingObjectiveFunction{
    CoverageMeter coverageMeter=null;
    
    public OutputCoverage() {
		super(ObjectiveFunctionType.MAXIMIZATION,true,false);
	}
    
    @Override
    public Double evaluate(RestfulAPITestSuiteSolution solution) {
        if(coverageMeter==null)
            initCoverageMeter(solution);
        coverageMeter.resetCoverage();
        coverageMeter.setTestSuite(solution.getVariables());
        coverageMeter.setTestResults(solution.getTestResults());
        double coveredOutputElements=(double)coverageMeter.getCoveredOutputElements();
        double totalOutputElements=(double)coverageMeter.getAllOutputElements();
        logEvaluation(coveredOutputElements/totalOutputElements);
        return coveredOutputElements/totalOutputElements;
    }
    
    void initCoverageMeter(RestfulAPITestSuiteSolution solution){
        coverageMeter=new CoverageMeter(new CoverageGatherer(solution.getProblem().getApiUnderTest()));        
    }
        
}

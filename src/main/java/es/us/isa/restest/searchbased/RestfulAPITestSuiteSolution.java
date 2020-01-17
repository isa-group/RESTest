/**
 * 
 */
package es.us.isa.restest.searchbased;

import es.us.isa.restest.testcases.TestCase;
import java.util.List;
import java.util.Map;
import org.uma.jmetal.solution.Solution;
import org.uma.jmetal.solution.impl.AbstractGenericSolution;

public class RestfulAPITestSuiteSolution extends AbstractGenericSolution<TestCase,RestfulAPITestSuiteGenerationProblem>{

    public RestfulAPITestSuiteSolution(RestfulAPITestSuiteGenerationProblem problem) {
        super(problem);
    }    
    
    @Override
    public String getVariableValueString(int i) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Solution<TestCase> copy() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Map<Object, Object> getAttributes() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
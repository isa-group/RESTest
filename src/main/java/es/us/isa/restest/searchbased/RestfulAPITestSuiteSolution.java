/**
 * 
 */
package es.us.isa.restest.searchbased;

import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import java.util.Collection;
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
        return getVariable(i).toString();
    }

    @Override
    public RestfulAPITestSuiteSolution copy() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Map<Object, Object> getAttributes() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public RestfulAPITestSuiteGenerationProblem getProblem() {
        return problem;
    }

    public TestCase getVariable(int i) {
        return getVariables().get(i);
    }
    
    public void setVariable(int i, TestCase  tc){
        getVariables().set(i, tc);
    }

    public Collection<TestResult> getTestResults() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    

    
}
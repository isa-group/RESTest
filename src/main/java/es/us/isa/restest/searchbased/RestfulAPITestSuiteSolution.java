/**
 * 
 */
package es.us.isa.restest.searchbased;

import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;

import java.util.*;

import org.uma.jmetal.solution.Solution;
import org.uma.jmetal.solution.impl.AbstractGenericSolution;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

import com.google.common.collect.Maps;

public class RestfulAPITestSuiteSolution extends AbstractGenericSolution<TestCase,RestfulAPITestSuiteGenerationProblem>{

    private Map<String,TestResult> testResults; // key = testCaseId
    
    public RestfulAPITestSuiteSolution(RestfulAPITestSuiteGenerationProblem problem) {
        this(problem, false);
    }

    /**
     * Auxiliary constructor to create a solution without test cases. Useful for the
     * {@link RestfulAPITestSuiteSolution#copy()} method (it is more computationally
     * efficient).
     * @param problem Problem to solve
     * @param withoutTestCases true if no test cases need to be added to the solution
     */
    public RestfulAPITestSuiteSolution(RestfulAPITestSuiteGenerationProblem problem, boolean withoutTestCases) {
        super(problem);
        this.testResults=new HashMap<>();
        if (!withoutTestCases)
            createVariables();
    }

    @Override
    public String getVariableValueString(int i) {
    	if(i<getVariables().size())
    		return getVariable(i).toString();
    	else
    		return "";
    			
    }

    @Override
    public RestfulAPITestSuiteSolution copy() {
    	RestfulAPITestSuiteSolution result=new RestfulAPITestSuiteSolution(this.problem, true);
    	TestCase testCase=null;
    	for(int i=0;i<this.getNumberOfVariables();i++) {
    		testCase=this.getVariable(i);
    		result.setVariable(i, copyTestCase(testCase));
    		if(testResults!=null && testResults.get(testCase.getId())!=null)
    			result.testResults.put(testCase.getId(), copyTestResult(testResults.get(testCase.getId())));
    		else
    			testResults=null;
    	}
    	result.getVariables().removeAll(Collections.singleton(null));
		return result;
    }
        
    private TestResult copyTestResult(TestResult testResult) {
		return new TestResult(testResult);
	}

	private TestCase copyTestCase(TestCase variable) {		
		return new TestCase(variable);
	}

	@Override
    public Map<Object, Object> getAttributes() {
        return attributes;
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

    /**
     * CAREFUL! This method replaces (i.e., eliminates and inserts) a test result
     * with another, it doesn't add a new one.
     * @param id The ID of the test result to eliminate
     * @param tr The new test result to set
     */
    public void replaceTestResult(String id, TestResult  tr){
        testResults.remove(id);
        if (tr != null)
            testResults.put(tr.getId(), tr);
    }

    public void setTestResult(String id, TestResult tr) {
        testResults.put(id, tr);
    }

    public TestResult getTestResult(String testCaseId) {
        return testResults.get(testCaseId);
    }

    public void addTestResults(Map<String, TestResult> results) {
        testResults.putAll(results);
    }

    public Collection<TestResult> getTestResults() {
        return testResults.values();
    }

    public void createVariables() {
    	int nVariables=computeTestSuiteSize();
        for(int i=0;i<nVariables;i++) {
            this.setVariable(i, problem.createRandomTestCase());
        }
        for(int i=nVariables;i<problem.getNumberOfVariables();i++) {
        	this.getVariables().remove(this.getVariables().size()-1);
        }
    }
    
    private int computeTestSuiteSize() {
    	int result=problem.getNumberOfVariables();
    	if(problem.getFixedTestSuiteSize()!=null)
    		return problem.getFixedTestSuiteSize();
    	else
    		return JMetalRandom.getInstance().nextInt(problem.getMinTestSuiteSize(), problem.getMaxTestSuiteSize());
    }
        
    
}
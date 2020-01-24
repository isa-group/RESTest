/**
 * 
 */
package es.us.isa.restest.searchbased;
import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.configuration.pojos.TestPath;
import es.us.isa.restest.searchbased.objectivefunction.RestfulAPITestingObjectiveFunction;
import es.us.isa.restest.specification.OpenAPISpecification;
import java.util.List;
import org.uma.jmetal.problem.impl.AbstractGenericProblem;

public class RestfulAPITestSuiteGenerationProblem extends AbstractGenericProblem<RestfulAPITestSuiteSolution>
{
    OpenAPISpecification apiUnderTest;
    Operation operationUnderTest;
    
    List<TestParameter> parameters;
    List<RestfulAPITestingObjectiveFunction> objectiveFunctions;

    public RestfulAPITestSuiteGenerationProblem(OpenAPISpecification apiUnderTest, Operation operationUnderTest,List<RestfulAPITestingObjectiveFunction> objFuncs) {
        this.apiUnderTest = apiUnderTest;
        this.operationUnderTest = operationUnderTest;
        this.operationUnderTest.getTestParameters();
        
        assert(objFuncs!=null);
        assert(objectiveFunctions.size()>0);
        this.objectiveFunctions=objFuncs;
        
        setNumberOfObjectives(this.objectiveFunctions.size());
    }
    
    

    @Override
    public void evaluate(RestfulAPITestSuiteSolution s) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public RestfulAPITestSuiteSolution createSolution() {
        return new RestfulAPITestSuiteSolution(this);
    }
    
}
/**
 *
 */
package es.us.isa.restest.searchbased;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.configuration.pojos.TestPath;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import es.us.isa.restest.searchbased.objectivefunction.RestfulAPITestingObjectiveFunction;
import es.us.isa.restest.specification.OpenAPISpecification;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.uma.jmetal.problem.impl.AbstractGenericProblem;

public class RestfulAPITestSuiteGenerationProblem extends AbstractGenericProblem<RestfulAPITestSuiteSolution> {

    OpenAPISpecification apiUnderTest;
    Operation operationUnderTest;

    List<TestParameter> parameters;
    Map<String, ITestDataGenerator> generators;
    List<RestfulAPITestingObjectiveFunction> objectiveFunctions;

    public RestfulAPITestSuiteGenerationProblem(OpenAPISpecification apiUnderTest, Operation operationUnderTest, List<RestfulAPITestingObjectiveFunction> objFuncs) {
        this.apiUnderTest = apiUnderTest;
        this.operationUnderTest = operationUnderTest;
        this.parameters = this.operationUnderTest.getTestParameters();
        this.generators = createGenerators(this.parameters);

        assert (objFuncs != null);
        assert (objectiveFunctions.size() > 0);
        this.objectiveFunctions = objFuncs;

        setNumberOfObjectives(this.objectiveFunctions.size());
    }

    @Override
    public void evaluate(RestfulAPITestSuiteSolution s) {
        int i = 0;
        for (RestfulAPITestingObjectiveFunction objFunc : objectiveFunctions) {
            s.setObjective(i, objFunc.evaluate(s));
            i++;
        }
    }

    @Override
    public RestfulAPITestSuiteSolution createSolution() {
        return new RestfulAPITestSuiteSolution(this);
    }

    public List<TestParameter> getParameters() {
        return parameters;
    }

    public Map<String, ITestDataGenerator> getGenerators() {
        return generators;
    }

    public Operation getOperationUnderTest() {
        return operationUnderTest;
    }

    public OpenAPISpecification getApiUnderTest() {
        return apiUnderTest;
    }
    
    

    private Map<String, ITestDataGenerator> createGenerators(List<TestParameter> testParameters) {
        HashMap<String, ITestDataGenerator> result = new HashMap<>();

        for (TestParameter param : testParameters) {
            result.put(param.getName(), TestDataGeneratorFactory.createTestDataGenerator(param.getGenerator()));
        }

        return result;
    }
    
    
    

}

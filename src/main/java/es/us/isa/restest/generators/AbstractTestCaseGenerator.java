package es.us.isa.restest.generators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.TestConfigurationVisitor;
import es.us.isa.restest.configuration.pojos.*;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;

public abstract class AbstractTestCaseGenerator {

	protected OpenAPISpecification spec;
	protected TestConfigurationObject conf;
	protected Map<String,ITestDataGenerator> generators;			// Test data generators (random, boundaryValue, fixedlist...)

	/**
	 * Generate a set of test cases
	 * @param filters Set the paths and HTTP methods to be included in the test configuration file
	 * @return Generated test cases (duplicates are possible)
	 */
	public Collection<TestCase> generate(Collection<TestConfigurationFilter> filters) {

		List<TestCase> testCases = new ArrayList<TestCase>();
		
		// Generate test cases for each path and method
		for(TestConfigurationFilter filter:filters) {
			
			if (filter.getPath()==null) {
				throw new IllegalArgumentException("Specify the path(s) to be tested");
			}

			for(HttpMethod method: filter.getMethods()) {
				// Generate test cases for the operation
				testCases.addAll(generate(filter.getPath(), method));
			}
		}
		
		return testCases;
	}

	/**
	 * Generate a set of test cases for the whole configuration file (all paths, all operations)
	 * @return Generated test cases (duplicates are possible)
	 */
	public Collection<TestCase> generate() {
		List<TestConfigurationFilter> filters = new ArrayList<>();

		for (TestPath testPath: conf.getTestConfiguration().getTestPaths()) {
			TestConfigurationFilter filter = new TestConfigurationFilter();
			filter.setPath(testPath.getTestPath());
			for (es.us.isa.restest.configuration.pojos.Operation operation: testPath.getOperations()) {
				switch(operation.getMethod().toLowerCase()) {
					case "get":
						filter.addGetMethod();
						break;
					case "post":
						filter.addPostMethod();
						break;
					case "put":
						filter.addPutMethod();
						break;
					case "delete":
						filter.addDeleteMethod();
						break;
					default:
						throw new IllegalArgumentException("Methods other than GET, POST, PUT and DELETE are not " +
								"allowed in the test configuration file");
				}
			}
			filters.add(filter);
		}

		return generate(filters);
	}

	protected Collection<? extends TestCase> generate(String path, HttpMethod method) {
		
		// Get specification operation
		Operation specOperation = spec.getSpecification().getPath(path).getOperationMap().get(method);
	
		// Get test configuration object for the operation
		es.us.isa.restest.configuration.pojos.Operation testOperation = TestConfigurationVisitor.getOperation(conf, path, method.name());
		
		List<TestCase> testCases = new ArrayList<TestCase>();
		
		// Create test data generators for each parameter
		createGenerators(testOperation.getTestParameters());
		
		while (hasNext()) {
			
			// Create test case with specific parameters and values
			TestCase test = generateNextTestCase(specOperation,testOperation,path,method);
			
			// Authentication
			if (conf.getAuth().getRequired()) {
				
				// Header parameters
				if (conf.getAuth().getHeaderParams()!=null)
					for (HeaderParam param: conf.getAuth().getHeaderParams())
						test.addHeaderParameter(param.getName(), param.getValue());
				
				// Query parameters
				if (conf.getAuth().getQueryParams()!=null)
					for (QueryParam param: conf.getAuth().getQueryParams())
						test.addQueryParameter(param.getName(), param.getValue());
			}
			
			// Set responses
			test.setExpectedOutputs(specOperation.getResponses());
			
			// Set expected output in case the request is successful
			test.setExpectedSuccessfulOutput(specOperation.getResponses().get(testOperation.getExpectedResponse()));
			
			// Add test case to the collection
			testCases.add(test);
		}
		
		return testCases;
	}

	// Returns true if there are more test cases to be generated. To be implemented on each subclass.
	protected abstract boolean hasNext();
	
	// Generate the next test case and update the generation index. To be implemented on each subclass.
	protected abstract TestCase generateNextTestCase(Operation specOperation,
			es.us.isa.restest.configuration.pojos.Operation testOperation, String path, HttpMethod method);
	
	// Create all generators needed for the parameters of an operation
	private void createGenerators(List<TestParameter> testParameters) {
		
		this.generators = new HashMap<String,ITestDataGenerator>();
		
		for(TestParameter param: testParameters)
			generators.put(param.getName(), TestDataGeneratorFactory.createTestDataGenerator(param.getGenerator()));
		
	}

}
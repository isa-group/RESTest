package es.us.isa.restest.generators;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import es.us.isa.idlreasoner.analyzer.Analyzer;
import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.TestConfigurationVisitor;
import es.us.isa.restest.configuration.pojos.*;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.specification.ParameterFeatures;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.AuthManager;
import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import io.swagger.models.parameters.Parameter;
import org.apache.commons.lang3.RandomStringUtils;

import static es.us.isa.restest.util.SpecificationVisitor.*;

public abstract class AbstractTestCaseGenerator {

	protected OpenAPISpecification spec;
	protected TestConfigurationObject conf;
	protected Map<String,ITestDataGenerator> generators;	// Test data generators (random, boundaryValue, fixedlist...)
	protected AuthManager authManager;						// For if multiple API keys are used for the API
	protected Boolean enableFaulty = true;					// True if faulty test cases want to be generated. Defaults to true
	protected Float faultyRatio = 0.1f;						// Ratio (0-1) of faulty test cases to be generated. Defaults to 0.1
	protected Boolean violateDependency;					// Whether to violate an inter-parameter dependency to create a faulty test case
	protected Analyzer idlReasoner;							// IDLReasoner to check if requests are valid or not
	protected int numberOfTest;								// Number of test cases to be generated for each operation
	protected int index;									// Number of test cases generated so far

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

	protected Collection<TestCase> generate(String path, HttpMethod method) {
		
		// Get specification operation
		Operation specOperation = spec.getSpecification().getPath(path).getOperationMap().get(method);

		if (hasDependencies(specOperation)) // If the operation contains dependencies, create new IDLReasoner for that operation
			idlReasoner = new Analyzer("oas", spec.getPath(), path, method.toString());
		else // Otherwise, set it to null so that it's not used
			idlReasoner = null;
	
		// Get test configuration object for the operation
		es.us.isa.restest.configuration.pojos.Operation testOperation = TestConfigurationVisitor.getOperation(conf, path, method.name());
		
		List<TestCase> testCases = new ArrayList<TestCase>();
		
		// Create test data generators for each parameter
		createGenerators(testOperation.getTestParameters());

		// Whether the next test case to generate must be faulty or not
		boolean nextIsFaulty = false;
		if (enableFaulty)
			nextIsFaulty = true;
		
		while (hasNext()) {

			// Generate faulty test cases until faultyRatio is reached
			if (nextIsFaulty && (float)index/(float)numberOfTest >= faultyRatio)
				nextIsFaulty = false;
			
			// Create test case with specific parameters and values
			TestCase test = generateNextTestCase(specOperation,testOperation,nextIsFaulty,path,method);
			
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

				// File containing all API keys
				if (conf.getAuth().getApiKeysPath()!=null)
					test.addQueryParameter(authManager.getApikeyName(), authManager.getApikey());
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
			es.us.isa.restest.configuration.pojos.Operation testOperation, Boolean faulty, String path, HttpMethod method);
	
	// Create all generators needed for the parameters of an operation
	private void createGenerators(List<TestParameter> testParameters) {
		
		this.generators = new HashMap<String,ITestDataGenerator>();
		
		for(TestParameter param: testParameters)
			generators.put(param.getName(), TestDataGeneratorFactory.createTestDataGenerator(param.getGenerator()));
		
	}

	public Boolean getEnableFaulty() {
		return enableFaulty;
	}

	public void setEnableFaulty(Boolean enableFaulty) {
		this.enableFaulty = enableFaulty;
	}

	public Float getFaultyRatio() {
		return faultyRatio;
	}

	public void setFaultyRatio(Float faultyRatio) {
		this.faultyRatio = faultyRatio;
	}

	public Boolean getViolateDependency() {
		return violateDependency;
	}

	public void setViolateDependency(Boolean violateDependency) {
		this.violateDependency = violateDependency;
	}
}
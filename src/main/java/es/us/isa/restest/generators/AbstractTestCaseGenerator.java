package es.us.isa.restest.generators;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.TestConfigurationVisitor;
import es.us.isa.restest.configuration.pojos.*;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.specification.ParameterFeatures;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.AuthManager;
import es.us.isa.restest.util.CSVManager;
import es.us.isa.restest.util.IDGenerator;
import es.us.isa.restest.util.SpecificationVisitor;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import org.javatuples.Pair;

import java.util.*;

import static es.us.isa.restest.util.CSVManager.createFileWithHeader;
import static es.us.isa.restest.util.FileManager.checkIfExists;

/**
 * Abstract class to be implemented by test case generators
 * @author Sergio Segura
 */

public abstract class AbstractTestCaseGenerator {

	protected long seed = -1;												// Seed
	protected Random rand;
	protected OpenAPISpecification spec;
	protected TestConfigurationObject conf;
	protected Map<Pair<String, String>,ITestDataGenerator> generators;		// Test data generators Map<Pair<ParameterName,Type(query or path)>, Generator (random, boundaryValue, fixedlist...)>
	protected OpenApiInteractionValidator validator;						// Validator used to know if a test case is valid or not
	protected AuthManager authManager;										// For if multiple API keys are used for the API
	protected Float faultyRatio = 0.1f;										// Ratio (0-1) of faulty test cases to be generated. Defaults to 0.1
	protected int numberOfTests;											// Number of test cases to be generated for each operation
	protected int index;													// Number of test cases generated so far
	protected int nFaulty;													// Number of faulty test cases generated so far
	protected int nNominal;													// Number of nominal test cases generated so far

	public AbstractTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
		preconditions(conf);

		this.spec = spec;
		this.conf = conf;

		// AuthManager configuration:
		String authPath = conf.getAuth().getApiKeysPath();
		if (authPath == null)
			authPath = conf.getAuth().getHeadersPath();
		if (authPath != null)
			this.authManager = new AuthManager(authPath);

		// Test case validator
		this.validator = OpenApiInteractionValidator.createFor(spec.getPath()).build();
		
		this.numberOfTests = nTests;
		this.index = 0;
		this.nFaulty = 0;
		this.nNominal = 0;

		this.rand = new Random();
		this.seed = rand.nextLong();
		rand.setSeed(this.seed);
	}
	
	
	// Reset numerical counters
	private void reset() {
		this.index = 0;
		this.nFaulty = 0;
		this.nNominal = 0;

	}

	/**
	 * Checks the following preconditions:
	 * <ol>
	 *     <li>Each parameter in the testConf must exist in the OAS</li>
	 *     <li>Each required parameter in the OAS must exist in the testConf and it must have a weight of "null" or "1"</li>
	 * </ol>
	 *
	 * @param conf the test configuration file
	 */
	private void preconditions(TestConfigurationObject conf) {

		for(Operation testOperation : conf.getTestConfiguration().getOperations()) {
			List<ParameterFeatures> requiredParams = SpecificationVisitor.getRequiredParameters(testOperation.getOpenApiOperation());

			if(testOperation.getTestParameters() != null) {

				for(TestParameter testParameter : testOperation.getTestParameters()) {

					ParameterFeatures param = SpecificationVisitor.findParameter(testOperation.getOpenApiOperation(), testParameter.getName(), testParameter.getIn());
					if(param == null) {
						throw new IllegalArgumentException("Each parameter in the testConf must exist in the OAS; unknown parameter: " + testParameter.getName() + ", in: " + testParameter.getIn());
					}

					if(requiredParams.contains(param) && testParameter.getWeight() != null && !testParameter.getWeight().equals(1F)) {
						throw new IllegalArgumentException("Each required parameter in the OAS must exist in the testConf and it must have a weight of 'null' or '1'; parameter: "
								+ testParameter.getName() + ", in: " + testParameter.getIn() + ", weight: " + testParameter.getWeight());
					}
				}
			}
		}
	}

	/**
	 * Generate a set of test cases
	 * @param filters Set the paths and HTTP methods to be tested
	 * @return Generated test cases (duplicates are possible)
	 */
	public Collection<TestCase> generate(Collection<TestConfigurationFilter> filters) {

		List<TestCase> testCases = new ArrayList<>();
		
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

		// Create filters for all the operations in the API
		for (Operation testOperation: conf.getTestConfiguration().getOperations()) {
			TestConfigurationFilter filter = filters.stream().filter(x -> x.getPath().equalsIgnoreCase(testOperation.getTestPath())).findFirst().orElse(null);
			int filterIndex = -1;

			if(filter == null) {
				filter = new TestConfigurationFilter();
				filter.setPath(testOperation.getTestPath());
			} else {
				filterIndex = filters.indexOf(filter);
			}

			switch(testOperation.getMethod().toLowerCase()) {
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

			if(filterIndex > -1) {
				filters.set(filterIndex, filter);
			} else {
				filters.add(filter);
			}

		}

		return generate(filters);
	}
	
	
	/* Generate test cases for testOperation */
	protected abstract Collection<TestCase> generateOperationTestCases(Operation testOperation);
	
	// Generate the next test case and update the generation index. To be implemented on each subclass.
	public abstract TestCase generateNextTestCase(Operation testOperation);

	/* Generate test cases for the operation defined by path/method */
	protected Collection<TestCase> generate(String path, HttpMethod method) {

		// Reset generator's counters
		reset();
		
		// Get test configuration object for the operation
		Operation testOperation = TestConfigurationVisitor.getOperation(conf, path, method.name());

		// Create test data generators for each parameter
		createGenerators(testOperation.getTestParameters());

		return generateOperationTestCases(testOperation);
	}

	
	// Select the parameters to be included in the test case based on their weight in the test configuration file.
	protected void setTestCaseParameters(TestCase test, Operation testOperation) {
		// Set parameters
		if(testOperation.getTestParameters() != null) {
			for (TestParameter confParam : testOperation.getTestParameters()) {
				if (confParam.getWeight() == null || rand.nextFloat() <= confParam.getWeight()) {
					ITestDataGenerator generator = generators.get(Pair.with(confParam.getName(), confParam.getIn()));
					test.addParameter(confParam, generator.nextValueAsString());
				}
			}
		}
	}
	
	
	protected void updateContentType(TestCase test, io.swagger.v3.oas.models.Operation operation) {
		if (operation.getRequestBody() != null && operation.getRequestBody().getContent().containsKey("application/x-www-form-urlencoded"))
			test.setInputFormat("application/x-www-form-urlencoded");
	}

	// Set authentication details
	public void authenticateTestCase(TestCase test) {
		// Authentication
		if (conf.getAuth().getRequired()) {

			// Header parameters
			if (conf.getAuth().getHeaderParams()!=null)
				for (Map.Entry<String, String> param: conf.getAuth().getHeaderParams().entrySet())
					test.addHeaderParameter(param.getKey(), param.getValue());

			// Query parameters
			if (conf.getAuth().getQueryParams()!=null)
				for (Map.Entry<String, String> param: conf.getAuth().getQueryParams().entrySet())
					test.addQueryParameter(param.getKey(), param.getValue());

			// File containing all API keys
			if (conf.getAuth().getApiKeysPath()!=null)
				for(String authProperty : authManager.getAuthPropertyNames())
					test.addQueryParameter(authProperty, authManager.getAuthProperty(authProperty));

			// File containing all auth headers
			if (conf.getAuth().getHeadersPath()!=null)
				for(String authProperty : authManager.getAuthPropertyNames())
					test.addHeaderParameter(authProperty, authManager.getAuthProperty(authProperty));
		}
	}

	// Returns true if there are more test cases to be generated. To be implemented on each subclass.
	protected abstract boolean hasNext();

	// Create an empty test case with a random name.
	protected TestCase createTestCaseTemplate(Operation testOperation) {
		String testId = "test_" + IDGenerator.generateId() + "_" + removeNotAlfanumericCharacters(testOperation.getOperationId());
		TestCase test = new TestCase(testId, false, testOperation.getOperationId(), testOperation.getTestPath(), HttpMethod.valueOf(testOperation.getMethod().toUpperCase()));
		updateContentType(test, testOperation.getOpenApiOperation());

		return test;
	}
		
	protected void updateIndexes(TestCase test) {
		// Update indexes
		index++;
		if (test.getFaulty()) {
			nFaulty++;
		} else {
			nNominal++;
		}
	}
	
	// Create all generators needed for the parameters of an operation. 
	public void createGenerators(List<TestParameter> testParameters) {
		
		this.generators = new HashMap<>();

		if(testParameters != null) {
			for(TestParameter param: testParameters)
				generators.put(Pair.with(param.getName(), param.getIn()), TestDataGeneratorFactory.createTestDataGenerator(param.getGenerator()));
		}
	}


	public Float getFaultyRatio() {
		return faultyRatio;
	}

	public void setFaultyRatio(Float faultyRatio) {
		this.faultyRatio = faultyRatio;
	}

	public int getnFaulty() {
		return nFaulty;
	}

	public void setnFaulty(int nFaulty) {
		this.nFaulty = nFaulty;
	}

	public int getnNominal() {
		return nNominal;
	}

	public void setnNominal(int nNominal) {
		this.nNominal = nNominal;
	}

	public Map<Pair<String, String>, ITestDataGenerator> getGenerators() {
		return generators;
	}

	public void setGenerators(Map<Pair<String, String>, ITestDataGenerator> generators) {
		this.generators = generators;
	}

	public AuthManager getAuthManager() {
		return authManager;
	}

	public void setAuthManager(AuthManager authManager) {
		this.authManager = authManager;
	}

	protected String removeNotAlfanumericCharacters(String s) {
		return s.replaceAll("[^A-Za-z0-9]", "");
	}

	public long getSeed() {
		return this.seed;
	}

	public void setSeed(long seed) {
		this.seed = seed;
		rand.setSeed(seed);
	}


	public OpenApiInteractionValidator getValidator() {
		return validator;
	}
}
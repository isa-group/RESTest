package es.us.isa.restest.generators;

import com.atlassian.oai.validator.SwaggerRequestResponseValidator;
import es.us.isa.idlreasoner.analyzer.Analyzer;
import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.TestConfigurationVisitor;
import es.us.isa.restest.configuration.pojos.*;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.AuthManager;
import es.us.isa.restest.util.CSVManager;
import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import io.swagger.models.parameters.Parameter;

import java.util.*;

import static es.us.isa.restest.util.CSVManager.createFileWithHeader;
import static es.us.isa.restest.util.FileManager.checkIfExists;
import static es.us.isa.restest.util.SpecificationVisitor.findParameter;
import static es.us.isa.restest.util.SpecificationVisitor.hasDependencies;

public abstract class AbstractTestCaseGenerator {

	protected long seed = -1;								// Seed
	protected Random rand;
	protected OpenAPISpecification spec;
	protected TestConfigurationObject conf;
	protected Map<String,ITestDataGenerator> generators;	// Test data generators (random, boundaryValue, fixedlist...)
	protected AuthManager authManager;						// For if multiple API keys are used for the API
	protected Float faultyRatio = 0.1f;						// Ratio (0-1) of faulty test cases to be generated. Defaults to 0.1
	protected SwaggerRequestResponseValidator validator;	// Validator used to know if a test case is valid or not
	protected int numberOfTest;								// Number of test cases to be generated for each operation
	protected int index;									// Number of test cases generated so far
	protected int nCurrentFaulty;							// Number of faulty test cases generated in the current iteration
	protected int nCurrentNominal;							// Number of nominal test cases generated in the current iteration
	protected int nFaulty;									// Number of faulty test cases generated so far
	protected int nNominal;									// Number of nominal test cases generated so far

	public AbstractTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
		this.spec = spec;
		this.conf = conf;

		// AuthManager configuration:
		String authPath = conf.getAuth().getApiKeysPath();
		if (authPath == null)
			authPath = conf.getAuth().getHeadersPath();
		if (authPath != null)
			this.authManager = new AuthManager(authPath);

		this.validator = SwaggerRequestResponseValidator.createFor(spec.getPath()).build();
		this.numberOfTest = nTests;
		this.index = 0;
		this.nFaulty = 0;
		this.nNominal = 0;
		this.nCurrentFaulty = 0;
		this.nCurrentNominal = 0;

		this.rand = new Random();
		this.seed = rand.nextLong();
		rand.setSeed(this.seed);
	}

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

	protected abstract Collection<TestCase> generateOperationTestCases(Operation specOperation,
			es.us.isa.restest.configuration.pojos.Operation testOperation, String path, HttpMethod method);

	protected Collection<TestCase> generate(String path, HttpMethod method) {

		// Get specification operation
		Operation specOperation = spec.getSpecification().getPath(path).getOperationMap().get(method);

		// Get test configuration object for the operation
		es.us.isa.restest.configuration.pojos.Operation testOperation = TestConfigurationVisitor.getOperation(conf, path, method.name());

		// Create test data generators for each parameter
		createGenerators(testOperation.getTestParameters());

		return generateOperationTestCases(specOperation, testOperation, path, method);
	}

	protected void setTestCaseParameters(TestCase test, Operation specOperation,
			es.us.isa.restest.configuration.pojos.Operation testOperation) {
		// Set parameters
		for (TestParameter confParam : testOperation.getTestParameters()) {
			Parameter specParameter = findParameter(specOperation, confParam.getName());

			if (specParameter.getRequired() || rand.nextFloat() <= confParam.getWeight()) {
				ITestDataGenerator generator = generators.get(confParam.getName());
				switch (specParameter.getIn()) {
					case "header":
						test.addHeaderParameter(confParam.getName(), generator.nextValueAsString());
						break;
					case "query":
						test.addQueryParameter(confParam.getName(), generator.nextValueAsString());
						break;
					case "path":
						test.addPathParameter(confParam.getName(), generator.nextValueAsString());
						break;
					case "body":
						test.setBodyParameter(generator.nextValueAsString());
						break;
					case "formData":
						test.addFormParameter(confParam.getName(), generator.nextValueAsString());
						break;
					default:
						throw new IllegalArgumentException("Parameter type not supported: " + specParameter.getIn());
				}
			}
		}
	}

	protected void authenticateTestCase(TestCase test) {
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
	
	// Generate the next test case and update the generation index. To be implemented on each subclass.
	protected abstract TestCase generateNextTestCase(Operation specOperation,
			es.us.isa.restest.configuration.pojos.Operation testOperation, String path, HttpMethod method, String faultyReason);

	protected void updateIndexes(boolean currentTestFaulty) {
		// Update indexes
		index++;
		if (currentTestFaulty) {
			nCurrentFaulty++;
			nFaulty++;
		} else {
			nCurrentNominal++;
			nNominal++;
		}
	}
	
	// Create all generators needed for the parameters of an operation
	protected void createGenerators(List<TestParameter> testParameters) {
		
		this.generators = new HashMap<String,ITestDataGenerator>();
		
		for(TestParameter param: testParameters)
			generators.put(param.getName(), TestDataGeneratorFactory.createTestDataGenerator(param.getGenerator()));

	}

	public void exportNominalFaultyToCSV(String filePath, String testClassName) {
		if (!checkIfExists(filePath)) // If the file doesn't exist, create it (only once)
			createFileWithHeader(filePath, "test_id,nNominal,nFaulty");
		if (testClassName.equals("total"))
			CSVManager.writeRow(filePath, testClassName + "," + nNominal + "," + nFaulty);
		else
			CSVManager.writeRow(filePath, testClassName + "," + nCurrentNominal + "," + nCurrentFaulty);
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

	public int getnCurrentFaulty() {
		return nCurrentFaulty;
	}

	public void setnCurrentFaulty(int nCurrentFaulty) {
		this.nCurrentFaulty = nCurrentFaulty;
	}

	public int getnCurrentNominal() {
		return nCurrentNominal;
	}

	public void setnCurrentNominal(int nCurrentNominal) {
		this.nCurrentNominal = nCurrentNominal;
	}

	protected String removeNotAlfanumericCharacters(String s) {
		return s.replaceAll("[^A-Za-z0-9]", "");
	}

	private long getSeed() {
		return this.seed;
	}

	private void setSeed(long seed) {
		this.seed = seed;
		rand.setSeed(seed);
	}
}
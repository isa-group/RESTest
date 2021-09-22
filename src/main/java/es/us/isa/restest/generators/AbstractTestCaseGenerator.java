package es.us.isa.restest.generators;

import java.util.*;
import java.util.stream.Stream;

import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.inputs.stateful.BodyGenerator;
import es.us.isa.restest.inputs.stateful.ParameterGenerator;
import es.us.isa.restest.mutation.TestCaseMutation;
import es.us.isa.restest.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;

import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.TestConfigurationVisitor;
import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import es.us.isa.restest.inputs.perturbation.ObjectPerturbator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.specification.ParameterFeatures;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.v3.oas.models.PathItem.HttpMethod;

import static es.us.isa.restest.configuration.TestConfigurationVisitor.hasStatefulGenerators;
import static es.us.isa.restest.configuration.TestConfigurationVisitor.isArteEnabled;

/**
 * Abstract class to be implemented by test case generators
 * @author Sergio Segura
 */

public abstract class AbstractTestCaseGenerator {

	public static final String INDIVIDUAL_PARAMETER_CONSTRAINT = "individual_parameter_constraint";
	private static Logger logger = LogManager.getLogger(AbstractTestCaseGenerator.class.getName());

	protected long seed = -1;												// Seed
	protected Random rand;
	protected OpenAPISpecification spec;
	protected TestConfigurationObject conf;
	// The following pairs stand for Pair<ParameterName,Type(query, path, header...)>
	protected Map<Pair<String, String>,List<ITestDataGenerator>> nominalGenerators;	// Nominal test data generators (random, boundaryValue, fixedlist...)
	protected Map<Pair<String, String>,List<ITestDataGenerator>> faultyGenerators;	// Faulty test data generators (random, boundaryValue, fixedlist...)
	protected AuthManager authManager;										// For if multiple API keys are used for the API
	protected Float faultyRatio = 0f;										// Ratio (0-1) of faulty test cases to be generated on each operation. Defaults to 0.1
	protected int numberOfTests;											// Number of test cases to be generated for each operation
	private int maxTriesPerTestCase=100;									// Maximum number of tries for generating a random test case conforming the input OAS schema.

	// Global counters
	protected int nTotalTests;												// Number of test cases generated so far
	protected int nTotalFaulty;												// Number of faulty test cases generated so far
	protected int nTotalNominal;											// Number of nominal test cases generated so far

	// Local counters for each operation
	protected int nTests;													// Number of test cases generated for the current operation
	protected int nFaulty;													// Number of faulty test cases generated for the current operation
	protected int nNominal;													// Number of nominal test cases generated for the current operation

	private boolean hasStatefulGenerators;
	private boolean isArteEnabled;
	private boolean checkTestCases;


	public AbstractTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
		preconditions(conf);

		this.spec = spec;
		this.conf = conf;

		// AuthManager configuration:
		String authPath = conf.getAuth().getApiKeysPath();
		if (authPath == null)
			authPath = conf.getAuth().getHeadersPath();
		if (authPath == null)
			authPath = conf.getAuth().getOauthPath();
		if (authPath != null && conf.getAuth().getOauthPath() != null)
			this.authManager = new AuthManager(authPath, true);
		else if (authPath != null)
			this.authManager = new AuthManager(authPath);

		this.numberOfTests = nTests;

		// Reset counters
		resetGenerator();


		this.rand = new Random();
		this.seed = rand.nextLong();
		rand.setSeed(this.seed);
	}


	// Reset all numerical counters
	public void resetGenerator() {
		this.nTotalTests = 0;
		this.nTotalFaulty = 0;
		this.nTotalNominal = 0;
		this.nTests = 0;
		this.nFaulty = 0;
		this.nNominal = 0;
	}

	// Reset counters for the current operation
	protected void resetOperation() {
		this.nTests = 0;
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
			List<ParameterFeatures> requiredParams = SpecificationVisitor.getRequiredParametersFeatures(testOperation.getOpenApiOperation());

			if(testOperation.getTestParameters() != null) {

				for(TestParameter testParameter : testOperation.getTestParameters()) {

					ParameterFeatures param = SpecificationVisitor.findParameterFeatures(testOperation.getOpenApiOperation(), testParameter.getName(), testParameter.getIn());
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
	 * @throws RESTestException if the test case generated does not conform to the specification
	 */
	public Collection<TestCase> generate(Collection<TestConfigurationFilter> filters) throws RESTestException {

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
	 * @throws RESTestException if the test HTTP method is other than 'get', 'post', 'put' or 'delete'
	 */
	public Collection<TestCase> generate() throws RESTestException {
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
					throw new RESTestException("Methods other than GET, POST, PUT and DELETE are not " +
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
	protected abstract Collection<TestCase> generateOperationTestCases(Operation testOperation) throws RESTestException;

	/**
	 * Generate the next test case and update the generation index. To be implemented on each subclass.
	 * This method MUST call the {@link #checkTestCaseValidity(TestCase)} method before returning the test case.
	 */
	public abstract TestCase generateNextTestCase(Operation testOperation) throws RESTestException;

	/* Generate test cases for the operation defined by path/method */
	protected Collection<TestCase> generate(String path, HttpMethod method) throws RESTestException {

		// Get test configuration object for the operation
		Operation testOperation = TestConfigurationVisitor.getOperation(conf, path, method.name());

		// Create test data generators for each parameter
		createGenerators(testOperation);

		// Update these booleans, which may differ for every operation
		hasStatefulGenerators = hasStatefulGenerators(testOperation);
		isArteEnabled = isArteEnabled(testOperation);

		return generateOperationTestCases(testOperation);
	}



	/* Generate a basic valid random test case according to the test configuration file in two steps:
	* 	1) Select the parameters to be included in the test case based on their weight.
	* 	2) Generate valid test values with the corresponding test data generators in the test configuration file. Input objects (if any) are not perturbated yet
	* 	3) Try to perturbate input objects (if any). If no valid perturbation can be generated, set the parameter to the original object provided in the test configuration file
	*/
	protected TestCase generateRandomValidTestCase(Operation testOperation) throws RESTestException {

		TestCase test = createTestCaseTemplate(testOperation);
		boolean perturbation = false;

		// Set parameters and values. Objects (if any) are initially not perturbated
		if(testOperation.getTestParameters() != null) {
			for (TestParameter confParam : testOperation.getTestParameters()) {
				if (confParam.getWeight() == null || rand.nextFloat() <= confParam.getWeight()) {
					ITestDataGenerator generator = getRandomGenerator(nominalGenerators.get(Pair.with(confParam.getName(), confParam.getIn())));
					if (generator instanceof ObjectPerturbator) {
						test.addParameter(confParam, ((ObjectPerturbator) generator).getRandomOriginalStringObject());		// Objects are not perturbated yet
						perturbation = true;
					}
					else if (generator instanceof BodyGenerator) {
						test.addParameter(confParam, ((BodyGenerator) generator).nextValueAsString(false));			// Objects are not mutated yet
					} else
						test.addParameter(confParam, generator.nextValueAsString());
				}
			}
		}

		// If a perturbation generator is included in the test configuration file, try to generate a new (valid) test case by perturbating a valid input object
		if (perturbation)
			perturbate(test, testOperation);

		return test;
	}

	/**
	 * Make sure the test case generated conforms to the specification. Otherwise, throw an exception and stop the execution
	 * There's an exception: if stateful generators are configured, or ARTE is enabled, we cannot assure that the test case
	 * will be valid, therefore we omit this
	 */
	protected void checkTestCaseValidity(TestCase test) throws RESTestException {
		if (!test.getFaulty() && checkTestCases && !hasStatefulGenerators && !isArteEnabled) {
			List<String> errors = test.getValidationErrors(OASAPIValidator.getValidator(spec));
			if (!errors.isEmpty()) {
				throw new RESTestException("The test case generated does not conform to the specification: " + errors);
			}
		}
	}

	/**
	 * Tries to make a test case faulty by using an invalid generator for a random parameter
	 * @param testCase The test case to modify
	 * @param testOperation The operation the test case refers to
	 * @return {@code true} if the test case was modified, {@code false} otherwise
	 */
	protected boolean makeTestCaseFaultyDueToInvalidGenerator(TestCase testCase, Operation testOperation) {

		if (faultyGenerators.isEmpty())
			return false;

		// Get random parameter (and faulty generator) which will contain an invalid value
		Object[] possibleFaultyParams = faultyGenerators.keySet().toArray();
		Pair<String, String> faultyParam = (Pair<String, String>)possibleFaultyParams[rand.nextInt(possibleFaultyParams.length)];
		ITestDataGenerator generator = getRandomGenerator(faultyGenerators.get(faultyParam));

		// Modify test case by adding invalid value
		String faultyValue = generator.nextValueAsString();
		testCase.addParameter(faultyParam.getValue1(), faultyParam.getValue0(), faultyValue);

		// Update faulty and faulty reason
		testCase.setFaulty(true);
		testCase.setFaultyReason(INDIVIDUAL_PARAMETER_CONSTRAINT + ":Set parameter " + faultyParam.getValue0() + " with invalid value " + faultyValue);

		return true;
	}

	/**
	 * Tries to make a test case faulty by violating an individual constraint (ex. excluding a required parameter)
	 * @param testCase The test case to modify
	 * @param testOperation The operation the test case refers to
	 * @return {@code true} if the test case was modified, {@code false} otherwise
	 * @throws RESTestException
	 */
	protected boolean makeTestCaseFaultyDueToIndividualConstraints(TestCase testCase, Operation testOperation) throws RESTestException {

		TestCase originalTest = new TestCase(testCase);
		originalTest.equals(testCase);
		ITestDataGenerator bodyGenerator = null;
		List<String> generationAlternatives = new ArrayList<>();
		generationAlternatives.add("mutation");

		if (testOperation.getOpenApiOperation().getRequestBody() != null) {
			TestParameter bodyParam = testOperation.getTestParameters().stream().filter(x -> x.getName().equals("body")).findFirst().orElse(null);
			Stream<ITestDataGenerator> s;
			if (bodyParam != null) {
				if (faultyGenerators.get(Pair.with(bodyParam.getName(), bodyParam.getIn())) != null) {
					s = Stream.concat(
							nominalGenerators.get(Pair.with(bodyParam.getName(), bodyParam.getIn())).stream(),
							faultyGenerators.get(Pair.with(bodyParam.getName(), bodyParam.getIn())).stream());
				} else {
					s = nominalGenerators.get(Pair.with(bodyParam.getName(), bodyParam.getIn())).stream();
				}
				bodyGenerator = s.filter(x -> x instanceof BodyGenerator || x instanceof ObjectPerturbator)
						.findFirst()
						.orElse(null);

				if (bodyGenerator != null) {
					generationAlternatives.add("request_body_mutation");
				}
			}
		}

		if (!faultyGenerators.isEmpty()) {
			generationAlternatives.add("invalid_generator");
		}

		Collections.shuffle(generationAlternatives);

		int i = 0;
		while (i < generationAlternatives.size() && originalTest.equals(testCase)) {
			if (generationAlternatives.get(i).equals("invalid_generator")) {// Test case with invalid generator
				makeTestCaseFaultyDueToInvalidGenerator(testCase, testOperation);
			}
			if (originalTest.equals(testCase) && generationAlternatives.get(i).equals("request_body_mutation")) { // Test case with invalid body
				makeTestCaseFaultyDueToInvalidRequestBody(testCase, testOperation, bodyGenerator);
			}
			if (originalTest.equals(testCase)) { // Valid test case and mutate it
				String mutationDescription = TestCaseMutation.mutate(testCase, testOperation.getOpenApiOperation());
				if (!mutationDescription.equals("")) { // A mutation has been applied
					testCase.setFaulty(true);
					testCase.setFaultyReason(INDIVIDUAL_PARAMETER_CONSTRAINT + ":" + mutationDescription);
				}
			}

			i++;
		}

		return !originalTest.equals(testCase);
	}

	/**
	 * Tries to make a test case faulty by generating an invalid body. Works only if there's a body and a
	 * BodyGenerator or ObjectPerturbator configured
	 * @param testCase The test case to modify
	 * @param testOperation The operation the test case refers to
	 * @param bodyGenerator The generator configured for the body parameter
	 * @return {@code true} if the test case was modified, {@code false} otherwise
	 * @throws RESTestException
	 */
	protected boolean makeTestCaseFaultyDueToInvalidRequestBody(TestCase testCase, Operation testOperation, ITestDataGenerator bodyGenerator) throws RESTestException {
		TestCase originalTest = new TestCase(testCase);
		TestParameter bodyParam = testOperation.getTestParameters().stream().filter(x -> x.getName().equals("body")).findFirst().orElse(null);
		List<String> errors = new ArrayList<>();

		if (bodyGenerator != null && bodyParam != null) {
			errors = new ArrayList<>();
			for (int i = 0; i < maxTriesPerTestCase && errors.isEmpty(); i++) {
				if (bodyGenerator instanceof BodyGenerator)
					testCase.addParameter(bodyParam, ((BodyGenerator) bodyGenerator).nextValueAsString(true));
				else if (bodyGenerator instanceof ObjectPerturbator)
					testCase.addParameter(bodyParam, bodyGenerator.nextValueAsString());
				errors = testCase.getValidationErrors(OASAPIValidator.getValidator(spec));
			}
			// No invalid body generated. Return null and try to generate faulty test case in different way
			if (errors.isEmpty()) {
				logger.warn("Maximum number of tries reached when trying to generate invalid JSON body for the operation {}", testOperation.getOpenApiOperation().getOperationId());
				testCase = originalTest;
			}
		}

		if (!originalTest.equals(testCase)) {
			testCase.setFaulty(true);
			String errorsMsg = String.join(" --- ", errors);
			testCase.setFaultyReason(INDIVIDUAL_PARAMETER_CONSTRAINT + ':' + " invalid request body: " + errorsMsg);
		}

		return !originalTest.equals(testCase);
	}


    /* Try to perturbate input objects using the ObjectPerturbator. If not possible, set the parameter to the original object provided in the test configuration file */
	private void perturbate(TestCase test, Operation testOperation) {

		for (TestParameter confParam : testOperation.getTestParameters()) {
			ITestDataGenerator generator = getRandomGenerator(nominalGenerators.get(Pair.with(confParam.getName(), confParam.getIn())));
			if (generator instanceof ObjectPerturbator) {
				boolean valid = false;
				for(int i=0;i<maxTriesPerTestCase && !valid; i++) {
					test.addParameter(confParam, generator.nextValueAsString());
					valid = test.isValid(OASAPIValidator.getValidator(spec));
				}

				// No valid perturbations generated. Set original object
				if (!valid) {
					test.addParameter(confParam, ((ObjectPerturbator) generator).getRandomOriginalStringObject());
					logger.warn("Maximum number of tries reached when trying to perturbate the input object for the operation {}", testOperation.getOpenApiOperation().getOperationId());
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

			// File containing OAuth details
			if (conf.getAuth().getOauthPath()!=null)
				test.addHeaderParameter("Authorization", authManager.getUpdatedOauthHeader());
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


	// Update counters
	protected void updateIndexes(TestCase test) {
		nTotalTests++;		// Total number of test cases generated by the generator
		nTests++;			// Number of test cases generated for the current operation
		if (test.getFaulty() != null) {
			if (test.getFaulty()) {
				nTotalFaulty++;        // Total number of faulty test cases
				nFaulty++;            // Number of faulty test cases generated for the current operation
			} else {
				nTotalNominal++;        // Total number of nominal test cases
				nNominal++;                // Number of nominal test cases generated for the current operation
			}
		}
	}

	// Create all generators needed for the parameters of an operation.
	public void createGenerators(Operation operation) {

		List<TestParameter> testParameters = operation.getTestParameters();
		this.nominalGenerators = new HashMap<>();
		this.faultyGenerators = new HashMap<>();

		if(testParameters != null) {
			for(TestParameter param: testParameters) {
				List<ITestDataGenerator> nomGens = new ArrayList<>();
				List<ITestDataGenerator> faultyGens = new ArrayList<>();
				for(Generator g : param.getGenerators()) {
					ITestDataGenerator gen = TestDataGeneratorFactory.createTestDataGenerator(g);

					if (gen instanceof BodyGenerator) {
						((BodyGenerator) gen).setDataDirPath(spec.getPath().substring(0, spec.getPath().lastIndexOf('/')));
						((BodyGenerator) gen).setSpec(spec);
						((BodyGenerator) gen).setOpenApiOperation(operation.getOpenApiOperation());
						((BodyGenerator) gen).setOperation("GET", operation.getTestPath());
					}
					if (gen instanceof ParameterGenerator) {
						((ParameterGenerator) gen).setDataDirPath(spec.getPath().substring(0, spec.getPath().lastIndexOf('/')));
						((ParameterGenerator) gen).setSpec(spec);
						((ParameterGenerator) gen).setOperation("GET", operation.getTestPath());
						((ParameterGenerator) gen).setParameterName(param.getName());
						((ParameterGenerator) gen).setParameterType(SpecificationVisitor.findParameterFeatures(operation.getOpenApiOperation(), param.getName(), param.getIn()).getType());
					}

					if(g.isValid()) nomGens.add(gen);
					else faultyGens.add(gen);
				}
				if (!nomGens.isEmpty())
					nominalGenerators.put(Pair.with(param.getName(), param.getIn()), nomGens);
				if (!faultyGens.isEmpty())
					faultyGenerators.put(Pair.with(param.getName(), param.getIn()), faultyGens);
			}
		}
	}

	protected ITestDataGenerator getRandomGenerator(List<ITestDataGenerator> generators) {
		return generators.get(rand.nextInt(generators.size()));
	}


	public Float getFaultyRatio() {
		return faultyRatio;
	}

	public void setFaultyRatio(Float faultyRatio) {
		this.faultyRatio = faultyRatio;
	}

	public int getnFaulty() {
		return nTotalFaulty;
	}

	public void setnFaulty(int nFaulty) {
		this.nTotalFaulty = nFaulty;
	}

	public int getnNominal() {
		return nTotalNominal;
	}

	public void setnNominal(int nNominal) {
		this.nTotalNominal = nNominal;
	}

	public Map<Pair<String, String>, List<ITestDataGenerator>> getNominalGenerators() {
		return nominalGenerators;
	}

	public Map<Pair<String, String>, List<ITestDataGenerator>> getFaultyGenerators() {
		return faultyGenerators;
	}

	public void setNominalGenerators(Map<Pair<String, String>, List<ITestDataGenerator>> nominalGenerators) {
		this.nominalGenerators = nominalGenerators;
	}

	public void setFaultyGenerators(Map<Pair<String, String>, List<ITestDataGenerator>> faultyGenerators) {
		this.faultyGenerators = faultyGenerators;
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


	public int getMaxTriesPerTestCase() {
		return maxTriesPerTestCase;
	}


	public void setMaxTriesPerTestCase(int maxTriesPerTestCase) {
		this.maxTriesPerTestCase = maxTriesPerTestCase;
	}

	public boolean isCheckTestCases() {
		return checkTestCases;
	}

	public void setCheckTestCases(boolean checkTestCases) {
		this.checkTestCases = checkTestCases;
	}
}
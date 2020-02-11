package es.us.isa.restest.generators;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

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

import static es.us.isa.restest.util.SpecificationVisitor.getParametersSubjectToInvalidValueChange;
import static es.us.isa.restest.util.SpecificationVisitor.getRequiredNotPathParameters;

public abstract class AbstractTestCaseGenerator {

	protected OpenAPISpecification spec;
	protected TestConfigurationObject conf;
	protected Map<String,ITestDataGenerator> generators;	// Test data generators (random, boundaryValue, fixedlist...)
	protected AuthManager authManager;						// For if multiple API keys are used for the API
	protected Boolean enableFaulty = true;					// True if faulty test cases want to be generated. Defaults to true
	protected Float faultyRatio = 0.1f;						// Ratio (0-1) of faulty test cases to be generated. Defaults to 0.1
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
	
		// Get test configuration object for the operation
		es.us.isa.restest.configuration.pojos.Operation testOperation = TestConfigurationVisitor.getOperation(conf, path, method.name());
		
		List<TestCase> testCases = new ArrayList<TestCase>();
		
		// Create test data generators for each parameter
		createGenerators(testOperation.getTestParameters());

		// Whether the next test case to generate must be faulty or not
		boolean nextIsFaulty = true;
		
		while (hasNext()) {

			// Generate faulty test cases until faultyRatio is reached
			if (nextIsFaulty && (float)index/(float)numberOfTest > faultyRatio)
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

	/**
	 * Given a valid (nominal) test case, mutate it and convert it into an invalid (faulty) test case.
	 * Set of possible mutations:
	 * <ol>
	 *     <li>Remove required parameter.</li>
	 *     <li>Change type of parameter value (e.g. use a string for an integer parameter).</li>
	 *     <li>Violate a constraint of a parameter (e.g. use an integer value higher than the maximum.</li>
	 *     <li>Body with invalid structure (only applicable to create or update operations).</li>
	 *     <li>Violate inter-parameter dependencies.</li>
	 * </ol>
	 * @param nominalTestCase Original valid test case
	 * @return Mutated invalid test case
	 */
	protected TestCase makeTestCaseFaulty(TestCase nominalTestCase, Operation specOperation) {
		final int nMutations = 2;
		boolean mutationApplied = false; // When one mutation is applied, the test case will be faulty
		List<Boolean> mutationsTried = new ArrayList<>(Arrays.asList(new Boolean[nMutations])); // In case one mutation cannot be applied, try others
		float randomVal;

		while (!mutationApplied && mutationsTried.contains(Boolean.FALSE)) {
			randomVal = ThreadLocalRandom.current().nextFloat();
			if (!mutationsTried.get(0) && randomVal < 1f/nMutations) { // Remove required parameter
				List<Parameter> candidateParameters = getRequiredNotPathParameters(specOperation); // Path parameters cannot be removed
				if (candidateParameters.size() != 0) {
					Parameter selectedParam = candidateParameters.get(ThreadLocalRandom.current().nextInt(0, candidateParameters.size()));
					switch (selectedParam.getIn()) {
						case "query":
							nominalTestCase.removeQueryParameter(selectedParam.getName());
							break;
						case "header":
							nominalTestCase.removeHeaderParameter(selectedParam.getName());
							break;
						case "body":
							nominalTestCase.setBodyParameter(null);
							break;
						// TODO: Support form-data parameters
					}
					mutationApplied = true; // Stop loop
				} else {
					mutationsTried.set(0, true); // This mutation cannot be applied, do not try again
				}
			} else if (!mutationsTried.get(1) && randomVal < 2f/nMutations) {
				List<Parameter> candidateParameters = getParametersSubjectToInvalidValueChange(specOperation); // Parameters that can be mutated to create a faulty test case
				if (candidateParameters.size() != 0) {
					Parameter selectedParam = candidateParameters.get(ThreadLocalRandom.current().nextInt(0, candidateParameters.size())); // Select one randomly
					mutateParameterValueInTestCase(nominalTestCase, selectedParam); // Mutate it to create a faulty test case
					mutationApplied = true; // Stop loop
				} else {
					mutationsTried.set(1, true); // This mutation cannot be applied, do not try again
				}
			} else {
				// TODO: Support other faulty test cases: Body with invalid structure, and violation of inter-parameter dependencies
			}
		}

		return nominalTestCase; // Return mutated nominalTestCase, i.e. faultyTestCase
	}

	/**
	 * Receives a test case and a parameter to mutate and mutates it.
	 * TODO: Improve the mutation alternatives.
	 * @param tc
	 * @param param
	 */
	private void mutateParameterValueInTestCase(TestCase tc, Parameter param) {
		ParameterFeatures pFeatures = new ParameterFeatures(param);
		String randomBigInt = Integer.toString(ThreadLocalRandom.current().nextInt(1000, 10001));
		Integer randomSmallInt = ThreadLocalRandom.current().nextInt(1, 10);
		String randomString = RandomStringUtils.randomAscii(ThreadLocalRandom.current().nextInt(10, 20));

		if (pFeatures.getEnumValues() != null) { // Value of enum range
			if (pFeatures.getType().equals("integer") || pFeatures.getType().equals("number"))
				setParameterToValueInTestCase(tc, pFeatures, randomBigInt); // Number enum
			else if (pFeatures.getType().equals("string"))
				setParameterToValueInTestCase(tc, pFeatures, randomString); // String enum
		} else if (pFeatures.getType().equals("boolean")) { // Boolean parameter with different type (e.g. string)
			setParameterToValueInTestCase(tc, pFeatures, randomString);
		} else if ((pFeatures.getType().equals("integer") || pFeatures.getType().equals("number"))) { // Number
			if (pFeatures.getMin() != null) { // Number with min constraint. Violate it
				if (pFeatures.getType().equals("number"))
					setParameterToValueInTestCase(tc, pFeatures, Float.toString(pFeatures.getMin().floatValue()-randomSmallInt));
				else if (pFeatures.getType().equals("integer"))
					setParameterToValueInTestCase(tc, pFeatures, Integer.toString(pFeatures.getMin().intValue()-randomSmallInt));
			} else if (pFeatures.getMax() != null) { // Number with max constraint. Violate it
				if (pFeatures.getType().equals("number"))
					setParameterToValueInTestCase(tc, pFeatures, Float.toString(pFeatures.getMax().floatValue()+randomSmallInt));
				else if (pFeatures.getType().equals("integer"))
					setParameterToValueInTestCase(tc, pFeatures, Integer.toString(pFeatures.getMax().intValue()+randomSmallInt));
			} else // Number parameter with different type (e.g. string)
				setParameterToValueInTestCase(tc, pFeatures, randomString);
		} else if (pFeatures.getType().equals("string")) { // String
			if (pFeatures.getFormat() != null) // String with format (URL, email, etc.). Violate format using random string
				setParameterToValueInTestCase(tc, pFeatures, randomString);
			else if (pFeatures.getMinLength() != null && pFeatures.getMinLength() > 1) // Replace with string with fewer chars than minLength
				setParameterToValueInTestCase(tc, pFeatures, RandomStringUtils.randomAscii(pFeatures.getMinLength()-1));
			else if (pFeatures.getMaxLength() != null) // Replace with string with more chars than maxLength
				setParameterToValueInTestCase(tc, pFeatures, RandomStringUtils.randomAscii(pFeatures.getMaxLength() + randomSmallInt));
		}
	}

	/**
	 * Receives a test case, a parameter and a value and sets the parameter to that value.
	 * The parameter features are passed, since depending on where the parameter is
	 * (query, path, etc.), one method or another must be called.
	 * @param tc
	 * @param pFeatures
	 * @param value
	 */
	private void setParameterToValueInTestCase(TestCase tc, ParameterFeatures pFeatures, String value) {
		switch (pFeatures.getIn()) {
			case "query":
				tc.addQueryParameter(pFeatures.getName(), value);
				break;
			case "header":
				tc.addHeaderParameter(pFeatures.getName(), value);
				break;
			case "path":
				tc.addPathParameter(pFeatures.getName(), value);
				break;
			// TODO: Support form-data parameters
		}
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
}
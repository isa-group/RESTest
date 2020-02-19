package es.us.isa.restest.generators;

import java.util.Random;

import es.us.isa.idlreasoner.analyzer.Analyzer;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.AuthManager;
import es.us.isa.restest.util.IDGenerator;
import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import io.swagger.models.parameters.Parameter;

import static es.us.isa.restest.mutation.TestCaseMutation.makeTestCaseFaulty;
import static es.us.isa.restest.util.IDLAdapter.restest2idlTestCase;
import static es.us.isa.restest.util.SpecificationVisitor.*;

public class RandomTestCaseGenerator extends AbstractTestCaseGenerator {

	private long seed = -1;								// Seed
	Random rand;
	
	public RandomTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
		this.spec = spec;
		this.conf = conf;
		this.numberOfTest = nTests;
		this.index =0;
		this.violateDependency = false;
		
		this.rand = new Random();
		this.seed = rand.nextLong();
		rand.setSeed(this.seed);
	}

	public RandomTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, String apiKeysPath, int nTests) {
		this(spec, conf, nTests);
		this.authManager = new AuthManager(apiKeysPath);
	}

	public RandomTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, Boolean enableFaulty, int nTests) {
		this(spec, conf, nTests);
		this.enableFaulty = enableFaulty;
	}

	public RandomTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, Boolean enableFaulty, String apiKeysPath, int nTests) {
		this(spec, conf, nTests);
		this.authManager = new AuthManager(apiKeysPath);
		this.enableFaulty = enableFaulty;
	}
	

	// Generate the next test case and update the generation index
	protected TestCase generateNextTestCase(Operation specOperation, es.us.isa.restest.configuration.pojos.Operation testOperation, Boolean faulty, String path, HttpMethod method) {

		Boolean isDesiredTestCase = false;
		String testId = removeNotAlfanumericCharacters(testOperation.getOperationId()) + "Test_" + IDGenerator.generateId();
		TestCase test = null;

		while(!isDesiredTestCase) {
			test = new TestCase(testId, faulty, testOperation.getOperationId(), path, method);

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

			// Algorithm to decide whether this test case must be faulty or not, and how:
			if (faulty) { // If this test case must be faulty
				if (idlReasoner != null) { // If the operation has dependencies
					if (violateDependency) { // If in this iteration, the test case must violate a dependency
						if (!idlReasoner.validRequest(restest2idlTestCase(test))) { // Check if the current request is INVALID
							isDesiredTestCase = true; // If so, return this test case
						}
					} else { // If in this iteration, the test case must be mutated to make it faulty
						if (!makeTestCaseFaulty(test, specOperation)) { // Try to make it faulty by mutating it. If it's not mutated...
							test.setFaulty(false); // ... set faulty to false, in order to have the right oracle
							if (idlReasoner.validRequest(restest2idlTestCase(test))) // And if all dependencies are fulfilled...
								test.setFulfillsDependencies(true); // Update property to have another oracle (400 status code)
						}
						isDesiredTestCase = true; // Return this test case
					}
				} else { // If the operation doesn't have dependencies
					test.setFulfillsDependencies(true); // All dependencies (none) are fulfilled
					if (!makeTestCaseFaulty(test, specOperation)) // Try to make it faulty by mutating it. If it's not mutated...
						test.setFaulty(false); // ... set faulty to false, in order to have the right oracle
					isDesiredTestCase = true; // Return this test case
				}

			} else { // If this test case must not be faulty
				test.setFulfillsDependencies(true); // All dependencies must be fulfilled for the test case to be valid
				if (idlReasoner != null) { // If the operation has dependencies
					if (idlReasoner.validRequest(restest2idlTestCase(test))) { // Check if the current request is valid
						isDesiredTestCase = true; // If so, return this test case
					}
				} else { // If the operation doesn't have dependencies
					isDesiredTestCase = true; // The test case will be valid for sure, so return it
				}
			}
		}
		
		index++;

		if (idlReasoner != null && faulty) // When trying to create faulty test cases, if the operation has dependencies...
			violateDependency = !violateDependency; // ... every two iterations, violate an inter-parameter dependency
		
		return test;
	}
	
	// Returns true if there are more test cases to be generated
	protected boolean hasNext() {
		Boolean res = index<numberOfTest;
		if (index == numberOfTest)
			index = 0;
		return res;
	}

	private long getSeed() {
		return this.seed;
	}

	private void setSeed(long seed) {
		this.seed = seed;
		rand.setSeed(seed);
	}

	private String removeNotAlfanumericCharacters(String s) {
		return s.replaceAll("[^A-Za-z0-9]", "");
	}
}

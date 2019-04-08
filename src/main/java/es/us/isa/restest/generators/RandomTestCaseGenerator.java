package es.us.isa.restest.generators;

import java.util.Random;

import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.SpecificationVisitor;
import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import io.swagger.models.parameters.Parameter;

public class RandomTestCaseGenerator extends AbstractTestCaseGenerator {

	int numberOfTest;									// Number of test cases to be generated for each path
	int index;											// Number of test cases generates so far
	private long seed = -1;								// Seed
	Random rand;
	
	public RandomTestCaseGenerator(OpenAPISpecification spec,TestConfigurationObject conf, int nTests) {
		this.spec = spec;
		this.conf = conf;
		this.numberOfTest = nTests;
		this.index =0;
		
    	this.rand = new Random();
    	this.seed = rand.nextLong();
    	rand.setSeed(seed);
	}
	

	// Generate the next test case and update the generation index
	protected TestCase generateNextTestCase(Operation specOperation, es.us.isa.restest.configuration.pojos.Operation testOperation, String path, HttpMethod method) {
		
		TestCase test = new TestCase(testOperation.getOperationId(), path, method);
		
		// Set parameters
		for(TestParameter confParam: testOperation.getTestParameters()) {
			Parameter specParameter = SpecificationVisitor.findParameter(specOperation, confParam.getName());
			
			if (specParameter.getRequired() || rand.nextFloat()<=confParam.getWeight()) {
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
				default:
					throw new IllegalArgumentException("Parameter type not supported: " + specParameter.getIn());
				}
			}
		}
		
		index++;
		
		return test;
	}
	
	// Returns true if there are more test cases to be generated
	protected boolean hasNext() {
		Boolean res = index<numberOfTest;
		if (index == numberOfTest)
			index = 0;
		return res;
	}
}

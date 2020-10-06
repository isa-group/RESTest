package es.us.isa.restest.generators;

import java.util.Collection;

import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.OASAPIValidator;

public class GeneratorTestHelper {

	public GeneratorTestHelper() {
		// TODO Auto-generated constructor stub
	}
	
	public static int numberOfValidTestCases(Collection<TestCase> testCases, OpenAPISpecification spec) {
		int validTests = 0;
		
		for(TestCase tc:testCases)
			if (tc.isValid(OASAPIValidator.getValidator(spec)))
				validTests++;
		
		return validTests;
	}
	
	public static int numberOfInvalidTestCases(Collection<TestCase> testCases, OpenAPISpecification spec) {
		int invalidTests = 0;
		
		for(TestCase tc:testCases)
			if (!tc.isValid(OASAPIValidator.getValidator(spec)))
				invalidTests++;
		
		return invalidTests;
	}

}

package es.us.isa.restest.testcases.writers;

import java.util.Collection;

import es.us.isa.restest.testcases.TestCase;

/**
 * This interface defines a test writer. The classes that implement this interface should create domain-specific test cases ready to be executed (ex. RESTAssured test cases)
 */
public interface IWriter {

	/**
	 * From a collection of domain-independent test cases, the method writes domain-specific ready-to-run test cases using frameworks like RESTAssured.
	 * @param testCases The collection of domain-independent test cases to be instantiated
	 */
	void write(Collection<TestCase> testCases);

}
package es.us.isa.restest.testcases.writers;

import java.util.Collection;

import es.us.isa.restest.testcases.TestCase;

/**
 * This interface defines a test writer. The classes that implement this interface are able to create a Java class with
 * domain-specific test cases ready to be executed.
 */
public interface IWriter {

	/**
	 * From a collection of domain-independent test cases, the method writes a Java class that instantiates those test
	 * cases in a framework.
	 * @param testCases The collection of domain-independent test cases to be instantiated
	 */
	void write(Collection<TestCase> testCases);

}
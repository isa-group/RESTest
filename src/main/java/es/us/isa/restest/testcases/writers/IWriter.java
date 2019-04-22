package es.us.isa.restest.testcases.writers;

import java.util.Collection;

import es.us.isa.restest.testcases.TestCase;

public interface IWriter {

	void write(Collection<TestCase> testCases);

}
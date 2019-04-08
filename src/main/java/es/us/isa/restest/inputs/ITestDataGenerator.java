package es.us.isa.restest.inputs;

public interface ITestDataGenerator {
	Object nextValue();
	String nextValueAsString();
}

package es.us.isa.restest.inputs.fixed;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import es.us.isa.restest.inputs.fixed.InputValueIterator;

public class InputValueIteratorTest {

	static List<String> stringValues;
	static List<Integer> intValues;
	Generator generator;

	@BeforeClass
	public static void setUp() {
		stringValues = new ArrayList<>();
		stringValues.add("short");
		stringValues.add("medium");
		stringValues.add("large");
		stringValues.add("enormous");
		
		intValues = new ArrayList<>();
		intValues.add(10);
		intValues.add(20);
		intValues.add(30);
		intValues.add(40);
	}

	@Before
	public void setupGenerator() {
		generator = new Generator();
		generator.setType("InputValue");
		generator.setGenParameters(new ArrayList<>());
	}
	
	@Test
	public void testStringIterator() {
		GenParameter values = new GenParameter();
		values.setName("values");
		values.setValues(stringValues);

		generator.getGenParameters().add(values);

		InputValueIterator<String> iterator = (InputValueIterator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		assertEquals("Wrong value", "short", iterator.nextValue());
		assertEquals("Wrong value", "medium",iterator.nextValue());
		assertEquals("Wrong value", "large", iterator.nextValue());
		assertEquals("Wrong value", "enormous", iterator.nextValue());
		assertEquals("Wrong value", "short", iterator.nextValue());
	}
	
	@Test
	public void testIntIterator() {
		InputValueIterator<Integer> iterator = new InputValueIterator<Integer>(intValues);
		assertEquals("Wrong value", 10, iterator.nextValue());
		assertEquals("Wrong value", 20, iterator.nextValue());
		assertEquals("Wrong value", 30, iterator.nextValue());
		assertEquals("Wrong value", 40, iterator.nextValue());
		assertEquals("Wrong value", 10, iterator.nextValue());
	}
	
	@Test
	public void testIteratorReset() {
		GenParameter values = new GenParameter();
		values.setName("values");
		values.setValues(stringValues);

		generator.getGenParameters().add(values);

		InputValueIterator<String> iterator = (InputValueIterator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		assertEquals("Wrong value", "short", iterator.nextValue());
		assertEquals("Wrong value", "medium",iterator.nextValue());
		iterator.resetIterator();
		assertEquals("Wrong value", "short", iterator.nextValue());
		assertEquals("Wrong value", "medium",iterator.nextValue());
		assertEquals("Wrong value", "large", iterator.nextValue());
		assertEquals("Wrong value", "enormous", iterator.nextValue());
		assertEquals("Wrong value", "short", iterator.nextValue());
	}
}

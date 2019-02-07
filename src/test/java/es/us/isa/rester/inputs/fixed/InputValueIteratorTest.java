package es.us.isa.rester.inputs.fixed;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

import es.us.isa.rester.inputs.fixed.InputValueIterator;

public class InputValueIteratorTest {

	static List<String> stringValues;
	static List<Integer> intValues;
	
	@BeforeClass
	public static void setUp() {
		stringValues = new ArrayList<String>();
		stringValues.add("short");
		stringValues.add("medium");
		stringValues.add("large");
		stringValues.add("enormous");
		
		intValues = new ArrayList<Integer>();
		intValues.add(10);
		intValues.add(20);
		intValues.add(30);
		intValues.add(40);
	}
	
	@Test
	public void testStringIterator() {
		InputValueIterator<String> iterator = new InputValueIterator<String>(stringValues);
		assertEquals("Wrong value", "short", iterator.nextValue());
		assertEquals("Wrong value", "medium",iterator.nextValue());
		assertEquals("Wrong value", "large", iterator.nextValue());
		assertEquals("Wrong value", "enormous", iterator.nextValue());
		assertEquals("Wrong value", "short", iterator.nextValue());	}
	
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
		InputValueIterator<String> iterator = new InputValueIterator<String>(stringValues);
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

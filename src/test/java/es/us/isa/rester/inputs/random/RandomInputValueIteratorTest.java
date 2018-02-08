package es.us.isa.rester.inputs.random;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

import es.us.isa.rester.inputs.random.RandomInputValueIterator;

public class RandomInputValueIteratorTest {

	static List<String> stringValues;
	
	@BeforeClass
	public static void setUp() {
		stringValues = new ArrayList<String>();
		stringValues.add("short");
		stringValues.add("medium");
		stringValues.add("large");
		stringValues.add("enormous");
	}
	
	@Test
	public void testRandomStringIterator() {
		RandomInputValueIterator<String> iterator = new RandomInputValueIterator<String>(stringValues);
		Set<String> stringSet = new HashSet<String>();
		while (stringSet.size()!=stringValues.size()) {
			String value = (String) iterator.nextValue();
			stringSet.add(value);
			System.out.println("Value: " + value);
		}	
	}
	
	@Test
	public void testSeed() {
		List<String> values1 = new ArrayList<String>();
		List<String> values2 = new ArrayList<String>();
		
		RandomInputValueIterator<String> iterator = new RandomInputValueIterator<String>(stringValues);
		for (int i = 0; i < 100; i++)
			values1.add((String)iterator.nextValue());
		
		RandomInputValueIterator<String> iterator2 = new RandomInputValueIterator<String>(stringValues);
		iterator2.setSeed(iterator.getSeed());
		for (int i = 0; i < 100; i++)
			values2.add((String)iterator2.nextValue());
		
		assertEquals("Both lists are not equal!", values1, values2);
	}

}

package es.us.isa.rester.inputs.random;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import es.us.isa.rester.inputs.random.RandomRegExpGenerator;

public class RandomRegExpGeneratorTest {

	@Test
	public void testRandomRegExpGeneration() {
		String regExp = "[0-3]([a-c]|[e-g]{1,5})";
		RandomRegExpGenerator gen = new RandomRegExpGenerator(regExp);
		for(int i=0; i<100;i++) {
			String value = gen.nextValue();
			assertTrue("The string does not match the regular expression", value.matches(regExp));
			System.out.println("String: " + value);
		}
	}
	
	@Test
	public void testRandomBoundedRegExpGeneration() {
		String regExp = "[0-3]([a-c]|[e-g]{1,5})";
		RandomRegExpGenerator gen = new RandomRegExpGenerator(regExp);
		gen.setMinLength(2);
		gen.setMaxLength(4);
		for(int i=0; i<100;i++) {
			String value = gen.nextValue();
			assertTrue("The string does not match the regular expression", value.matches(regExp));
			assertTrue("The string does not match the requested lengthn", value.length() >= 2 && value.length() <= 4);
			System.out.println("Bounded string: " + value);
		}
	}
	
	@Test
	public void testSeed() {
		List<String> values1 = new ArrayList<String>();
		List<String> values2 = new ArrayList<String>();
		
		String regExp = "[0-3]([a-c]|[e-g]{1,5})";
		RandomRegExpGenerator gen = new RandomRegExpGenerator(regExp);
		for (int i = 0; i < 100; i++) 
			values1.add(gen.nextValue());
		
		
		RandomRegExpGenerator gen2 = new RandomRegExpGenerator(regExp);
		gen2.setSeed(gen.getSeed());
		for (int i = 0; i < 100; i++) {
			values2.add(gen2.nextValue());
		}
		
		assertEquals("Both lists are not equal!", values1, values2);
	}
}

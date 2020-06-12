package es.us.isa.restest.inputs.random;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import org.junit.Before;
import org.junit.Test;

public class RandomRegExpGeneratorTest {

	Generator generator;

	@Before
	public void setup() {
		generator = new Generator();
		generator.setType("RandomRegExp");
		generator.setGenParameters(new ArrayList<>());
	}

	@Test
	public void testRandomRegExpGeneration() {
		String regExp = "[0-3]([a-c]|[e-g]{1,5})";

		GenParameter regExpGen = new GenParameter();
		regExpGen.setName("regExp");
		regExpGen.setValues(Collections.singletonList(regExp));

		generator.getGenParameters().add(regExpGen);

		RandomRegExpGenerator gen = (RandomRegExpGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		for(int i=0; i<100;i++) {
			String value = gen.nextValue();
			assertTrue("The string does not match the regular expression", value.matches(regExp));
			//System.out.println("String: " + value);
		}
	}
	
	@Test
	public void testRandomBoundedRegExpGeneration() {
		String regExp = "[0-3]([a-c]|[e-g]{1,5})";

		GenParameter regExpGen = new GenParameter();
		regExpGen.setName("regExp");
		regExpGen.setValues(Collections.singletonList(regExp));

		GenParameter minLength = new GenParameter();
		minLength.setName("minLength");
		minLength.setValues(Collections.singletonList("3"));

		GenParameter maxLength = new GenParameter();
		maxLength.setName("maxLength");
		maxLength.setValues(Collections.singletonList("4"));

		generator.getGenParameters().addAll(Arrays.asList(regExpGen, minLength, maxLength));

		RandomRegExpGenerator gen = (RandomRegExpGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		for(int i=0; i<100;i++) {
			String value = gen.nextValue();
			assertTrue("The string does not match the regular expression", value.matches(regExp));
			assertTrue("The string does not match the requested length", value.length() >= 3 && value.length() <= 4);
			assertFalse("The string has a forbidden pattern", value.substring(1, 2).matches("[a-c]"));
			//System.out.println("Bounded string: " + value);
		}
	}
	
	@Test
	public void testSeed() {
		List<String> values1 = new ArrayList<>();
		List<String> values2 = new ArrayList<>();
		
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

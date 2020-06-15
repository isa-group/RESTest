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

import es.us.isa.restest.util.DataType;

public class RandomNumberGeneratorTest {

	Generator generator;

	@Before
	public void setupGenerator() {
		generator = new Generator();
		generator.setType("RandomNumber");
		generator.setGenParameters(new ArrayList<>());
	}

	@Test
	public void testRandomIntGeneration() {
		GenParameter type = new GenParameter();
		type.setName("type");
		type.setValues(Collections.singletonList("int32"));

		generator.getGenParameters().add(type);

		RandomNumberGenerator gen = (RandomNumberGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		for(int i=0;i<100;i++) {
			Object number = gen.nextValue();
			assertTrue("Not an Integer", number instanceof Integer);
			//System.out.println("Unbounded Integer: " + (Integer) number);
		}
	}

	@Test
	public void testRandomInt64Generation() {
		GenParameter type = new GenParameter();
		type.setName("type");
		type.setValues(Collections.singletonList("int64"));

		generator.getGenParameters().add(type);

		RandomNumberGenerator gen = (RandomNumberGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		for(int i=0;i<100;i++) {
			Object number = gen.nextValue();
			assertTrue("Not an Integer", number instanceof Integer);
			//System.out.println("Unbounded Integer: " + (Integer) number);
		}
	}
	
	@Test
	public void testRandomBoundedIntGeneration() {
		Integer min = 10;
		Integer max = 40;

		GenParameter type = new GenParameter();
		type.setName("type");
		type.setValues(Collections.singletonList("integer"));

		GenParameter minGen = new GenParameter();
		minGen.setName("min");
		minGen.setValues(Collections.singletonList(min.toString()));

		GenParameter maxGen = new GenParameter();
		maxGen.setName("max");
		maxGen.setValues(Collections.singletonList(max.toString()));

		generator.getGenParameters().addAll(Arrays.asList(type, minGen, maxGen));

		RandomNumberGenerator gen = (RandomNumberGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		for(int i=0;i<100;i++) {
			Object number = gen.nextValue();
			assertTrue("Not an Integer", number instanceof Integer);
			assertTrue("Out of range", (Integer)number >= min && (Integer)number <= max);
			//System.out.println("Bounded Integer: " + (Integer) number);
		}
	}
	
	@Test
	public void testRandomLongGeneration() {
		GenParameter type = new GenParameter();
		type.setName("type");
		type.setValues(Collections.singletonList("long"));

		generator.getGenParameters().add(type);

		RandomNumberGenerator gen = (RandomNumberGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		for(int i=0;i<100;i++) {
			Object number = gen.nextValue();
			assertTrue("Not a Long", number instanceof Long);
			//System.out.println("Unbounded Long: " + (Long) number);
		}
	}
	
	@Test
	public void testRandomBoundedLongGeneration() {
		Long min = 1000L;
		Long max = 1000000L;

		GenParameter type = new GenParameter();
		type.setName("type");
		type.setValues(Collections.singletonList("long"));

		GenParameter minGen = new GenParameter();
		minGen.setName("min");
		minGen.setValues(Collections.singletonList(min.toString()));

		GenParameter maxGen = new GenParameter();
		maxGen.setName("max");
		maxGen.setValues(Collections.singletonList(max.toString()));

		generator.getGenParameters().addAll(Arrays.asList(type, minGen, maxGen));

		RandomNumberGenerator gen = (RandomNumberGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		for(int i=0;i<100;i++) {
			Object number = gen.nextValue();
			assertTrue("Not a Long", number instanceof Long);
			assertTrue("Out of range", (Long) number >= min && (Long) number <= max);
			//System.out.println("Bounded Long: " + (Long) number);
		}
	}
	
	@Test
	public void testRandomFloatGeneration() {
		GenParameter type = new GenParameter();
		type.setName("type");
		type.setValues(Collections.singletonList("float"));

		generator.getGenParameters().add(type);

		RandomNumberGenerator gen = (RandomNumberGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		for(int i=0;i<100;i++) {
			Object number = gen.nextValue();
			assertTrue("Not a Float", number instanceof Float);
			//System.out.println("Unbounded Float: " + (Float) number);
		}
	}
	
	@Test
	public void testRandomBoundedFloatGeneration() {
		Float min = 0.2f;
		Float max = 0.6f;

		GenParameter type = new GenParameter();
		type.setName("type");
		type.setValues(Collections.singletonList("float"));

		GenParameter minGen = new GenParameter();
		minGen.setName("min");
		minGen.setValues(Collections.singletonList(min.toString()));

		GenParameter maxGen = new GenParameter();
		maxGen.setName("max");
		maxGen.setValues(Collections.singletonList(max.toString()));

		generator.getGenParameters().addAll(Arrays.asList(type, minGen, maxGen));

		RandomNumberGenerator gen = (RandomNumberGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		for(int i=0;i<100;i++) {
			Object number = gen.nextValue();
			assertTrue("Not a Float", number instanceof Float);
			assertTrue("Out of range", (Float) number >= min && (Float) number <= max);
		}
	}
	
	@Test
	public void testRandomDoubleGeneration() {
		GenParameter type = new GenParameter();
		type.setName("type");
		type.setValues(Collections.singletonList("double"));

		generator.getGenParameters().add(type);

		RandomNumberGenerator gen = (RandomNumberGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		for(int i=0;i<100;i++) {
			Object number = gen.nextValue();
			assertTrue("Not a Double", number instanceof Double);
		}
	}
	
	@Test
	public void testRandomBoundedDoubleGeneration() {
		Double min = 0.2d;
		Double max = 0.8d;

		GenParameter type = new GenParameter();
		type.setName("type");
		type.setValues(Collections.singletonList("number"));

		GenParameter minGen = new GenParameter();
		minGen.setName("min");
		minGen.setValues(Collections.singletonList(min.toString()));

		GenParameter maxGen = new GenParameter();
		maxGen.setName("max");
		maxGen.setValues(Collections.singletonList(max.toString()));

		generator.getGenParameters().addAll(Arrays.asList(type, minGen, maxGen));

		RandomNumberGenerator gen = (RandomNumberGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		for(int i=0;i<100;i++) {
			Object number = gen.nextValue();
			assertTrue("Not a Double", number instanceof Double);
			assertTrue("Out of range", (Double) number >= min && (Double) number <= max);
		}
	}
	
	@Test
	public void testSeed() {
		List<Integer> values1 = new ArrayList<>();
		List<Integer> values2 = new ArrayList<>();
		
		RandomNumberGenerator gen = new RandomNumberGenerator(DataType.INT64);
		for (int i = 0; i < 100; i++) 
			values1.add((Integer) gen.nextValue());
		
		
		RandomNumberGenerator gen2 = new RandomNumberGenerator(DataType.INT64);
		gen2.setSeed(gen.getSeed());
		for (int i = 0; i < 100; i++) {
			values2.add((Integer) gen2.nextValue());
		}
		
		assertEquals("Both lists are not equal!", values1, values2);
	}
	
}

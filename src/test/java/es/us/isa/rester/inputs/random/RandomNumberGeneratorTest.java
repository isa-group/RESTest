package es.us.isa.rester.inputs.random;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import es.us.isa.rester.inputs.random.RandomNumberGenerator;
import es.us.isa.rester.util.DataType;

public class RandomNumberGeneratorTest {

	@Test
	public void testRandomIntGeneration() {
		RandomNumberGenerator gen = new RandomNumberGenerator(DataType.INT64);
		for(int i=0;i<100;i++) {
			Object number = gen.nextValue();
			assertTrue("Not an Integer", number instanceof Integer);
			System.out.println("Unbounded Integer: " + (Integer) number);
		}
	}
	
	@Test
	public void testRandomBoundedIntGeneration() {
		RandomNumberGenerator gen = new RandomNumberGenerator(DataType.INTEGER);
		int min = 10;
		int max = 40;
		gen.setMin(min);
		gen.setMax(max);
		for(int i=0;i<100;i++) {
			Object number = gen.nextValue();
			assertTrue("Not an Integer", number instanceof Integer);
			assertTrue("Out of range", (Integer)number >= min && (Integer)number <= max);
			System.out.println("Bounded Integer: " + (Integer) number);
		}
	}
	
	@Test
	public void testRandomLongGeneration() {
		RandomNumberGenerator gen = new RandomNumberGenerator(DataType.LONG);
		for(int i=0;i<100;i++) {
			Object number = gen.nextValue();
			assertTrue("Not a Long", number instanceof Long);
			System.out.println("Unbounded Long: " + (Long) number);
		}
	}
	
	@Test
	public void testRandomBoundedLongGeneration() {
		RandomNumberGenerator gen = new RandomNumberGenerator(DataType.LONG);
		long min = 1000;
		long max = 1000000;
		gen.setMin(min);
		gen.setMax(max);
		for(int i=0;i<100;i++) {
			Object number = gen.nextValue();
			assertTrue("Not a Long", number instanceof Long);
			assertTrue("Out of range", (Long) number >= min && (Long) number <= max);
			System.out.println("Bounded Long: " + (Long) number);
		}
	}
	
	@Test
	public void testRandomFloatGeneration() {
		RandomNumberGenerator gen = new RandomNumberGenerator(DataType.FLOAT);
		for(int i=0;i<100;i++) {
			Object number = gen.nextValue();
			assertTrue("Not a Float", number instanceof Float);
			System.out.println("Unbounded Float: " + (Float) number);
		}
	}
	
	@Test
	public void testRandomBoundedFloatGeneration() {
		RandomNumberGenerator gen = new RandomNumberGenerator(DataType.FLOAT);
		float min = (float) 0.2;
		float max = (float) 0.6;
		gen.setMin(min);
		gen.setMax(max);
		for(int i=0;i<100;i++) {
			Object number = gen.nextValue();
			assertTrue("Not a Float", number instanceof Float);
			assertTrue("Out of range", (Float) number >= min && (Float) number <= max);
			System.out.println("Bounded Float: " + (Float) number);
		}
	}
	
	@Test
	public void testRandomDoubleGeneration() {
		RandomNumberGenerator gen = new RandomNumberGenerator(DataType.DOUBLE);
		for(int i=0;i<100;i++) {
			Object number = gen.nextValue();
			assertTrue("Not a Double", number instanceof Double);
			System.out.println("Unbounded Float: " + (Double) number);
		}
	}
	
	@Test
	public void testRandomBoundedDoubleGeneration() {
		RandomNumberGenerator gen = new RandomNumberGenerator(DataType.NUMBER);
		double min = 0.2;
		double max = 0.8;
		gen.setMin(min);
		gen.setMax(max);
		for(int i=0;i<100;i++) {
			Object number = gen.nextValue();
			assertTrue("Not a Double", number instanceof Double);
			assertTrue("Out of range", (Double) number >= min && (Double) number <= max);
			System.out.println("Bounded Double: " + (Double) number);
		}
	}
	
	@Test
	public void testSeed() {
		List<Integer> values1 = new ArrayList<Integer>();
		List<Integer> values2 = new ArrayList<Integer>();
		
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

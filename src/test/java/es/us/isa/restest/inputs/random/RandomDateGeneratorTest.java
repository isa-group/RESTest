package es.us.isa.restest.inputs.random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

public class RandomDateGeneratorTest {

	Generator generator;

	@Before
	public void setupGenerator() {
		generator = new Generator();
		generator.setType("RandomDate");
		generator.setGenParameters(new ArrayList<>());
	}

	@Test
	public void testRandomDateGeneration() {
		RandomDateGenerator gen = (RandomDateGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
		for(int i=0; i<100;i++) {
			Date value = gen.nextValue();
			assertTrue("Not a date", value instanceof Date);
			SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			String date = sdfDate.format(value).replaceAll(" ", "T") + "Z";
			System.out.println("Unbounded date: " +  date);
		}
	}
	
	@Test
	public void testRandomDateGenerationFromToday() {
		GenParameter fromToday = new GenParameter();
		fromToday.setName("fromToday");
		fromToday.setValues(Collections.singletonList("true"));

		generator.getGenParameters().add(fromToday);

		RandomDateGenerator gen = (RandomDateGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		for(int i=0; i<100;i++) {
			Date value = gen.nextValue();
			assertTrue("Not a date", value instanceof Date);
			assertTrue("Out of range", value.after(new Date()));
			SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			String date = sdfDate.format(value).replaceAll(" ", "T") + "Z";
			System.out.println("Unbounded date (from today): " +  date);
		}
	}
	
	@Test
	public void testRandomBoundedDateGeneration() throws ParseException {
		GenParameter startDate = new GenParameter();
		startDate.setName("startDate");
		startDate.setValues(Collections.singletonList("2015-06-12"));

		GenParameter endDate = new GenParameter();
		endDate.setName("endDate");
		endDate.setValues(Collections.singletonList("2017-08-15"));

		generator.getGenParameters().addAll(Arrays.asList(startDate, endDate));

		RandomDateGenerator gen = (RandomDateGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		for(int i=0; i<100;i++) {
			Date value = gen.nextValue();
			assertTrue("Not a date", value instanceof Date);
			assertTrue("Out of range", value.before(gen.getEndDate()));
			assertTrue("Out of range", value.after(gen.getStartDate()));
			SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			String date = sdfDate.format(value).replaceAll(" ", "T") + "Z";
			System.out.println("Bounded date: " +  date);
		}
	}
	
	@Test
	public void testRandomDaysBasedBoundedDateGeneration() throws ParseException {
		int startDays = 30;
		int endDays = 90;
		GenParameter startDaysGen = new GenParameter();
		startDaysGen.setName("startDays");
		startDaysGen.setValues(Collections.singletonList("30"));

		GenParameter endDaysGen = new GenParameter();
		endDaysGen.setName("endDays");
		endDaysGen.setValues(Collections.singletonList("90"));

		generator.getGenParameters().addAll(Arrays.asList(startDaysGen, endDaysGen));

		RandomDateGenerator gen = (RandomDateGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
		for(int i=0; i<100;i++) {
			Date value = gen.nextValue();
			assertTrue("Not a date", value instanceof Date);
			assertTrue("Out of range", value.after(new DateTime(new Date()).plusDays(startDays).toDate()));
			assertTrue("Out of range", value.before(new DateTime(new Date()).plusDays(endDays).toDate()));
			SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			String date = sdfDate.format(value).replaceAll(" ", "T") + "Z";
			System.out.println("Bounded date: " +  date);
		}
	}
	
	@Test
	public void testRandomDateAsStringGeneration() throws ParseException {
		for(int i=0; i<100;i++) {
			RandomDateGenerator gen = (RandomDateGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
			
			// Generate object
			gen.setSeed(i+1000);
			Date value = gen.nextValue();

			// Generate string
			gen.setSeed(i+1000);
			String date = gen.nextValueAsString();
			
			// Check they are equal
			SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			String objectDate = sdfDate.format(value);
			assertEquals("Incorrect string value", date, objectDate);
			
		}
	}
	
	@Test
	public void testCustomDateFormat() throws ParseException {
		GenParameter format = new GenParameter();
		format.setName("format");
		format.setValues(Collections.singletonList("yyyy-MM-dd"));

		generator.getGenParameters().addAll(Collections.singletonList(format));

		for(int i=0; i<100;i++) {
			RandomDateGenerator gen = (RandomDateGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
			
			// Generate object
			gen.setSeed(i+1000);
			Date value = gen.nextValue();

			// Generate string
			gen.setSeed(i+1000);
			String date = gen.nextValueAsString();
			
			// Check they are equal
			SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");
			String objectDate = sdfDate.format(value);
			assertEquals("Incorrect string value", date, objectDate);
			//System.out.println("Date (yyyy-MM-dd): " + date);
			
		}
	}
	
	@Test
	public void testSeed() {
		List<String> values1 = new ArrayList<String>();
		List<String> values2 = new ArrayList<String>();
		
		RandomDateGenerator gen = new RandomDateGenerator();
		for (int i = 0; i < 100; i++) {
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
			values1.add(format.format(gen.nextValue()));
		}
		
		RandomDateGenerator gen2 = new RandomDateGenerator();
		gen2.setSeed(gen.getSeed());
		for (int i = 0; i < 100; i++) {
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
			values2.add(format.format(gen2.nextValue()));
		}
		
		assertEquals("Both lists are not equal!", values1, values2);
	}
	
}

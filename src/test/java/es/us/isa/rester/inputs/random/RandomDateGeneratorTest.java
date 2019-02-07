package es.us.isa.rester.inputs.random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.joda.time.DateTime;
import org.junit.Test;

import es.us.isa.rester.inputs.random.RandomDateGenerator;

public class RandomDateGeneratorTest {

	@Test
	public void testRandomDateGeneration() {
		RandomDateGenerator gen = new RandomDateGenerator();
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
		RandomDateGenerator gen = new RandomDateGenerator();
		gen.setFromToday(true);
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
		RandomDateGenerator gen = new RandomDateGenerator();
		gen.setStartDate("2015-06-12");
		gen.setEndDate("2017-08-15");
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
		RandomDateGenerator gen = new RandomDateGenerator();
		gen.setStartDays(startDays);
		gen.setEndDays(endDays);
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
			RandomDateGenerator gen = new RandomDateGenerator();
			
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
		for(int i=0; i<100;i++) {
			RandomDateGenerator gen = new RandomDateGenerator();
			gen.setFormat("yyyy-MM-dd");
			
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

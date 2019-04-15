package es.us.isa.restest.util;

import static org.junit.Assert.*;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

public class IDGeneratorTest {


	@Test
	public void shortIdTest() {
		Random rand = new Random();
		long seed = 28;
		
		rand.setSeed(seed);
		String id1= IDGenerator.generateId(rand);
		
		rand.setSeed(seed);
		String id2= IDGenerator.generateId(rand);
		
		System.out.println("ID1: " + id1);
		System.out.println("ID2: " + id2);
		assertTrue("The ids are not equal", id1.equals(id2));
	}

}

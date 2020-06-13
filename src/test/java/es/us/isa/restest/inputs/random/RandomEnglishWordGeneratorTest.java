package es.us.isa.restest.inputs.random;

import static org.junit.Assert.*;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class RandomEnglishWordGeneratorTest {

	Generator generator;

	@Before
	public void setup() {
		generator = new Generator();
		generator.setType("RandomEnglishWord");
		generator.setGenParameters(new ArrayList<>());
	}

	@Test
	public void testRandomStringGeneration() {
		RandomEnglishWordGenerator gen = (RandomEnglishWordGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
		for (int i=0;i<100;i++) {
			String value = gen.nextValue();
			int nWords = numberOfWords(value);
			assertTrue("Incorrect number of words", nWords>=1 && nWords<=3);
			//System.out.println(i + ". Generate words: " + value + " (" + nWords + ")");
		}	
	}
	
	@Test
	public void testRandomOneWordStringGeneration() {
		GenParameter minWords = new GenParameter();
		minWords.setName("minWords");
		minWords.setValues(Collections.singletonList("1"));

		GenParameter maxWords = new GenParameter();
		maxWords.setName("maxWords");
		maxWords.setValues(Collections.singletonList("1"));

		generator.getGenParameters().addAll(Arrays.asList(minWords, maxWords));

		RandomEnglishWordGenerator gen = (RandomEnglishWordGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		for (int i=0;i<100;i++) {
			String value = gen.nextValue();
			int nWords = numberOfWords(value);
			assertEquals("Incorrect number of words", 1, nWords);
			//System.out.println(i + ". Generate words: " + value + " (" + nWords + ")");
		}	
	}
	
	@Test
	public void testRandomBoundedStringGeneration() {
		GenParameter minWords = new GenParameter();
		minWords.setName("minWords");
		minWords.setValues(Collections.singletonList("2"));

		GenParameter maxWords = new GenParameter();
		maxWords.setName("maxWords");
		maxWords.setValues(Collections.singletonList("6"));

		generator.getGenParameters().addAll(Arrays.asList(minWords, maxWords));

		RandomEnglishWordGenerator gen = (RandomEnglishWordGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		for (int i=0;i<100;i++) {
			String value = gen.nextValue();
			int nWords = numberOfWords(value);
			assertTrue("Incorrect number of words", nWords>=2 && nWords<=6);
			//System.out.println(i + ". Generate words: " + value + " (" + nWords + ")");
		}	
	}
	
	@Test
	public void testRandomBoundedStringNoCompoundGeneration() {
		GenParameter minWords = new GenParameter();
		minWords.setName("minWords");
		minWords.setValues(Collections.singletonList("2"));

		GenParameter maxWords = new GenParameter();
		maxWords.setName("maxWords");
		maxWords.setValues(Collections.singletonList("6"));

		generator.getGenParameters().addAll(Arrays.asList(minWords, maxWords));

		RandomEnglishWordGenerator gen = (RandomEnglishWordGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
		gen.setGenerateCompounds(false);

		for (int i=0;i<100;i++) {
			String value = gen.nextValue();
			int nWords = numberOfWords(value);
			assertTrue("Incorrect number of words", nWords>=2 && nWords<=6);
			//System.out.println(i + ". Generate words: " + value + " (" + nWords + ")");
		}	
	}
	
	private int numberOfWords(String sentence) {
		String trimmed = sentence.trim();
		return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
	}

}

package es.us.isa.rester.inputs.random;

import static org.junit.Assert.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import es.us.isa.rester.inputs.random.RandomEnglishWordGenerator;

public class RandomEnglishWordGeneratorTest {

	@Test
	public void testRandomStringGeneration() {
		RandomEnglishWordGenerator gen = new RandomEnglishWordGenerator();
		for (int i=0;i<100;i++) {
			String value = gen.nextValue();
			int nWords = numberOfWords(value);
			assertTrue("Incorrect number of words", nWords>=1 && nWords<=3);
			//System.out.println(i + ". Generate words: " + value + " (" + nWords + ")");
		}	
	}
	
	@Test
	public void testRandomOneWordStringGeneration() {
		RandomEnglishWordGenerator gen = new RandomEnglishWordGenerator();
		gen.setMinWords(1);
		gen.setMaxWords(1);
		for (int i=0;i<100;i++) {
			String value = gen.nextValue();
			int nWords = numberOfWords(value);
			assertTrue("Incorrect number of words", nWords==1);
			//System.out.println(i + ". Generate words: " + value + " (" + nWords + ")");
		}	
	}
	
	@Test
	public void testRandomBoundedStringGeneration() {
		RandomEnglishWordGenerator gen = new RandomEnglishWordGenerator();
		gen.setMinWords(2);
		gen.setMaxWords(6);
		for (int i=0;i<100;i++) {
			String value = gen.nextValue();
			int nWords = numberOfWords(value);
			assertTrue("Incorrect number of words", nWords>=2 && nWords<=6);
			//System.out.println(i + ". Generate words: " + value + " (" + nWords + ")");
		}	
	}
	
	@Test
	public void testRandomBoundedStringNoCompoundGeneration() {
		RandomEnglishWordGenerator gen = new RandomEnglishWordGenerator();
		gen.setMinWords(2);
		gen.setMaxWords(6);
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
		int words = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
		return words;
	}

}

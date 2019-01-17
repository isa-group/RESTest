package es.us.isa.rester.inputs.random;

import static org.junit.Assert.*;
import org.junit.Test;


public class RandomStringGeneratorTest {

    @Test
    public void testDefaultConstructor() {
        RandomStringGenerator strGen = new RandomStringGenerator();
        assertEquals("maxLength parameter should be 10 by default", 10, strGen.getMaxLength());
        assertEquals("minLength parameter should be 0 by default", 0, strGen.getMinLength());
        assertEquals("includeAlphabetic parameter should be true by default", true, strGen.getIncludeAlphabetic());
        assertEquals("includeNumbers parameter should be false by default", false, strGen.getIncludeNumbers());
        assertEquals("includeSpecialCharacters parameter should be false by default", false, strGen.getIncludeSpecialCharacters());
    }

    @Test
    public void testSetters() {
        RandomStringGenerator strGen = new RandomStringGenerator();
        strGen.setIncludeAlphabetic(false);
        strGen.setIncludeNumbers(true);
        strGen.setIncludeSpecialCharacters(true);
        strGen.setMaxLength(15);
        strGen.setMinLength(3);
        assertEquals("maxLength parameter should be 15", 15, strGen.getMaxLength());
        assertEquals("minLength parameter should be 3", 3, strGen.getMinLength());
        assertEquals("includeAlphabetic parameter should be false", false, strGen.getIncludeAlphabetic());
        assertEquals("includeNumbers parameter should be true", true, strGen.getIncludeNumbers());
        assertEquals("includeSpecialCharacters parameter should be true", true, strGen.getIncludeSpecialCharacters());
    }

    @Test
    public void testEmptyString() {
        RandomStringGenerator strGen = new RandomStringGenerator(25, 30, false, false, false);
        String testString = strGen.nextValue();
        System.out.println(testString);
        assertTrue("The generated string should not contain any character", !testString.matches(".*[0-9A-Za-z].*"));
    }

    @Test
    public void testStringWithoutNumbers() {
        RandomStringGenerator strGen = new RandomStringGenerator(25, 30, true, false, true);
        String testString = strGen.nextValue();
        System.out.println(testString);
        assertTrue("The generated string should not contain any number", !testString.matches(".*[0-9].*"));
    }

    @Test
    public void testStringWithoutAlphabet() {
        RandomStringGenerator strGen = new RandomStringGenerator(25, 30, false, true, true);
        String testString = strGen.nextValue();
        System.out.println(testString);
        assertTrue("The generated string should not contain any alphabetic character", !testString.matches(".*[A-Za-z].*"));
    }

    @Test
    public void testStringWithoutNumbersOrAlphabet() {
        RandomStringGenerator strGen = new RandomStringGenerator(25, 30, false, false, true);
        String testString = strGen.nextValue();
        System.out.println(testString);
        assertTrue("The generated string should not contain any alphanumeric character", !testString.matches(".*[0-9A-Za-z].*"));
    }
}

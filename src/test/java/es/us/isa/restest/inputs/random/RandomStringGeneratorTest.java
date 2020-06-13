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


public class RandomStringGeneratorTest {

    Generator generator;

    @Before
    public void setup() {
        generator = new Generator();
        generator.setType("RandomString");
        generator.setGenParameters(new ArrayList<>());
    }

    @Test
    public void testDefaultConstructor() {
        RandomStringGenerator strGen = (RandomStringGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

        assertEquals("maxLength parameter should be 10 by default", 10, strGen.getMaxLength());
        assertEquals("minLength parameter should be 0 by default", 0, strGen.getMinLength());
        assertTrue("includeAlphabetic parameter should be true by default", strGen.getIncludeAlphabetic());
        assertFalse("includeNumbers parameter should be false by default", strGen.getIncludeNumbers());
        assertFalse("includeSpecialCharacters parameter should be false by default", strGen.getIncludeSpecialCharacters());
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
        assertFalse("includeAlphabetic parameter should be false", strGen.getIncludeAlphabetic());
        assertTrue("includeNumbers parameter should be true", strGen.getIncludeNumbers());
        assertTrue("includeSpecialCharacters parameter should be true", strGen.getIncludeSpecialCharacters());
    }

    @Test
    public void testEmptyString() {
        GenParameter minLength = new GenParameter();
        minLength.setName("minLength");
        minLength.setValues(Collections.singletonList("25"));

        GenParameter maxLength = new GenParameter();
        maxLength.setName("maxLength");
        maxLength.setValues(Collections.singletonList("30"));

        GenParameter includeSpecialCharacters = new GenParameter();
        includeSpecialCharacters.setName("includeSpecialCharacters");
        includeSpecialCharacters.setValues(Collections.singletonList("false"));

        GenParameter includeNumbers = new GenParameter();
        includeNumbers.setName("includeNumbers");
        includeNumbers.setValues(Collections.singletonList("false"));

        GenParameter includeAlphabetic = new GenParameter();
        includeAlphabetic.setName("includeAlphabetic");
        includeAlphabetic.setValues(Collections.singletonList("false"));

        generator.getGenParameters().addAll(Arrays.asList(minLength, maxLength, includeSpecialCharacters, includeNumbers, includeAlphabetic));

        RandomStringGenerator strGen = (RandomStringGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

        String testString = strGen.nextValue();
        System.out.println(testString);
        assertTrue("The generated string should not contain any character", testString.matches("^$"));
    }

    @Test
    public void testStringWithoutNumbers() {
        GenParameter minLength = new GenParameter();
        minLength.setName("minLength");
        minLength.setValues(Collections.singletonList("25"));

        GenParameter maxLength = new GenParameter();
        maxLength.setName("maxLength");
        maxLength.setValues(Collections.singletonList("30"));

        GenParameter includeSpecialCharacters = new GenParameter();
        includeSpecialCharacters.setName("includeSpecialCharacters");
        includeSpecialCharacters.setValues(Collections.singletonList("true"));

        GenParameter includeNumbers = new GenParameter();
        includeNumbers.setName("includeNumbers");
        includeNumbers.setValues(Collections.singletonList("false"));

        GenParameter includeAlphabetic = new GenParameter();
        includeAlphabetic.setName("includeAlphabetic");
        includeAlphabetic.setValues(Collections.singletonList("true"));

        generator.getGenParameters().addAll(Arrays.asList(minLength, maxLength, includeSpecialCharacters, includeNumbers, includeAlphabetic));

        RandomStringGenerator strGen = (RandomStringGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

        String testString = strGen.nextValue();
        System.out.println(testString);
        assertFalse("The generated string should not contain any number", testString.matches(".*[0-9].*"));
    }

    @Test
    public void testStringWithoutAlphabet() {
        GenParameter minLength = new GenParameter();
        minLength.setName("minLength");
        minLength.setValues(Collections.singletonList("25"));

        GenParameter maxLength = new GenParameter();
        maxLength.setName("maxLength");
        maxLength.setValues(Collections.singletonList("30"));

        GenParameter includeSpecialCharacters = new GenParameter();
        includeSpecialCharacters.setName("includeSpecialCharacters");
        includeSpecialCharacters.setValues(Collections.singletonList("true"));

        GenParameter includeNumbers = new GenParameter();
        includeNumbers.setName("includeNumbers");
        includeNumbers.setValues(Collections.singletonList("true"));

        GenParameter includeAlphabetic = new GenParameter();
        includeAlphabetic.setName("includeAlphabetic");
        includeAlphabetic.setValues(Collections.singletonList("false"));

        generator.getGenParameters().addAll(Arrays.asList(minLength, maxLength, includeSpecialCharacters, includeNumbers, includeAlphabetic));

        RandomStringGenerator strGen = (RandomStringGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

        String testString = strGen.nextValue();
        System.out.println(testString);
        assertFalse("The generated string should not contain any alphabetic character", testString.matches(".*[A-Za-z].*"));
    }

    @Test
    public void testStringWithoutNumbersOrAlphabet() {
        GenParameter minLength = new GenParameter();
        minLength.setName("minLength");
        minLength.setValues(Collections.singletonList("25"));

        GenParameter maxLength = new GenParameter();
        maxLength.setName("maxLength");
        maxLength.setValues(Collections.singletonList("30"));

        GenParameter includeSpecialCharacters = new GenParameter();
        includeSpecialCharacters.setName("includeSpecialCharacters");
        includeSpecialCharacters.setValues(Collections.singletonList("true"));

        GenParameter includeNumbers = new GenParameter();
        includeNumbers.setName("includeNumbers");
        includeNumbers.setValues(Collections.singletonList("false"));

        GenParameter includeAlphabetic = new GenParameter();
        includeAlphabetic.setName("includeAlphabetic");
        includeAlphabetic.setValues(Collections.singletonList("false"));

        generator.getGenParameters().addAll(Arrays.asList(minLength, maxLength, includeSpecialCharacters, includeNumbers, includeAlphabetic));

        RandomStringGenerator strGen = (RandomStringGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

        String testString = strGen.nextValue();
        System.out.println(testString);
        assertFalse("The generated string should not contain any alphanumeric character", testString.matches(".*[0-9A-Za-z].*"));
    }
}

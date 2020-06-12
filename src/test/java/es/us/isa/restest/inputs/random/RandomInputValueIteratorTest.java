package es.us.isa.restest.inputs.random;

import static org.junit.Assert.*;

import java.util.*;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class RandomInputValueIteratorTest {

	Generator generator;
	static List<String> stringValues;
	
	@BeforeClass
	public static void setUp() {
		stringValues = new ArrayList<>();
		stringValues.add("short");
		stringValues.add("medium");
		stringValues.add("large");
		stringValues.add("enormous");
	}

	@Before
	public void setupGenerator() {
		generator = new Generator();
		generator.setType("RandomInputValue");
		generator.setGenParameters(new ArrayList<>());
	}
	
	@Test
	public void testRandomStringIterator() {
		GenParameter values = new GenParameter();
		values.setName("values");
		values.setValues(stringValues);

		generator.getGenParameters().add(values);

		RandomInputValueIterator<String> iterator = (RandomInputValueIterator) TestDataGeneratorFactory.createTestDataGenerator(generator);
		Set<String> stringSet = new HashSet<>();
		while (stringSet.size()!=stringValues.size()) {
			String value = (String) iterator.nextValue();
			stringSet.add(value);
		}
		stringSet.forEach(x -> assertTrue("Invalid value: " + x, stringValues.contains(x)));
	}

	@Test
	public void testRandomStringIteratorWithCSVFile() {
		GenParameter values = new GenParameter();
		values.setName("csv");
		values.setValues(Collections.singletonList("src/main/resources/TestData/BingSupportedLanguages.csv"));

		generator.getGenParameters().add(values);

		RandomInputValueIterator<String> iterator = (RandomInputValueIterator) TestDataGeneratorFactory.createTestDataGenerator(generator);
		Set<String> stringSet = new HashSet<>();
		while (stringSet.size()!=stringValues.size()) {
			String value = (String) iterator.nextValue();
			stringSet.add(value);
		}
		stringSet.forEach(x -> assertTrue("Invalid value: " + x, iterator.getValues().contains(x)));
	}

	@Test
	public void testRandomStringIteratorMultipleValues() {
		GenParameter values = new GenParameter();
		values.setName("values");
		values.setValues(stringValues);

		GenParameter minValues = new GenParameter();
		minValues.setName("minValues");
		minValues.setValues(Collections.singletonList("1"));

		GenParameter maxValues = new GenParameter();
		maxValues.setName("maxValues");
		maxValues.setValues(Collections.singletonList("3"));

		generator.getGenParameters().addAll(Arrays.asList(values, minValues, maxValues));

		RandomInputValueIterator<String> iterator = (RandomInputValueIterator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		for (int i=0; i<100;i++) {
			String value = iterator.nextValueAsString();
			int length = value.split(",").length;
			assertTrue("Invalid value: " + value, length >= 1 && length <=3);
		}
	}

	@Test
	public void testRandomStringIteratorMultipleValuesCustomSeparator() {
		GenParameter values = new GenParameter();
		values.setName("values");
		values.setValues(stringValues);

		GenParameter minValues = new GenParameter();
		minValues.setName("minValues");
		minValues.setValues(Collections.singletonList("1"));

		GenParameter maxValues = new GenParameter();
		maxValues.setName("maxValues");
		maxValues.setValues(Collections.singletonList("3"));

		GenParameter separator = new GenParameter();
		separator.setName("separator");
		separator.setValues(Collections.singletonList("#"));

		generator.getGenParameters().addAll(Arrays.asList(values, minValues, maxValues, separator));

		RandomInputValueIterator<String> iterator = (RandomInputValueIterator) TestDataGeneratorFactory.createTestDataGenerator(generator);

		for (int i=0; i<100;i++) {
			String value = iterator.nextValueAsString();
			int length = value.split(",").length;
			assertTrue("Invalid value: " + value, length >= 1 && length <=3);
			if (length > 1) {
				assertTrue("Invalid separator: " + value, value.contains("#"));
			}
		}
	}
	
	@Test
	public void testSeed() {
		List<String> values1 = new ArrayList<>();
		List<String> values2 = new ArrayList<>();
		
		RandomInputValueIterator<String> iterator = new RandomInputValueIterator<>(stringValues);
		for (int i = 0; i < 100; i++)
			values1.add((String)iterator.nextValue());
		
		RandomInputValueIterator<String> iterator2 = new RandomInputValueIterator<>(stringValues);
		iterator2.setSeed(iterator.getSeed());
		for (int i = 0; i < 100; i++)
			values2.add((String)iterator2.nextValue());
		
		assertEquals("Both lists are not equal!", values1, values2);
	}

}

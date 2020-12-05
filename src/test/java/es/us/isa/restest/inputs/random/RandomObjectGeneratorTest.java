package es.us.isa.restest.inputs.random;

import static org.junit.Assert.*;

import java.util.*;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import org.junit.Before;
import org.junit.Test;

public class RandomObjectGeneratorTest {

    Generator generator;

    @Before
    public void setup() {
        generator = new Generator();
        generator.setType("RandomObject");
        generator.setGenParameters(new ArrayList<>());
    }

    @Test
    public void testConstructorWithoutArguments() {
        RandomObjectGenerator objGen = (RandomObjectGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
        objGen.setSeed(99999);

        assertEquals("The RandomObjectGenerator has not been initialized properly", 99999, objGen.getSeed());
    }

    @Test
    public void testConstructorWithArguments() {
        List<Object> objList = new ArrayList<>();
        fillList(objList, 2);

        GenParameter values = new GenParameter();
        values.setName("values");
        values.setObjectValues(objList);

        generator.getGenParameters().add(values);

        RandomObjectGenerator objGen = (RandomObjectGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

        assertEquals("There should be 2 objects in the list", 2, objGen.getValues().size());
    }

    @Test
    public void testConstructorWithFileArguments() {
        GenParameter files = new GenParameter();
        files.setName("files");
        files.setValues(Collections.singletonList("src/test/resources/restest-test-resources/compare1.postman_collection.json"));

        generator.getGenParameters().add(files);

        RandomObjectGenerator objGen = (RandomObjectGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

        assertEquals("There should be 1 object in the list", 1, objGen.getValues().size());
    }

    @Test
    public void testNullNextValue() {
        RandomObjectGenerator objGen = (RandomObjectGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

        assertNull("The next value should be null", objGen.nextValue());
    }

    @Test
    public void testNullNextValueAsString() {
        RandomObjectGenerator objGen = (RandomObjectGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

        assertEquals("The next value should be null", "null", objGen.nextValueAsString());
    }

    @Test
    public void testNextValue() {
        List<Object> objList = new ArrayList<>();
        fillList(objList, 1);

        GenParameter values = new GenParameter();
        values.setName("values");
        values.setObjectValues(objList);

        generator.getGenParameters().add(values);

        RandomObjectGenerator objGen = (RandomObjectGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

        System.out.println(objGen.nextValue().getClass());

        assertTrue("The next value should be of type HashMap", objGen.nextValue() instanceof java.util.HashMap);
    }

    @Test
    public void testNextValueAsString() {
        List<Object> objList = new ArrayList<>();
        fillList(objList, 1);

        GenParameter values = new GenParameter();
        values.setName("values");
        values.setObjectValues(objList);

        generator.getGenParameters().add(values);

        RandomObjectGenerator objGen = (RandomObjectGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);

        assertEquals("The next value is not the expected one", "{\"key1\":\"value1\",\"key2\":\"value2\"}", objGen.nextValueAsString());
    }

    private void fillList(List<Object> objList, int size) {
        Map<String, String> obj1 = new HashMap<>();
        obj1.put("key1", "value1");
        obj1.put("key2", "value2");
        objList.add(obj1);

        if (size != 1) {
            Map<String, String> obj2 = new HashMap<>();
            obj2.put("key3", "value3");
            obj2.put("key4", "value4");
            objList.add(obj2);
        }
    }
}

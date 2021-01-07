package es.us.isa.restest.inputs.perturbation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class ObjectPerturbatorTest {

    Generator generator;

    @Before
    public void setupGenerator() {
        generator = new Generator();
        generator.setType("ObjectPerturbator");
        generator.setGenParameters(new ArrayList<>());
    }

    @Test
    public void testConstructorWithoutArguments() {
        ObjectPerturbator objectPerturbator = (ObjectPerturbator) TestDataGeneratorFactory.createTestDataGenerator(generator);
        MatcherAssert.assertThat(objectPerturbator.getOriginalObjects(), Matchers.empty());
    }

    @Test
    public void testConstructorWithArgumentJsonNode() {
        JsonNode originalObject = new ObjectNode(null);

        ObjectPerturbator objectPerturbator = new ObjectPerturbator(originalObject);

        assertNotNull("The originalObjects list should not be null", objectPerturbator.getOriginalObjects());
        assertEquals("The original object should be '{}'", "{}", objectPerturbator.getOriginalStringObjects().get(0));
    }

    @Test
    public void testWithObject() {
        JsonNode originalObject = new ObjectNode(null);

        GenParameter object = new GenParameter();
        object.setName("object");
        object.setObjectValues(Collections.singletonList(originalObject));

        GenParameter singleOrder = new GenParameter();
        singleOrder.setName("singleOrder");
        singleOrder.setValues(Collections.singletonList("true"));

        generator.getGenParameters().addAll(Arrays.asList(object, singleOrder));

        ObjectPerturbator objectPerturbator = (ObjectPerturbator) TestDataGeneratorFactory.createTestDataGenerator(generator);

        assertNotNull("The originalObjects list should not be null", objectPerturbator.getOriginalObjects());
        assertEquals("The original object should be '{}'", "{}", objectPerturbator.getOriginalStringObjects().get(0));
    }

    @Test
    public void testConstructorWithArgumentString() {
        String originalObject = "{}";

        ObjectPerturbator objectPerturbator = new ObjectPerturbator(originalObject);

        assertNotNull("The originalObjects list should not be null", objectPerturbator.getOriginalObjects());
        assertEquals("The original object should be '{}'", "{}", objectPerturbator.getOriginalStringObjects().get(0));
    }

    @Test
    public void testConstructorWithMultipleArguments() {
        String[] originalObjects = {"{}", "[]", "{\"prop1\": \"val1\", \"prop2\": [1, true, {}]}"};
        ObjectPerturbator objectPerturbator = new ObjectPerturbator(Arrays.asList(originalObjects));
        assertNotNull("The originalObjects list should not be null", objectPerturbator.getOriginalObjects());
        for(String object : objectPerturbator.getOriginalStringObjects()) {
            boolean b = object.equals("{}") || object.equals("[]") || object.equals("{\"prop1\":\"val1\",\"prop2\":[1,true,{}]}");
            assertTrue("The original objects should be '{}', '[]' and '{\"prop1\": \"val1\", \"prop2\": [1, true, {}]}'", b);
        }
    }

    @Test
    public void testWithArgumentString() {
        String originalObject = "{}";

        GenParameter stringObject = new GenParameter();
        stringObject.setName("stringObject");
        stringObject.setValues(Collections.singletonList(originalObject));

        generator.getGenParameters().add(stringObject);

        ObjectPerturbator objectPerturbator = (ObjectPerturbator) TestDataGeneratorFactory.createTestDataGenerator(generator);
        assertNotNull("The originalObjects list should not be null", objectPerturbator.getOriginalObjects());
        assertEquals("The original object should be '{}'", "{}", objectPerturbator.getOriginalStringObjects().get(0));
    }

    @Test
    public void testConstructorWithArgumentFile() throws IOException {
        String filePath = "src/test/resources/auth/Sample/apikeys.json";

        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonObject = mapper.readTree(new File("src/test/resources/auth/Sample/apikeys.json"));

        GenParameter stringObject = new GenParameter();
        stringObject.setName("file");
        stringObject.setValues(Collections.singletonList(filePath));

        generator.getGenParameters().add(stringObject);

        ObjectPerturbator objectPerturbator = (ObjectPerturbator) TestDataGeneratorFactory.createTestDataGenerator(generator);
        assertNotNull("The originalObjects list should not be null", objectPerturbator.getOriginalObjects());
        assertEquals("The original object should be '" + jsonObject.toString() + "'", jsonObject.toString(), objectPerturbator.getOriginalStringObjects().get(0));
    }



    @Test
    public void testNextValue() {
        String originalObject = "[]";

        GenParameter stringObject = new GenParameter();
        stringObject.setName("stringObject");
        stringObject.setValues(Collections.singletonList(originalObject));

        generator.getGenParameters().add(stringObject);

        ObjectPerturbator objectPerturbator = (ObjectPerturbator) TestDataGeneratorFactory.createTestDataGenerator(generator);

        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObjects().get(0), objectPerturbator.nextValue());
    }

    @Test
    public void testNextValueAsString() {
        String originalObject = "{\"prop1\": \"val1\", \"prop2\": [1, true, {}]}";

        GenParameter stringObject = new GenParameter();
        stringObject.setName("stringObject");
        stringObject.setValues(Collections.singletonList(originalObject));

        generator.getGenParameters().add(stringObject);

        ObjectPerturbator objectPerturbator = (ObjectPerturbator) TestDataGeneratorFactory.createTestDataGenerator(generator);

        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObjects().get(0), objectPerturbator.nextValueAsString());
    }
}

package es.us.isa.restest.inputs.perturbation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import static org.junit.Assert.*;

public class ObjectPerturbatorTest {

    @Test
    public void testConstructorWithoutArguments() {
        ObjectPerturbator objectPerturbator = new ObjectPerturbator();
        assertNull("The originalObject should be null", objectPerturbator.getOriginalObject());
    }

    @Test
    public void testConstructorWithArgumentJsonNode() {
        JsonNode originalObject = new ObjectNode(null);
        ObjectPerturbator objectPerturbator = new ObjectPerturbator(originalObject);
        assertNotNull("The originalObject should not be null", objectPerturbator.getOriginalObject());
        assertEquals("The original object should be '{}'", "{}", objectPerturbator.getOriginalStringObject());
    }

    @Test
    public void testConstructorWithArgumentString() {
        String originalObject = "{}";
        ObjectPerturbator objectPerturbator = new ObjectPerturbator(originalObject);
        assertNotNull("The originalObject should not be null", objectPerturbator.getOriginalObject());
        assertEquals("The original object should be '{}'", "{}", objectPerturbator.getOriginalStringObject());
    }

    @Test
    public void testNextValue() {
        String originalObject = "{}";
        ObjectPerturbator objectPerturbator = new ObjectPerturbator(originalObject);
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
        assertNotEquals(objectPerturbator.getOriginalObject(), objectPerturbator.nextValue());
    }

    @Test
    public void testNextValueAsString() {
        String originalObject = "{\"prop1\": \"val1\", \"prop2\": [1, true, {}]}";
        ObjectPerturbator objectPerturbator = new ObjectPerturbator(originalObject);
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
        assertNotEquals(objectPerturbator.getOriginalStringObject(), objectPerturbator.nextValueAsString());
    }
}

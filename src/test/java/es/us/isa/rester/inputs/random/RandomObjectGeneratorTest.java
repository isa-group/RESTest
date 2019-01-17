package es.us.isa.rester.inputs.random;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class RandomObjectGeneratorTest {

    @Test
    public void testConstructorWithoutArguments() {
        RandomObjectGenerator objGen = new RandomObjectGenerator();
        objGen.setSeed(99999);

        assertEquals("The RandomObjectGenerator has not been initialized properly", 99999, objGen.getSeed());
    }

    @Test
    public void testConstructorWithArguments() {
        List<Object> objList = new ArrayList<>();
        fillList(objList, 2);
        RandomObjectGenerator objGen = new RandomObjectGenerator(objList);

        assertEquals("There should be 2 objects in the list", 2, objGen.getValues().size());
    }

    @Test
    public void testNullNextValue() {
        RandomObjectGenerator objGen = new RandomObjectGenerator();

        assertEquals("The next value should be null", null, objGen.nextValue());
    }

    @Test
    public void testNullNextValueAsString() {
        RandomObjectGenerator objGen = new RandomObjectGenerator();

        assertEquals("The next value should be null", "null", objGen.nextValueAsString());
    }

    @Test
    public void testNextValue() {
        List<Object> objList = new ArrayList<>();
        fillList(objList, 1);
        RandomObjectGenerator objGen = new RandomObjectGenerator();
        objGen.setValues(objList);
        System.out.println(objGen.nextValue().getClass());

        assertTrue("The next value should be of type HashMap", objGen.nextValue() instanceof java.util.HashMap);
    }

    @Test
    public void testNextValueAsString() {
        List<Object> objList = new ArrayList<>();
        fillList(objList, 1);
        RandomObjectGenerator objGen = new RandomObjectGenerator();
        objGen.setValues(objList);

        assertEquals("The next value is not the expected one", "{\"key1\":\"value1\",\"key2\":\"value2\"}", objGen.nextValueAsString());
    }

    private List<Object> fillList(List<Object> objList, int size) {
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
        return objList;
    }
}

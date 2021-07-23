package es.us.isa.restest.inputs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static es.us.isa.restest.inputs.fuzzing.FuzzingDictionary.*;
import static org.junit.Assert.*;

public class FuzzingDictionaryTest {

    @Test
    public void dictionaryInitializedTest() {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, List<String>> fuzzingDict = null;
        try {
            fuzzingDict = objectMapper.readValue(new File("src/main/resources/fuzzing-dictionary.json"), new TypeReference<HashMap<String, List<String>>>(){});
        } catch (IOException e) {
            fail("fuzzing-dictionary.json could not be loaded");
            e.printStackTrace();
        }
        assertNotNull(getFuzzingDict());
        assertEquals(fuzzingDict, getFuzzingDict());
    }

    @Test
    public void stringValuesTest() {
        assertEquals(5, getFuzzingStringValues().size());
        assertTrue(getFuzzingStringValues().containsAll(Arrays.asList("null", "", "\\0", "randomString", "one space")));
    }

    @Test
    public void invalidTypeDefaultsToStringValuesTest() {
        assertEquals(5, getFuzzingValues("invalidType").size());
        assertTrue(getFuzzingStringValues().containsAll(Arrays.asList("null", "", "\\0", "randomString", "one space")));
    }

    @Test
    public void invalidTypeDefaultsToStringValuesSingleValueTest() {
        assertTrue(Arrays.asList("null", "", "\\0", "randomString", "one space").contains(getFuzzingValue("invalidType")));
    }

    @Test
    public void getNodeFromValueStringTest() {
        assertTrue(getNodeFromValue("This is a string") instanceof TextNode);
    }

    @Test
    public void getNodeFromValueNumberTest() {
        assertTrue(getNodeFromValue("42") instanceof LongNode);
    }

    @Test
    public void getNodeFromValueBooleanTest() {
        assertTrue(getNodeFromValue("true") instanceof BooleanNode);
        assertTrue(getNodeFromValue("false") instanceof BooleanNode);
    }

    @Test
    public void getNodeFromValueNullTest() {
        assertTrue(getNodeFromValue("null") instanceof NullNode);
    }

    @Test
    public void getNodeFuzzingValueStringTest() {
        JsonNode node = getNodeFuzzingValue("string");
        assertTrue(node instanceof TextNode || node instanceof NullNode);
    }

    @Test
    public void getNodeFuzzingValueNumberTest() {
        JsonNode node = getNodeFuzzingValue("number");
        assertTrue(node instanceof TextNode || node instanceof NullNode
                || node instanceof LongNode || node instanceof DoubleNode
                || node instanceof DecimalNode || node instanceof BigIntegerNode);
    }

    @Test
    public void getNodeFuzzingValueBooleanTest() {
        JsonNode node = getNodeFuzzingValue("boolean");
        assertTrue(node instanceof TextNode || node instanceof NullNode
                || node instanceof LongNode || node instanceof BooleanNode);
    }

    @Test
    public void getNodeFuzzingValueWrongTypeTest() {
        JsonNode node = getNodeFuzzingValue("wrong");
        assertTrue(node instanceof TextNode || node instanceof NullNode);
    }
}

package es.us.isa.restest.inputs.fuzzing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class FuzzingDictionary {

    private static Map<String, List<String>> fuzzingDict; // Fuzzing dictionary
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger logger = LogManager.getLogger(FuzzingDictionary.class);

    static {
        try {
            fuzzingDict = objectMapper.readValue(new File("src/main/resources/fuzzing-dictionary.json"), new TypeReference<HashMap<String, List<String>>>(){});
        } catch (IOException e) {
            logger.error("Error processing JSON fuzzing dictionary", e);
        }
    }

    public static JsonNode getNodeFuzzingValue(String type) {
        String value = getFuzzingValue(type);
        return getNodeFromValue(value);
    }

    public static JsonNode getNodeFromValue(String value) {
        JsonNode node = null;

        if (NumberUtils.isCreatable(value)) {
            Number n = NumberUtils.createNumber(value);
            if (n instanceof Integer || n instanceof Long)
                node = objectMapper.getNodeFactory().numberNode(n.longValue());
            else if (n instanceof BigInteger)
                node = objectMapper.getNodeFactory().numberNode((BigInteger) n);
            else if (n instanceof Double || n instanceof Float)
                node = objectMapper.getNodeFactory().numberNode(n.doubleValue());
            else if (n instanceof BigDecimal)
                node = objectMapper.getNodeFactory().numberNode((BigDecimal) n);
        } else if("true".equals(value) || "false".equals(value))
            node = objectMapper.getNodeFactory().booleanNode(Boolean.parseBoolean(value));
        else if ("null".equals(value))
            node = objectMapper.getNodeFactory().nullNode();
        else
            node = objectMapper.getNodeFactory().textNode(value);

        return node;
    }

    public static String getFuzzingValue(String type) {
        List<String> values = getFuzzingValues(type);
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    public static List<String> getFuzzingValues(String type) {
        switch (type) {
            case "integer":
                return getFuzzingIntegerValues();
            case "number":
                return getFuzzingNumberValues();
            case "boolean":
                return getFuzzingBooleanValues();
            default:
                return getFuzzingStringValues();
        }
    }

    public static List<String> getFuzzingCommonValues() {
        return new ArrayList<>(fuzzingDict.get("common"));
    }

    public static List<String> getFuzzingStringValues() {
        List<String> fuzzingList = new ArrayList<>(fuzzingDict.get("common"));
        fuzzingList.addAll(fuzzingDict.get("string"));
        return fuzzingList;
    }

    public static List<String> getFuzzingIntegerValues() {
        List<String> fuzzingList = new ArrayList<>(fuzzingDict.get("common"));
        fuzzingList.addAll(fuzzingDict.get("integer"));
        return fuzzingList;
    }

    public static List<String> getFuzzingNumberValues() {
        List<String> fuzzingList = new ArrayList<>(fuzzingDict.get("common"));
        fuzzingList.addAll(fuzzingDict.get("number"));
        return fuzzingList;
    }

    public static List<String> getFuzzingBooleanValues() {
        List<String> fuzzingList = new ArrayList<>(fuzzingDict.get("common"));
        fuzzingList.addAll(fuzzingDict.get("boolean"));
        return fuzzingList;
    }

    public static Map<String, List<String>> getFuzzingDict() {
        return fuzzingDict;
    }
}

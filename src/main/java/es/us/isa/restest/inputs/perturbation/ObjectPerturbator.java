package es.us.isa.restest.inputs.perturbation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.us.isa.jsonmutator.JsonMutator;
import es.us.isa.restest.inputs.ITestDataGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * This class leverages the {@link es.us.isa.jsonmutator.JsonMutator} to perturb
 * an original, valid JSON object (generally used as input for an API operation)
 * and transform it into another JSON object (possibly invalid, but not guaranteed).
 * For the moment, only single-order perturbations are applied, i.e., only one
 * mutation is applied at a time.
 *
 * @author Alberto Martin-Lopez
 */
public class ObjectPerturbator implements ITestDataGenerator {

    private List<JsonNode> originalObjects;
    private JsonMutator jsonMutator;
    private ObjectMapper objectMapper;
    private Boolean singleOrder = true; // True if single order mutation, false otherwise
    private Random random = new SecureRandom();

    private static final String LOGGER_ERROR_MSG = "An error occurred when deserializing JSON:";
    private static Logger logger = LogManager.getLogger(ObjectPerturbator.class.getName());

    public ObjectPerturbator() {
        this.objectMapper = new ObjectMapper();
        this.jsonMutator = new JsonMutator();
        this.originalObjects = new ArrayList<>();
    }

    public ObjectPerturbator(JsonNode originalObject) {
        this();
        this.originalObjects.add(originalObject);
    }

    public ObjectPerturbator(Object originalObject) {
        this();
        JsonNode jsonObject = objectMapper.valueToTree(originalObject);
        originalObjects.add(jsonObject);
    }

    public ObjectPerturbator(String originalObject) {
        this();
        try {
            JsonNode jsonObject = objectMapper.readTree(originalObject);
            originalObjects.add(jsonObject);
        } catch (IOException e) {
            logger.error(LOGGER_ERROR_MSG);
            logger.error(e.getMessage(), e);
            System.exit(1);
        }
    }

    public ObjectPerturbator(List<String> stringObjects) {
        this();
        try {
            for(String stringObject : stringObjects) {
                JsonNode jsonObject = objectMapper.readTree(stringObject);
                this.originalObjects.add(jsonObject);
            }
        } catch (IOException e) {
            logger.error(LOGGER_ERROR_MSG);
            logger.error(e.getMessage(), e);
            System.exit(1);
        }
    }

    @Override
    public JsonNode nextValue() {
        int index = random.nextInt(originalObjects.size());
        JsonNode beforeMutating = originalObjects.get(index).deepCopy();
        JsonNode afterMutating = jsonMutator.mutateJson(originalObjects.get(index), singleOrder);
        originalObjects.set(index, beforeMutating);
        return afterMutating;
    }

    @Override
    public String nextValueAsString() {
        try {
            return objectMapper.writeValueAsString(nextValue());
        } catch (JsonProcessingException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    public List<JsonNode> getOriginalObjects() {
        return originalObjects;
    }

    public JsonNode getRandomOriginalObject() {
        int index = random.nextInt(originalObjects.size());
        return originalObjects.get(index).deepCopy();
    }

    public List<String> getOriginalStringObjects() {
        List<String> stringObjects = new ArrayList<>();
        try {
            for(JsonNode originalObject : originalObjects) {
                stringObjects.add(objectMapper.writeValueAsString(originalObject));
            }
        } catch (JsonProcessingException e) {
            logger.error(e.getMessage(), e);
        }
        return stringObjects;
    }

    public String getRandomOriginalStringObject() {
        try {
            return objectMapper.writeValueAsString(getRandomOriginalObject());
        } catch (JsonProcessingException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    public void addOriginalObject(JsonNode originalObject) {
        this.originalObjects.add(originalObject);
    }

    public void addOriginalObject(Object originalObject) {
        this.originalObjects.add(objectMapper.valueToTree(originalObject));
    }

    public void addOriginalObject(String originalObject) {
        try {
            this.originalObjects.add(objectMapper.readTree(originalObject));
        } catch (IOException e) {
            logger.error(LOGGER_ERROR_MSG);
            logger.error(e.getMessage(), e);
            System.exit(1);
        }
    }

    public void setOriginalObjects(List<String> stringObjects) {
        try {
            for(String stringObject : stringObjects) {
                JsonNode jsonObject = objectMapper.readTree(stringObject);
                this.originalObjects.add(jsonObject);
            }
        } catch (IOException e) {
            logger.error(LOGGER_ERROR_MSG);
            logger.error(e.getMessage(), e);
            System.exit(1);
        }
    }

    public Boolean getSingleOrder() {
        return singleOrder;
    }

    public void setSingleOrder(Boolean singleOrder) {
        this.singleOrder = singleOrder;
    }
}

package es.us.isa.restest.inputs.perturbation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.us.isa.jsonmutator.JsonMutator;
import es.us.isa.restest.inputs.ITestDataGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

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

    private JsonNode originalObject;
    private JsonMutator jsonMutator;
    private ObjectMapper objectMapper;
    private Boolean singleOrder = true; // True if single order mutation, false otherwise
    private static Logger logger = LogManager.getLogger(ObjectPerturbator.class.getName());

    public ObjectPerturbator() {
        this.objectMapper = new ObjectMapper();
        this.jsonMutator = new JsonMutator();
    }

    public ObjectPerturbator(JsonNode originalObject) {
        this();
        this.originalObject = originalObject;
    }

    public ObjectPerturbator(Object originalObject) {
        this();
        this.originalObject = objectMapper.valueToTree(originalObject);
    }

    public ObjectPerturbator(String originalObject) {
        this();
        try {
            this.originalObject = objectMapper.readTree(originalObject);
        } catch (IOException e) {
            logger.error("An error occurred when deserializing JSON:");
            logger.error(e.getMessage(), e);
            System.exit(1);
        }
    }

    @Override
    public JsonNode nextValue() {
        JsonNode beforeMutating = originalObject.deepCopy();
        JsonNode afterMutating = jsonMutator.mutateJson(originalObject, singleOrder);
        originalObject = beforeMutating;
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

    public JsonNode getOriginalObject() {
        return originalObject;
    }

    public String getOriginalStringObject() {
        try {
            return objectMapper.writeValueAsString(originalObject);
        } catch (JsonProcessingException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    public void setOriginalObject(JsonNode originalObject) {
        this.originalObject = originalObject;
    }

    public void setOriginalObject(Object originalObject) {
        this.originalObject = objectMapper.valueToTree(originalObject);
    }

    public void setOriginalObject(String originalObject) {
        try {
            this.originalObject = objectMapper.readTree(originalObject);
        } catch (IOException e) {
            logger.error("An error occurred when deserializing JSON:");
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

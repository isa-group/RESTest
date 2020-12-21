package es.us.isa.restest.inputs.random;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Given a list of objects, the RandomObjectGenerator returns one of those randomly
 */
public class RandomObjectGenerator extends RandomGenerator {

    private List<Object> values;

    private static final Logger logger = LogManager.getLogger(RandomObjectGenerator.class.getName());

    public RandomObjectGenerator() {
        super();
        values = new ArrayList<>();
    }

    public RandomObjectGenerator(List<Object> values) {
        super();

        this.values = values;
    }

    @Override
    public Object nextValue() {
        Object value=null;

        if (!values.isEmpty())
            value= values.get(rand.nextInt(0, values.size()-1));

        return value;
    }

    @Override
    public String nextValueAsString() {
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonValue = null;
        try {
            jsonValue = objectMapper.writeValueAsString(nextValue());
        } catch (JsonProcessingException e) {
            logger.error("Exception: ", e);
        }
        return jsonValue;
    }

    public List<Object> getValues() {
        return values;
    }

    public void setValues(List<Object> values) {
        this.values = values;
    }
}

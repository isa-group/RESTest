package es.us.isa.rester.inputs.random;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RandomObjectGenerator extends RandomGenerator {

    private List<Object> values;

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
            e.printStackTrace();
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

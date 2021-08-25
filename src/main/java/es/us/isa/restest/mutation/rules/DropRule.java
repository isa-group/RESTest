package es.us.isa.restest.mutation.rules;

import io.swagger.v3.oas.models.media.Schema;

import java.util.*;

public class DropRule extends PathRule {

    private static DropRule instance;

    private DropRule() {
        super();
    }

    public static DropRule getInstance() {
        if (instance == null) {
            instance = new DropRule();
        }
        return instance;
    }

    protected void applyNodeFuzzingRule(Schema<?> schema, String objectChild) {
        List<String> propertyNames = new ArrayList<>(schema.getProperties().keySet());
        propertyNames.remove(objectChild);
        String dropProperty = propertyNames.get(random.nextInt(propertyNames.size()));
        schema.getProperties().remove(dropProperty);
    }

}

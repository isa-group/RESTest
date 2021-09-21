package es.us.isa.restest.mutation.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayList;
import java.util.List;

public class SelectRule extends PathRule {

    private static SelectRule instance;

    private SelectRule() {
        super();
    }

    public static SelectRule getInstance() {
        if (instance == null) {
            instance = new SelectRule();
        }
        return instance;
    }

    protected void applyNodeFuzzingRule(Schema<?> schema, String objectChild) {
        List<String> propertyNames = new ArrayList<>(schema.getProperties().keySet());
        propertyNames.remove(objectChild);

        if(!propertyNames.isEmpty()) {
            String selectedProperty = propertyNames.get(random.nextInt(propertyNames.size()));
            for (String propertyName : propertyNames) {
                if (!selectedProperty.equals(propertyName)) {
                    schema.getProperties().remove(propertyName);
                }
            }
        }
    }
}

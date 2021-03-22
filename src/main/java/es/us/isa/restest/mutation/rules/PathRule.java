package es.us.isa.restest.mutation.rules;

import es.us.isa.restest.specification.OpenAPISpecification;
import io.swagger.v3.oas.models.media.Schema;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public abstract class PathRule {

    protected final Random random = new SecureRandom();
    protected OpenAPISpecification spec;

    protected PathRule() {}

    public void apply(Schema<?> schema) {
       List<Map.Entry<String, Schema>> objectNodes = new ArrayList<>();

        for(Map.Entry<String, Schema> entry : schema.getProperties().entrySet()) {
            if (entry.getValue().getType().equals("object")) {
                objectNodes.add(entry);
            }
        }

        String objectChild = "";
        if (!objectNodes.isEmpty()) {
            Map.Entry<String, Schema> entry = objectNodes.get(random.nextInt(objectNodes.size()));
            objectChild = entry.getKey();
            apply(entry.getValue());
        }

        applyNodeFuzzingRule(schema, objectChild);
    }

    protected abstract void applyNodeFuzzingRule(Schema<?> schema, String objectChild);

    public void setSpec(OpenAPISpecification spec) {
        this.spec = spec;
    }
}

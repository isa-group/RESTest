package es.us.isa.restest.mutation.rules;

import es.us.isa.restest.specification.OpenAPISpecification;
import io.swagger.v3.oas.models.media.Schema;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public abstract class SingleRule {

    protected final Random random = new SecureRandom();
    protected OpenAPISpecification spec;

    protected SingleRule() {}

    public void apply(Schema<?> schema, boolean internalNode) {
        List<Schema> objectNodes = getAllObjectNodes(schema, internalNode);
        Schema s = objectNodes.get(random.nextInt(objectNodes.size()));

        applyNodeFuzzingRule(s);
    }

    private List<Schema> getAllObjectNodes(Schema<?> schema, boolean internalNode) {
        List<Schema> objectNodes = new ArrayList<>();

        objectNodes.add(schema);

        for(Map.Entry<String, Schema> entry : schema.getProperties().entrySet()) {
            if (entry.getValue().getType().equals("object")) {
                objectNodes.addAll(getAllObjectNodes(entry.getValue(), internalNode));
            } else if (!internalNode) {
                objectNodes.add(entry.getValue());

            }
        }

        return objectNodes;
    }

    protected abstract void applyNodeFuzzingRule(Schema<?> schema);

    public void setSpec(OpenAPISpecification spec) {
        this.spec = spec;
    }

}

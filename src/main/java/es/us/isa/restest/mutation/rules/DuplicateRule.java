package es.us.isa.restest.mutation.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayList;
import java.util.List;

import static es.us.isa.restest.util.SchemaManager.generateFullyResolvedSchema;

public class DuplicateRule extends SingleRule {

    private static DuplicateRule instance;

    private DuplicateRule() {
        super();
    }

    public static DuplicateRule getInstance() {
        if (instance == null) {
            instance = new DuplicateRule();
        }
        return instance;
    }

    @Override
    protected void applyNodeFuzzingRule(Schema<?> schema, OpenAPI spec) {
        List<String> propertyNames = new ArrayList<>(schema.getProperties().keySet());
        String duplicatedProperty = propertyNames.get(random.nextInt(propertyNames.size()));
        Schema duplicatedSchema = generateFullyResolvedSchema(schema.getProperties().get(duplicatedProperty), spec);
        schema.getProperties().put(duplicatedProperty + "-duplicated", duplicatedSchema);
    }
}

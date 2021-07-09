package es.us.isa.restest.mutation.rules;

import es.us.isa.restest.util.SchemaManager;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayList;
import java.util.List;

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
    protected void applyNodeFuzzingRule(Schema<?> schema) {
        List<String> propertyNames = new ArrayList<>(schema.getProperties().keySet());
        String duplicatedProperty = propertyNames.get(random.nextInt(propertyNames.size()));
        Schema duplicatedSchema;
        if (schema.getProperties().get(duplicatedProperty) instanceof ArraySchema) {
            duplicatedSchema = SchemaManager.copyArraySchema((ArraySchema) schema.getProperties().get(duplicatedProperty));
        } else {
            duplicatedSchema = SchemaManager.copySchema(schema.getProperties().get(duplicatedProperty));
        }

        schema.getProperties().put(duplicatedProperty + "-duplicated", duplicatedSchema);
    }
}

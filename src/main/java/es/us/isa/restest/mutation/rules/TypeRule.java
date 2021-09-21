package es.us.isa.restest.mutation.rules;

import com.google.common.collect.Lists;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

import java.util.List;

public class TypeRule extends SingleRule {

    private static TypeRule instance;

    private TypeRule() {
        super();
    }

    public static TypeRule getInstance() {
        if (instance == null) {
            instance = new TypeRule();
        }
        return instance;
    }

    @Override
    protected void applyNodeFuzzingRule(Schema<?> schema, OpenAPI spec) {
        List<String> types = Lists.newArrayList("string", "integer", "boolean", "object", "array");
        String type = schema.getType();
        if (type.equals("number")) {
            type = "integer";
        }

        types.remove(type);
        String newType = types.get(random.nextInt(types.size()));
        schema.type(newType);
        schema.example(null);
    }
}

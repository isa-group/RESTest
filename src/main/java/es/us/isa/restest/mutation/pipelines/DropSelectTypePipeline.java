package es.us.isa.restest.mutation.pipelines;

import es.us.isa.restest.mutation.rules.DropRule;
import es.us.isa.restest.mutation.rules.SelectRule;
import es.us.isa.restest.mutation.rules.TypeRule;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

import java.security.SecureRandom;
import java.util.Random;

public class DropSelectTypePipeline {

    private static DropSelectTypePipeline instance;

    private DropSelectTypePipeline() {}

    public static DropSelectTypePipeline getInstance() {
        if (instance == null) {
            instance = new DropSelectTypePipeline();
        }
        return instance;
    }

    private static final Random random = new SecureRandom();

    public void apply(Schema<?> schema, OpenAPI spec) {
        DropRule.getInstance().apply(schema, spec);
        if (random.nextDouble() > 0.5) {
            SelectRule.getInstance().apply(schema, spec);
        }
        if (random.nextDouble() > 0.1) {
            TypeRule.getInstance().apply(schema, false, spec);
        }
    }
}

package es.us.isa.restest.mutation.pipelines;

import es.us.isa.restest.mutation.rules.DropRule;
import es.us.isa.restest.mutation.rules.SelectRule;
import es.us.isa.restest.mutation.rules.TypeRule;
import io.swagger.v3.oas.models.media.Schema;

import java.security.SecureRandom;
import java.util.Random;

public class DropSelectTypePipeline {

    private DropSelectTypePipeline() {}

    private static final Random random = new SecureRandom();

    public static void apply(Schema<?> schema) {
        DropRule.getInstance().apply(schema);
        if (random.nextDouble() > 0.5) {
            SelectRule.getInstance().apply(schema);
        }
        if (random.nextDouble() > 0.1) {
            TypeRule.getInstance().apply(schema, false);
        }
    }
}

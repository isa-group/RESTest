package es.us.isa.restest.mutation;

import es.us.isa.restest.mutation.pipelines.DropSelectTypePipeline;
import es.us.isa.restest.mutation.rules.DuplicateRule;
import es.us.isa.restest.util.SchemaManager;
import io.swagger.v3.oas.models.media.Schema;

import java.security.SecureRandom;
import java.util.Random;

public class SchemaMutation {

    private final Random random = new SecureRandom();

    private Schema schema;

    public SchemaMutation(Schema schema) {
        this.schema = schema;
    }

    public Schema mutate() {
        MutationPipeline mutation = MutationPipeline.values()[random.nextInt(MutationPipeline.values().length)];
        Schema mutatedSchema = SchemaManager.copySchema(schema);
        switch (mutation) {
            case DUPLICATE:
                DuplicateRule.getInstance().apply(mutatedSchema, true);
                break;
            case DROP_SELECT_TYPE:
                DropSelectTypePipeline.apply(mutatedSchema);
                break;
            default:
        }

        return mutatedSchema;
    }

    public enum MutationPipeline {
        DROP_SELECT_TYPE, DUPLICATE
    }
}



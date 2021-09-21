package es.us.isa.restest.mutation;

import es.us.isa.restest.mutation.pipelines.DropSelectTypePipeline;
import es.us.isa.restest.mutation.rules.DuplicateRule;
import es.us.isa.restest.util.SchemaManager;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

import java.security.SecureRandom;
import java.util.Random;

import static es.us.isa.restest.util.SchemaManager.generateFullyResolvedSchema;

public class SchemaMutation {

    private final Random random = new SecureRandom();

    private Schema schema;
    private OpenAPI spec;

    public SchemaMutation(Schema schema, OpenAPI spec) {
        this.schema = schema;
        this.spec = spec;
    }

    public Schema mutate() {
        MutationPipeline mutation = MutationPipeline.values()[random.nextInt(MutationPipeline.values().length)];
        Schema mutatedSchema = generateFullyResolvedSchema(schema, spec);
        switch (mutation) {
            case DUPLICATE:
                DuplicateRule.getInstance().apply(mutatedSchema, true, spec);
                break;
            case DROP_SELECT_TYPE:
                DropSelectTypePipeline.getInstance().apply(mutatedSchema, spec);
                break;
            default:
        }

        return mutatedSchema;
    }

    public enum MutationPipeline {
        DROP_SELECT_TYPE, DUPLICATE
    }
}



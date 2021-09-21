package es.us.isa.restest.mutation.rules;

import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.util.SchemaManager;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.Test;

import java.util.Map;

import static es.us.isa.restest.util.SchemaManager.generateFullyResolvedSchema;
import static es.us.isa.restest.util.SchemaManager.resolveSchema;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TypeRuleTest {

    @Test
    public void applyTypeRuleCommentsPostCommentTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger.yaml");
        Schema originalSchema = spec.getSpecification().getPaths().get("/comments").getPost().getRequestBody().getContent().get("application/json").getSchema();
        originalSchema = generateFullyResolvedSchema(originalSchema, spec.getSpecification());
        Schema mutatedSchema = generateFullyResolvedSchema(originalSchema, spec.getSpecification());

        TypeRule.getInstance().apply(mutatedSchema, false, spec.getSpecification());

        assertEquals(1, getMutationsApplied(originalSchema, mutatedSchema));
    }

    @Test
    public void applyTypeRulePetstorePostPetTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/specifications/petstore.yaml");
        Schema originalSchema = spec.getSpecification().getPaths().get("/pet").getPost().getRequestBody().getContent().get("application/json").getSchema();
        originalSchema = generateFullyResolvedSchema(originalSchema, spec.getSpecification());
        Schema mutatedSchema = generateFullyResolvedSchema(originalSchema, spec.getSpecification());

        TypeRule.getInstance().apply(mutatedSchema, false, spec.getSpecification());

        assertEquals(1, getMutationsApplied(originalSchema, mutatedSchema));
    }

    private int getMutationsApplied(Schema originalSchema, Schema mutatedSchema) {
        int mutationsApplied = 0;

        if (!mutatedSchema.getType().equals(originalSchema.getType())) {
            mutationsApplied++;
        }

        if (mutatedSchema.getType().equals("object") && originalSchema.getType().equals("object")) {
            for (Object o : mutatedSchema.getProperties().entrySet()) {
                Map.Entry<String, Schema> entry = (Map.Entry<String, Schema>) o;
                mutationsApplied += getMutationsApplied((Schema) originalSchema.getProperties().get(entry.getKey()), entry.getValue());

            }
        }

        return mutationsApplied;
    }

}

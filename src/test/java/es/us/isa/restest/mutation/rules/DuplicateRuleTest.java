package es.us.isa.restest.mutation.rules;

import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.util.SchemaManager;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertTrue;

public class DuplicateRuleTest {

    @Test
    public void applyDuplicateRuleCommentsPostCommentTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger.yaml");
        Schema originalSchema = spec.getSpecification().getPaths().get("/comments").getPost().getRequestBody().getContent().get("application/json").getSchema();
        Schema mutatedSchema = SchemaManager.copySchema(originalSchema);

        DuplicateRule.getInstance().apply(mutatedSchema, true);

        assertTrue(checkMutationIsValid(originalSchema, mutatedSchema));
    }

    @Test
    public void applyDuplicateRulePetstorePostPetTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/specifications/petstore.yaml");
        Schema originalSchema = spec.getSpecification().getPaths().get("/pet").getPost().getRequestBody().getContent().get("application/json").getSchema();
        Schema mutatedSchema = SchemaManager.copySchema(originalSchema);

        DuplicateRule.getInstance().apply(mutatedSchema, true);

        assertTrue(checkMutationIsValid(originalSchema, mutatedSchema));
    }

    private boolean checkMutationIsValid(Schema originalSchema, Schema mutatedSchema) {
        boolean valid = false;

        for (Object o : mutatedSchema.getProperties().entrySet()) {
            Map.Entry<String, Schema> entry = (Map.Entry<String, Schema>) o;
            if (entry.getKey().endsWith("-duplicated")) {
                String propertyName = entry.getKey().replace("-duplicated", "");
                valid = originalSchema.getProperties().containsKey(propertyName) && ((Schema) originalSchema.getProperties().get(propertyName)).getType().equals(entry.getValue().getType());
            } else if (entry.getValue().getType().equals("object")) {
                valid = checkMutationIsValid((Schema) originalSchema.getProperties().get(entry.getKey()), entry.getValue());
            }
            if (valid) {
                break;
            }
        }

        return valid;
    }

}

package es.us.isa.restest.mutation.rules;

import es.us.isa.restest.specification.OpenAPISpecification;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SelectRuleTest {

    @Test
    public void applySelectRuleCommentsPostCommentTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger.yaml");
        Schema postCommentSchema = spec.getSpecification().getPaths().get("/comments").getPost().getRequestBody().getContent().get("application/json").getSchema();

        SelectRule.getInstance().apply(postCommentSchema);

        assertEquals(1, postCommentSchema.getProperties().size());
    }

    @Test
    public void applySelectRulePetstorePostPetTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/specifications/petstore.yaml");
        Schema postPetSchema = spec.getSpecification().getPaths().get("/pet").getPost().getRequestBody().getContent().get("application/json").getSchema();

        SelectRule.getInstance().apply(postPetSchema);

        assertEquals(2, postPetSchema.getProperties().size());
        assertEquals(1, ((Schema) postPetSchema.getProperties().get("category")).getProperties().size());
    }
}

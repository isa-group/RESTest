package es.us.isa.restest.mutation.rules;

import es.us.isa.restest.specification.OpenAPISpecification;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.Test;

import static es.us.isa.restest.util.SchemaManager.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DropRuleTest {

    @Test
    public void applyDropRuleCommentsPostCommentTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger.yaml");
        Schema postCommentSchema = spec.getSpecification().getPaths().get("/comments").getPost().getRequestBody().getContent().get("application/json").getSchema();
        postCommentSchema = generateFullyResolvedSchema(postCommentSchema, spec.getSpecification());

        DropRule.getInstance().apply(postCommentSchema, spec.getSpecification());

        assertEquals(4, postCommentSchema.getProperties().size());
    }

    @Test
    public void applyDropRulePetstorePostPetTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/specifications/petstore.yaml");
        Schema postPetSchema = spec.getSpecification().getPaths().get("/pet").getPost().getRequestBody().getContent().get("application/json").getSchema();
        postPetSchema = generateFullyResolvedSchema(postPetSchema, spec.getSpecification());

        DropRule.getInstance().apply(postPetSchema, spec.getSpecification());

        assertEquals(5, postPetSchema.getProperties().size());
        assertEquals(1, ((Schema) postPetSchema.getProperties().get("category")).getProperties().size());
    }

    @Test
    public void applyDropRuleCommentsPutCommentTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger_forTestSuite5.yaml");
        Schema putCommentSchema = spec.getSpecification().getPaths().get("/comments/{id}").getPut().getRequestBody().getContent().get("application/json").getSchema();
        putCommentSchema = generateFullyResolvedSchema(putCommentSchema, spec.getSpecification());

        DropRule.getInstance().apply(putCommentSchema, spec.getSpecification());

        assertEquals(6, putCommentSchema.getProperties().size());
        assertEquals(2, ((Schema) putCommentSchema.getProperties().get("updates")).getProperties().size());
        ArraySchema tagsSchema = (ArraySchema) putCommentSchema.getProperties().get("tags");
        assertTrue(tagsSchema == null || tagsSchema.getItems().getProperties().size() == 1);
    }

    @Test
    public void applyDropRuleCommentsPostCommentArrayTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger_forTestSuite6.yaml");
        Schema postCommentSchema = spec.getSpecification().getPaths().get("/comments/multiple").getPost().getRequestBody().getContent().get("application/json").getSchema();
        postCommentSchema = generateFullyResolvedSchema(postCommentSchema, spec.getSpecification());

        DropRule.getInstance().apply(postCommentSchema, spec.getSpecification());

        assertEquals(4, ((ArraySchema)postCommentSchema).getItems().getProperties().size());
    }
}

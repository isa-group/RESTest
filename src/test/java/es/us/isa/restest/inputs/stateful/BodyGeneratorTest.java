package es.us.isa.restest.inputs.stateful;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import es.us.isa.restest.specification.OpenAPISpecification;
import io.swagger.v3.oas.models.Operation;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.*;

public class BodyGeneratorTest {
    Generator generator;

    @Before
    public void setUp() {
        generator = new Generator();
        generator.setType("BodyGenerator");
        generator.setGenParameters(new ArrayList<>());
    }

    @Test
    public void bodyGenerationDefaultValueTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger_forTestSuite5.yaml");
        String operationPath = "/comments/{id}";
        Operation oasOperation = spec.getSpecification().getPaths().get(operationPath).getPut();
        String dataDirPath = "src/test/resources/jsonData/nonExistingPath";

        String defaultJson = "{\"id\":\"c1\",\"text\":\"Test\"}";
        GenParameter defaultValue = new GenParameter();
        defaultValue.setName("defaultValue");
        defaultValue.setValues(Collections.singletonList(defaultJson));

        generator.getGenParameters().add(defaultValue);

        BodyGenerator gen = (BodyGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
        gen.setSpec(spec);
        gen.setDataDirPath(dataDirPath);
        gen.setOpenApiOperation(oasOperation);
        gen.setOperation("GET", operationPath);

        String value = gen.nextValueAsString(false);

        assertNotNull("The generator could not generate a body parameter", value);
        assertEquals(defaultJson, value);
    }

    @Test
    public void bodyGenerationTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger_forTestSuite5.yaml");
        String operationPath = "/comments/{id}";
        Operation oasOperation = spec.getSpecification().getPaths().get(operationPath).getPut();
        String dataDirPath = "src/test/resources/jsonData/stateful_body1";

        GenParameter defaultValue = new GenParameter();
        defaultValue.setName("defaultValue");
        defaultValue.setValues(Collections.singletonList("{}"));

        generator.getGenParameters().add(defaultValue);

        BodyGenerator gen = (BodyGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
        gen.setSpec(spec);
        gen.setDataDirPath(dataDirPath);
        gen.setOpenApiOperation(oasOperation);
        gen.setOperation("GET", operationPath);

        String value = gen.nextValueAsString(false);

        assertNotNull("The generator could not generate a body parameter", value);

        try {
            JsonNode body = (new ObjectMapper()).readTree(value);
            assertEquals("category1", body.get("tags").get(0).get("category").asText());
            assertEquals("2013-04-16T20:44:53.950", body.get("updates").get("created").asText());
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            fail("The body could not be parsed as a JSON");
        }
    }

    @Test
    public void bodyGenerationFirstLevelArrayTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger_forTestSuite6.yaml");
        String operationPath = "/comments/multiple";
        Operation oasOperation = spec.getSpecification().getPaths().get(operationPath).getPost();
        String dataDirPath = "src/test/resources/jsonData/stateful_body1";

        GenParameter defaultValue = new GenParameter();
        defaultValue.setName("defaultValue");
        defaultValue.setValues(Collections.singletonList("{}"));

        generator.getGenParameters().add(defaultValue);

        BodyGenerator gen = (BodyGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
        gen.setSpec(spec);
        gen.setDataDirPath(dataDirPath);
        gen.setOpenApiOperation(oasOperation);
        gen.setOperation("GET", operationPath);

        String value = gen.nextValueAsString(false);

        assertNotNull("The generator could not generate a body parameter", value);

        try {
            JsonNode body = (new ObjectMapper()).readTree(value);
            assertEquals("markSpecter", body.get(0).get("userName").asText());
            assertEquals("Too bad, too late.", body.get(0).get("text").asText());
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            fail("The body could not be parsed as a JSON");
        }
    }

    @Test
    public void bodyGenerationDiverseDataTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger_forTestSuite5.yaml");
        String operationPath = "/diverse/body/data";
        Operation oasOperation = spec.getSpecification().getPaths().get(operationPath).getPost();
        String dataDirPath = "src/test/resources/jsonData/stateful_body1";

        GenParameter defaultValue = new GenParameter();
        defaultValue.setName("defaultValue");
        defaultValue.setValues(Collections.singletonList("{}"));

        generator.getGenParameters().add(defaultValue);

        BodyGenerator gen = (BodyGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
        gen.setSpec(spec);
        gen.setDataDirPath(dataDirPath);
        gen.setOpenApiOperation(oasOperation);
        gen.setOperation("GET", operationPath);

        String value = gen.nextValueAsString(false);

        assertNotNull("The generator could not generate a body parameter", value);

        try {
            JsonNode body = (new ObjectMapper()).readTree(value);

            // Assert all fields
            assertTrue(body.get("numberParam").isNumber());
            assertTrue(body.get("numberParam").intValue() == 1 || body.get("numberParam").intValue() == 2);
            assertTrue(body.get("numberParam2").isTextual());
            assertEquals("stringExample", body.get("numberParam2").textValue());
            assertTrue(body.get("booleanParam").isBoolean());
            assertTrue(body.get("booleanParam").booleanValue() == true || body.get("booleanParam").booleanValue() == false);
            assertTrue(body.get("booleanParam2").isNull());
            assertEquals(null, body.get("booleanParam2").textValue());
            assertTrue(body.get("enumParam").isTextual());
            assertTrue(body.get("enumParam").textValue().equals("Review") || body.get("enumParam").textValue().equals("Request"));
            if (body.get("stringParam").isTextual())
                assertTrue(body.get("stringParam").textValue().equals("Example 1") || body.get("stringParam").textValue().equals("Example 2"));
            else if (body.get("stringParam").isNumber())
                assertEquals(1, body.get("stringParam").intValue());
            else if (body.get("stringParam").isBoolean())
                assertEquals(false, body.get("stringParam").booleanValue());
            else if (body.get("stringParam").isNull())
                assertEquals(null, body.get("stringParam").textValue());
            else
                fail("The property 'stringParam' should be either string, number, boolean or null");
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            fail("The body could not be parsed as a JSON");
        }
    }
}

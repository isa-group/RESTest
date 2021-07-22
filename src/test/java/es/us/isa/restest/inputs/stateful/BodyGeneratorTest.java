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
    public void bodyGenerationTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger_forTestSuite5.yaml");
        String operationPath = "/comments/{id}";
        Operation oasOperation = spec.getSpecification().getPaths().get(operationPath).getPut();
        String dataDirPath = "src/test/resources/jsonData/stateful_bodies";

        GenParameter defaultValue = new GenParameter();
        defaultValue.setName("defaultValue");
        defaultValue.setValues(Collections.singletonList("{}"));

        generator.getGenParameters().add(defaultValue);

        BodyGenerator gen = (BodyGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
        gen.setSpec(spec);
        gen.setDataDirPath(dataDirPath);

        String value = gen.nextValueAsString(oasOperation, operationPath, false);

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
}

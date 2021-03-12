package es.us.isa.restest.inputs.fuzzing;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.util.JSONManager;
import io.swagger.v3.oas.models.Operation;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.assertNotNull;

public class BodyGeneratorTest {

    Generator generator;

    @Before
    public void setUp() {
        generator = new Generator();
        generator.setType("StatefulBodyGenerator");
        generator.setGenParameters(new ArrayList<>());
    }

    @Test
    public void statefulBodyGenerationTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger.yaml");
        Operation oasOperation = spec.getSpecification().getPaths().get("/comments").getPost();
        String dataDirPath = "src/test/resources/jsonData";

        GenParameter defaultValue = new GenParameter();
        defaultValue.setName("defaultValue");
        defaultValue.setValues(Collections.singletonList("{}"));

        generator.getGenParameters().add(defaultValue);

        BodyGenerator gen = (BodyGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
        gen.setSpec(spec);
        gen.setDataDirPath(dataDirPath);

        String value = gen.nextValueAsString(oasOperation, "/comments", true);

        assertNotNull("The generator cannot create a body", value);

        Object jsonObject = JSONManager.readJSONFromString(value);

        assertNotNull("The body generated is not valid", jsonObject);
    }

    @Test
    public void statefulBodyGenerationGettingDataFromExampleTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger_forTestSuite3.yaml");
        Operation oasOperation = spec.getSpecification().getPaths().get("/comments").getPost();
        String dataDirPath = "";

        GenParameter defaultValue = new GenParameter();
        defaultValue.setName("defaultValue");
        defaultValue.setValues(Collections.singletonList("{}"));

        generator.getGenParameters().add(defaultValue);

        BodyGenerator gen = (BodyGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
        gen.setSpec(spec);
        gen.setDataDirPath(dataDirPath);

        String value = gen.nextValueAsString(oasOperation, "/comments", true);

        assertNotNull("The generator cannot create a body", value);

        Object jsonObject = JSONManager.readJSONFromString(value);

        assertNotNull("The body generated is not valid", jsonObject);
    }

    @Test
    public void statefulBodyGenerationWithDefaultDataNoMutationTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger_forTestSuite4.yaml");
        Operation oasOperation = spec.getSpecification().getPaths().get("/comments").getPost();
        String dataDirPath = "";

        GenParameter defaultValue = new GenParameter();
        defaultValue.setName("defaultValue");
        defaultValue.setValues(Collections.singletonList("{}"));

        generator.getGenParameters().add(defaultValue);

        BodyGenerator gen = (BodyGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
        gen.setSpec(spec);
        gen.setDataDirPath(dataDirPath);

        String value = gen.nextValueAsString(oasOperation, "/comments", false);

        assertNotNull("The generator cannot create a body", value);

        Object jsonObject = JSONManager.readJSONFromString(value);

        assertNotNull("The body generated is not valid", jsonObject);
    }
}

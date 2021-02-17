package es.us.isa.restest.inputs.stateful;

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

public class StatefulBodyGeneratorTest {

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

        StatefulBodyGenerator gen = (StatefulBodyGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
        gen.setSpec(spec);
        gen.setDataDirPath(dataDirPath);

        String value = gen.nextValueAsString(oasOperation, "/comments");

        assertNotNull("The generator cannot create a body", value);

        Object jsonObject = JSONManager.readJSONFromString(value);

        assertNotNull("The body generated is not valid", jsonObject);
    }
}

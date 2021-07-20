package es.us.isa.restest.inputs.fuzzing;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import es.us.isa.restest.specification.OpenAPISpecification;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ParameterGeneratorTest {

    Generator generator;

    @Before
    public void setUp() {
        generator = new Generator();
        generator.setType("ParameterGenerator");
        generator.setGenParameters(new ArrayList<>());
    }

    @Test
    public void parameterGenerationTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger.yaml");
        String dataDirPath = "src/test/resources/jsonData";
        String parameter = "id";

        GenParameter defaultValue = new GenParameter();
        defaultValue.setName("defaultValue");
        defaultValue.setValues(Collections.singletonList("{}"));

        generator.getGenParameters().add(defaultValue);

        ParameterGenerator gen = (ParameterGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
        gen.setSpec(spec);
        gen.setDataDirPath(dataDirPath);
        gen.setParameterName(parameter);

        String value = gen.nextValueAsString("/comments");

        assertNotNull("The generator cannot find an 'id' parameter", value);
        assertTrue("The id generated is not valid", value.matches("c[0-9]+"));
    }

    @Test
    public void parameterGenerationDepthLevel1Test() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger.yaml");
        String dataDirPath = "src/test/resources/jsonData";
        String parameter = "id";

        GenParameter defaultValue = new GenParameter();
        defaultValue.setName("defaultValue");
        defaultValue.setValues(Collections.singletonList("{}"));

        generator.getGenParameters().add(defaultValue);

        ParameterGenerator gen = (ParameterGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
        gen.setSpec(spec);
        gen.setDataDirPath(dataDirPath);
        gen.setParameterName(parameter);

        String value = gen.nextValueAsString("/comments/{id}");

        assertNotNull("The generator cannot find an 'id' parameter", value);
        assertTrue("The id generated is not valid", value.matches("c[0-9]+"));
    }
}

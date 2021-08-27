package es.us.isa.restest.inputs.stateful;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import es.us.isa.restest.specification.OpenAPISpecification;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.*;

public class ParameterGeneratorTest {

    Generator generator;
    ParameterGenerator gen;

    @Before
    public void setUp() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger.yaml");

        generator = new Generator();
        generator.setType("ParameterGenerator");
        generator.setGenParameters(new ArrayList<>());

        GenParameter defaultValue = new GenParameter();
        defaultValue.setName("defaultValue");
        defaultValue.setValues(Collections.singletonList("{}"));

        generator.getGenParameters().add(defaultValue);

        gen = (ParameterGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
        gen.setSpec(spec);
        gen.setOperation("GET", "/comments");
    }

    @Test
    public void parameterGenerationTest() {
        String dataDirPath = "src/test/resources/jsonData";
        String parameter = "id";

        gen.setDataDirPath(dataDirPath);
        gen.setParameterName(parameter);
        gen.setOperation("GET", "/comments");

        String value = gen.nextValueAsString();

        assertNotNull("The generator cannot find an 'id' parameter", value);
        assertTrue("The id generated is not valid", value.matches("c[0-9]+"));
    }

    @Test
    public void parameterGenerationDepthLevel1Test() {
        String dataDirPath = "src/test/resources/jsonData";
        String parameter = "id";

        gen.setDataDirPath(dataDirPath);
        gen.setParameterName(parameter);
        gen.setOperation("GET", "/comments");

        String value = gen.nextValueAsString();

        assertNotNull("The generator cannot find an 'id' parameter", value);
        assertTrue("The id generated is not valid", value.matches("c[0-9]+"));
    }

    @Test
    public void parameterGenerationSameOperationAltParamName() {
        String dataDirPath = "src/test/resources/jsonData/stateful_body2";
        String parameter = "id";
        String altParamName = "userName";

        gen.setDataDirPath(dataDirPath);
        gen.setParameterName(parameter);
        gen.setOperation("GET", "/comments");
        gen.setAltParameterName(altParamName);

        String value = gen.nextValueAsString();

        assertNotNull("The generator cannot find an 'id' parameter", value);
        assertEquals("The id generated is not valid", "markSpecter", value);
    }

    @Test
    public void parameterGenerationAltOperationPathSameParamName() {
        String dataDirPath = "src/test/resources/jsonData/stateful_body2";
        String parameter = "tags.category";
        String altOperationPath = "/messages";

        gen.setDataDirPath(dataDirPath);
        gen.setParameterName(parameter);
        gen.setOperation("GET", "/comments");
        gen.setAltOperationPath(altOperationPath);

        String value = gen.nextValueAsString();

        assertNotNull("The generator cannot find a 'tags.category' parameter", value);
        assertEquals("The tags.category generated is not valid", "category2", value);
    }

    @Test
    public void parameterGenerationAltOperationPathAltParamName() {
        String dataDirPath = "src/test/resources/jsonData/stateful_body2";
        String parameter = "tags.category";
        String altParamName = "updates.last_updated";
        String altOperationPath = "/messages";

        gen.setDataDirPath(dataDirPath);
        gen.setParameterName(parameter);
        gen.setAltParameterName(altParamName);
        gen.setOperation("GET", "/comments");
        gen.setAltOperationPath(altOperationPath);

        String value = gen.nextValueAsString();

        assertNotNull("The generator cannot find an 'updates.last_updated' parameter", value);
        assertEquals("The updates.last_updated generated is not valid", "2014-04-16T20:44:53.950", value);
    }

    @Test
    public void parameterGenerationAltOperationPathNonExistingAltParamName() {
        String dataDirPath = "src/test/resources/jsonData/stateful_body2";
        String parameter = "tags.category";
        String altParamName = "nonExistingProperty";
        String altOperationPath = "/messages";

        gen.setDataDirPath(dataDirPath);
        gen.setParameterName(parameter);
        gen.setAltParameterName(altParamName);
        gen.setOperation("GET", "/comments");
        gen.setAltOperationPath(altOperationPath);

        String value = gen.nextValueAsString();

        assertNotNull("The generator cannot find a 'nonExistingProperty' parameter", value);
        assertEquals("The nonExistingProperty generated is not valid", "{}", value);
    }

    @Test
    public void parameterGenerationNonExistingAltOperationPathAltParamName() {
        String dataDirPath = "src/test/resources/jsonData/stateful_body2";
        String parameter = "tags.category";
        String altParamName = "uniquePropertyInThisOperation";
        String altOperationPath = "/nonExistingPath";

        gen.setDataDirPath(dataDirPath);
        gen.setParameterName(parameter);
        gen.setAltParameterName(altParamName);
        gen.setOperation("GET", "/comments");
        gen.setAltOperationPath(altOperationPath);

        String value = gen.nextValueAsString();

        assertNotNull("The generator cannot find a 'uniquePropertyInThisOperation' parameter", value);
        assertEquals("The uniquePropertyInThisOperation generated is not valid", "uniqueValue", value);
    }

    @Test
    public void parameterGenerationNonExistingAltOperationPathNonExistingAltParamName() {
        String dataDirPath = "src/test/resources/jsonData/stateful_body2";
        String parameter = "tags.category";
        String altParamName = "nonExistingProperty";
        String altOperationPath = "/nonExistingPath";

        gen.setDataDirPath(dataDirPath);
        gen.setParameterName(parameter);
        gen.setAltParameterName(altParamName);
        gen.setOperation("GET", "/comments");
        gen.setAltOperationPath(altOperationPath);

        String value = gen.nextValueAsString();

        assertNotNull("The generator cannot find a 'nonExistingProperty' parameter", value);
        assertEquals("The nonExistingProperty generated is not valid", "{}", value);
    }
}

package es.us.isa.restest.inputs.stateful;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.util.JSONManager;
import io.swagger.v3.oas.models.Operation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertNotNull;

@RunWith(Parameterized.class)
public class BodyGeneratorParameterizedTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {"src/test/resources/Comments/swagger.yaml", "src/test/resources/jsonData", "/comments" ,true},
                {"src/test/resources/Comments/swagger_forTestSuite3.yaml", "", "/comments", true},
                {"src/test/resources/Comments/swagger_forTestSuite4.yaml", "", "/comments", false},
                {"src/test/resources/Comments/swagger_forTestSuite6.yaml", "", "/comments/multiple", false},
                {"src/test/resources/Traccar/openapi.yml", "", "/devices", false},
                {"src/test/resources/Graphhopper/openapi.yaml", "", "/route", false}
        });
    }

    private final Generator generator;
    private final String specPath;
    private final String dataDirPath;
    private final String operationPath;
    private final boolean mutate;

    public BodyGeneratorParameterizedTest(String specPath, String dataDirPath, String operationPath, boolean mutate) {
        generator = new Generator();
        generator.setType("BodyGenerator");
        generator.setGenParameters(new ArrayList<>());

        this.specPath = specPath;
        this.dataDirPath = dataDirPath;
        this.operationPath = operationPath;
        this.mutate = mutate;
    }

    @Test
    public void bodyGenerationTest() {
        OpenAPISpecification spec = new OpenAPISpecification(specPath);
        Operation oasOperation = spec.getSpecification().getPaths().get(operationPath).getPost();

        GenParameter defaultValue = new GenParameter();
        defaultValue.setName("defaultValue");
        defaultValue.setValues(Collections.singletonList("{}"));

        generator.getGenParameters().add(defaultValue);

        BodyGenerator gen = (BodyGenerator) TestDataGeneratorFactory.createTestDataGenerator(generator);
        gen.setSpec(spec);
        gen.setDataDirPath(dataDirPath);
        gen.setOpenApiOperation(oasOperation);
        gen.setOperation("GET", operationPath);

        String value = gen.nextValueAsString(mutate);

        assertNotNull("The generator cannot create a body", value);

        Object jsonObject = JSONManager.readJSONFromString(value);

        assertNotNull("The body generated is not valid", jsonObject);
    }
}

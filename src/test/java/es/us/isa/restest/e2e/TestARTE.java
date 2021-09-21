package es.us.isa.restest.e2e;

import es.us.isa.restest.configuration.pojos.*;
import es.us.isa.restest.inputs.semantic.ARTEInputGenerator;
import es.us.isa.restest.inputs.semantic.objects.SemanticOperation;
import es.us.isa.restest.inputs.semantic.objects.SemanticParameter;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.util.PropertyManager;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.inputs.semantic.objects.SemanticOperation.getSemanticOperationsWithValuesFromPreviousIterations;
import static es.us.isa.restest.inputs.semantic.regexGenerator.RegexGeneratorUtils.getCsvPaths;
import static es.us.isa.restest.inputs.semantic.regexGenerator.RegexGeneratorUtils.readCsv;
import static es.us.isa.restest.util.FileManager.checkIfExists;
import static es.us.isa.restest.util.FileManager.deleteFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestARTE {

    @Before
    public void resetSingleton() throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field properties = PropertyManager.class.getDeclaredField("properties");
        properties.setAccessible(true);
        properties.set(null, null);

        Field experimentProperties = PropertyManager.class.getDeclaredField("experimentProperties");
        experimentProperties.setAccessible(true);
        experimentProperties.set(null, null);

        Field csvPath = ARTEInputGenerator.class.getDeclaredField("csvPath");
        csvPath.setAccessible(true);
        csvPath.set(null, null);
    }

    @Test
    public void testRunARTE() throws IOException {

        String basePath = "src/test/resources/semanticAPITests/ClimaCell/";
        String propertiesPath = basePath + "climacell.properties";
        String minSupport = "20";
        String threshold = "100";
        String limit = "30";

        String [] args = { propertiesPath, minSupport, threshold, limit };

        ARTEInputGenerator.main(args);

        String testConfSemanticPath = basePath + "testConfSemantic.yaml";
        String swaggerPath = basePath + "ClimaCell.yaml";
        String testConfOriginalPath = basePath + "testConf.yaml";

        TestConfigurationObject testConfSemantic = loadConfiguration(testConfSemanticPath, new OpenAPISpecification(swaggerPath));

        // Existence of testConfSemantic
        assertTrue(checkIfExists(testConfSemanticPath));

        // Size of the CSV files with TestData
        List<Operation> operationList = testConfSemantic.getTestConfiguration().getOperations();

        Set<SemanticOperation> semanticOperations = getSemanticOperationsWithValuesFromPreviousIterations(operationList, "ClimaCell");

        assertEquals(2, semanticOperations.size());

        for(SemanticOperation semanticOperation: semanticOperations) {

            Set<SemanticParameter> semanticParameters = semanticOperation.getSemanticParameters();
            assertEquals(2, semanticParameters.size());

            for (SemanticParameter semanticParameter: semanticParameters) {
                // Get CSV path
                String csvValuesPath = getCsvPaths(semanticParameter.getTestParameter()).get(0);

                // Assert it exists
                assertTrue(checkIfExists(csvValuesPath));


                // Check CSV size
                List<String> valuesFromCSV =  readCsv(csvValuesPath);
                assertEquals(Integer.parseInt(limit), valuesFromCSV.size());

            }
        }

    }

    @Test
    public void testRunARTEWithoutParameterLimit() throws IOException {

        String basePath = "src/test/resources/semanticAPITests/ClimaCell/";
        String propertiesPath = basePath + "climacell.properties";
        String minSupport = "20";
        String threshold = "100";
        String limit = null;

        String [] args = { propertiesPath, minSupport, threshold, limit };

        ARTEInputGenerator.main(args);

        String testConfSemanticPath = basePath + "testConfSemantic.yaml";
        String swaggerPath = basePath + "ClimaCell.yaml";
        String testConfOriginalPath = basePath + "testConf.yaml";
        TestConfigurationObject testConf = loadConfiguration(testConfOriginalPath, new OpenAPISpecification(swaggerPath));

        // Existence of testConfSemantic
        assertTrue(checkIfExists(testConfOriginalPath));
        assertTrue(checkIfExists(testConfSemanticPath));

        TestConfigurationObject testConfSemantic = loadConfiguration(testConfSemanticPath, new OpenAPISpecification(swaggerPath));
        // Size of the CSV files with TestData
        List<Operation> operationList = testConfSemantic.getTestConfiguration().getOperations();

        Set<SemanticOperation> semanticOperations = getSemanticOperationsWithValuesFromPreviousIterations(operationList, "ClimaCell");

        assertEquals(2, semanticOperations.size());

        for(SemanticOperation semanticOperation: semanticOperations) {

            Set<SemanticParameter> semanticParameters = semanticOperation.getSemanticParameters();
            assertEquals(2, semanticParameters.size());

            for (SemanticParameter semanticParameter: semanticParameters) {
                // Get CSV path
                String csvValuesPath = getCsvPaths(semanticParameter.getTestParameter()).get(0);

                // Assert it exists
                assertTrue(checkIfExists(csvValuesPath));

            }
        }

    }

    @Test
    public void testRunARTEOpsWithoutParametersAndRequiredParamsWithoutValues() throws IOException {

        String basePath = "src/test/resources/restest-test-resources/";
        String propertiesPath = basePath + "restcountries_arte_props.properties";
        String minSupport = "20";
        String threshold = "100";
        String limit = "30";

        String[] args = {propertiesPath, minSupport, threshold, limit};

        ARTEInputGenerator.main(args);

        String testConfSemanticPath = basePath + "testConfSemantic.yaml";
        String swaggerPath = basePath + "restcountries_arte_openapi.yaml";
        String testConfOriginalPath = basePath + "restcountries_arte_testConf.yaml";

        // Existence of testConfSemantic
        assertTrue(checkIfExists(testConfOriginalPath));
        assertTrue(checkIfExists(testConfSemanticPath));

        // Assertions on parameter regionalBloc of testConfSemantic, which should contain fuzzing values
        TestConfigurationObject testConfSemantic = loadConfiguration(testConfSemanticPath, new OpenAPISpecification(swaggerPath));
        List<Operation> operationList = testConfSemantic.getTestConfiguration().getOperations();
        Generator fuzzingGenerator = operationList.get(operationList.size()-1).getTestParameters().get(1).getGenerators().get(0);

        assertEquals("RandomInputValue", fuzzingGenerator.getType());
        assertEquals("values", fuzzingGenerator.getGenParameters().get(0).getName());
        assertEquals(5, fuzzingGenerator.getGenParameters().get(0).getValues().size());
        assertTrue(fuzzingGenerator.getGenParameters().get(0).getValues().containsAll(Arrays.asList("null", "", "\\0", "one space", "randomString")));

        deleteFile(testConfSemanticPath);
        deleteFile(basePath + "time_ARTE.csv");
    }

    @Test
    public void testRunARTEFormDataParameters() throws IOException {

        String basePath = "src/test/resources/restest-test-resources/";
        String propertiesPath = basePath + "languagetool_arte_props.properties";
        String minSupport = "20";
        String threshold = "100";
        String limit = "30";

        String[] args = {propertiesPath, minSupport, threshold, limit};

        ARTEInputGenerator.main(args);

        String testConfSemanticPath = basePath + "testConfSemantic.yaml";
        String swaggerPath = basePath + "languagetool_arte_openapi.json";
        String testConfOriginalPath = basePath + "languagetool_arte_testConf.yaml";

        // Existence of testConfSemantic
        assertTrue(checkIfExists(testConfOriginalPath));
        assertTrue(checkIfExists(testConfSemanticPath));

        // Existence of time_ARTE.csv and CSV files
        assertTrue(checkIfExists(basePath + "time_ARTE.csv"));

        TestConfigurationObject testConfSemantic = loadConfiguration(testConfSemanticPath, new OpenAPISpecification(swaggerPath));
        Operation operation = testConfSemantic.getTestConfiguration().getOperations().get(0);
        TestParameter languageParam = operation.getTestParameters().get(2);
        TestParameter motherTongueParam = operation.getTestParameters().get(3);
        TestParameter preferredVariantsParam = operation.getTestParameters().get(4);
        String languageCsvPath = languageParam.getGenerators().get(0).getGenParameters().get(0).getValues().get(0);
        String motherTongueCsvPath = motherTongueParam.getGenerators().get(0).getGenParameters().get(0).getValues().get(0);
        String preferredVariantsCsvPath = preferredVariantsParam.getGenerators().get(0).getGenParameters().get(0).getValues().get(0);

        assertTrue(checkIfExists(languageCsvPath));
        assertTrue(checkIfExists(motherTongueCsvPath));
        assertTrue(checkIfExists(preferredVariantsCsvPath));

        deleteFile(testConfSemanticPath);
        deleteFile(basePath + "time_ARTE.csv");
        deleteFile(languageCsvPath);
        deleteFile(motherTongueCsvPath);
        deleteFile(preferredVariantsCsvPath);
    }

}

package es.us.isa.restest.e2e;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.SemanticOperation;
import es.us.isa.restest.configuration.pojos.SemanticParameter;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.inputs.semantic.ARTEInputGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.configuration.pojos.SemanticOperation.getSemanticOperationsWithValuesFromPreviousIterations;
import static es.us.isa.restest.inputs.semantic.regexGenerator.RegexGeneratorUtils.getCsvPaths;
import static es.us.isa.restest.inputs.semantic.regexGenerator.RegexGeneratorUtils.readCsv;
import static es.us.isa.restest.util.FileManager.checkIfExists;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestARTE {

    //     private static final String propertiesFilePath = "src/test/resources/semanticAPITests/ClimaCell/climacell.properties";
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
        String swaggerPath = basePath + "swagger.yaml";
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
    public void testRunARTEWithoutSemanticParameters() {

        String propertiesPathSemantic = "src/test/resources/semanticAPITests/DHL/dhl_semantic.properties";
        String[] argsARTE = {propertiesPathSemantic, "20", "50", "30"};

        ARTEInputGenerator.main(argsARTE);

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
        String swaggerPath = basePath + "swagger.yaml";
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

}

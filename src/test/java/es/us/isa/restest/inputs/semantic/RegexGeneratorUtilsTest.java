package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.pojos.*;
import es.us.isa.restest.inputs.semantic.objects.SemanticOperation;
import es.us.isa.restest.inputs.semantic.objects.SemanticParameter;
import es.us.isa.restest.specification.OpenAPISpecification;
import org.junit.Test;

import java.util.*;
import java.util.regex.Pattern;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.PREDICATES;
import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.RANDOM_INPUT_VALUE;
import static es.us.isa.restest.inputs.semantic.objects.SemanticOperation.getSemanticOperationsWithValuesFromPreviousIterations;
import static es.us.isa.restest.inputs.semantic.ARTEInputGenerator.LIMIT;
import static es.us.isa.restest.inputs.semantic.regexGenerator.RegexGeneratorUtils.*;
import static es.us.isa.restest.main.TestGenerationAndExecution.getExperimentName;
import static es.us.isa.restest.util.CSVManager.collectionToCSV;
import static org.junit.Assert.*;

public class RegexGeneratorUtilsTest {

    @Test
    public void testGetCsvPaths() {

        String confPath = "src/test/resources/semanticAPITests/OMDb/testConfSemantic.yaml";
        OpenAPISpecification specification = new OpenAPISpecification("src/test/resources/semanticAPITests/OMDb/swagger.yaml");
        TestConfigurationObject conf = loadConfiguration(confPath, specification);

        List<Operation> operations = conf.getTestConfiguration().getOperations();
        List<SemanticOperation> semanticOperations = new ArrayList<>(getSemanticOperationsWithValuesFromPreviousIterations(operations, getExperimentName()));

        Set<SemanticParameter> semanticParameters = semanticOperations.get(0).getSemanticParameters();

        // Parameter title (t)
        SemanticParameter semanticParameterTitle = semanticParameters.stream()
                .filter( x-> x.getTestParameter().getName().equals("t"))
                .findFirst().orElseThrow(()-> new NullPointerException("Parameter not found"));

        List<String> csvPaths = getCsvPaths(semanticParameterTitle.getTestParameter());

        assertEquals("Wrong csvPaths size", 1, csvPaths.size());
        assertEquals("Wrong csvPaths", "src/main/resources/TestData/Generated/OMDb API/search_t.csv", csvPaths.get(0));

    }

    @Test
    public void testUpdateCsvWithRegex() {

        String csvPath = "src/test/resources/semanticAPITests/OMDb/sampleCSVRegexFiltering.csv";
        String regex = "^\\w[^\\d]$";

        // Read initial values from csv
        Set<String> initialValues = new HashSet<>(readCsv(csvPath));

        // Update csv with regex
        updateCsvWithRegex(csvPath, regex);

        // Read csv and check that all the values match the regex
        Set<String> newValues = new HashSet<>(readCsv(csvPath));

        // 4 values remaining after filtering
        assertEquals("The size of the new dataset is incorrect", 4, newValues.size());
        // The filtered values must satisfy the regex
        assertTrue("Not all the values in the filtered csv satisfy the regex", newValues.stream().allMatch(Pattern.compile(regex).asPredicate()));


        // Rewrite csv with old values (before executing the test)
        collectionToCSV(csvPath, initialValues);

    }


    @Test
    public void testAddResultsToCSV() {

        String csvPath = "src/test/resources/semanticAPITests/OMDb/sampleCSVRegexFiltering.csv";

        List<GenParameter> genParameters = new ArrayList<>();

        GenParameter genParameterPredicates = new GenParameter();
        genParameterPredicates.setName(PREDICATES);
        genParameterPredicates.setValues(Collections.singletonList("http://dbpedia.org/ontology/countryCode"));
        genParameters.add(genParameterPredicates);

        GenParameter genParameterCSV = new GenParameter();
        genParameterCSV.setName("csv");
        genParameterCSV.setValues(Collections.singletonList(csvPath));
        genParameters.add(genParameterCSV);

        Generator generator = new Generator();
        generator.setType(RANDOM_INPUT_VALUE);
        generator.setValid(true);
        generator.setGenParameters(genParameters);

        TestParameter testParameter = new TestParameter();
        testParameter.setName("name");
        testParameter.setIn("query");
        testParameter.setWeight(0.5f);
        testParameter.setGenerators(Collections.singletonList(generator));

        SemanticParameter semanticParameter = new SemanticParameter(testParameter);

        List<String> initialValues = readCsv(csvPath);

        List<String> newValues = new ArrayList<>();
        if(LIMIT != null) {
            for(int i = 0; i < LIMIT + 10; i++) {
                newValues.add(Integer.toString(i));
            }
        } else {
            newValues.add("1");
            newValues.add("2");
            newValues.add("3");
        }

        addResultsToCSV(semanticParameter, new HashSet<>(newValues));

        List<String> updatedCSV =  readCsv(csvPath);
        // Check that the new list of values contains the original list
        assertTrue("The updated CSV must contain the initial CSV", updatedCSV.containsAll(initialValues));

        // Check that the size of the new list is equal to initialValues.size() + LIMIT newValues.size in other case
        if(LIMIT != null) {
            assertEquals("Incorrect size of the updated CSV", updatedCSV.size(), initialValues.size() + LIMIT);
        } else {
            assertEquals("Incorrect size of the updated CSV", updatedCSV.size(), initialValues.size() + newValues.size());
        }

        // Rewrite initial values
        collectionToCSV(csvPath, initialValues);

    }

}

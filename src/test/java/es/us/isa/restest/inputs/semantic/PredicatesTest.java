package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.pojos.*;
import es.us.isa.restest.inputs.semantic.objects.SemanticOperation;
import es.us.isa.restest.inputs.semantic.objects.SemanticParameter;
import es.us.isa.restest.specification.OpenAPISpecification;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.SEMANTIC_PARAMETER;
import static es.us.isa.restest.inputs.semantic.objects.SemanticOperation.getSemanticOperationsWithValuesFromPreviousIterations;
import static es.us.isa.restest.inputs.semantic.ARTEInputGenerator.getSemanticOperations;
import static es.us.isa.restest.inputs.semantic.NLPUtils.extractPredicateCandidatesFromDescription;
import static es.us.isa.restest.inputs.semantic.Predicates.*;
import static es.us.isa.restest.inputs.semantic.SPARQLUtils.getParameterValues;
import static es.us.isa.restest.main.TestGenerationAndExecution.getExperimentName;
import static org.junit.Assert.*;

public class PredicatesTest {

    @Test
    public void testExtractPredicateCandidatesFromDescriptionRule1() {
        String parameterName1 = "currency";
        String parameterDescription1 = "A valid currency code";

        Map<Double, Set<String>> descriptionCandidates = extractPredicateCandidatesFromDescription(parameterName1, parameterDescription1);

        assertEquals("Error extracting predicate candidates from description", 1, descriptionCandidates.keySet().size());
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(3.0).contains("currencycode"));

    }

    @Test
    public void testComputeSupportOfPredicate() {
        String predicate = "http://dbpedia.org/ontology/countryCode";

        TestParameter testParameter = new TestParameter();
        testParameter.setName("Name");
        testParameter.setIn("query");
        testParameter.setWeight(0.5f);

        Generator generator = new Generator();
        generator.setType(SEMANTIC_PARAMETER);
        generator.setValid(true);
        generator.setGenParameters(new ArrayList<>());

        testParameter.setGenerators(Collections.singletonList(generator));

        Integer support = computeSupportOfPredicate(predicate, testParameter);

        assertTrue("Error computing support", support > 0);


    }

    @Test
    public void testExecutePredicateSPARQLQuery() {

        // Generate the query
        String predicateQuery = generatePredicateQuery("currencyCode");

        String predicateToIgnore = "http://dbpedia.org/ontology/currencyCode";

        TestParameter testParameter = new TestParameter();
        testParameter.setName("Name");
        testParameter.setIn("query");
        testParameter.setWeight(0.5f);

        Generator generator = new Generator();
        generator.setType(SEMANTIC_PARAMETER);
        generator.setValid(true);

        GenParameter genParameter = new GenParameter();
        genParameter.setName("regExp");
        genParameter.setValues(Collections.singletonList("^[A-Z]{3}$"));
        generator.setGenParameters(Collections.singletonList(genParameter));

        testParameter.setGenerators(Collections.singletonList(generator));


        String obtainedPredicate = executePredicateSPARQLQuery(predicateQuery, testParameter, Collections.singletonList(predicateToIgnore));

        assertNotNull("Obtained predicate must not be null", obtainedPredicate);
        assertNotEquals("Predicate to ignore not ignored", obtainedPredicate, predicateToIgnore);


    }

    @Test
    public void testGetPredicatesOfSingleParameter() {
        String parameterName = "iata_icao";

        String predicateToIgnore = "http://dbpedia.org/property/iata";

        TestParameter testParameter = new TestParameter();
        testParameter.setName("Name");
        testParameter.setIn("query");
        testParameter.setWeight(0.5f);

        Generator generator = new Generator();
        generator.setType(SEMANTIC_PARAMETER);
        generator.setValid(true);

        GenParameter genParameter = new GenParameter();
        genParameter.setName("regExp");
        genParameter.setValues(Collections.singletonList("^[A-Z]{3}$"));
        generator.setGenParameters(Collections.singletonList(genParameter));

        testParameter.setGenerators(Collections.singletonList(generator));

        Set<String> obtainedPredicates = getPredicatesOfSingleParameter(parameterName, testParameter, Collections.singletonList(predicateToIgnore));

        // Check list is not empty
        assertFalse("No predicates found", obtainedPredicates.isEmpty());
        // Check that predicate to ignore is not in the list
        assertFalse("The predicate to ignore must be ignored", obtainedPredicates.contains(predicateToIgnore));

    }

    @Test
    public void testGetPredicatesFromDescription() {
        Map<Double, Set<String>> descriptionCandidates = new HashMap<>();
        Set<String> values2 = new HashSet<>();
        String keyword = "currencyCode";
        values2.add(keyword);


        descriptionCandidates.put(1.0, Collections.singleton("thisPredicatesDoesNotExists"));
        descriptionCandidates.put(2.0, values2);



        String predicateToIgnore = "http://dbpedia.org/ontology/currencyCode";

        TestParameter testParameter = new TestParameter();
        testParameter.setName("Name");
        testParameter.setIn("query");
        testParameter.setWeight(0.5f);

        Generator generator = new Generator();
        generator.setType(SEMANTIC_PARAMETER);
        generator.setValid(true);

        GenParameter genParameter = new GenParameter();
        genParameter.setName("regExp");
        genParameter.setValues(Collections.singletonList("^[A-Z]{3}$"));
        generator.setGenParameters(Collections.singletonList(genParameter));

        testParameter.setGenerators(Collections.singletonList(generator));

        // Check that the obtained predicate contains the desired keyword
        // Check that the obtained predicate is not the predicate to discard
        String obtainedPredicate = getPredicatesFromDescription(descriptionCandidates, testParameter, Collections.singletonList(predicateToIgnore));

        assertTrue("The obtained predicate does not match the keyword", obtainedPredicate.contains(keyword));
        assertNotEquals("The predicate to ignore must be ignored", predicateToIgnore, obtainedPredicate);
    }

    @Test
    public void testGetPredicates() {

        String confPath = "src/test/resources/semanticAPITests/OMDb/testConfSemantic.yaml";
        OpenAPISpecification specification = new OpenAPISpecification("src/test/resources/semanticAPITests/OMDb/swagger.yaml");
        TestConfigurationObject conf = loadConfiguration(confPath, specification);
        String regex = "^\\w*$";

        List<Operation> operations = conf.getTestConfiguration().getOperations();
        Set<SemanticOperation> semanticOperations = getSemanticOperationsWithValuesFromPreviousIterations(operations, getExperimentName());

        for(SemanticOperation semanticOperation: semanticOperations) {

            Set<SemanticParameter> semanticParameters = semanticOperation.getSemanticParameters();

            assertEquals("Incorrect number of semantic operations", 3, semanticParameters.size());

            for(SemanticParameter semanticParameter: semanticParameters) {
                Set<String> oldPredicates = semanticParameter.getPredicates();
                Set<String> newPredicates = getPredicates(semanticOperation, semanticParameter, regex, specification);
                assertFalse("The new predicates must be different from the previous ones", newPredicates.containsAll(oldPredicates));
            }
        }
    }

    @Test
    public void testSetPredicatesAndGetParameterValues() {
        OpenAPISpecification specification = new OpenAPISpecification("src/test/resources/semanticAPITests/OMDb/swagger.yaml");

        String confPath = "src/test/resources/semanticAPITests/OMDb/testConf.yaml";
        TestConfigurationObject conf = loadConfiguration(confPath, specification);
        Set<SemanticOperation> semanticOperations = getSemanticOperations(conf);

        // Get initial set of predicates
        for(SemanticOperation semanticOperation: semanticOperations) {

            setPredicates(semanticOperation, specification);

            for(SemanticParameter semanticParameter: semanticOperation.getSemanticParameters()) {
                assertFalse("No predicates found", semanticParameter.getPredicates().isEmpty());
                assertEquals("Wrong number of remaining tries", 0, semanticParameter.getNumberOfTriesToGenerateRegex());
            }

        }

        for(SemanticOperation semanticOperation: semanticOperations) {
            // Get initial set of values
            Map<String, Set<String>> results = new HashMap<>();

            try{
                results = getParameterValues(semanticOperation.getSemanticParameters().stream().filter(x->!x.getPredicates().isEmpty()).collect(Collectors.toSet()));
            }catch(Exception e){
                System.err.println(e.getMessage());
            }

            for(Set<String> valuesOfParameter: results.values()) {
                assertFalse("No values found for parameter", valuesOfParameter.isEmpty());
            }

            semanticOperation.updateSemanticParametersValues(results);

            for(SemanticParameter semanticParameter: semanticOperation.getSemanticParameters()) {
                assertFalse("No values added to the semantic parameter", semanticParameter.getValues().isEmpty());
            }

        }

    }



}

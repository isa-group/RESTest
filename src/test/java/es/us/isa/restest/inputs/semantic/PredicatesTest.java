package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.configuration.pojos.TestParameter;
import nu.xom.jaxen.util.SingletonList;
import org.chocosolver.solver.constraints.nary.nvalue.amnv.graph.G;
import org.junit.Test;

import java.util.*;

import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.GEN_PARAM_REG_EXP;
import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.SEMANTIC_PARAMETER;
import static es.us.isa.restest.inputs.semantic.NLPUtils.extractPredicateCandidatesFromDescription;
import static es.us.isa.restest.inputs.semantic.Predicates.*;
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
        Set<String> values = new HashSet<>();
        String keyword = "currencyCode";
        values.add(keyword);

        descriptionCandidates.put(1.0, values);



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



}

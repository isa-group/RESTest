package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.pojos.*;
import es.us.isa.restest.inputs.semantic.objects.SemanticOperation;
import es.us.isa.restest.inputs.semantic.objects.SemanticParameter;
import es.us.isa.restest.specification.OpenAPISpecification;
import org.javatuples.Pair;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.junit.Test;

import java.util.*;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.inputs.semantic.NLPUtils.posTagging;
import static es.us.isa.restest.inputs.semantic.Predicates.generatePredicateQuery;
import static es.us.isa.restest.inputs.semantic.SPARQLUtils.generateQuery;
import static es.us.isa.restest.inputs.semantic.ARTEInputGenerator.getSemanticOperations;
import static org.junit.Assert.*;

public class SPARQLQueryGenerationTest {

	@Test
	public void testSemanticOperationsRetrieval(){
		OpenAPISpecification specification = new OpenAPISpecification("src/test/resources/semanticAPITests/ClimaCell/ClimaCell.yaml");
		TestConfigurationObject conf = loadConfiguration("src/test/resources/semanticAPITests/ClimaCell/testConf.yaml", specification);

		Set<SemanticOperation> semanticOperations = getSemanticOperations(conf);
		assertEquals("Incorrect number of semantic operations", 2, semanticOperations.size());

		for(SemanticOperation semanticOperation: semanticOperations){
			assertEquals("Incorrect number of semantic parameters", 2, semanticOperation.getSemanticParameters().size());
		}

	}

	@Test
	public void testObtainParameterNameFromDescription(){
		String parameterName = "t";
		String parameterDescription = "The title of a movie";

		List<String> possibleNames = posTagging(parameterDescription, parameterName);

		assertEquals("Error obtaining complete parameter name", "title", possibleNames.get(0));

	}

	@Test
	public void testGeneratePredicateQuery(){
		String predicateQuery = generatePredicateQuery("keyword");

		String targetPredicateQuery = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
				"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
				"\n" +
				"SELECT distinct ?predicate where {\n" +
				"    ?predicate a rdf:Property\n" +
				"    OPTIONAL { ?predicate rdfs:label ?label }\n" +
				"\n" +
				"FILTER regex(str(?predicate), \"keyword\" , 'i')\n" +
				"}\n" +
				"order by strlen(str(?predicate)) \n";

		assertEquals("Error generating predicate query", predicateQuery, targetPredicateQuery);

		Query query = QueryFactory.create(predicateQuery);

		assertNotNull(query);
	}

	@Test
	public void testGetParameterValues(){

		OpenAPISpecification specification = new OpenAPISpecification("src/test/resources/semanticAPITests/ClimaCell/ClimaCell.yaml");
		TestConfigurationObject conf = loadConfiguration("src/test/resources/semanticAPITests/ClimaCell/testConf.yaml", specification);

		Set<SemanticOperation> semanticOperations = getSemanticOperations(conf);
		SemanticOperation semanticOperation = semanticOperations.stream().findFirst().orElse(null);
		assertNotNull(semanticOperation);

		Set<SemanticParameter> semanticParameters = semanticOperation.getSemanticParameters();

		for(SemanticParameter semanticParameter: semanticParameters){
			semanticParameter.setPredicates(Collections.singleton("http://dbpedia.org/ontology/" + semanticParameter.getTestParameter().getName()));
		}

		Pair<String, Map<String, String>> queryString = generateQuery(semanticParameters, false);

		Query query = QueryFactory.create(queryString.getValue0());
		assertNotNull(query);
	}

}

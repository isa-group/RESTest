package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.main.CreateTestConf;
import es.us.isa.restest.specification.OpenAPISpecification;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.*;
import org.apache.jena.sparql.engine.http.QueryEngineHTTP;


import java.util.*;
import es.us.isa.restest.configuration.pojos.TestParameter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static es.us.isa.restest.inputs.semantic.NLPUtils.posTagging;

public class Predicates {
    private static final Logger log = LogManager.getLogger(Predicates.class);

    // TODO: Consider the possibility of adding owl predicates
    // TODO: Add support/threshold
    // TODO: Add by combination of length/support
    // TODO: Add limit
    // TODO: Wordnet/Description in case the function returns no results
    public static Map<TestParameter, List<String>> getPredicates(SemanticOperation semanticOperation, OpenAPISpecification spec){
        Set<TestParameter> parameters = semanticOperation.getSemanticParameters().keySet();

        Map<TestParameter, List<String>> res = new HashMap<>();

        for(TestParameter p: parameters){

            String parameterName = p.getName();
            log.info("Obtaining predicates of parameter {}", parameterName);

            // If the paramater name is only a character, compare with description
            if(parameterName.length() == 1){
                PathItem pathItem = spec.getSpecification().getPaths().get(semanticOperation.getOperationPath());
                String description = getParameterDescription(pathItem, parameterName, semanticOperation.getOperationMethod());
                parameterName =  posTagging(description, p.getName()).get(0);
            }

            List<String> predicates = getPredicatesOfSingleParameter(parameterName);
            res.put(p, predicates);
        }
        return res;
    }


    public static List<String> getPredicatesOfSingleParameter(String parameterName){

        // Query creation
        String queryString = generatePredicateQuery(parameterName);

        // Query execution
        List<String> res = executePredicateSPARQLQuery(queryString);

        if(res.size() < 5){
            String[] words = parameterName.split("_");
            // If snake_case
            if(words.length > 1){
                // Join words
                String newQuery = generatePredicateQuery(String.join("", words));
                res.addAll(executePredicateSPARQLQuery(newQuery));

                if(res.size() <5) {
                    // Execute one query for each word in snake_case
                    for(String word: words){
                        String query = generatePredicateQuery(word);
                        res.addAll(executePredicateSPARQLQuery(query));
                    }
                }

            }

            // If camelCase
            String[] wordsCamel = parameterName.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
            if(res.size() < 5 && wordsCamel.length >1){
                // Execute one query for each word in camelCase
                for(String word: wordsCamel){
                    String query = generatePredicateQuery(word);
                    res.addAll(executePredicateSPARQLQuery(query));
                }

            }
        }

        System.out.println(res);
        return res;
    }


    public static String generatePredicateQuery(String parameterName){

        String queryString = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                "\n" +
                "SELECT distinct ?predicate where {\n" +
                "    ?predicate a rdf:Property\n" +
                "    OPTIONAL { ?predicate rdfs:label ?label }\n" +
                "\n" +
                "FILTER regex(str(?predicate), \"" + parameterName +  "\" , 'i')\n" +
                "}\n" +
                "order by strlen(str(?predicate)) " +
                "\n";

        return queryString;
    }


    public static List<String> executePredicateSPARQLQuery(String queryString){
        List<String> res = new ArrayList<>();

        Query query = QueryFactory.create(queryString);
        QueryExecution qexec = QueryExecutionFactory.sparqlService("http://dbpedia.org/sparql", query);
        ((QueryEngineHTTP)qexec).addParam("timeout", "10000");

        // Execute query
        int iCount = 0;
        ResultSet rs = qexec.execSelect();
        while (rs.hasNext() && iCount<5) {
            iCount++;

            QuerySolution qs = rs.next();
            Iterator<String> itVars = qs.varNames();

            while(itVars.hasNext()){
                String sVar =itVars.next();
                String szVal = qs.get(sVar).toString();

                res.add(szVal);
            }

        }
        return res;
    }

    private static String getParameterDescription(PathItem pathItem, String parameterName, String method){

        Operation operation = null;

        switch(method) {
            case "get":
                operation = pathItem.getGet();
                break;
            case "put":
                operation = pathItem.getPut();
                break;
            case "post":
                operation = pathItem.getPost();
                break;
            case "delete":
                operation = pathItem.getDelete();
                break;
            case "options":
                operation = pathItem.getOptions();
                break;
            case "head":
                operation = pathItem.getHead();
                break;
            case "patch":
                operation = pathItem.getPatch();
                break;
            case "trace":
                operation = pathItem.getTrace();
                break;
        }

        List<Parameter> parameters = operation.getParameters();

        for(Parameter parameter: parameters){
            if(parameter.getName().equals(parameterName)){
                return  parameter.getDescription();
            }
        }

        return parameterName;
    }

}

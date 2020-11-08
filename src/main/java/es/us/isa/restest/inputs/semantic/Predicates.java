package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.specification.OpenAPISpecification;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.*;
import org.apache.jena.sparql.engine.http.QueryEngineHTTP;

import java.util.*;
import java.util.stream.Collectors;

import es.us.isa.restest.configuration.pojos.TestParameter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static es.us.isa.restest.inputs.semantic.NLPUtils.extractPredicateCandidatesFromDescription;
import static es.us.isa.restest.inputs.semantic.NLPUtils.posTagging;
import static es.us.isa.restest.inputs.semantic.SPARQLUtils.executeSPARQLQueryCount;
import static es.us.isa.restest.inputs.semantic.SPARQLUtils.generateQuery;
import static es.us.isa.restest.inputs.semantic.SemanticInputGenerator.szEndpoint;

public class Predicates {

    private static final Integer minSupport = 20;
    private static final Logger log = LogManager.getLogger(Predicates.class);

    public static Map<TestParameter, List<String>> getPredicates(SemanticOperation semanticOperation, OpenAPISpecification spec){
        Set<TestParameter> parameters = semanticOperation.getSemanticParameters().keySet();

        Map<TestParameter, List<String>> res = new HashMap<>();

        for(TestParameter p: parameters){

            String parameterName = p.getName();
            log.info("Obtaining predicates of parameter {}", parameterName);

            // Get description
            PathItem pathItem = spec.getSpecification().getPaths().get(semanticOperation.getOperationPath());
            String parameterDescription = getParameterDescription(pathItem, parameterName, semanticOperation.getOperationMethod());

            // If the paramater name is only a character, compare with description
            if(parameterName.length() == 1 && parameterDescription!=null){
                List<String> possibleNames = posTagging(parameterDescription, p.getName());
                if(possibleNames.size()>0){
                    parameterName = possibleNames.get(0);
                }
            }

            // DESCRIPTION
            Map<Double, Set<String>> descriptionCandidates = new HashMap<>();

            if(parameterDescription != null){
                // Extract candidates from description
                descriptionCandidates = extractPredicateCandidatesFromDescription(parameterName, parameterDescription);
            }

            // Compute support ordered by priority, if one of the candidates surpasses the threshold, it is used as predicate
            String predicateDescription = getPredicatesFromDescription(descriptionCandidates, p);

            if(predicateDescription != null){
                res.put(p, Collections.singletonList(predicateDescription));
            }else{
                // PARAMETER NAME
                List<String> predicates = getPredicatesOfSingleParameter(parameterName, p);
                if(predicates.size()>0){
                    res.put(p, predicates);
                }
            }
        }

        return res;
    }

    public static String getPredicatesFromDescription(Map<Double, Set<String>> descriptionCandidates, TestParameter testParameter){

        List<Double> orderedKeySet = descriptionCandidates.keySet().stream().sorted(Collections.reverseOrder()).collect(Collectors.toList());

        for(Double key: orderedKeySet){
            for(String match: descriptionCandidates.get(key)){

                String queryString = generatePredicateQuery(match);
                String predicate = executePredicateSPARQLQuery(queryString, testParameter);

                if(predicate != null){
                    log.info("Candidate {} selected with predicate: {}", match, predicate);
                    return  predicate;
                }
            }
        }

        return null;
    }


    public static List<String> getPredicatesOfSingleParameter(String parameterName, TestParameter testParameter){

        // PARAMETER NAME
        // Query creation
        String queryString = generatePredicateQuery(parameterName);

        // Query execution
        String predicate = executePredicateSPARQLQuery(queryString, testParameter);

        if(predicate == null){
            String[] words = parameterName.split("_");
            // If snake_case
            if(words.length > 1){
                // Join words (convert to camel case)
                String newQuery = generatePredicateQuery(String.join("", words));
                predicate = executePredicateSPARQLQuery(newQuery, testParameter);

                if(predicate == null) {
                    // Execute one query for each word in snake_case
                    List<String> predicates = new ArrayList<>();
                    for(String word: words){

                        String query = generatePredicateQuery(word);
                        String wordPredicate = executePredicateSPARQLQuery(query, testParameter);
                        if(wordPredicate!=null){
                            predicates.add(wordPredicate);
                        }

                    }
                    if(predicates.size()>0){
                        return predicates;
                    }
                }

            }

            // If camelCase
            String[] wordsCamel = parameterName.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
            if(predicate==null && wordsCamel.length >1){
                List<String> predicates = new ArrayList<>();
                // Execute one query for each word in camelCase
                for(String word: wordsCamel){
                    String query = generatePredicateQuery(word);
                    String wordPredicate = executePredicateSPARQLQuery(query, testParameter);
                    if(wordPredicate!=null){
                        predicates.add(wordPredicate);
                    }
                }
                if(predicates.size() > 0){
                    return predicates;
                }

            }
        }

        if(predicate != null){
            return Collections.singletonList(predicate);
        }else{
            return new ArrayList<>();
        }
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


    public static String executePredicateSPARQLQuery(String queryString, TestParameter testParameter){

        Query query = QueryFactory.create(queryString);
        QueryExecution qexec = QueryExecutionFactory.sparqlService(szEndpoint, query);
        ((QueryEngineHTTP)qexec).addParam("timeout", "10000");

        // Execute query
        int iCount = 0;
        ResultSet rs = qexec.execSelect();
        while (rs.hasNext() && iCount<5) {
            iCount++;

            QuerySolution qs = rs.next();
            Iterator<String> itVars = qs.varNames();

            while(itVars.hasNext()){
                String sVar = itVars.next();
                String szVal = qs.get(sVar).toString();

                Integer support = computeSupportOfPredicate(szVal, testParameter);

                if(support >= minSupport){
                    return szVal;
                }
            }

        }

        return null;
    }

    public static Integer computeSupportOfPredicate(String predicate, TestParameter testParameter){

        // Generate query
        Map<TestParameter, List<String>> parameterWithPredicate = new HashMap<>();
        parameterWithPredicate.put(testParameter, Collections.singletonList(predicate));

        String queryString = generateQuery(parameterWithPredicate, true);

        // Execute query
        Integer supportOfPredicate = executeSPARQLQueryCount(queryString, szEndpoint);

        return supportOfPredicate;
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

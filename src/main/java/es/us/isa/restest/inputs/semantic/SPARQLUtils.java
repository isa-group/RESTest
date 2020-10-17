package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.pojos.TestParameter;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.sparql.engine.http.QueryEngineHTTP;
import io.swagger.v3.oas.models.parameters.Parameter;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;


public class SPARQLUtils {

    // DBPedia Endpoint
    private static String szEndpoint = "http://dbpedia.org/sparql";


    public static Map<String, Set<String>> getParameterValues(Map<TestParameter, List<String>> parametersWithPredicates) throws Exception {

        String queryString = generateQuery(parametersWithPredicates, false);
        System.out.println(queryString);

        Map<String, Set<String>> result = executeSPARQLQuery(queryString, szEndpoint);

        Set<String> parameterNames = result.keySet();
        Set<String> subGraphParameterNames = new HashSet<>();

        if (parameterNames.size() > 1){
            for(String parameterName: parameterNames){
                if(result.get(parameterName).size() < 100){
                    subGraphParameterNames.add(parameterName);
                }
            }

            if(subGraphParameterNames.size() == parameterNames.size()){     // Same set case

                Integer maxSupport = 0;
                Map<TestParameter, List<String>> subGraphParametersWithPredicates = new HashMap<>();
                String isolatedParameterName = "";

                // For para recorrer cada uno de los parámetros
                for(String subGraphParameterName: subGraphParameterNames){

                    Map<TestParameter, List<String>> currentSubGraphParametersWithPredicates = parametersWithPredicates.entrySet().stream()
                            .filter(x -> !x.getKey().getName().equals(subGraphParameterName))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                    // Compute support
                    String queryCount = generateQuery(currentSubGraphParametersWithPredicates, true);
                    Integer currentSupport = executeSPARQLQueryCount(queryCount, szEndpoint);

                    // Comparar tamaño con el acumulador
                    if(currentSupport > maxSupport){
                        maxSupport = currentSupport;
                        subGraphParametersWithPredicates = currentSubGraphParametersWithPredicates;
                        isolatedParameterName = subGraphParameterName;
                    }

                }

                // Call the isolated parameter and add to result
                Map<TestParameter, List<String>> isolatedParameter = parametersWithPredicates.entrySet().stream()
                        .filter(x -> !subGraphParameterNames.contains(x.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                Map<String, Set<String>> subResultIsolated = getParameterValues(isolatedParameter);
                result.get(isolatedParameterName).addAll(subResultIsolated.get(isolatedParameterName));


                // Add the subgraph to results
                Map<String, Set<String>> subResult = getParameterValues(subGraphParametersWithPredicates);
                for(String parameterName: subResult.keySet()){
                    result.get(parameterName).addAll(subResult.get(parameterName));
                }


            }else{  // Smaller Set case
                Map<TestParameter, List<String>> subGraphParametersWithPredicates = parametersWithPredicates.entrySet().stream()
                        .filter(x -> subGraphParameterNames.contains(x.getKey().getName()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                // Create SubResult
                Map<String, Set<String>> subResult = getParameterValues(subGraphParametersWithPredicates);

                // Add the results of the recursive call to result
                for(String parameterName: subResult.keySet()){
                    result.get(parameterName).addAll(subResult.get(parameterName));
                }
            }

        }

        return result;
    }

    // Execute a Query
    // Returns Map<ParameterName, Set<ParameterValue>>
    public static Map<String, Set<String>> executeSPARQLQuery(String szQuery, String szEndpoint)
            throws Exception
    {
        Map<String, Set<String>> res = new HashMap<>();

        // Create a Query with the given String
        Query query = QueryFactory.create(szQuery);

        // Create the Execution Factory using the given Endpoint
        QueryExecution qexec = QueryExecutionFactory.sparqlService(
                szEndpoint, query);

        // Set Timeout
        ((QueryEngineHTTP)qexec).addParam("timeout", "10000");

        // Execute Query
        ResultSet rs = qexec.execSelect();

        rs.getResultVars().stream().forEach(x->res.put(x, new HashSet<String>()));
        while (rs.hasNext()) {
            // Get Result
            QuerySolution qs = rs.next();

            // Get Variable Names
            Iterator<String> itVars = qs.varNames();

            while (itVars.hasNext()) {
                String szVar = itVars.next();

                // Gets an RDF node
                RDFNode szVal = qs.get(szVar);
                String szValString = "";

                if(szVal.isURIResource()){
                    szValString = szVal.asResource().getLocalName().replace("_", " ").trim();

                }else{
                    szValString = szVal.asLiteral().getString();
                }

                if(szValString.trim().equals("")){
                    URI uri = new URI(szVal.toString());
                    String[] segments = uri.getPath().split("/");
                    szValString = segments[segments.length-1].replace("_", " ");
                }

                res.get(szVar).add(szValString);

//                System.out.println("[" + szVar + "]: " + szValString);
            }

        }

        return res;
    }

    public static String generateQuery(Map<TestParameter, List<String>> parametersWithPredicates, Boolean count) {
        String queryString = "";
        String filters = "";

        // Add prefixes
        queryString = queryString + "PREFIX dbo: <http://dbpedia.org/ontology/> \n";
        queryString = queryString + "PREFIX dbp: <http://dbpedia.org/property/> \n";
        queryString = queryString + "PREFIX dbr: <http://dbpedia.org/resource/> \n";

        List<TestParameter> allParameters = new ArrayList<>(parametersWithPredicates.keySet());
        List<String> allParametersName = allParameters.stream()
                .map(x-> x.getName())
                .collect(Collectors.toList());


        // Random String and parameter string
        String randomString = generateRandomString(allParametersName);
        String parametersString = generateParametersString(allParametersName);

        // First line
        if(count){
            queryString = queryString + "Select distinct count(*)  where { \n\n";
        }else {
            queryString = queryString + "Select distinct " + parametersString + "  where { \n\n";
        }

        // Required parameters
        int requiredSize = allParameters.size();
        if (requiredSize > 0) {

            queryString = queryString + "?" + randomString;

            for (int i = 0; i <= (requiredSize - 2); i++) {
                // Add required parameter to query
                TestParameter currentParameter = allParameters.get(i);
                String currentParameterName = currentParameter.getName();

                // Predicates
                List<String> predicates = parametersWithPredicates.get(currentParameter);
                String predicatesString = getPredicatesString(predicates);

                queryString = queryString + "\t" + predicatesString + " ?" + currentParameterName + " ; \n";

                // TODO: Test with multiple parameters
                // TODO: IMPLEMENT
//                filters = filters + generateSPARQLFilters(currentParameter);
            }
            // TODO: Test with multiple parameters
            TestParameter lastParameter = allParameters.get(requiredSize - 1);
            String lastParameterName = lastParameter.getName();

            // Predicates
            List<String> predicates = parametersWithPredicates.get(lastParameter);
            String predicatesString = getPredicatesString(predicates);

            queryString = queryString + "\t" + predicatesString + " ?" + lastParameterName + " . \n\n";

            // TODO: IMPLEMENT
//            filters = filters + generateSPARQLFilters(lastParameter);
        }

        // Add filters
        queryString = queryString + "\n" + filters;


        // Close query
        queryString = queryString + "\n}  \n";
        return queryString;

    }

    public static Integer executeSPARQLQueryCount(String szQuery, String szEndpoint)
            throws Exception
    {

        // Create a Query with the given String
        Query query = QueryFactory.create(szQuery);

        // Create the Execution Factory using the given Endpoint
        QueryExecution qexec = QueryExecutionFactory.sparqlService(
                szEndpoint, query);

        // Set Timeout
        ((QueryEngineHTTP)qexec).addParam("timeout", "10000");

        // Execute Query
        ResultSet rs = qexec.execSelect();

        QuerySolution qs = rs.next();
        Integer res = qs.get("?callret-0").asLiteral().getInt();
        return res;
    }

    private static String generateRandomString(List<String> allParameters){
        String res = getAlphaNumericString();
        if(allParameters.contains(res)){
            return generateRandomString(allParameters);
        }else{
            return res;
        }
    }

    private static String getAlphaNumericString() {

        // chose a Character random from this String
        String AlphaNumericString = "abcdefghijklmnopqrstuvxyz";

        // create StringBuffer size of AlphaNumericString
        StringBuilder sb = new StringBuilder(10);

        for (int i = 0; i < 10; i++) {
            int index
                    = (int)(AlphaNumericString.length()
                    * Math.random());

            sb.append(AlphaNumericString
                    .charAt(index));
        }

        return sb.toString();
    }

    private static String generateParametersString(List<String> allParameters){
        String res = "";
        for(int i=0; i <= allParameters.size()-1; i++){
            res = res + " ?" + allParameters.get(i);
        }

        return res;
    }

    private static  String getPredicatesString(List<String> predicates){
        String res = "<" + predicates.get(0) + ">";

        for(int i = 1; i<predicates.size(); i++){
            res = res + "|<" + predicates.get(i) + ">";
        }

        return res;
    }

}

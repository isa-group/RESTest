package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.pojos.ParameterValues;
import es.us.isa.restest.configuration.pojos.SemanticParameter;
import es.us.isa.restest.configuration.pojos.TestParameter;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.sparql.engine.http.QueryEngineHTTP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static es.us.isa.restest.inputs.semantic.GenerateSPARQLFilters.generateSPARQLFilters;
import static es.us.isa.restest.inputs.semantic.SemanticInputGenerator.THRESHOLD;
import static es.us.isa.restest.inputs.semantic.SemanticInputGenerator.szEndpoint;


public class SPARQLUtils {

    private static final Logger log = LogManager.getLogger(SPARQLUtils.class);



    public static Map<String, Set<String>> getParameterValues(Set<SemanticParameter> semanticParameters) throws Exception {

        Map<String, Set<String>> result = new HashMap<>();

        if(semanticParameters.size()>0) {

            String queryString = generateQuery(semanticParameters, false);
            System.out.println(queryString);

            result = executeSPARQLQuery(queryString, szEndpoint);

            Set<String> parameterNames = result.keySet();
            Set<String> subGraphParameterNames = new HashSet<>();

            if (parameterNames.size() > 1) {
                for (String parameterName : parameterNames) {
                    if (result.get(parameterName).size() < THRESHOLD) {
                        subGraphParameterNames.add(parameterName);
                    }
                }

                if (subGraphParameterNames.size() == parameterNames.size()) {     // Same set case
                    log.info("Insufficient inputs for all parameters, looking for connected component with greatest support");

                    Integer maxSupport = 0;
                    Set<SemanticParameter> subGraphParameters = new HashSet<>();
                    String isolatedParameterName = "";

                    // Iterate parameters
                    for (String subGraphParameterName : subGraphParameterNames) {
                        Set<SemanticParameter> currentSubGraphParameters = semanticParameters.stream()
                                .filter(x-> !x.getTestParameter().getName().equals(subGraphParameterName)).collect(Collectors.toSet());

                        // Compute support
                        String queryCount = generateQuery(currentSubGraphParameters, true);
                        Integer currentSupport = executeSPARQLQueryCount(queryCount, szEndpoint);

                        // Compare size with accumulator
                        if (currentSupport >= maxSupport) {
                            maxSupport = currentSupport;
                            subGraphParameters = currentSubGraphParameters;
                            isolatedParameterName = subGraphParameterName;
                        }

                    }

                    log.info("Isolating parameter {} to increase support", isolatedParameterName);
                    // Call the isolated parameter and add to result
                    String finalIsolatedParameterName = isolatedParameterName;
                    Set<SemanticParameter> isolatedParameter = semanticParameters.stream()
                            .filter(x-> x.getTestParameter().getName().equals(finalIsolatedParameterName))
                            .collect(Collectors.toSet());

                    Map<String, Set<String>> subResultIsolated = getParameterValues(isolatedParameter);
                    result.get(isolatedParameterName).addAll(subResultIsolated.get(isolatedParameterName));


                    // Add the subgraph to results
                    Map<String, Set<String>> subResult = getParameterValues(subGraphParameters);
                    for (String parameterName : subResult.keySet()) {
                        result.get(parameterName).addAll(subResult.get(parameterName));
                    }


                } else if ((subGraphParameterNames.size() > 0) && (subGraphParameterNames.size() < parameterNames.size())) {  // Smaller Set case
                    log.info("Insufficient inputs for a group of parameters, querying DBPedia with a subset of parameters");

                    Set<SemanticParameter> subGraphParameters = semanticParameters.stream()
                            .filter(x-> subGraphParameterNames.contains(x.getTestParameter().getName()))
                            .collect(Collectors.toSet());

                    // Create SubResult
                    Map<String, Set<String>> subResult = getParameterValues(subGraphParameters);

                    // Add the results of the recursive call to result
                    for (String parameterName : subResult.keySet()) {
                        result.get(parameterName).addAll(subResult.get(parameterName));
                    }
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
//        ((QueryEngineHTTP)qexec).addParam("timeout", "10000");

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


                    szValString = szVal.asResource().toString();

                    URI uri = new URI(szValString);
                    String host = uri.getHost();

                    if(host!=null && szEndpoint.contains(uri.getHost())){
                        szValString = szVal.asResource().getLocalName().replace("_", " ").trim();
                    }
                }else{
                    szValString = szVal.asLiteral().getString();
                }

                if(szValString.trim().equals("")){
                    URI uri = new URI(szVal.toString());
                    String[] segments = uri.getPath().split("/");
                    if(segments.length > 0){
                        szValString = segments[segments.length-1].replace("_", " ");
                    }
                }

                res.get(szVar).add(szValString);

            }

        }

        return res;
    }

    public static String generateQuery(Set<SemanticParameter> semanticParameters, Boolean count) {
        String queryString = "";
        String filters = "";

        List<SemanticParameter> allParameters = new ArrayList<>(semanticParameters);

        List<String> allParametersName = allParameters.stream()
                .map(x-> x.getTestParameter().getName())
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
                SemanticParameter currentParameter = allParameters.get(i);
                String currentParameterName = currentParameter.getTestParameter().getName();

                // Predicates
                Set<String> predicates = currentParameter.getPredicates();
                // Exception if size=0 or null
                String predicatesString = getPredicatesString(predicates);

                queryString = queryString + "\t" + predicatesString + " ?" + currentParameterName + " ; \n";

                filters = filters + generateSPARQLFilters(currentParameter.getTestParameter());
            }
            SemanticParameter lastParameter = allParameters.get(requiredSize - 1);
            String lastParameterName = lastParameter.getTestParameter().getName();

            // Predicates
            Set<String> predicates = lastParameter.getPredicates();
            String predicatesString = getPredicatesString(predicates);

            queryString = queryString + "\t" + predicatesString + " ?" + lastParameterName + " . \n\n";


            filters = filters + generateSPARQLFilters(lastParameter.getTestParameter());
        }

        // Add filters
        queryString = queryString + "\n" + filters;


        // Close query
        queryString = queryString + "\n}  \n";
        return queryString;

    }


    public static Integer executeSPARQLQueryCount(String szQuery, String szEndpoint){

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

    private static  String getPredicatesString(Set<String> predicates){

        if(predicates==null || predicates.size()==0){
            throw new NullPointerException("A semantic parameter must contain at least one predicate");
        }

        String res = "";
        Iterator<String> predicatesIterator = predicates.iterator();
        res = res + "<" + predicatesIterator.next() + ">";

        while (predicatesIterator.hasNext()){
            res = res + "|<" + predicatesIterator.next() + ">";
        }

        return res;
    }

    public static Set<String> getNewValues(ParameterValues parameterValues,
                                           Set<String> predicates, String regex){
        Map<String, Set<String>> result = new HashMap<>();
        // TODO: Check if the regular expression has been added in the previous step (getPredicates)

        SemanticParameter semanticParameter = new SemanticParameter(parameterValues.getTestParameter());
        semanticParameter.setPredicates(predicates);

        String queryString = generateQuery(Collections.singleton(semanticParameter), false);

        try {
            result = executeSPARQLQuery(queryString, szEndpoint);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result.get(parameterValues.getTestParameter().getName());

    }

}

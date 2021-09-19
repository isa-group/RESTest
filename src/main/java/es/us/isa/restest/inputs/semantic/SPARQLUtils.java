package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.inputs.semantic.objects.SemanticParameter;
import es.us.isa.restest.configuration.pojos.TestParameter;
import org.javatuples.Pair;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.jena.sparql.engine.http.QueryEngineHTTP;


import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import static es.us.isa.restest.inputs.semantic.ARTEInputGenerator.*;
import static es.us.isa.restest.inputs.semantic.GenerateSPARQLFilters.generateSPARQLFilters;


public class SPARQLUtils {

    private SPARQLUtils(){
        throw new IllegalStateException("Utilities class");
    }

    private static final Logger log = LogManager.getLogger(SPARQLUtils.class);



    public static Map<String, Set<String>> getParameterValues(Set<SemanticParameter> semanticParameters) throws Exception {

        Map<String, Set<String>> result = new HashMap<>();

        if(!semanticParameters.isEmpty()) {

            Pair<String, Map<String, String>> queryString = generateQuery(semanticParameters, false);
            log.info(queryString.getValue0());
            // kebab-case
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
                        Pair<String, Map<String, String>> queryCount = generateQuery(currentSubGraphParameters, true);
                        Integer currentSupport = executeSPARQLQueryCount(queryCount.getValue0(), szEndpoint);

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


                } else if (!subGraphParameterNames.isEmpty() && (subGraphParameterNames.size() < parameterNames.size())) {  // Smaller Set case
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
    public static Map<String, Set<String>> executeSPARQLQuery(Pair<String, Map<String, String>> szQuery, String szEndpoint)
            throws URISyntaxException
    {
        Map<String, Set<String>> res = new HashMap<>();

        // Create a Query with the given String
        Query query = QueryFactory.create(szQuery.getValue0());

        // Create the Execution Factory using the given Endpoint
        QueryExecution qexec = QueryExecutionFactory.sparqlService(
                szEndpoint, query);

        // Set Timeout
        qexec.setTimeout(10000000, 10000000);
        ((QueryEngineHTTP)qexec).addParam("timeout", "10000000");

        // Execute Query
        ResultSet rs = qexec.execSelect();

        rs.getResultVars().stream().forEach(x->res.put(x, new HashSet<>()));

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


                    if(host!=null && "http://dbpedia.org/sparql".contains(uri.getHost())){
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

                szValString = szValString.replace("\"", "'");
                res.get(szVar).add(szValString);

            }

        }


        Map<String, String> parameterNamesMap = szQuery.getValue1();
        Map<String, String> newKeys = new HashMap<>();
        for(String key: res.keySet()) {
            if (!parameterNamesMap.containsKey(key)){
                // replace key (randomly generated) with original name (kebab-case)
                Map.Entry<String, String> newEntry = parameterNamesMap.entrySet().stream().filter(entry -> key.equals(entry.getValue())).findFirst().orElse(null);
                if(newEntry!=null){
                    String newKey = newEntry.getKey();
                    newKeys.put(key, newKey);
                }
            }
        }

        for(String oldKey: newKeys.keySet()){
            res.put(newKeys.get(oldKey), res.get(oldKey));
            res.remove(oldKey);
        }

        return res;
    }

    public static Map<String, String> getAllParametersName(List<SemanticParameter> allParameters){

        // key: original name, value: new name
        Map<String, String> res = new HashMap<>();

        List<String> allParametersName = allParameters.stream()
                .map(x-> x.getTestParameter().getName())
                .collect(Collectors.toList());

        for(String parameterName: allParametersName){
            if(parameterName.contains("-")){
                // If the parameter name is in kebab-case, generate a new string as variable name
                res.put(parameterName, generateRandomString(allParametersName));
            } else {
                res.put(parameterName, parameterName);
            }
        }
        return res;
    }

    public static Pair<String, Map<String, String>> generateQuery(Set<SemanticParameter> semanticParameters, boolean count) {
        String queryString = "";
        String filters = "";

        List<SemanticParameter> allParameters = new ArrayList<>(semanticParameters);

        Map<String, String> allParametersNameMap = getAllParametersName(allParameters);


        // Random String and parameter string
        String randomString = generateRandomString(new ArrayList<>(allParametersNameMap.values()));
        String parametersString = generateParametersString(new ArrayList<>(allParametersNameMap.values()));

        // First line
        if(count){
            if(allParametersNameMap.keySet().size() == 1){
                queryString = queryString + "Select count(distinct " + parametersString + ")" + " where { \n\n";
            }else{
                queryString = queryString + "Select distinct count(*)  where { \n\n";
            }
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
                currentParameterName = allParametersNameMap.get(currentParameterName);      // kebab-case

                // Predicates
                Set<String> predicates = currentParameter.getPredicates();
                // Exception if size=0 or null
                String predicatesString = getPredicatesString(predicates);

                queryString = queryString + "\t" + predicatesString + " ?" + currentParameterName + " ; \n";

                filters = filters + generateSPARQLFilters(currentParameter.getTestParameter());
            }
            SemanticParameter lastParameter = allParameters.get(requiredSize - 1);
            String lastParameterName = lastParameter.getTestParameter().getName();
            lastParameterName = allParametersNameMap.get(lastParameterName);      // kebab-case

            // Predicates
            Set<String> predicates = lastParameter.getPredicates();
            String predicatesString = getPredicatesString(predicates);

            queryString = queryString + "\t" + predicatesString + " ?" + lastParameterName + " . \n\n";


            filters = filters + generateSPARQLFilters(lastParameter.getTestParameter());
        }

        // Add filters
        queryString = queryString + "\n" + filters;


        // Close query
        queryString = queryString + "\n} ";

        return Pair.with(queryString, allParametersNameMap);

    }


    public static Integer executeSPARQLQueryCount(String szQuery, String szEndpoint){

        // Create a Query with the given String
        Query query = QueryFactory.create(szQuery);

        // Create the Execution Factory using the given Endpoint
        QueryExecution qexec = QueryExecutionFactory.sparqlService(
                szEndpoint, query);

        // Set Timeout
        qexec.setTimeout(10000000, 10000000);
        ((QueryEngineHTTP)qexec).addParam("timeout", "10000000");

        // Execute Query
        Integer res = 0;
        ResultSet rs = qexec.execSelect();
        if(rs.hasNext()){
            QuerySolution qs = rs.next();
            res = qs.get("?callret-0").asLiteral().getInt();
        }
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
        String alphaNumericString = "abcdefghijklmnopqrstuvxyz";

        // create StringBuffer size of AlphaNumericString
        StringBuilder sb = new StringBuilder(10);

        for (int i = 0; i < 10; i++) {
            int index
                    = (int)(alphaNumericString.length()
                    * Math.random());

            sb.append(alphaNumericString
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

        if(predicates==null || predicates.isEmpty()){
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

    public static Set<String> getNewValues(SemanticParameter oldSemanticParameter,
                                           Set<String> predicates, String regex){
        Map<String, Set<String>> result = new HashMap<>();

        // We create a new Semantic parameter containing the new predicates
        TestParameter testParameter = oldSemanticParameter.getTestParameter();
        // Add regex to semanticParameter
        testParameter.addRegexToTestParameter(regex);
        SemanticParameter newSemanticParameter = new SemanticParameter(testParameter);
        newSemanticParameter.setPredicates(predicates);

        Pair<String, Map<String, String>> queryString = generateQuery(Collections.singleton(newSemanticParameter), false);

        try {
            result = executeSPARQLQuery(queryString, szEndpoint);
        } catch (Exception e) {
            log.error(e.getMessage());
        }

        return result.get(newSemanticParameter.getTestParameter().getName());

    }

}

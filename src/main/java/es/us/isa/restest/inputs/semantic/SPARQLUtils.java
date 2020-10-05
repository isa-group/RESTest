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

    // Execute a Query
    // TODO: Remove duplicates after filtering (datatype)
    // Returns List<Map<ParameterName, ParameterValue>>
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
//        int iCount = 0;
        ResultSet rs = qexec.execSelect();

        rs.getResultVars().stream().forEach(x->res.put(x, new HashSet<String>()));
        while (rs.hasNext()) {
            // Get Result
            QuerySolution qs = rs.next();

            // Get Variable Names
            Iterator<String> itVars = qs.varNames();


            // Count
//            iCount++;
//            System.out.println("\nResult " + iCount + ": ");

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

    public static String generateQuery(Map<TestParameter, List<String>> parametersWithPredicates) {
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
        queryString = queryString + "Select distinct " + parametersString + "  where { \n\n";

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

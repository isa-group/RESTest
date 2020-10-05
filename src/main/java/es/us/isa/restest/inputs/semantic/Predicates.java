package es.us.isa.restest.inputs.semantic;

import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.*;
import org.apache.jena.sparql.engine.http.QueryEngineHTTP;


import java.util.*;
import es.us.isa.restest.configuration.pojos.TestParameter;

public class Predicates {
    // TODO: Consider the possibility of adding owl predicates
    // TODO: Add support/threshold
    // TODO: Add by combination of length/support
    // TODO: Add limit
    // TODO: Wordnet/Description in case the function returns no results
    // TODO: size()=0 exception
    public static Map<TestParameter, List<String>> getPredicates(Set<TestParameter> parameters){
        Map<TestParameter, List<String>> res = new HashMap<>();

        for(TestParameter p: parameters){
            List<String> predicates = getPredicatesOfSingleParameter(p.getName());
            res.put(p, predicates);
        }
        return res;
    }

    public static List<String> getPredicatesOfSingleParameter(String parameterName){
        // Query creation
        // TODO: Order by length (provisional)
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

        System.out.println(res);
        return res;
    }
}

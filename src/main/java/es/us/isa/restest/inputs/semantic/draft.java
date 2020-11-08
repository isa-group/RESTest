package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.configuration.pojos.TestParameter;
import net.sf.extjwnl.data.Exc;

import java.util.*;

import static es.us.isa.restest.inputs.semantic.SPARQLUtils.executeSPARQLQuery;
import static es.us.isa.restest.inputs.semantic.SPARQLUtils.generateQuery;
import static es.us.isa.restest.inputs.semantic.SemanticInputGenerator.szEndpoint;

public class draft {

    public static void main(String[] args) throws Exception {

//        Map<TestParameter, List<String>> parametersWithPredicates = new HashMap<>();
//
//
//        List<String> listWebsite = new ArrayList<>();
//        listWebsite.add("http://dbpedia.org/property/website");
//
//        TestParameter website = new TestParameter();
//        List<GenParameter> genParameters = new ArrayList<>();
////        website.se
//        website.setName("website");
//
//        parametersWithPredicates.put(website, listWebsite);
//
//
//        String szQuery = generateQuery(parametersWithPredicates, false);
//        Map<String, Set<String>> results =  executeSPARQLQuery(szQuery, szEndpoint);

        Map<TestParameter, List<String>> parametersWithPredicates = new HashMap<>();


        List<String> listWebsite = new ArrayList<>();
        listWebsite.add("http://dbpedia.org/ontology/country");

        TestParameter website = new TestParameter();

        Generator generator = new Generator();
        generator.setType("String");

        List<GenParameter> genParameters = new ArrayList<>();
        generator.setGenParameters(genParameters);


        website.setGenerator(generator);
        website.setName("country");

        parametersWithPredicates.put(website, listWebsite);


        String szQuery = generateQuery(parametersWithPredicates, false);
        Map<String, Set<String>> results =  executeSPARQLQuery(szQuery, szEndpoint);

        for(String countryName: results.get("country")){
            System.out.println(countryName);
        }

    }

}

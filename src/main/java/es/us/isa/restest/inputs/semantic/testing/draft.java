package es.us.isa.restest.inputs.semantic.testing;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.configuration.pojos.TestParameter;
import org.apache.jena.atlas.test.Gen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.RANDOM_INPUT_VALUE;
import static es.us.isa.restest.inputs.semantic.Predicates.getPredicatesOfSingleParameter;

public class draft {

    public static void main(String[] args) {

        System.setProperty("http.maxConnections", "100000");

        String regex = "^(?:\\w[^\"](?:\\w\\w[^\"](?:[^\"][^\"])?+)?+)*+$";

        List<String> predicatesToIgnore = new ArrayList<>();
        predicatesToIgnore.add("http://dbpedia.org/property/hotelName");

        TestParameter testParameter = new TestParameter();

        testParameter.setName("hotelName");
        testParameter.setIn("query");
        testParameter.setWeight(0.1f);


        GenParameter genParameter = new GenParameter();
        genParameter.setName("predicates");


        List<GenParameter> genParameters = new ArrayList<>();
        genParameters.add(genParameter);

        Generator generator = new Generator();
        generator.setType(RANDOM_INPUT_VALUE);
        generator.setValid(true);
        generator.setGenParameters(genParameters);

        List<Generator> generators = new ArrayList<>();
        generators.add(generator);
        testParameter.setGenerators(generators);


        testParameter.addRegexToSemanticParameter(regex);

        Set<String> predicates = new HashSet<>();

        predicates = getPredicatesOfSingleParameter("hotelName", testParameter, predicatesToIgnore);

        System.out.println(predicates);

    }



}



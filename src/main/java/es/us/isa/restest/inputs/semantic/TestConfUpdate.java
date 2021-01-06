package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.pojos.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.RANDOM_INPUT_VALUE;

public class TestConfUpdate {

    public static void updateTestConf(TestConfigurationObject newConf, SemanticParameter semanticParameter,
                                      String path, Integer opIndex){

        // CSV GenParameter
        GenParameter csvGenParameter = new GenParameter();
        csvGenParameter.setName("csv");
        csvGenParameter.setValues(Collections.singletonList(path));

        // PREDICATES GenParameter
        GenParameter predicatesGenParameter = new GenParameter();
        predicatesGenParameter.setName("predicates");
        predicatesGenParameter.setValues(new ArrayList<>(semanticParameter.getPredicates()));

        // List of GenParameters
        List<GenParameter> genParameterList = new ArrayList<>();
        genParameterList.add(csvGenParameter);
        genParameterList.add(predicatesGenParameter);

        // GENERATOR
        Generator newGenerator = new Generator();
        newGenerator.setGenParameters(genParameterList);
        newGenerator.setType(RANDOM_INPUT_VALUE);

        newConf.getTestConfiguration().getOperations()
                .get(opIndex)
                .getTestParameters().stream()
                .filter(x ->x.equals(semanticParameter.getTestParameter())).findFirst().get()
                .setGenerator(newGenerator);

    }
}

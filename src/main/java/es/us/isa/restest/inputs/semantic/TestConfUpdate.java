package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;

import java.util.Collections;

import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.RANDOM_INPUT_VALUE;

public class TestConfUpdate {

    public static void updateTestConf(TestConfigurationObject newConf, TestParameter parameter,
                                      String path, Integer opIndex){

        GenParameter newGenParameter = new GenParameter();
        newGenParameter.setName("csv");
        newGenParameter.setValues(Collections.singletonList(path));

        Generator newGenerator = new Generator();
        newGenerator.setGenParameters(Collections.singletonList(newGenParameter));
        newGenerator.setType(RANDOM_INPUT_VALUE);

        newConf.getTestConfiguration().getOperations()
                .get(opIndex)
                .getTestParameters().stream()
                .filter(x ->x.equals(parameter)).findFirst().get()
                .setGenerator(newGenerator);

    }
}

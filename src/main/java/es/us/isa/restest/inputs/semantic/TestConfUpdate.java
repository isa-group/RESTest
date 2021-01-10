package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.*;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.stream.IntStream;

import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.RANDOM_INPUT_VALUE;
import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.SEMANTIC_PARAMETER;
import static es.us.isa.restest.main.TestGenerationAndExecution.getConfPath;


public class TestConfUpdate {

    private static final Logger log = LogManager.getLogger(TestConfUpdate.class);

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
        newGenerator.setValid(true);
        newGenerator.setType(RANDOM_INPUT_VALUE);

        TestParameter testParameter = newConf.getTestConfiguration().getOperations()
                .get(opIndex)
                .getTestParameters().stream()
                .filter(x ->x.equals(semanticParameter.getTestParameter()))
                .findFirst().get();
        List<Generator> generators = testParameter.getGenerators();

        generators.removeIf(x->x.getType().equalsIgnoreCase(SEMANTIC_PARAMETER));
        generators.add(newGenerator);

        testParameter.setGenerators(generators);

    }

    public static void updateTestConfWithNewPredicates(TestConfigurationObject conf, ParameterValues parameterValues, Set<String>  newPredicates){
        Integer opIndex = IntStream.range(0, conf.getTestConfiguration().getOperations().size())
                .filter(i -> parameterValues.getOperation().getOperationId().equals(conf.getTestConfiguration().getOperations().get(i).getOperationId()))
                .findFirst().getAsInt();

        TestParameter testParameter = conf.getTestConfiguration().getOperations()
                .get(opIndex)
                .getTestParameters().stream()
                .filter(x ->x.getName().equals(parameterValues.getTestParameter().getName()))
                .findFirst().get();
        Generator generator = testParameter.getGenerators().stream()
                .filter(x->x.getType().equals(RANDOM_INPUT_VALUE) && x.getGenParameters().stream().anyMatch(y->y.getName().equals("predicates"))).findFirst().get();

        GenParameter genParameter = generator.getGenParameters().stream()
                .filter(x->x.getName().equals("predicates")).findFirst().get();

        List<String> oldPredicates = genParameter.getValues();

        oldPredicates.addAll(newPredicates);

        genParameter.setValues(oldPredicates);

        // Write new test configuration to file
        TestConfigurationIO.toFile(conf, getConfPath());
        log.info("Test configuration file updated");

    }
}

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

import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.*;


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
        predicatesGenParameter.setName(PREDICATES);
        predicatesGenParameter.setValues(new ArrayList<>(semanticParameter.getPredicates()));

        // NUMBER_OF_TRIES_TO_GENERATE_REGEX
        GenParameter numberOfTriesToGenerateRegexGenParameter = new GenParameter();
        numberOfTriesToGenerateRegexGenParameter.setName(NUMBER_OF_TRIES_TO_GENERATE_REGEX);
        List<String> numberOfTriesList = new ArrayList<>();
        numberOfTriesList.add(Integer.toString(semanticParameter.getNumberOfTriesToGenerateRegex()));
        numberOfTriesToGenerateRegexGenParameter.setValues(numberOfTriesList);

        // List of GenParameters
        List<GenParameter> genParameterList = new ArrayList<>();
        genParameterList.add(csvGenParameter);
        genParameterList.add(predicatesGenParameter);
        genParameterList.add(numberOfTriesToGenerateRegexGenParameter);

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
        if(semanticParameter.getValues().size() == 0){
            testParameter.setWeight(0.0f);
        }

    }

    public static void updateTestConfWithIncreasedNumberOfTries(
            TestConfigurationObject conf, String confPath,
            SemanticOperation semanticOperation, SemanticParameter semanticParameter) {

        semanticParameter.increaseNumberOfTriesToGenerateRegex();

        int opIndex = IntStream.range(0, conf.getTestConfiguration().getOperations().size())
                .filter(i -> semanticOperation.getOperationId().equals(conf.getTestConfiguration().getOperations().get(i).getOperationId()))
                .findFirst().getAsInt();

        TestParameter testParameter = conf.getTestConfiguration().getOperations()
                .get(opIndex)
                .getTestParameters().stream()
                .filter(x ->x.getName().equals(semanticParameter.getTestParameter().getName()))
                .findFirst().orElseThrow(() -> new NullPointerException("No TestParameter found"));

        Generator generator = testParameter.getGenerators().stream()
                .filter(x->x.getType().equals(RANDOM_INPUT_VALUE) && x.getGenParameters().stream().anyMatch(y->y.getName().equals(PREDICATES)))
                .findFirst().orElseThrow(() -> new NullPointerException("No Generator found"));

        GenParameter genParameterNumberOfTries = generator.getGenParameters().stream()
                .filter(x->x.getName().equals(NUMBER_OF_TRIES_TO_GENERATE_REGEX)).findFirst()
                .orElseThrow( () -> new NullPointerException("Number of tries to generate regex genParameter found"));

        List<String> newValues = new ArrayList<>();
        newValues.add(Integer.toString(semanticParameter.getNumberOfTriesToGenerateRegex()));
        genParameterNumberOfTries.setValues(newValues);

        // Write new test configuration to file
        TestConfigurationIO.toFile(conf, confPath);
        log.info("Number of tries increased for parameter " + testParameter.getName());

    }

    public static void updateTestConfWithNewPredicates(
            TestConfigurationObject conf, String confPath, SemanticOperation semanticOperation,
            SemanticParameter semanticParameter, Set<String>  newPredicates
    ){

        int opIndex = IntStream.range(0, conf.getTestConfiguration().getOperations().size())
                .filter(i -> semanticOperation.getOperationId().equals(conf.getTestConfiguration().getOperations().get(i).getOperationId()))
                .findFirst().getAsInt();

        TestParameter testParameter = conf.getTestConfiguration().getOperations()
                .get(opIndex)
                .getTestParameters().stream()
                .filter(x ->x.getName().equals(semanticParameter.getTestParameter().getName()))
                .findFirst().orElseThrow(() -> new NullPointerException("No TestParameter found"));

        Generator generator = testParameter.getGenerators().stream()
                .filter(x->x.getType().equals(RANDOM_INPUT_VALUE) && x.getGenParameters().stream().anyMatch(y->y.getName().equals(PREDICATES))).findFirst().orElseThrow(() -> new NullPointerException("No Generator found"));

        GenParameter genParameter = generator.getGenParameters().stream()
                .filter(x->x.getName().equals(PREDICATES)).findFirst()
                .orElseThrow( () -> new NullPointerException("No predicates genParameter found"));

        List<String> oldPredicates = genParameter.getValues();

        oldPredicates.addAll(newPredicates);

        genParameter.setValues(oldPredicates);

        // Set the numberOfTriesToGenerateRegex to 0
        GenParameter genParameterNumberOfTries = generator.getGenParameters().stream()
                .filter(x -> x.getName().equals(NUMBER_OF_TRIES_TO_GENERATE_REGEX)).findFirst()
                .orElseThrow(() -> new NullPointerException("Number of tries to generate regex not found"));

        List<String> zero = new ArrayList<>();
        zero.add("0");
        genParameterNumberOfTries.setValues(zero);

        // Write new test configuration to file
        TestConfigurationIO.toFile(conf, confPath);
        log.info("Test configuration file updated");

    }


}

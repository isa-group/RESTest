package es.us.isa.restest.inputs.semantic.regexGenerator;

import es.us.isa.restest.configuration.pojos.*;
import es.us.isa.restest.testcases.TestCase;
import it.units.inginf.male.configuration.Configuration;
import it.units.inginf.male.inputs.DataSet;
import it.units.inginf.male.outputs.FinalSolution;
import it.units.inginf.male.outputs.Results;
import it.units.inginf.male.postprocessing.BasicPostprocessor;
import it.units.inginf.male.postprocessing.JsonPostProcessor;
import it.units.inginf.male.strategy.ExecutionStrategy;
import it.units.inginf.male.strategy.impl.CoolTextualExecutionListener;
import javafx.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.RANDOM_INPUT_VALUE;
import static es.us.isa.restest.util.CSVManager.collectionToCSV;
import static es.us.isa.restest.util.CSVManager.readValues;
import static es.us.isa.restest.util.FileManager.createFileIfNotExists;
import static es.us.isa.restest.util.FileManager.deleteFile;

public class RegexGeneratorUtils {

    private static final Logger logger = LogManager.getLogger(RegexGeneratorUtils.class.getName());

    public static FinalSolution learnRegex(String name, Set<String> matches, Set<String> unmatches, Boolean print) {
        // Configuration
        SimpleConfig simpleConfiguration = new SimpleConfig();

        simpleConfiguration.numberOfJobs = 32; // -j
        simpleConfiguration.generations = 100; // -g
        simpleConfiguration.numberThreads = 4; // -t
        simpleConfiguration.populationSize = 500; //-p
        simpleConfiguration.termination = 20; //-e
        simpleConfiguration.populateOptionalFields = false;
        simpleConfiguration.isStriped = false;

        // Create dataset
        simpleConfiguration.dataset = new DataSet(name, matches, unmatches);

        Configuration config = simpleConfiguration.buildConfiguration();
        config.setPostProcessor(new JsonPostProcessor());
        config.getPostprocessorParameters().put(BasicPostprocessor.PARAMETER_NAME_POPULATE_OPTIONAL_FIELDS, Boolean.toString(simpleConfiguration.populateOptionalFields));


        Results results = new Results(config);

        CoolTextualExecutionListener consolelistener = new CoolTextualExecutionListener(config, results, print);

        long startTime = System.currentTimeMillis();
        ExecutionStrategy strategy = config.getStrategy();


        try {
            strategy.execute(config, consolelistener);
        } catch (Exception e) {
            e.printStackTrace();
        }


        if (config.getPostProcessor() != null) {
            startTime = System.currentTimeMillis() - startTime;
            config.getPostProcessor().elaborate(config, results, startTime);
        }

        FinalSolution finalSolution = results.getBestSolution();
        return finalSolution;

    }

    // This method returns all the Semantic parameters of a given testConf file
    public static Map<Pair<Operation, TestParameter>, Set<String>> getMapOfSemanticParameters(List<Operation> operations){
        Map<Pair<Operation, TestParameter>, Set<String>> res = new HashMap<>();

        for(Operation operation: operations){
            // Adding parameters that use a csv to the maps
            for(TestParameter testParameter: operation.getTestParameters()){
                List<Generator> generatorList = testParameter.getGenerators();
                for(Generator generator: generatorList){
                    if(generator.getType().equals(RANDOM_INPUT_VALUE)){
                        for(GenParameter genParameter: generator.getGenParameters()){

                            if(genParameter.getName().equals("predicates")){
                                // Adding the pair <OperationId, parameterName> to the map
                                Pair<Operation, TestParameter> operationAndParameter = new Pair(operation, testParameter);
                                res.put(operationAndParameter, new HashSet<>());
                            }
                        }
                    }
                }
            }
        }

        return res;
    }

    public static List<String> getCsvPaths(TestParameter testParameter){
        List<String> res = new ArrayList<>();

        for(Generator generator: testParameter.getGenerators()){
            if(generator.getType().equals(RANDOM_INPUT_VALUE) && generator.getGenParameters().stream().anyMatch(x->x.getName().equals("predicates"))){
                res = generator.getGenParameters().stream().filter(x->x.getName().equals("csv"))
                        .flatMap(x->x.getValues().stream()).collect(Collectors.toList());
            }
        }

        return res;
    }

    public static void updateCsvWithRegex(ParameterValues parameterValues, String regex){
        Pattern pattern = Pattern.compile(regex);
        List<String> csvPaths = getCsvPaths(parameterValues.getTestParameter());

        // Filter CSVs by regex
        for(String csvPath: csvPaths){
            // Read csv as list
            List<String> csvValues = readValues(csvPath);

            // Filter list by regex
            List<String> matches = csvValues.stream()
                    .filter(pattern.asPredicate())
                    .collect(Collectors.toList());

            // Rewrite csv
            deleteFile(csvPath);
            createFileIfNotExists(csvPath);

            // Write the Set of values as a csv file
            try {
                collectionToCSV(csvPath, matches);
                logger.info("CSV file updated");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public static void addResultsToCSV(ParameterValues parameterValues, Set<String> results){
        List<String> csvPaths = getCsvPaths(parameterValues.getTestParameter());

        // TODO: Consider creating a separated CSV file
        String csvPath = csvPaths.get(0);

        // Update the first CSV file with new data
        List<String> csvValues = readValues(csvPath);

        // add new values
        csvValues.addAll(results);

        // Rewrite csv
        deleteFile(csvPath);
        createFileIfNotExists(csvPath);

        // Write the Set of values as a csv file
        try {
            collectionToCSV(csvPath, csvValues);
            logger.info("CSV file updated");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void updateValidAndInvalidValues(
            TestCase testCase,
            Map<Pair<Operation, TestParameter>, Set<String>> validValues,
            Map<Pair<Operation, TestParameter>, Set<String>> invalidValues,
            Set<ParameterValues> valuesFromPreviousIterations,
            String responseCode){

        String operationId = testCase.getOperationId();

        // Iterate semantic parameters (Filter by operationId)
        Set<TestParameter> parametersOfOperation = validValues.keySet().stream()
                .filter(x -> x.getKey().equals(operationId)).map(x -> x.getValue())
                .collect(Collectors.toSet());

        for (TestParameter parameter : parametersOfOperation) {
            Pair<String, TestParameter> pair = new Pair<>(operationId, parameter);

            // Search parameter value in corresponding map
            String value = testCase.getParameterValue(parameter.getIn(), parameter.getName());

            // Add parameter value to a map depending on the response code
            // 5XX codes are not taken into consideration
            if(value != null){
                switch (responseCode.charAt(0)) {
                    case '2':
                        validValues.get(pair).add(value);
                        break;
                    case '4':
//                        if(isTestValueInvalid(testCase, parameter, valuesFromPreviousIterations, validValues)){
                            // Add only if the rest of the parameter values are considered valid (from previous or current iterations)
                            invalidValues.get(pair).add(value);
//                        }
                        break;
                }
            }
        }
    }

    public static Boolean isTestValueInvalid
            (TestCase testCase,
             TestParameter parameterToDiscard,
             Set<ParameterValues> valuesFromPreviousIterations,
             Map<Pair<String, TestParameter>, Set<String>> validValues
            ){
        // TODO: Check if this method is uncommented (remember refactorization)

        String operationId = testCase.getOperationId();

        // Get values of current iteration and remove parameterToDiscard
        Map<Pair<String, TestParameter>, Set<String>> validValuesOfOperation =
                validValues
                        .keySet().stream()
                        .filter(x->x.getKey().equals(operationId) && !x.getValue().getName().equals(parameterToDiscard.getName()))
                        .filter(validValues::containsKey).collect(Collectors.toMap(Function.identity(), validValues::get));

        Set<ParameterValues> valuesFromPreviousIterationsOfOperation = valuesFromPreviousIterations.stream()
                .filter(x->x.getOperation().getOperationId().equals(operationId) && !x.getTestParameter().getName().equals(parameterToDiscard.getName()))
                .collect(Collectors.toSet());

        // Iterate test parameters, if the rest of parameter values are valid at some point, add the parameterToDiscard to the invalid set (return true)
        for(ParameterValues parameterValues: valuesFromPreviousIterationsOfOperation){
            TestParameter testParameter = parameterValues.getTestParameter();

            String value = testCase.getParameterValue(testParameter.getIn(), testParameter.getName());
            Pair<String, TestParameter> operationParameter = new Pair<>(operationId, testParameter);

            if(
                    value!=null &&
                    !parameterValues.getValidValues().contains(value) &&
                    !validValuesOfOperation.get(operationParameter).contains(value)
            ){
                return false;
            }
        }

        return true;
    }





//    public static void main(String[] args) throws Exception {
//
//        // -------------------------------------------------------- CREATING DATASET --------------------------------------------------------
//        String name = "getAlbums_locale";
//
//        Set<String> matches = new HashSet<>();
//        matches.add("en_Us");
//        matches.add("es_esp");
//        matches.add("po_iuy");
//        matches.add("lk_hgf");
//        matches.add("mn_bvc");
//
//        Set<String> unmatches = new HashSet<>();
//        unmatches.add("qwe");
//        unmatches.add("rty");
//        unmatches.add("uio");
//        unmatches.add("pas");
//        unmatches.add("dfg");
//        // ----------------------------------------------------------------------------------------------------------------------------------
//
//
//        FinalSolution solution = learnRegex(name, matches, unmatches, false);
//        System.out.println("-----------------------SOLUTION: " + solution.getSolution());
//    }



}
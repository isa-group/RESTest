package es.us.isa.restest.inputs.semantic.regexGenerator;

import es.us.isa.restest.configuration.pojos.*;
import es.us.isa.restest.inputs.semantic.objects.SemanticOperation;
import es.us.isa.restest.inputs.semantic.objects.SemanticParameter;
import es.us.isa.restest.testcases.TestCase;
import it.units.inginf.male.configuration.Configuration;
import it.units.inginf.male.inputs.DataSet;
import it.units.inginf.male.outputs.FinalSolution;
import it.units.inginf.male.outputs.Results;
import it.units.inginf.male.postprocessing.BasicPostprocessor;
import it.units.inginf.male.postprocessing.JsonPostProcessor;
import it.units.inginf.male.strategy.ExecutionStrategy;
import it.units.inginf.male.strategy.impl.CoolTextualExecutionListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.PREDICATES;
import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.RANDOM_INPUT_VALUE;
import static es.us.isa.restest.inputs.semantic.ARTEInputGenerator.LIMIT;
import static es.us.isa.restest.util.CSVManager.collectionToCSV;
import static es.us.isa.restest.util.CSVManager.readValues;
import static es.us.isa.restest.util.FileManager.createFileIfNotExists;
import static es.us.isa.restest.util.FileManager.deleteFile;

public class RegexGeneratorUtils {

    private RegexGeneratorUtils(){
        throw new IllegalStateException("Utilities class");
    }

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
            logger.error(e.getMessage());
        }


        if (config.getPostProcessor() != null) {
            startTime = System.currentTimeMillis() - startTime;
            config.getPostProcessor().elaborate(config, results, startTime);
        }

        return results.getBestSolution();

    }

    public static List<String> getCsvPaths(TestParameter testParameter){
        List<String> res = new ArrayList<>();

        for(Generator generator: testParameter.getGenerators()){
            if(generator.getType().equals(RANDOM_INPUT_VALUE) && generator.getGenParameters().stream().anyMatch(x->x.getName().equals(PREDICATES))){
                res = generator.getGenParameters().stream().filter(x->x.getName().equals("csv"))
                        .flatMap(x->x.getValues().stream()).collect(Collectors.toList());
            }
        }

        return res;
    }

    public static void updateCsvWithRegex(String csvPath, String regex){
        Pattern pattern = Pattern.compile(regex);

        // Read CSV as list
        List<String> csvValues = readCsv(csvPath);

        // Filter list by regex
        List<String> matches = csvValues.stream().filter(pattern.asPredicate()).collect(Collectors.toList());

        // Rewrite csv
        deleteFile(csvPath);
        createFileIfNotExists(csvPath);

        // Write the Set of values as a csv file
        collectionToCSV(csvPath, matches);

    }

    public static List<String> readCsv(String csvFile) {

        List<String> res = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line = "";
            while((line = br.readLine()) != null) {
                res.add(line);
            }
        } catch(IOException e) {
            logger.error(e.getMessage());
        }
        return res;
    }

    public static void updateCsvWithRegex(SemanticParameter semanticParameter, String regex){
        List<String> csvPaths = getCsvPaths(semanticParameter.getTestParameter());

        // Filter CSVs by regex
        for(String csvPath: csvPaths){
            updateCsvWithRegex(csvPath, regex);
            logger.info("CSV file updated");
        }

    }

    public static void addResultsToCSV(SemanticParameter semanticParameter, Set<String> results){
        List<String> csvPaths = getCsvPaths(semanticParameter.getTestParameter());

        String csvPath = csvPaths.get(0);

        // Update the first CSV file with new data
        List<String> csvValues = readValues(csvPath);

        // add new values
        if (LIMIT == null){
            csvValues.addAll(results);
        } else{
            List<String> resultsAsList = new ArrayList<>(results);
            Collections.shuffle(resultsAsList);

            Set<String> subSet = resultsAsList.stream().limit(LIMIT).collect(Collectors.toSet());

            csvValues.addAll(subSet);
        }


        // Rewrite csv
        deleteFile(csvPath);
        createFileIfNotExists(csvPath);

        // Write the Set of values as a csv file
            collectionToCSV(csvPath, csvValues);
            logger.info("CSV file updated");

    }

    public static void updateValidAndInvalidValues(Set<SemanticOperation> semanticOperations, TestCase testCase, String responseCode){

        String testCaseOperationId = testCase.getOperationId();

        // Get SemanticOperation
        SemanticOperation semanticOperation = semanticOperations.stream()
                .filter(x-> x.getOperationId().equals(testCaseOperationId)).findFirst()
                .orElseThrow(() -> new NullPointerException("Semantic Operation not found"));

        // Get SemanticParameter of iteration
        Set<SemanticParameter> semanticParametersOfOperation = semanticOperation.getSemanticParameters();



        for (SemanticParameter semanticParameter : semanticParametersOfOperation) {
            // Search parameter value in corresponding map
            TestParameter testParameter = semanticParameter.getTestParameter();
            String value = testCase.getParameterValue(testParameter.getIn(), testParameter.getName());

            // Add parameter value to a map depending on the response code
            // 5XX codes are not taken into consideration
            if(value != null){
                switch (responseCode.charAt(0)) {
                    case '2':
                        // Add valid value to SemanticParameter
                        semanticParameter.addValidValue(value);
                        break;
                    case '4':
                        if(isTestValueInvalid(testCase, semanticParameter, semanticOperation)){
                            // Add only if the rest of the parameter values are considered valid (from previous or current iterations)
                            semanticParameter.addInvalidValue(value);
                        }
                        break;
                    default:
                        logger.error("Potential bug detected");
                        break;
                }
            }
        }
    }

    public static boolean isTestValueInvalid
            (TestCase testCase,
             SemanticParameter parameterToDiscard,
             SemanticOperation semanticOperation
            ){
        // This function returns true if all the other values of the semantic parameters are valid, meaning that
        // the current value is invalid
        for(SemanticParameter otherSemanticParameter: semanticOperation.getSemanticParameters()) {
            //Check that all the other values are valid
            if(!otherSemanticParameter.equals(parameterToDiscard)){

                // Get the value from the current testCase
                String currentValueOfOtherParameter = testCase.getParameterValue(otherSemanticParameter.getTestParameter().getIn(), otherSemanticParameter.getTestParameter().getName());

                if (
                        currentValueOfOtherParameter != null &&
                        !otherSemanticParameter.getValidValues().contains(currentValueOfOtherParameter)
                ){
                    return false;
                }
            }
        }
        return true;
    }


}
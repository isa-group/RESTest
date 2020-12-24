package es.us.isa.restest.inputs.semantic.regexGenerator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.units.inginf.male.Main;
import it.units.inginf.male.configuration.Configuration;
import it.units.inginf.male.inputs.DataSet;
import it.units.inginf.male.inputs.DataSet.Example;
import it.units.inginf.male.outputs.FinalSolution;
import it.units.inginf.male.outputs.Results;
import it.units.inginf.male.postprocessing.BasicPostprocessor;
import it.units.inginf.male.postprocessing.JsonPostProcessor;
import it.units.inginf.male.strategy.ExecutionStrategy;
import it.units.inginf.male.strategy.impl.CoolTextualExecutionListener;
import it.units.inginf.male.utils.Utils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConsoleRegexTurtle {

    private static String WARNING_MESSAGE = "\nWARNING\n"
            + "The quality of the solution depends on a number of factors, including size and syntactical properties of the learning information.\n"
            + "The algorithms embedded in this experimental prototype have always been tested with at least 25 matches over at least 2 examples.\n"
            + "It is very unlikely that a smaller number of matches allows obtaining a useful solution.\n";

    public static void main(String[] args) {
        SimpleConfig simpleConfiguration = new SimpleConfig();

        //Set defaults for commandline parameters
        simpleConfiguration.datasetName = "src/main/resources/datasets/countries-dataset.json"; // -d
        simpleConfiguration.outputFolder = "src/main/resources/output"; // -o
        //load simpleconfig defaults
        simpleConfiguration.numberOfJobs = 32; // -j
        simpleConfiguration.generations = 100; // -g
        simpleConfiguration.numberThreads = 4; // -t
        simpleConfiguration.populationSize = 500; //-p
        simpleConfiguration.termination = 20; //-e
        simpleConfiguration.populateOptionalFields = false;
        simpleConfiguration.isStriped = false;


        try {
            simpleConfiguration.dataset = loadDataset(simpleConfiguration.datasetName);
        } catch (IOException ex) {
            System.out.println("Problem opening the dataset file " + simpleConfiguration.datasetName + "\n");
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        }
        //Output warning about learning size
        String message = null;
        int numberPositiveExamples = 0;
        for (Example example : simpleConfiguration.dataset.getExamples()) {
            if (example.getNumberMatches() > 0) {
                numberPositiveExamples++;
            }
        }
        if (simpleConfiguration.dataset.getNumberMatches() < 25 || numberPositiveExamples < 2) {
            message = WARNING_MESSAGE;
        }
        Configuration config = simpleConfiguration.buildConfiguration();
        //change defaults for console usage
        config.setPostProcessor(new JsonPostProcessor());
        config.getPostprocessorParameters().put(BasicPostprocessor.PARAMETER_NAME_POPULATE_OPTIONAL_FIELDS, Boolean.toString(simpleConfiguration.populateOptionalFields));
        config.setOutputFolderName(simpleConfiguration.outputFolder);

        Results results = new Results(config);
        results.setComment(simpleConfiguration.comment);
        try {
            //This is an optional information
            results.setMachineHardwareSpecifications(Utils.cpuInfo());
        } catch (IOException ex) {
            Logger.getLogger(ConsoleRegexTurtle.class.getName()).log(Level.SEVERE, null, ex);
        }
        CoolTextualExecutionListener consolelistener = new CoolTextualExecutionListener(message, config, results);

        long startTime = System.currentTimeMillis();
        ExecutionStrategy strategy = config.getStrategy();
        try {
            strategy.execute(config, consolelistener);
        } catch (Exception ex) {
            Logger.getLogger(ConsoleRegexTurtle.class.getName()).log(Level.SEVERE, null, ex);
        }

        if (config.getPostProcessor() != null) {
            startTime = System.currentTimeMillis() - startTime;
            config.getPostProcessor().elaborate(config, results, startTime);
        }
        writeBestPerformances(results.getBestSolution());
    }

    private static DataSet loadDataset(String dataSetFilename) throws IOException {
        FileInputStream fis = new FileInputStream(new File(dataSetFilename));
        InputStreamReader isr = new InputStreamReader(fis);
        StringBuilder sb;
        try (BufferedReader bufferedReader = new BufferedReader(isr)) {
            sb = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
            }
        }
        String json = sb.toString();
        return loadDatasetJson(json);
    }

    private static DataSet loadDatasetJson(String jsonDataset) {
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        DataSet dataset = gson.fromJson(jsonDataset, DataSet.class);
        return dataset;
    }

    private static void writeBestPerformances(FinalSolution solution) {
        if (solution != null) {
            System.out.println("Best on learning (JAVA): " + solution.getSolution());
            System.out.println("Best on learning (JS): " + solution.getSolutionJS());

            System.out.println("******Stats for Extraction task******");
            System.out.println("******Stats on training******");
            System.out.println("F-measure: " + solution.getTrainingPerformances().get("match f-measure"));
            System.out.println("Precision: " + solution.getTrainingPerformances().get("match precision"));
            System.out.println("Recall: " + solution.getTrainingPerformances().get("match recall"));
            System.out.println("Char precision: " + solution.getTrainingPerformances().get("character precision"));
            System.out.println("Char recall: " + solution.getTrainingPerformances().get("character recall"));
            System.out.println("******Stats on validation******");
            System.out.println("F-measure " + solution.getValidationPerformances().get("match f-measure"));
            System.out.println("Precision: " + solution.getValidationPerformances().get("match precision"));
            System.out.println("Recall: " + solution.getValidationPerformances().get("match recall"));
            System.out.println("Char precision: " + solution.getValidationPerformances().get("character precision"));
            System.out.println("Char recall: " + solution.getValidationPerformances().get("character recall"));
            System.out.println("******Stats on learning******");
            System.out.println("F-measure: " + solution.getLearningPerformances().get("match f-measure"));
            System.out.println("Precision: " + solution.getLearningPerformances().get("match precision"));
            System.out.println("Recall: " + solution.getLearningPerformances().get("match recall"));
            System.out.println("Char precision: " + solution.getLearningPerformances().get("character precision"));
            System.out.println("Char recall: " + solution.getLearningPerformances().get("character recall"));

        }
    }

}
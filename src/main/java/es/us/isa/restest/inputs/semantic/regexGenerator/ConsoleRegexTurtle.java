package es.us.isa.restest.inputs.semantic.regexGenerator;

import es.us.isa.restest.util.RESTestException;
import it.units.inginf.male.configuration.Configuration;
import it.units.inginf.male.inputs.DataSet;
import it.units.inginf.male.outputs.FinalSolution;
import it.units.inginf.male.outputs.Results;
import it.units.inginf.male.postprocessing.BasicPostprocessor;
import it.units.inginf.male.postprocessing.JsonPostProcessor;
import it.units.inginf.male.strategy.ExecutionStrategy;
import it.units.inginf.male.strategy.impl.CoolTextualExecutionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ConsoleRegexTurtle {

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
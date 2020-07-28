/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.experiment;


import com.google.common.collect.Lists;

import es.us.isa.restest.searchbased.SearchBasedTestSuiteGenerator;
import es.us.isa.restest.searchbased.objectivefunction.Coverage;
import es.us.isa.restest.searchbased.objectivefunction.InputCoverage;
import es.us.isa.restest.searchbased.objectivefunction.RestfulAPITestingObjectiveFunction;
import es.us.isa.restest.searchbased.objectivefunction.SuiteSize;
import es.us.isa.restest.searchbased.objectivefunction.UniqueFailures;
import es.us.isa.restest.searchbased.terminationcriteria.MaxEvaluations;
import es.us.isa.restest.searchbased.terminationcriteria.MaxExecutedRequests;
import es.us.isa.restest.specification.OpenAPISpecification;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static es.us.isa.restest.util.FileManager.createDir;
import static es.us.isa.restest.searchbased.terminationcriteria.And.and;

/**
 *
 * @author japar
 */
public class BikeWiseExperiment {

    private static int fixedTestSuiteSize = 2; // Number of test cases per suite
    private static int minTestSuiteSize = 1;
    private static int maxTestSuiteSize = 10;
    private static int populationSize = 10; // Population size for the evolutionary algorithm
    private static int maxEvaluations = 1000;
    private static int maxExecutedRequests=900;
    private static String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml"; // Path to OAS specification file
    private static String confPath = "src/test/resources/Bikewise/fullConf.yaml"; // Path to test configuration file
    private static String experimentName = "bikewise"; // Experiment name
    private static String targetDir = "src/generation/java/searchbased"; // Directory where tests will be generated.
    private static long seed = 1979;

    public static void main(String[] args) {

        createDir(targetDir);

        List<RestfulAPITestingObjectiveFunction> objectiveFunctions = Lists.newArrayList(
                new UniqueFailures(),
    			new SuiteSize()
    	);
    	
        SearchBasedTestSuiteGenerator generator=new SearchBasedTestSuiteGenerator(
                            OAISpecPath, 
                            confPath,
                            experimentName,
                            objectiveFunctions,
                            targetDir,
                            seed,
                            minTestSuiteSize,
                            maxTestSuiteSize,                            
                            populationSize,
                            and(	new MaxEvaluations(maxEvaluations),
                            		new MaxExecutedRequests(maxExecutedRequests)));
                       
        try {
            generator.run();
        } catch (IOException ex) {
            Logger.getLogger(BikeWiseExperiment.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
}

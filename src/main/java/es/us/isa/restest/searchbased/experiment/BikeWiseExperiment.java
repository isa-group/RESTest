/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.experiment;


import com.google.common.collect.Lists;

import es.us.isa.restest.searchbased.SearchBasedTestSuiteGenerator;
import es.us.isa.restest.searchbased.objectivefunction.Coverage;
import es.us.isa.restest.searchbased.objectivefunction.RestfulAPITestingObjectiveFunction;
import es.us.isa.restest.searchbased.objectivefunction.Size;
import es.us.isa.restest.specification.OpenAPISpecification;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author japar
 */
public class BikeWiseExperiment {

    private static int numTestCases = 5;												// Number of test cases per operation
    private static String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";		// Path to OAS specification file
    private static String confPath = "src/test/resources/Bikewise/fullConf.yaml";		// Path to test configuration file
    private static String targetDir = "src/generation/java/serachbased/bikewise";					// Directory where tests will be generated.
    private static String APIName = "Bikewise";											// API name
    private static String packageName = "bikewise";										// Package name of the test class.
    private static String testClassName = "BikewiseTest";								// Name of the class to be generated
    private static OpenAPISpecification spec;											// OAS
    private static boolean enableStats = true;											// Collect coverage statistics
    private static long seed = 1979;
    
    public static void main(String[] args) {
        
    	List<RestfulAPITestingObjectiveFunction> objectiveFunctions=Lists.newArrayList(
    			new Coverage(),
    			new Size()
    	);
    	
        SearchBasedTestSuiteGenerator generator=new SearchBasedTestSuiteGenerator(
                            OAISpecPath, 
                            Optional.of(confPath),
                            Optional.of("api/v2/locations"),
                            Optional.of("get"),
                            APIName,
                            objectiveFunctions,
                            targetDir,
                            seed);
        try {
            generator.run();
        } catch (IOException ex) {
            Logger.getLogger(BikeWiseExperiment.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
}

package es.us.isa.restest.searchbased.experiment;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.randomsearch.RandomSearch;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.util.experiment.util.ExperimentAlgorithm;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteGenerationProblem;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.searchbased.SearchBasedTestSuiteGenerator;
import es.us.isa.restest.searchbased.objectivefunction.InputCoverage;
import es.us.isa.restest.searchbased.objectivefunction.RestfulAPITestingObjectiveFunction;
import es.us.isa.restest.searchbased.objectivefunction.SuiteSize;

public class RandomSearchExperiment {

	
	public static void main(String[] args) {
		// Experiment configuration
		long seed=1979;
		int populationSize=100;
		int maxEvaluations=10000;
		int independentRuns=2;
		
		// Problem configuration 
		String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";		    // Path to OAS specification file
	    String confPath = "src/test/resources/Bikewise/fullConf.yaml";		    // Path to test configuration file
	    String experimentName = "bikewise";                                      // Experiment name
	    String targetDir = "src/generation/java/searchbased";	// Directory where tests will be generated.
	    String resourcePath ="/v2/incidents";
	    String method ="GET";
	    int minTestSuiteSize=2;
	    int maxTestSuiteSize=10;
	    List<RestfulAPITestingObjectiveFunction> objectiveFunctions=List.of(
	    		new SuiteSize(),
	    		new InputCoverage()
	    		);
	    SearchBasedTestSuiteGenerator generator=new SearchBasedTestSuiteGenerator(
                OAISpecPath, 
                Optional.of(confPath),
                Optional.of(resourcePath),
                Optional.of(method),
                experimentName,
                objectiveFunctions,
                targetDir,
                seed,
                minTestSuiteSize,
                maxTestSuiteSize,
                maxEvaluations,
                populationSize);
	    List<RestfulAPITestSuiteGenerationProblem> problems = List.of();
	    List<ExperimentAlgorithm<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>>> algorithms = null;
	    Algorithm<List<RestfulAPITestSuiteSolution>> randomSearch=new RandomSearch(generator.getProblems().get(0).getProblem(),maxEvaluations);
	    generator.setAlgorithms(List.of(new ExperimentAlgorithm<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>>(randomSearch,generator.getProblems().get(0),0)));
	    try {
			generator.run();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}

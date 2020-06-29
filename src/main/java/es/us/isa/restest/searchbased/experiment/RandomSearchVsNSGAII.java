package es.us.isa.restest.searchbased.experiment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.randomsearch.RandomSearch;
import org.uma.jmetal.util.experiment.util.ExperimentAlgorithm;
import org.uma.jmetal.util.experiment.util.ExperimentProblem;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteGenerationProblem;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.searchbased.SearchBasedTestSuiteGenerator;
import es.us.isa.restest.searchbased.objectivefunction.InputCoverage;
import es.us.isa.restest.searchbased.objectivefunction.RestfulAPITestingObjectiveFunction;
import es.us.isa.restest.searchbased.objectivefunction.SuiteSize;

public class RandomSearchVsNSGAII {
	// Experiment configuration
	long seed=1979;
	int NSGAIIpopulationSize=100;
	int maxEvaluations=10000;
	int independentRuns=2;
	
	// Problem configuration 
	private String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";		    // Path to OAS specification file
    private String confPath = "src/test/resources/Bikewise/fullConf.yaml";		    // Path to test configuration file
    private String experimentName = "bikewise";                                      // Experiment name
    private String targetDir = "src/generation/java/searchbased";	// Directory where tests will be generated.
    private String resourcePath ="/v2/incidents";
    private String method ="GET";
    private int minTestSuiteSize=2;
    private int maxTestSuiteSize=10;
	
    
    List<RestfulAPITestSuiteGenerationProblem> problems = null;
    List<ExperimentAlgorithm<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>>> algorithms = null;
    
	public RandomSearchVsNSGAII(){
		problems=createProblems(createObjectiveFunctions());
		algorithms=createAlgorithms(problems);
	}
	
    private List<ExperimentAlgorithm<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>>> createAlgorithms(List<RestfulAPITestSuiteGenerationProblem> problems2) {
    	List<ExperimentAlgorithm<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>>> algorithms=new ArrayList<>();
		
		Algorithm<List<RestfulAPITestSuiteSolution>> NSGAII=null;
		Algorithm<List<RestfulAPITestSuiteSolution>> randomSearch=null;
		ExperimentProblem<RestfulAPITestSuiteSolution> ep=null;		
		for(int runId=0;runId<independentRuns;runId++) {
			for(RestfulAPITestSuiteGenerationProblem problem:problems) {
				ep=new ExperimentProblem<>(problem);
				NSGAII=SearchBasedTestSuiteGenerator.createDefaultAlgorithm(seed,NSGAIIpopulationSize, maxEvaluations, problem);
				randomSearch=new RandomSearch(problem,maxEvaluations);
				algorithms.add(new ExperimentAlgorithm<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>>(NSGAII, "NSGAII", ep,runId ));			
				algorithms.add(new ExperimentAlgorithm<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>>(randomSearch,"RandomSearch",ep,runId));			
			}
		}
				
		return algorithms;
	}

	public static void main(String[] args) {
    	RandomSearchVsNSGAII experiment=new RandomSearchVsNSGAII();
    	experiment.run();
	}
    
    public void run()
    {
    	String experimentName="RandomSearchVsNSGAII";
    	SearchBasedTestSuiteGenerator generator=new SearchBasedTestSuiteGenerator(experimentName, targetDir, seed, problems,algorithms);
    	try {
			generator.runExperiment(independentRuns,8);
		} catch (IOException e) {			
			e.printStackTrace();
		}
    }


	private List<RestfulAPITestSuiteGenerationProblem> createProblems(List<RestfulAPITestingObjectiveFunction> objectiveFunctions) {
		List<RestfulAPITestSuiteGenerationProblem> problems=new ArrayList<>();
		RestfulAPITestSuiteGenerationProblem bikewiseProblem=SearchBasedTestSuiteGenerator
																.buildProblem(OAISpecPath, 
																			Optional.of(confPath),
																			Optional.of(resourcePath), 
																			Optional.of(method), 
																			objectiveFunctions, 
																			targetDir, 
																			minTestSuiteSize, 
																			maxTestSuiteSize);
		problems.add(bikewiseProblem);
		return problems;
	}
	
	private  List<RestfulAPITestingObjectiveFunction> createObjectiveFunctions(){
		return List.of(
				new SuiteSize(),
				new InputCoverage()
				);
		
	}
}

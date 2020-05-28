/**
 *
 */
package es.us.isa.restest.searchbased;

import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator;
import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfiguration;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestPath;
import es.us.isa.restest.searchbased.objectivefunction.RestfulAPITestingObjectiveFunction;
import es.us.isa.restest.searchbased.operators.AbstractAPITestCaseMutationOperator;
import es.us.isa.restest.searchbased.operators.AllMutationOperators;
import es.us.isa.restest.searchbased.operators.ParameterAdditionMutation;
import es.us.isa.restest.searchbased.operators.ParameterRemovalMutation;
import es.us.isa.restest.searchbased.operators.RandomParameterValueMutation;
import es.us.isa.restest.searchbased.operators.ResourceChangeMutation;
import es.us.isa.restest.searchbased.operators.SinglePointTestSuiteCrossover;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;
import org.uma.jmetal.qualityindicator.impl.Epsilon;
import org.uma.jmetal.qualityindicator.impl.GenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.GenericIndicator;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistancePlus;
import org.uma.jmetal.qualityindicator.impl.Spread;
import org.uma.jmetal.qualityindicator.impl.hypervolume.PISAHypervolume;
import org.uma.jmetal.util.AlgorithmRunner;
import org.uma.jmetal.util.JMetalLogger;
import org.uma.jmetal.util.experiment.Experiment;
import org.uma.jmetal.util.experiment.ExperimentBuilder;
import org.uma.jmetal.util.experiment.component.ComputeQualityIndicators;
import org.uma.jmetal.util.experiment.component.ExecuteAlgorithms;
import org.uma.jmetal.util.experiment.component.GenerateBoxplotsWithR;
import org.uma.jmetal.util.experiment.component.GenerateFriedmanTestTables;
import org.uma.jmetal.util.experiment.component.GenerateLatexTablesWithStatistics;
import org.uma.jmetal.util.experiment.component.GenerateWilcoxonTestTablesWithR;
import org.uma.jmetal.util.experiment.util.ExperimentAlgorithm;
import org.uma.jmetal.util.experiment.util.ExperimentProblem;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;
import org.uma.jmetal.util.pseudorandom.impl.MersenneTwisterGenerator;

import com.google.common.collect.Lists;

public class SearchBasedTestSuiteGenerator {

    // Configuration   
    int nsga2PopulationSize = 10;
    int maxEvaluations = 10;
    long seed = 1979;
    
    // Members:
    RestfulAPITestSuiteGenerationProblem problem;
    private final List<ExperimentProblem<RestfulAPITestSuiteSolution>> problems;
    List<ExperimentAlgorithm<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>>> algorithms;
    ExperimentBuilder<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>> experimentBuilder;

    public SearchBasedTestSuiteGenerator(String apiDescriptionPath, Optional<String> configFilePath,  String experimentName, List<RestfulAPITestingObjectiveFunction> objectiveFunctions,String targetPath, long seed) {
    	this(apiDescriptionPath, configFilePath, Optional.empty(),Optional.empty(),experimentName,objectiveFunctions,experimentName,seed);
    }
    
    public SearchBasedTestSuiteGenerator(String apiDescriptionPath, Optional<String> configFilePath, Optional<String> resourcePath, Optional<String> method, String experimentName, List<RestfulAPITestingObjectiveFunction> objectiveFunctions,String targetPath, long seed) {
        this.seed=seed;
        JMetalRandom.getInstance().setSeed(seed);
    	problem = buildProblem(apiDescriptionPath, configFilePath, resourcePath, method,objectiveFunctions, targetPath);    	
        problems = new ArrayList<>();        
        problems.add(new ExperimentProblem<>(problem));

        algorithms = configureAlgorithms();
        experimentBuilder = new ExperimentBuilder<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>>(experimentName)
                .setExperimentBaseDirectory(targetPath)
        		.setAlgorithmList(algorithms)
                .setProblemList(problems)
                .setOutputParetoFrontFileName("EVAL")
                .setOutputParetoSetFileName("SOL")
                .setReferenceFrontDirectory("/pareto_fronts")
                .setIndicatorList(indicators())
                .setNumberOfCores(8)
                .setIndependentRuns(1);

    }

    private List<ExperimentAlgorithm<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>>> configureAlgorithms() {

    	MersenneTwisterGenerator generator=new MersenneTwisterGenerator(seed); 
    	List<ExperimentAlgorithm<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>>> result = new ArrayList<>();
        Algorithm<List<RestfulAPITestSuiteSolution>> algorithm = null;

        AllMutationOperators mutation=new AllMutationOperators(Lists.newArrayList(
        		new ParameterAdditionMutation(0.2,generator),
        		new ParameterRemovalMutation(0.2,generator),
        		new RandomParameterValueMutation(0.2,generator),
        		new ResourceChangeMutation(0.2,generator)
        ));
        
        ExperimentAlgorithm<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>> expAlg=null;
        int runId=1;
        for (ExperimentProblem ep : problems) {
            algorithm = new NSGAIIBuilder<>(
                    		ep.getProblem(),
                    		new SinglePointTestSuiteCrossover(1.0),
                    		mutation,
                    		nsga2PopulationSize)
            			.setMaxEvaluations(maxEvaluations)
            			.build();
            expAlg=new ExperimentAlgorithm<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>>(algorithm, ep, runId);
           
            result.add(expAlg);
            runId++;
        }
        return result;
    }    
    
    private RestfulAPITestSuiteGenerationProblem buildProblem(String apiDescriptionPath, Optional<String> configFilePath, Optional<String> resourcePath, Optional<String> operation,List<RestfulAPITestingObjectiveFunction> objFuncs, String targetPath) {
        OpenAPISpecification apiUnderTest = new OpenAPISpecification(apiDescriptionPath);
        TestConfigurationObject configuration = loadTestConfiguration(apiUnderTest, configFilePath);
        TestPath pathUnderTest = null;
        Operation operationUnderTest = null;
        if(resourcePath.isPresent() && operation.isPresent()) {
        	pathUnderTest = findPathUnderTest(configuration,resourcePath.get());
        	operationUnderTest=findOperationUnderTest(configuration, resourcePath.get(), operation.get());
        }
        RestfulAPITestSuiteGenerationProblem problem = new RestfulAPITestSuiteGenerationProblem(apiUnderTest, pathUnderTest,operationUnderTest, configuration, objFuncs, targetPath, JMetalRandom.getInstance().getRandomGenerator());
        return problem;
    }

    private TestPath findPathUnderTest(TestConfigurationObject configuration, String resourcePath) {
    	TestPath result = null;
        for (TestPath tp : configuration.getTestConfiguration().getTestPaths()) {
            if (tp.getTestPath().equalsIgnoreCase(resourcePath)) {
                        result = tp;
                        break;
                    }
        }
        return result;
	}

    public void run() throws IOException {
    	JMetalLogger.logger.info("Generating testSuites for: " + problem + " using as objectives :"+ problem.getObjectiveFunctions() );
    	JMetalLogger.logger.info("Starting the execution of: " + algorithms.get(0).getAlgorithm().getClass().getSimpleName());
    	 AlgorithmRunner algorithmRunner = new AlgorithmRunner.Executor((Algorithm<?>) algorithms.get(0).getAlgorithm())
    		        .execute() ;
    	 long computingTime = algorithmRunner.getComputingTime() ;

    	 List<RestfulAPITestSuiteSolution> suites=algorithms.get(0).getAlgorithm().getResult();

    	 JMetalLogger.logger.info("Total execution time: " + computingTime + "ms");
    	 JMetalLogger.logger.info("The algorithm generated "+suites.size()+" test suites.");
    	 JMetalLogger.logger.info("Objectives values have been written to file FUN.tsv");
    	 JMetalLogger.logger.info("Variables values have been written to file VAR.tsv");
    	 int index=1;
    	 for(RestfulAPITestSuiteSolution suite:suites) {
    		 JMetalLogger.logger.info("TestSuite "+index);
    		 for(int i=0;i<suite.getNumberOfObjectives();i++) {
    			 JMetalLogger.logger.info("    Objective "+suite.getProblem().getObjectiveFunctions().get(i).getClass().getSimpleName()+": " + suite.getObjective(i)) ;
    		 }
    		 int i=1;
    		 for(TestCase testCase:suite.getVariables()) {
    			 JMetalLogger.logger.info("    Solution "+i+": " + testCase) ;
    			 i++;
    		 }
    		 index++;
    	 }
    }

	public void runExperiment() throws IOException {

        Experiment<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>> experiment = experimentBuilder.build();

        ExecuteAlgorithms executeAlgorithms = new ExecuteAlgorithms<>(experiment);
        executeAlgorithms.run();

        ComputeQualityIndicators computeIndicators = new ComputeQualityIndicators<>(experiment);
        computeIndicators.run();

        GenerateLatexTablesWithStatistics latexTablesGenerator = new GenerateLatexTablesWithStatistics(experiment);
        latexTablesGenerator.run();

        GenerateWilcoxonTestTablesWithR rWicoxonTestTablesGeneartor = new GenerateWilcoxonTestTablesWithR<>(experiment);
        rWicoxonTestTablesGeneartor.run();
        GenerateFriedmanTestTables rFriedmanTestTablesGenerator = new GenerateFriedmanTestTables<>(experiment);
        rFriedmanTestTablesGenerator.run();

        GenerateBoxplotsWithR rBoxplotGenerator;
        rBoxplotGenerator = new GenerateBoxplotsWithR<>(experiment);
        rBoxplotGenerator.setRows(3);
        rBoxplotGenerator.setColumns(3);
        rBoxplotGenerator.run();

    }



    private List<GenericIndicator<RestfulAPITestSuiteSolution>> indicators() {
        List<GenericIndicator<RestfulAPITestSuiteSolution>> result = new ArrayList<>();
        result.add(new Epsilon<>());
        result.add(new Spread<>());
        result.add(new GenerationalDistance<>());
        result.add(new PISAHypervolume<>());
        result.add(new InvertedGenerationalDistance<>());
        result.add(new InvertedGenerationalDistancePlus<>());
        return result;
    }

    private TestConfigurationObject loadTestConfiguration(OpenAPISpecification apiUnderTest, Optional<String> configFilePath) {
        
        TestConfigurationObject tco = null;
        if (configFilePath.isPresent()) {
            tco = TestConfigurationIO.loadConfiguration(configFilePath.get());        
        } else {
            configFilePath = Optional.of("./testConfiguration.txt");
        }

        if (tco == null) {
            DefaultTestConfigurationGenerator generator = new DefaultTestConfigurationGenerator(apiUnderTest);
            tco = generator.generate(configFilePath.get(), Collections.EMPTY_LIST);
            
        }
        return tco;
    }

    private Operation findOperationUnderTest(TestConfigurationObject configuration, String resourcePath, String method) {
        Operation result = null;
        TestPath tp = findPathUnderTest(configuration,resourcePath);
        if (tp!=null) {
        	for (Operation op : tp.getOperations()) {
        		if (op.getMethod().equalsIgnoreCase(method)) {
                        result = op;
                        break;
                }
            }
        }
        return result;
    }

    public ExperimentBuilder<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>> getExperimentBuilder() {
        return experimentBuilder;
    }

    // Delete a directory
    private void deleteDir(String dirPath) {
        File dir = new File(dirPath);

        try {
            FileUtils.deleteDirectory(dir);
        } catch (IOException e) {
            System.err.println("Error deleting target dir");
            e.printStackTrace();
        }
    }

    // Create target dir if it does not exist
    private void createTargetDir() {
        String targetDir = this.problem.targetPath;
        File dir = new File(targetDir + "/");
        dir.mkdirs();
    }

    public int getPopulationSize(){
    	return nsga2PopulationSize; 
    }
    
    public void setPopulationSize(int popSize) {
    	if(popSize>0)
    		this.nsga2PopulationSize=popSize;
    }
    
    public long getSeed() {
    	return seed;
    }
    
    public void setSeed(long seed) {
    	this.seed = seed;
    }
}

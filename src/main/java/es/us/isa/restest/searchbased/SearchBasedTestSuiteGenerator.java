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
import es.us.isa.restest.searchbased.operators.SinglePointCrossover;
import es.us.isa.restest.specification.OpenAPISpecification;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;
import org.uma.jmetal.qualityindicator.impl.Epsilon;
import org.uma.jmetal.qualityindicator.impl.GenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.GenericIndicator;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistancePlus;
import org.uma.jmetal.qualityindicator.impl.Spread;
import org.uma.jmetal.qualityindicator.impl.hypervolume.PISAHypervolume;
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

public class SearchBasedTestSuiteGenerator {
    // Configuration   
    int nsga2PopulationSize=100;
    // Members:
    RestfulAPITestSuiteGenerationProblem problem;
    private final List<ExperimentProblem<RestfulAPITestSuiteSolution>> problems;
    List<ExperimentAlgorithm<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>>> algorithms;
    ExperimentBuilder<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>> experimentBuilder;

    public SearchBasedTestSuiteGenerator(String apiDescriptionPath, Optional<String> configFilePath, String resourcePath, String method, String experimentName, String targetPath) {
        problem = buildProblem(apiDescriptionPath, configFilePath,resourcePath,method,targetPath);
        problems = new ArrayList<>();
        problems.add(new ExperimentProblem<>(problem));

        algorithms = configureAlgorithms();
        experimentBuilder = new ExperimentBuilder<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>>(experimentName)
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
        List<ExperimentAlgorithm<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>>> result=new ArrayList<>();
        Algorithm<List<RestfulAPITestSuiteSolution>> algorithm=null;
        
        for(ExperimentProblem ep:problems){
            algorithm=new NSGAIIBuilder<>(
                                    ep.getProblem(),
                                    new SinglePointCrossover(1.0),
                                    null,
                                    nsga2PopulationSize
                                ).build();
        }
        return result;
    }

    private RestfulAPITestSuiteGenerationProblem buildProblem(String apiDescriptionPath, Optional<String> configFilePath,String resourcePath,String operation, String targetPath) {
        OpenAPISpecification apiUnderTest = new OpenAPISpecification(apiDescriptionPath);
        TestConfiguration configuration=loadTestConfiguration(apiUnderTest, configFilePath);        
        Operation operationUnderTest = findOperationUnderTest(configuration,resourcePath,operation);
        List<RestfulAPITestingObjectiveFunction> objFuncs = new ArrayList<>();
        RestfulAPITestSuiteGenerationProblem problem = new RestfulAPITestSuiteGenerationProblem(apiUnderTest, operationUnderTest, objFuncs,targetPath);

        return problem;
    }

    public void run() throws IOException {

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

    private TestConfiguration loadTestConfiguration(OpenAPISpecification apiUnderTest, Optional<String> configFilePath) {
        TestConfiguration result=null;
        TestConfigurationObject tco=null;
        if(configFilePath.isPresent()){
            tco=TestConfigurationIO.loadConfiguration(configFilePath.get());
            result=tco.getTestConfiguration();
        }else
            configFilePath=Optional.of("./testConfiguration.txt");
        
        if(result==null){
            DefaultTestConfigurationGenerator generator=new DefaultTestConfigurationGenerator(apiUnderTest);
            tco=generator.generate(configFilePath.get(), Collections.EMPTY_LIST);
            result=tco.getTestConfiguration();
        }
        return result;            
    }

    private Operation findOperationUnderTest(TestConfiguration configuration, String resourcePath, String method) {
        Operation result=null;
        for(TestPath tp:configuration.getTestPaths()){
            if(tp.getTestPath().equals(resourcePath)){
                for(Operation op:tp.getOperations()){
                    if(op.getMethod().equals(method)){
                        result=op;
                        break;
                    }
                }
            }
        }
        return result;
    }

    public ExperimentBuilder<RestfulAPITestSuiteSolution, List<RestfulAPITestSuiteSolution>> getExperimentBuilder() {
        return experimentBuilder;
    }        

}

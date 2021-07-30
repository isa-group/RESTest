package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.*;

import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.util.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.inputs.semantic.Predicates.setPredicates;
import static es.us.isa.restest.inputs.semantic.SPARQLUtils.*;
import static es.us.isa.restest.inputs.semantic.TestConfUpdate.updateTestConf;
import static es.us.isa.restest.util.CSVManager.collectionToCSV;
import static es.us.isa.restest.util.CSVManager.setToCSVWithLimit;
import static es.us.isa.restest.util.FileManager.*;
import static es.us.isa.restest.util.PropertyManager.readProperty;
import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.SEMANTIC_PARAMETER;
import static es.us.isa.restest.util.Timer.TestStep.ALL;

public class ARTEInputGenerator {

    // Properties file with configuration settings
    private static final String propertiesFilePath = "src/test/resources/SemanticAPIs/Spotify/spotify.properties";
    private static OpenAPISpecification specification;
    private static String confPath;
    private static String semanticConfPath;
    private static String csvPath = "src/main/resources/TestData/Generated/";           // Path in which the generated input values will be stored


    // Parameters
    // Minimum support of a predicate
    public static final Integer minSupport = 20;
    // Parameter minimum threshold of unique parameter values to obtain: default 100
    public static final Integer THRESHOLD = 100;
    // Limit
    public static final Integer LIMIT = 30;
    // DBPedia Endpoint     http://dbpedia.org/sparql       http://localhost:8890/sparql
    public static final String szEndpoint = "http://dbpedia.org/sparql";


    private static final Logger log = LogManager.getLogger(ARTEInputGenerator.class);

    public static void main(String[] args) throws IOException {
        
        // ONLY FOR LOCAL COPY OF DBPEDIA
//        System.setProperty("http.maxConnections", "10000");

        Timer.startCounting(ALL);

        setEvaluationParameters();

        log.info(confPath);
        TestConfigurationObject conf = loadConfiguration(confPath, specification);

        // Key: OperationName       Value: Parameters
        log.info("Obtaining semantic operations");
        Set<SemanticOperation> semanticOperations = getSemanticOperations(conf);



        for(SemanticOperation semanticOperation: semanticOperations){

            log.info("Obtaining predicates of operation {}", semanticOperation.getOperationId());
            setPredicates(semanticOperation, specification);

            Map<String, Set<String>> result = new HashMap<>();
            try{
                // Query DBPedia
                log.info("Querying DBPedia for operation {}...", semanticOperation.getOperationId());
                // Values are generated only if the size of Set<predicates> is greater than 0
                result = getParameterValues(semanticOperation.getSemanticParameters().stream().filter(x->!x.getPredicates().isEmpty()).collect(Collectors.toSet()));
            }catch(Exception e){
                log.error(e.getMessage());
            }

            semanticOperation.updateSemanticParametersValues(result);

        }

        if(semanticOperations.isEmpty()){
            log.info("No semantic operations found");
        }

        // Create dir for automatically generated csv files
        createDir(csvPath);
        log.info("Generating csv files");
        
        // Generate a csv file for each parameter
        // File name = OperationName_ParameterName
        // Delete file if it exists
        TestConfigurationObject newConf = conf;
        for(SemanticOperation semanticOperation: semanticOperations){

            Integer opIndex = IntStream.range(0, newConf.getTestConfiguration().getOperations().size())
                    .filter(i -> semanticOperation.getOperationId().equals(newConf.getTestConfiguration().getOperations().get(i).getOperationId()))
                    .findFirst().getAsInt();

            for(SemanticParameter semanticParameter: semanticOperation.getSemanticParameters()){
                String fileName = "/" + semanticOperation.getOperationName().replaceAll("<>","") + "_" + semanticParameter.getTestParameter().getName() + ".csv";
                String path = csvPath + fileName;
                deleteFile(path);
                createFileIfNotExists(path);

                // Write the Set of values as a csv file
                if(LIMIT == null){
                    collectionToCSV(path, semanticParameter.getValues());
                } else{
                    setToCSVWithLimit(path, semanticParameter.getValues());
                }

                // Update TestConfFile
                updateTestConf(newConf, semanticParameter, path, opIndex);

                log.info("Csv file generated in {}", path);

            }
        }

        // Write new test configuration to file
        TestConfigurationIO.toFile(newConf, semanticConfPath);
        log.info("Test configuration file updated");

        Timer.stopCounting(ALL);
        generateTimeReport();        // Iteration = 1

    }

    private static void generateTimeReport() {
        Path path = Paths.get(confPath);
        Path dir = path.getParent();
        Path fn = path.getFileSystem().getPath("time_ARTE.csv");
        Path target = (dir == null) ? fn : dir.resolve(fn);
        String timePath = target.toString();
        try {
            Timer.exportToCSV(timePath, 1);
        } catch (RuntimeException e) {
            log.error("The time report cannot be generated. Stack trace:");
            log.error(e.getMessage());
        }
        log.info("Time report generated.");
    }


    private static void setEvaluationParameters() {

        String OAISpecPath = readProperty(propertiesFilePath, "oas.path");
        confPath = readProperty(propertiesFilePath, "conf.path");
        specification = new OpenAPISpecification(OAISpecPath);
        csvPath = csvPath + specification.getSpecification().getInfo().getTitle();

        Path path = Paths.get(confPath);
        Path dir = path.getParent();
        Path fn = path.getFileSystem().getPath("testConfSemantic.yaml");
        Path target = (dir == null) ? fn : dir.resolve(fn);

        semanticConfPath = target.toString();

    }

    public static Set<SemanticOperation> getSemanticOperations(TestConfigurationObject testConfigurationObject){
        Set<SemanticOperation> semanticOperations = new HashSet<>();

        for(Operation operation: testConfigurationObject.getTestConfiguration().getOperations()){
            Set<TestParameter> semanticParameters = getSemanticParameters(operation);
            if(!semanticParameters.isEmpty()){
                log.info("Semantic operation {} added to list of semantic operations", operation.getOperationId());
                semanticOperations.add(new SemanticOperation(operation, semanticParameters));
            }
        }

        return semanticOperations;
    }

    private static Set<TestParameter> getSemanticParameters(Operation operation){

        Set<TestParameter> res = new HashSet<>();

        for(TestParameter testParameter: operation.getTestParameters()){
            int numberOfSemanticParameters = (int) testParameter.getGenerators().stream().filter(y -> y.getType().equalsIgnoreCase(SEMANTIC_PARAMETER)).count();

            if(numberOfSemanticParameters == 1){
                res.add(testParameter);
            }else if(numberOfSemanticParameters > 1){
                throw new IllegalArgumentException("There can only be one " + "'" + SEMANTIC_PARAMETER + "'" + " generator per parameter");
            }
        }

        return res;
    }

}

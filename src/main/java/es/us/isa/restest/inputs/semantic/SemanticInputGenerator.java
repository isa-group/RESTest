package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.*;

import es.us.isa.restest.specification.OpenAPISpecification;
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
import static es.us.isa.restest.util.FileManager.*;
import static es.us.isa.restest.util.PropertyManager.readProperty;
import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.SEMANTIC_PARAMETER;

public class SemanticInputGenerator {

    // Properties file with configuration settings
    private static String propertiesFilePath = "----";
    private static OpenAPISpecification specification;
    private static String OAISpecPath;
    private static String confPath;
    private static String semanticConfPath;
    private static String csvPath = "src/main/resources/TestData/Generated/";           // Path in which the generated input values will be stored
    public static final Integer THRESHOLD = 100;
    // DBPedia Endpoint     http://dbpedia.org/sparql       http://localhost:8890/sparql
    public static final String szEndpoint = "http://localhost:8890/sparql";

    private static final Logger log = LogManager.getLogger(SemanticInputGenerator.class);


    public static void main(String[] args) throws IOException {
        
        // ONLY FOR LOCAL COPY OF DBPEDIA
        System.setProperty("http.maxConnections", "10000");

        setEvaluationParameters();

        System.out.println(confPath);
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
                result = getParameterValues(semanticOperation.getSemanticParameters().stream().filter(x->x.getPredicates().size()>0).collect(Collectors.toSet()));
            }catch(Exception ex){
                System.err.println(ex);
            }

            semanticOperation.updateSemanticParametersValues(result);

        }

        if(semanticOperations.size() == 0){
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
                collectionToCSV(path, semanticParameter.getValues());

                // Update TestConfFile
                updateTestConf(newConf, semanticParameter, path, opIndex);

                log.info("Csv file generated in {}", path);

            }
        }

        // Write new test configuration to file
        TestConfigurationIO.toFile(newConf, semanticConfPath);
        log.info("Test configuration file updated");

    }


    private static void setEvaluationParameters() {

        OAISpecPath = readProperty(propertiesFilePath, "oas.path");
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
            if(semanticParameters.size() > 0){
                log.info("Semantic operation {} added to list of semantic operations", operation.getOperationId());
                semanticOperations.add(new SemanticOperation(operation, semanticParameters));
            }
        }

        return semanticOperations;
    }

    private static Set<TestParameter> getSemanticParameters(Operation operation){
        Set<TestParameter> res = operation.getTestParameters().stream()
                .filter(x-> x.getGenerators().stream().anyMatch(y-> y.getType().equalsIgnoreCase(SEMANTIC_PARAMETER)))
                .collect(Collectors.toSet());

        return res;
    }

}
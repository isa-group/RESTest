package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.*;

import es.us.isa.restest.specification.OpenAPISpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static es.us.isa.restest.inputs.semantic.Predicates.getPredicates;
import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.inputs.semantic.SPARQLUtils.*;
import static es.us.isa.restest.inputs.semantic.TestConfUpdate.updateTestConf;
import static es.us.isa.restest.util.FileManager.*;
import static es.us.isa.restest.util.PropertyManager.readProperty;
import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.SEMANTIC_PARAMETER;

public class SemanticInputGenerator {

    private static final Logger log = LogManager.getLogger(SemanticInputGenerator.class);
    private static OpenAPISpecification spec;
    private static String OAISpecPath;
    private static String confPath;
    private static String csvPath = "src/main/resources/TestData/Generated/";
    public static final Integer THRESHOLD = 200;


    public static void main(String[] args) throws IOException {
        setEvaluationParameters(readProperty("evaluation.properties.dir") + "/book.properties");

        TestConfigurationObject conf = loadConfiguration(confPath, spec);

        // Key: OperationName       Value: Parameters
        log.info("Obtaining semantic operations");
        List<SemanticOperation> semanticOperations = getSemanticOperations(conf);



        for(SemanticOperation semanticOperation: semanticOperations){

            log.info("Obtaining predicates of operation {}", semanticOperation.getOperationId());
            Map<TestParameter, List<String>> parametersWithPredicates = getPredicates(semanticOperation, spec);


            Map<String, Set<String>> result = new HashMap<>();
            try{
                // Query DBPedia
                log.info("Querying DBPedia for operation {}...", semanticOperation.getOperationId());
                result = getParameterValues(parametersWithPredicates);
            }catch(Exception ex){
                System.err.println(ex);
            }

            for(TestParameter testParameter: semanticOperation.getSemanticParameters().keySet()){
                Map<TestParameter, Set<String>> map = semanticOperation.getSemanticParameters();
                map.put(testParameter, result.get(testParameter.getName()));
                semanticOperation.setSemanticParameters(map);
            }

        }

        // Create dir for automatically generated csv files
        createDir(csvPath);
        log.info("Generating csv files");
        
        // Generate a csv file for each parameter
        // File name = OperationName_ParameterName
        // Delete file if it exists
        TestConfigurationObject newConf = conf;
        for(SemanticOperation operation: semanticOperations){

            Integer opIndex = IntStream.range(0, newConf.getTestConfiguration().getOperations().size())
                    .filter(i -> operation.getOperationId().equals(newConf.getTestConfiguration().getOperations().get(i).getOperationId()))
                    .findFirst().getAsInt();

            for(TestParameter parameter: operation.getSemanticParameters().keySet()){
                String fileName = "/" + operation.getOperationName() + "_" + parameter.getName() + ".csv";
                String path = csvPath + fileName;
                deleteFile(path);
                createFileIfNotExists(path);

                // Write the Set of values as a csv file
                FileWriter writer = new FileWriter(path);
                String collect = operation.getSemanticParameters().get(parameter)
                        .stream().collect(Collectors.joining("\n"));

                writer.write(collect);
                writer.close();

                // Update TestConfFile
                updateTestConf(newConf, parameter, path, opIndex);

                log.info("Csv file generated in {}", path);

            }
        }

        // Write new test configuration to file
        TestConfigurationIO.toFile(newConf, confPath);
        log.info("Test configuration file updated");

    }

    private static void setEvaluationParameters(String evalPropertiesFilePath) {
        OAISpecPath = readProperty(evalPropertiesFilePath, "oaispecpath");
        confPath = readProperty(evalPropertiesFilePath, "confpath");
        spec = new OpenAPISpecification(OAISpecPath);
        csvPath = csvPath + spec.getSpecification().getInfo().getTitle();
    }

    public static List<SemanticOperation> getSemanticOperations(TestConfigurationObject testConfigurationObject){
        List<SemanticOperation> semanticOperations = new ArrayList<>();

        for(Operation operation: testConfigurationObject.getTestConfiguration().getOperations()){
            List<TestParameter> semanticParameters = getSemanticParameters(operation);
            if(semanticParameters.size() > 0){
                log.info("Semantic operation {} added to list of semantic operations", operation.getOperationId());
                semanticOperations.add(new SemanticOperation(operation, semanticParameters));
            }
        }

        return semanticOperations;
    }

    private static List<TestParameter> getSemanticParameters(Operation operation){

        List<TestParameter> res = operation.getTestParameters().stream()
                .filter(x -> x.getGenerator().getType().equalsIgnoreCase(SEMANTIC_PARAMETER))
                .collect(Collectors.toList());

        return res;
    }

}

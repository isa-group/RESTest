package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.SemanticOperation;
import es.us.isa.restest.configuration.pojos.SemanticParameter;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.writers.postman.pojos.Query;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.droidmate.saigen.Lib;
import org.droidmate.saigen.storage.QueryResult;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.inputs.semantic.SemanticInputGenerator.getSemanticOperations;
import static es.us.isa.restest.inputs.semantic.TestConfUpdate.updateTestConf;
import static es.us.isa.restest.util.CSVManager.collectionToCSV;
import static es.us.isa.restest.util.FileManager.*;
import static es.us.isa.restest.util.PropertyManager.readProperty;


public class SAIGENInputGenerator {

    // Properties file with configuration settings
    private static String propertiesFilePath = "src/test/resources/SemanticAPIs/WeatherForecast14Days/weatherForecast14Days_original.properties";
    private static OpenAPISpecification specification;
    private static String OAISpecPath;
    private static String confPath;
    private static String saigenConfPath;
    private static String csvPath = "src/main/resources/TestData/SAIGEN/";           // Path in which the generated input values will be stored

    // Parameter minimum threshold of unique parameter values to obtain
    // The value of the minimum support parameter can be changed in the Predicates.java class of this same package
//    public static final Integer THRESHOLD = 100;
    // DBPedia Endpoint     http://dbpedia.org/sparql       http://localhost:8890/sparql
//    public static final String szEndpoint = "http://localhost:8890/sparql";

    private static final Logger log = LogManager.getLogger(SAIGENInputGenerator.class);

    public static void main(String[] args) throws IOException {

        // ONLY FOR LOCAL COPY OF DBPEDIA
        System.setProperty("http.maxConnections", "10000");

        setSaigenEvaluationParameters();

        System.out.println(confPath);
        TestConfigurationObject conf = loadConfiguration(confPath, specification);

        // key: OperationName   Value: Parameters
        log.info("Obtaining semantic operations for SAIGEN");
        Set<SemanticOperation> semanticOperations = getSemanticOperations(conf);



        for(SemanticOperation semanticOperation: semanticOperations){
            log.info("Leveraging SAIGEN for the operation {}", semanticOperation.getOperationId());

            List<String> parameterNames = getParameterNamesSaigen(semanticOperation);

            // Query SAIGEN
            List<String> parameterNamesLowercase = parameterNames.stream().map(String::toLowerCase).collect(Collectors.toList());
            List<QueryResult> queryResults = Lib.Companion.getInputsForLabels(parameterNamesLowercase);

            // Convert QueryResult to result (Map<String, Set<String>)
            // TODO: Check SAIGEN case sensitive
            Map<String, Set<String>> result = convertQueryResultsToMap(queryResults, parameterNames);

            // Update parameter values with SAIGEN
            semanticOperation.updateSemanticParametersValues(result);

        }

        if(semanticOperations.size() == 0){
            log.info("No semantic operations found");
        }

        // Create dir for automatically generated csv files
        createDir(csvPath);
        log.info("Generating csv files for SAIGEN");

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
        TestConfigurationIO.toFile(newConf, saigenConfPath);
        log.info("Test configuration file updated");

    }

    private static void setSaigenEvaluationParameters() {

        OAISpecPath = readProperty(propertiesFilePath, "oas.path");
        confPath = readProperty(propertiesFilePath, "conf.path");
        specification = new OpenAPISpecification(OAISpecPath);
        csvPath = csvPath + specification.getSpecification().getInfo().getTitle();

        Path path = Paths.get(confPath);
        Path dir = path.getParent();
        Path fn = path.getFileSystem().getPath("testConfSaigen.yaml");
        Path target = (dir == null) ? fn : dir.resolve(fn);

        saigenConfPath = target.toString();

    }

    private static List<String> getParameterNamesSaigen(SemanticOperation semanticOperation){
        List<String> res = new ArrayList<>();

        for(SemanticParameter parameter: semanticOperation.getSemanticParameters()){
            res.add(parameter.getTestParameter().getName());
        }

        return res;
    }

    // We use parameterNames because SAIGEN converts the label to lowercase
    private static Map<String, Set<String>> convertQueryResultsToMap(List<QueryResult> queryResults, List<String> parameterNames) {

        Map<String, Set<String>> res = new HashMap<>();

        for(String parameterName: parameterNames) {
            QueryResult values = queryResults.stream().filter(x-> x.getLabel().equals(parameterName.toLowerCase())).findFirst().orElse(null);
            if (values != null) {
                Set<String> setOfValues = new HashSet<>();
                setOfValues.addAll(values.getValues());
                res.put(parameterName, setOfValues);
            } else {
                res.put(parameterName, new HashSet<String>());
            }

        }

        return res;
    }


}

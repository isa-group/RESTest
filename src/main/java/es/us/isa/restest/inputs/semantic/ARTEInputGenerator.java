package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.*;

import es.us.isa.restest.inputs.semantic.objects.SemanticOperation;
import es.us.isa.restest.inputs.semantic.objects.SemanticParameter;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.util.Timer;
import org.apache.jena.query.ARQ;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    private static  String propertiesFilePath = "src/test/resources/semanticAPITests/ClimaCell/climacell.properties";
    private static OpenAPISpecification specification;
    private static String confPathOriginal;
    private static String semanticConfPath;
    private static String csvPath;           // Path in which the generated input values will be stored

    // Parameters
    // Minimum support of a predicate
    public static Integer minSupport = 20;
    // Parameter minimum threshold of unique parameter values to obtain: default 100
    public static Integer THRESHOLD = 100;
    // Limit
    public static Integer LIMIT = null;
    // Proxy
    public static String proxy = null;
    // DBPedia Endpoint
    public static final String szEndpoint = PropertyManager.readProperty("arte.endpoint");


    private static final Logger log = LogManager.getLogger(ARTEInputGenerator.class);

    /*
     * There seems to be a problem with using Apache Jena when packaged into a fat JAR
     * (e.g., when running RESTest as a JAR). This is somehow related with the Maven
     * Shade plugin [1]. A workaround [2] is to initialize org.apache.jena.query.ARQ
     * before any query (org.apache.jena.query.QueryFactory.create), so we do it here
     * statically to make sure that it always happens.
     *
     * [1] https://jena.apache.org/documentation/notes/jena-repack.html
     * [2] https://stackoverflow.com/questions/54905185/how-to-debug-nullpointerexception-at-apache-jena-queryexecutionfactory-during-cr
     */
    static {
        ARQ.init();
    }

    public static void main(String[] args) {

        Timer.startCounting(ALL);

        if (args.length > 0) {
            propertiesFilePath = args[0];
            minSupport = Integer.parseInt(args[1]);
            THRESHOLD = Integer.parseInt(args[2]);

            try {
                LIMIT = Integer.parseInt(args[3]);
            } catch(NumberFormatException nfe) {
                LIMIT = null;
            }

            if (args.length <= 4 || "null".equals(args[4]) || args[4].split(":").length != 2)
                proxy = null;
            else
                proxy = args[4];
        }
        
        // ONLY FOR LOCAL COPY OF DBPEDIA
        if (szEndpoint.contains("localhost") || szEndpoint.contains("127.0.0.1"))
            System.setProperty("http.maxConnections", "10000");

        // ONLY FOR WHEN PROXY IS REQUIRED
        if (proxy != null) {
            System.setProperty("http.proxyHost", proxy.split(":")[0]);
            System.setProperty("http.proxyPort", proxy.split(":")[1]);
            System.setProperty("http.nonProxyHosts", "localhost|127.0.0.1");
            System.setProperty("https.proxyHost", proxy.split(":")[0]);
            System.setProperty("https.proxyPort", proxy.split(":")[1]);
            System.setProperty("https.nonProxyHosts", "localhost|127.0.0.1");
        }

        setEvaluationParameters();

        log.info(confPathOriginal);
        TestConfigurationObject conf = loadConfiguration(confPathOriginal, specification);

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
        Path path = Paths.get(confPathOriginal);
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
        confPathOriginal = readProperty(propertiesFilePath, "conf.path");
        specification = new OpenAPISpecification(OAISpecPath);
        csvPath = PropertyManager.readProperty("arte.generatedInputValuesPath") + specification.getSpecification().getInfo().getTitle();

        Path path = Paths.get(confPathOriginal);
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

        if (operation.getTestParameters() != null) {
            for (TestParameter testParameter : operation.getTestParameters()) {
                int numberOfSemanticParameters = (int) testParameter.getGenerators().stream().filter(y -> y.getType().equalsIgnoreCase(SEMANTIC_PARAMETER)).count();

                if (numberOfSemanticParameters == 1) {
                    res.add(testParameter);
                } else if (numberOfSemanticParameters > 1) {
                    throw new IllegalArgumentException("There can only be one " + "'" + SEMANTIC_PARAMETER + "'" + " generator per parameter");
                }
            }
        }

        return res;
    }

}

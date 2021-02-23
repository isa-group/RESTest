package es.us.isa.restest.inputs.semantic.testing;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.ParameterValues;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.specification.OpenAPISpecification;
import it.units.inginf.male.outputs.FinalSolution;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.RANDOM_INPUT_VALUE;
import static es.us.isa.restest.inputs.semantic.Predicates.getPredicates;
import static es.us.isa.restest.inputs.semantic.Predicates.getPredicatesToIgnore;
import static es.us.isa.restest.inputs.semantic.SPARQLUtils.getNewValues;
import static es.us.isa.restest.inputs.semantic.TestConfUpdate.updateTestConfWithNewPredicates;
import static es.us.isa.restest.inputs.semantic.regexGenerator.RegexGeneratorUtils.*;
import static es.us.isa.restest.inputs.semantic.testing.MainTesting.readCsv;
import static es.us.isa.restest.main.TestGenerationAndExecution.getTestConfigurationObject;
import static es.us.isa.restest.util.PropertyManager.readProperty;

public class MainTestingSkyscannerRegex {

    // Parámetros a cambiar
    private static String propertiesPath = "src/test/resources/SemanticAPIs/SkyscannerFlightSearch/skyscanner.properties";
    private static String operationPath = "/apiservices/browseroutes/v1.0/{country}/{currency}/{locale}/{originplace}/{destinationplace}/{outboundpartialdate}/{inboundpartialdate}";
    private static String semanticParameterName = "country";   // "currency";
    private static String baseUrl = "https://skyscanner-skyscanner-flight-search-v1.p.rapidapi.com";
    private static String apiKey = "-----";

    // Parámetros derivados
    private static OpenAPISpecification spec;
    private static String confPath;
    private static String OAISpecPath;
    private static Operation operation;
    private static TestParameter testParameter;
    private static ParameterValues parameterValues;
    private static String host;
    private static TestConfigurationObject conf;

    public static void main(String[] args) throws IOException, InterruptedException {

        Set<String> validSet = new HashSet<>();
        Set<String> invalidSet = new HashSet<>();

        // DELETE (ONLY FOR LOCAL COPY OF DBPEDIA)
        System.setProperty("http.maxConnections", "10000000");

        setParameters();

        String csvPath = getCsvPath();
        List<String> semanticInputs = readCsv(csvPath);
        System.out.println("Number of inputs " + semanticInputs.size());

        Collections.shuffle(semanticInputs);

        ParameterValues parameterValues = new ParameterValues("skyscanner", operation, testParameter);



        // API Calls
        int i = 1;
        for(String semanticInput: semanticInputs){
            try {

                System.out.println("Iteration number: " + i);
                i++;
                System.out.println(semanticInput);

                // Generating regex for currency
//                String url = baseUrl + "/apiservices/browseroutes/v1.0/UK/"+ semanticInput + "/en-US/SFO-sky/JFK-sky/anytime/anytime";
                // Generating regex for country
                String url = baseUrl + "/apiservices/browseroutes/v1.0/" + semanticInput + "/USD/en-US/SFO-sky/JFK-sky/anytime/anytime";
//                                      /apiservices/browseroutes/v1.0/{country}/{currency}/{locale}/{originplace}/{destinationplace}/{outboundpartialdate}/{inboundpartialdate}


                OkHttpClient client = new OkHttpClient();

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("x-rapidapi-key", apiKey)
                        .addHeader("x-rapidapi-host", "skyscanner-skyscanner-flight-search-v1.p.rapidapi.com")
                        .build();

                Response response = client.newCall(request).execute();

                if(response.code() == 200){
                    validSet.add(semanticInput);
                    System.out.println(semanticInput + " added to valid set");
                }else{
                    invalidSet.add(semanticInput);
                    System.out.println(semanticInput + " added to invalid set");
                }

                System.out.println("RESPONSE CODE: " + response.code());
                System.out.println(response.body().string());
                System.out.println("Valid values: " + validSet);
                System.out.println("Invalid values: " + invalidSet);
                System.out.println("--------------------------------------------------------------------------------------");

                TimeUnit.SECONDS.sleep(3);

                if(validSet.size() >= 5 && invalidSet.size() >= 5){
                    List<String> predicatesToIgnore = getPredicatesToIgnore(parameterValues.getTestParameter());
                    String name = parameterValues.getOperation().getOperationId() + "_" + parameterValues.getTestParameter().getName();

                    System.out.println("Generating regex...");
                    FinalSolution solution = learnRegex(name, validSet, invalidSet,false);
                    String regex = solution.getSolution();

                    System.out.println("Regex learned: " + regex);
                    System.out.println("Accuracy: " + solution.getValidationPerformances().get("character accuracy"));
                    System.out.println("Precision: " + solution.getValidationPerformances().get("match precision"));
                    System.out.println("Recall: " + solution.getValidationPerformances().get("match recall"));
                    System.out.println("F1-Score: " + solution.getValidationPerformances().get("match f-measure"));

                    System.out.println("--------------------------------------------------------------------------------------");

                    TimeUnit.SECONDS.sleep(3);

                    if(solution.getValidationPerformances().get("match recall") >= 0.9){
                        System.out.println("Updating csv with regex");
                        updateCsvWithRegex(parameterValues, regex);

                        // Update CSVs of valid and invalid values according to the generated regex
                        updateCsvWithRegex(parameterValues.getValidCSVPath(), regex);
                        updateCsvWithRegex(parameterValues.getInvalidCSVPath(), regex);

                        if(predicatesToIgnore.size() < 2){
                            Set<String> predicates = getPredicates(parameterValues, regex, predicatesToIgnore, spec);
                            System.out.println("Predicates obtained: " + predicates);

                            if(predicates.size()>0){
                                Set<String> results = getNewValues(parameterValues, predicates, regex);
                                System.out.println("Results: " + results);

                                // Add results to the corresponding CSV Path
                                addResultsToCSV(parameterValues, results);

                                // Add predicate to TestParameter and update testConf file
                                updateTestConfWithNewPredicates(conf, confPath, parameterValues, predicates);


                                System.out.println("Second predicate successfully obtained");


                            }

                        }

                        break;

                    }

                }

            }catch (Exception e){
                e.printStackTrace();
            }

            TimeUnit.SECONDS.sleep(3);

        }


    }

    private static void setParameters(){
        OAISpecPath = readProperty(propertiesPath, "oas.path");
        confPath = readProperty(propertiesPath, "conf.path");
        spec = new OpenAPISpecification(OAISpecPath);

        conf = loadConfiguration(confPath, spec);

        operation = conf.getTestConfiguration().getOperations().stream().filter(x -> x.getTestPath().equals(operationPath)).findFirst().get();
        testParameter = operation.getTestParameters().stream().filter(x->x.getName().equals(semanticParameterName)).findFirst().get();
        host = operation.getTestParameters().stream()
                .filter(x-> x.getName().equals("X-RapidAPI-Host")).findFirst().get()
                .getGenerators().stream().filter(x->x.getType().equals(RANDOM_INPUT_VALUE)).findFirst().get()
                .getGenParameters().stream().filter(x->x.getName().equals("values")).findFirst().get()
                .getValues().get(0);

    }

    private static String getCsvPath(){
        return testParameter
                .getGenerators().stream().filter(x -> x.getType().equals(RANDOM_INPUT_VALUE)).findFirst().get()
                .getGenParameters().stream().filter(x->x.getName().equals("csv")).findFirst().get()
                .getValues().get(0);
    }

}

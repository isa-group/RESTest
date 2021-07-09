package es.us.isa.restest.inputs.semantic.testing;

import com.github.jsonldjava.utils.Obj;
import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.ParameterValues;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.specification.OpenAPISpecification;
import it.units.inginf.male.outputs.FinalSolution;

import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.json.Json;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.RANDOM_INPUT_VALUE;
import static es.us.isa.restest.inputs.semantic.Predicates.getPredicates;
import static es.us.isa.restest.inputs.semantic.Predicates.getPredicatesToIgnore;
import static es.us.isa.restest.inputs.semantic.SPARQLUtils.getNewValues;
import static es.us.isa.restest.inputs.semantic.TestConfUpdate.updateTestConfWithNewPredicates;
import static es.us.isa.restest.inputs.semantic.regexGenerator.RegexGeneratorUtils.*;
import static es.us.isa.restest.inputs.semantic.testing.MainTesting.readCsv;
import static es.us.isa.restest.inputs.semantic.testing.api.AeroDataBox.aeroDataBox_FlightTimeToAnotherAirportByIataIcao_codeTo_regex;
import static es.us.isa.restest.inputs.semantic.testing.api.AirportIX.*;
import static es.us.isa.restest.inputs.semantic.testing.api.AlphaVantage.alphaVantage_currencyExchangeRate_fromCurrency_regex;
import static es.us.isa.restest.inputs.semantic.testing.api.AlphaVantage.alphaVantage_currencyExchangeRate_toCurrency_regex;
import static es.us.isa.restest.inputs.semantic.testing.api.ApiBasketball.apiBasketball_standings_season_regex;
import static es.us.isa.restest.inputs.semantic.testing.api.ApiFootball.apiFootball_leaguesCountryCountryNameSeason_season_regex;
import static es.us.isa.restest.inputs.semantic.testing.api.AviationReferenceData.aviationReferenceData_airport_code_regex;
import static es.us.isa.restest.inputs.semantic.testing.api.CoronavirusMap.coronavirusMap_region;
import static es.us.isa.restest.inputs.semantic.testing.api.FlightData.flightData_cityDirections_currency_regex;
import static es.us.isa.restest.inputs.semantic.testing.api.GeoDBCities.geoDbCities_administrativeDivisions_languageCode_regex;
import static es.us.isa.restest.inputs.semantic.testing.api.RealtyInUs.realtyInUs_mortageCheckEquitityRates_state_regex;
import static es.us.isa.restest.inputs.semantic.testing.api.RealtyMoleProperty.realtyMoleProperty_saleListings_state_regex;
import static es.us.isa.restest.inputs.semantic.testing.api.RedlineZipcode.redlineStateToZipcode_regex;
import static es.us.isa.restest.inputs.semantic.testing.api.YahooFinance.yahooFinanceGetFinancialData_regex;
import static es.us.isa.restest.util.PropertyManager.readProperty;

public class MainTestingRegexGeneration {

    // Parameters to change
    private static final String propertiesPath = "----";
    private static final String operationPath = "/saleListings";
    private static final String semanticParameterName = "state";
    private static final String apiKey = "---";

    // Derived parameters
    private static OpenAPISpecification spec;
    private static String confPath;
    private static String OAISpecPath;
    private static Operation operation;
    private static TestParameter testParameter;
    private static ParameterValues parameterValues;
    private static String host;
    private static TestConfigurationObject conf;


    public static void main(String[] args) {

        // This class is used to test the automatic generation of regular expressions for the RapidAPI dataset. (Experiment 1 part 2)
        // This experiment consists on creating API calls until obtaining 5 valid values and 5 invalid values of a given parameter
        // If a regex that achieves a performance over 90% of recall, the data will be filtered
        // The APIs for which results are obtained are Flight data (parameter country), API basketball (parameter season) and API football (parameter season)
        // There is a different file for testing Skyscanner API (MainTestingSkyscannerRegex)

        // (ONLY FOR LOCAL COPY OF DBPEDIA)
        System.setProperty("http.maxConnections", "10000000");

        setParameters();

        Set<String> validSet = new HashSet<>();
        Set<String> invalidSet = new HashSet<>();

        String csvPath = getCsvPath();
        List<String> semanticInputs = readCsv(csvPath);
        System.out.println("Number of inputs " + semanticInputs.size());

//        Collections.shuffle(semanticInputs);

        ParameterValues parameterValues = new ParameterValues("RealtyMoleProperty_semantic", operation, testParameter);      // TODO: Change

        // API call
        int i = 1;
        for(String semanticInput: semanticInputs){
            try{
                System.out.println("Iteration number: " + i);
                i++;
                System.out.println(semanticInput);
                Response response = realtyMoleProperty_saleListings_state_regex(semanticInput, apiKey, host);        // TODO: Replace

                System.out.println("###########");
                System.out.println(response);

                // TODO: Realty Mole Property
                if (response == null || response.code() != 200) {     // TODO: DELETE
                    invalidSet.add(semanticInput);
                    System.out.println(semanticInput + " added to invalid");
                } else {
                    validSet.add(semanticInput);
                    System.out.println(semanticInput + " added to valid");
                }

//                validation_errors400Code(response, semanticInput, validSet, invalidSet);                    // TODO: Replace

                System.out.println("Valid values: " + validSet);
                System.out.println("Invalid values: " + invalidSet);
                System.out.println("--------------------------------------------------------------------------------------");

                TimeUnit.SECONDS.sleep(3);



                if(validSet.size()>=5 && invalidSet.size()>=5){

                    List<String> predicatesToIgnore = getPredicatesToIgnore(parameterValues.getTestParameter());

                    System.out.println("PREDICATES TO IGNORE: ");
                    System.out.println(predicatesToIgnore);

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


    // Validation function
    // VALIDATION in  ApiFootball (the errors are shown in a parameter)
    public static void validation_apiFootball(String response, String semanticInput, Set<String> validSet, Set<String> invalidSet){
        JSONObject Jobject = new JSONObject(response);
        JSONObject api = Jobject.getJSONObject("api");

        Boolean isValid = true;
        if(api.has("error")){
            isValid = false;
        }

        if(isValid){
            validSet.add(semanticInput);
            System.out.println(semanticInput + " added to valid");
        }else{
            invalidSet.add(semanticInput);
            System.out.println(semanticInput + " added to invalid");
        }

    }

    // Validation function
    // VALIDATION in  AirportIX (the errors are shown in a parameter)
    public static void validation_airportIX(String response, String semanticInput, Set<String> validSet, Set<String> invalidSet){
        JSONObject Jobject = new JSONObject(response);

        Boolean isValid = !Jobject.getBoolean("error");

        if(isValid){
            validSet.add(semanticInput);
            System.out.println(semanticInput + " added to valid");
        }else{
            invalidSet.add(semanticInput);
            System.out.println(semanticInput + " added to invalid");
        }

    }

    // VALIDATION IN Yahoo Finance
    public static void validation_yahooFinance(String response, String semanticInput, Set<String> validSet, Set<String> invalidSet){
        JSONObject Jobject = new JSONObject(response);

        Boolean isValid = true;
        if(Jobject.has("error")){
            isValid = false;
        }

        if(isValid){
            validSet.add(semanticInput);
            System.out.println(semanticInput + " added to valid");
        }else{
            invalidSet.add(semanticInput);
            System.out.println(semanticInput + " added to invalid");
        }

    }

    // VALIDATION IN Alpha vantage
    public static void validation_alphaVantage(String response, String semanticInput, Set<String> validSet, Set<String> invalidSet){
        JSONObject Jobject = new JSONObject(response);

        Boolean isValid = true;
        if(Jobject.has("Error Message")){
            isValid = false;
        }

        if(isValid){
            validSet.add(semanticInput);
            System.out.println(semanticInput + " added to valid");
        }else{
            invalidSet.add(semanticInput);
            System.out.println(semanticInput + " added to invalid");
        }

    }

    // VALIDATION in ApiBasketball (the errors are shown in a parameter)
    public static void validation_apiBasketball(String response, String semanticInput, Set<String> validSet, Set<String> invalidSet){
        JSONObject Jobject = new JSONObject(response);
        Object errors = Jobject.get("errors");

        Boolean isValid = false;
        if (errors instanceof JSONObject){
            isValid = false;
        }else{
            JSONArray jsonArray = (JSONArray) errors;
            isValid = jsonArray.length() == 0;
        }

        if(isValid){
            validSet.add(semanticInput);
            System.out.println(semanticInput + " added to valid");
        }else{
            invalidSet.add(semanticInput);
            System.out.println(semanticInput + " added to invalid");
        }
    }

    // VALIDATION IN Realty in US
    // Check equitity rates
    public static void validation_realtyInUs(String response, String semanticInput, Set<String> validSet, Set<String> invalidSet){
        JSONObject Jobject = new JSONObject(response);

        Boolean isValid = true;
        if(Jobject.has("status")){
            isValid = false;
        }

        if(isValid){
            validSet.add(semanticInput);
            System.out.println(semanticInput + " added to valid");
        }else{
            invalidSet.add(semanticInput);
            System.out.println(semanticInput + " added to invalid");
        }

    }

    // VALIDATION IN Skyscanner, flightData, redline zipcode, Realty Mole Property, GeoDBCity, AeroDataBox, AviationReferenceData (errors with 4XX code)
    public static void validation_errors400Code(Response response, String semanticInput, Set<String> validSet, Set<String> invalidSet) {
        if(response.code() == 200){
            validSet.add(semanticInput);
            System.out.println(semanticInput + " added to valid");
        }else{
            invalidSet.add(semanticInput);
            System.out.println(semanticInput + " added to invalid");
        }

    }

}

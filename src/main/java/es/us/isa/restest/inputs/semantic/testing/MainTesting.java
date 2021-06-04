package es.us.isa.restest.inputs.semantic.testing;

import com.squareup.okhttp.*;
import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.RANDOM_INPUT_VALUE;
import static es.us.isa.restest.inputs.semantic.testing.api.AirportInfo.airportInfo_iata;
import static es.us.isa.restest.inputs.semantic.testing.api.AirportInfo.airportInfo_icao;
import static es.us.isa.restest.inputs.semantic.testing.api.ApiBasketball.*;
import static es.us.isa.restest.inputs.semantic.testing.api.ApiFootball.*;
import static es.us.isa.restest.inputs.semantic.testing.api.Asos.*;
import static es.us.isa.restest.inputs.semantic.testing.api.CarbonFootprint.carbonFootprint_PM;
import static es.us.isa.restest.inputs.semantic.testing.api.Climacell.climacell_lat;
import static es.us.isa.restest.inputs.semantic.testing.api.Climacell.climacell_lon;
import static es.us.isa.restest.inputs.semantic.testing.api.CoronavirusMap.coronavirusMap_region;
import static es.us.isa.restest.inputs.semantic.testing.api.CountriesCities.*;
import static es.us.isa.restest.inputs.semantic.testing.api.CurrencyConverter.*;
import static es.us.isa.restest.inputs.semantic.testing.api.FixerCurrency.*;
import static es.us.isa.restest.inputs.semantic.testing.api.FlightData.*;
import static es.us.isa.restest.inputs.semantic.testing.api.GoogleMapsGeocoding.*;
import static es.us.isa.restest.inputs.semantic.testing.api.GreatCircleMapper.greatCircleMapper_iataIcao;
import static es.us.isa.restest.inputs.semantic.testing.api.Hotels.*;
import static es.us.isa.restest.inputs.semantic.testing.api.MovieDatabase.*;
import static es.us.isa.restest.inputs.semantic.testing.api.OpenWeatherMap.*;
import static es.us.isa.restest.inputs.semantic.testing.api.PublicHoliday.publicHoliday_countryCode;
import static es.us.isa.restest.inputs.semantic.testing.api.PublicHoliday.publicHoliday_year;
import static es.us.isa.restest.inputs.semantic.testing.api.RedlineZipcode.*;
import static es.us.isa.restest.inputs.semantic.testing.api.RentEstimate.*;
import static es.us.isa.restest.inputs.semantic.testing.api.SimilarWeb.similarweb_website;
import static es.us.isa.restest.inputs.semantic.testing.api.Skyscanner.*;
import static es.us.isa.restest.inputs.semantic.testing.api.Spott.*;
import static es.us.isa.restest.inputs.semantic.testing.api.SubtitlesForYoutube.getSubtitleJson;
import static es.us.isa.restest.inputs.semantic.testing.api.TrueWayGeocoding.*;
import static es.us.isa.restest.inputs.semantic.testing.api.UsRestaurantMenus.*;
import static es.us.isa.restest.inputs.semantic.testing.api.UsWeatherByZipcode.UsWeatherByZipcode_zipcode;
import static es.us.isa.restest.inputs.semantic.testing.api.WeatherForecast14Days.*;
import static es.us.isa.restest.inputs.semantic.testing.api.YahooFinance.yahooFinanceGetFinancialData;
import static es.us.isa.restest.util.PropertyManager.readProperty;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;


public class MainTesting {

    // Parameters to change
    private static final String propertiesPath = "src/test/resources/SemanticAPIs/Spott/spott_saigen.properties";
    private static final String operationPath = "/places";
    private static final String semanticParameterName = "language";
    private static final Integer limit = Integer.MAX_VALUE;
    private static final String apiKey = "6a615b46f4mshab392a25b2bc44dp16cee9jsn2bd2d62e5f69";

    // Derived parameters
    private static OpenAPISpecification spec;
    private static String confPath;
    private static String OAISpecPath;
    private static Operation operation;
    private static String host;
    private static TestConfigurationObject conf;

    public static void main(String[] args) throws IOException, InterruptedException {
        setParameters(propertiesPath);

        String csvPath = getCsvPath();
        List<String> semanticInputs = readCsv(csvPath);


        System.out.println("Number of inputs " + semanticInputs.size());

        int maxCut = (limit < 10) ? limit : 10;

        Collections.shuffle(semanticInputs);

        // Select 10 random values
        List<String> randomSubList = semanticInputs.subList(0, Math.min(maxCut, semanticInputs.size()));


        // API Calls
        int i = 1;
        for(String semanticInput: randomSubList){
            try {

                System.out.println(semanticInput);

                System.out.println("Iteration number " + i + "/" + maxCut);

                // RapidAPI operation to test
                // In some cases it is required to change an attribute of the API class (e.g., operationPath in api/Climacell.java)
                // Note that there is a different file for the Skyscanner API
                spottLanguage(semanticInput, apiKey, host);        // TODO: REPLACE

                i++;


            }catch (Exception e){
                System.err.println("Exception ocurred");
            }

            TimeUnit.SECONDS.sleep(2);

        }


    }

    private static void setParameters(String propertyPath){
        OAISpecPath = readProperty(propertyPath, "oas.path");
        confPath = readProperty(propertyPath, "conf.path");
        spec = new OpenAPISpecification(OAISpecPath);

        conf = loadConfiguration(confPath, spec);

        operation = conf.getTestConfiguration().getOperations().stream().filter(x -> x.getTestPath().equals(operationPath)).findFirst().get();
        host = operation.getTestParameters().stream()
                .filter(x-> x.getName().equals("X-RapidAPI-Host")).findFirst().get()
                .getGenerators().stream().filter(x->x.getType().equals(RANDOM_INPUT_VALUE)).findFirst().get()
                .getGenParameters().stream().filter(x->x.getName().equals("values")).findFirst().get()
                .getValues().get(0);

    }

    private static String getCsvPath(){
        return operation.getTestParameters().stream()
                .filter(x-> x.getName().equals(semanticParameterName))
                .findFirst().get()
                .getGenerators().stream().filter(x -> x.getType().equals(RANDOM_INPUT_VALUE)).findFirst().get()
                .getGenParameters().stream().filter(x->x.getName().equals("csv")).findFirst().get()
                .getValues().get(0);
    }

    public static List<String> readCsv(String csvFile) {

        List<String> res = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(csvFile));
            String line = "";
            while((line = br.readLine()) != null) {
                res.add(line);
            }
            br.close();
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
        return res;
    }

    public static void printResponse(String uri) throws IOException {
        System.out.println(uri);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(uri)
                .get()
                .addHeader("x-rapidapi-host", host)
                .addHeader("x-rapidapi-key", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");


    }

    public static void printResponsePost(String uri, String bodyString) throws IOException {
        System.out.println(uri);

        OkHttpClient client = new OkHttpClient();

        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
        RequestBody body = RequestBody.create(mediaType, bodyString);

        Request request = new Request.Builder()
                .url(uri)
                .post(body)
                .addHeader("x-rapidapi-host", host)
                .addHeader("x-rapidapi-key", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");


    }



    public static void printResponseFlightData(String uri) throws IOException {
        System.out.println(uri);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(uri)
                .get()
                .addHeader("x-access-token", "5b3ef0bdeca04643188c6610c30056f5")
                .addHeader("x-rapidapi-host", host)
                .addHeader("x-rapidapi-key", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");


    }


}

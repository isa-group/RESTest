package es.us.isa.restest.inputs.semantic.testing;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
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
import static es.us.isa.restest.util.PropertyManager.readProperty;


public class MainTestingAmadeusHotel {

    /*
    ARTE has generated a regular expression for the parameter hotelName of this API and has generated new input values. In order to properly recreate
    Experiment 1, change the path to the csv files in the testConfSemantic file to hotelName_beforeRegex or generate the input values again (Demo ARTE.mp4)
     */

    // Parameters to change
    private static String propertiesPath = "src/test/resources/SemanticAPIs/CommercialAPIs/AmadeusHotel/amadeusHotelSaigen_hotelOffers.properties";
    private static String operationPath = "/shopping/hotel-offers";
    private static String semanticParameterName = "longitude";
    private static String baseUrl = "https://test.api.amadeus.com/v2";
    private static Integer limit = 9;
    private static String bearer = "---";

    // Derived parameters
    private static OpenAPISpecification spec;
    private static String confPath;
    private static String OAISpecPath;
    private static Operation operation;
    private static TestConfigurationObject conf;

    public static void main(String[] args) throws IOException, InterruptedException {
        setParameters(propertiesPath);

        String csvPath = getCsvPath();
        List<String> semanticInputs = readCsv(csvPath);


        System.out.println("Number of inputs " + semanticInputs.size());

        Integer maxCut = (limit < 10) ? limit : 10;

        Collections.shuffle(semanticInputs);

        // Select 10 random values
        List<String> randomSubList = semanticInputs.subList(0, maxCut);

        // API Calls
        int i = 1;
        for(String semanticInput: randomSubList){
            try {

                System.out.println(semanticInput);

//                String query = "?cityCode="+ semanticInput + "&radius=300&radiusUnit=KM";
//                String query = "?hotelId=RTVLCBON&lang=" + semanticInput;//"?term=a";
//                String query = "?cityCode=PAR&radius=300&radiusUnit=KM&currency=" + semanticInput;
                String query = "?radius=300&radiusUnit=KM&latitude=49.288&longitude=" + semanticInput;
                String url = baseUrl + operationPath + query;


                System.out.println(url);

                OkHttpClient client = new OkHttpClient();

                client.setConnectTimeout(30, TimeUnit.SECONDS); // connect timeout
                client.setReadTimeout(30, TimeUnit.SECONDS);    // socket timeout

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("Authorization", "Bearer " + bearer)
                        .build();

                Response response = client.newCall(request).execute();

                System.out.println("Iteration number " + i + "/" + maxCut);

                System.out.println("RESPONSE CODE: " + response.code());
                System.out.println(response.body().string());
                System.out.println("--------------------------------------------------------------------------------------");


                i++;
            }catch (Exception e){
                System.out.println(e);
            }

            TimeUnit.SECONDS.sleep(3);

        }


    }

    private static void setParameters(String propertyPath){
        OAISpecPath = readProperty(propertyPath, "oas.path");
        confPath = readProperty(propertyPath, "conf.path");
        spec = new OpenAPISpecification(OAISpecPath);

        conf = loadConfiguration(confPath, spec);

        operation = conf.getTestConfiguration().getOperations().stream().filter(x -> x.getTestPath().equals(operationPath)).findFirst().get();

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


}

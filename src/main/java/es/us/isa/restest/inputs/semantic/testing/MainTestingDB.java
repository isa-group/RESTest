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


public class MainTestingDB {

    // Parámetros a cambiar
    private static String propertiesPath = "/semantic/commercial/db.properties";
    private static String operationPath = "/stations";
    private static String semanticParameterName = "eva";
    private static String baseUri = "https://api.deutschebahn.com/stada/v2";
    private static Integer limit = Integer.MAX_VALUE;
    private static String apiKey = "Bearer --";


    // Parámetros derivados
    private static OpenAPISpecification spec;
    private static String confPath;
    private static String OAISpecPath;
    private static Operation operation;
    private static TestConfigurationObject conf;

    public static void main(String[] args) throws IOException, InterruptedException {
        setParameters(readProperty("evaluation.properties.dir") + propertiesPath);

        String csvPath = getCsvPath();
        List<String> semanticInputs = readCsv(csvPath);


        System.out.println("Number of inputs " + semanticInputs.size());

        Integer maxCut = (limit < 10) ? limit : 10;

        Collections.shuffle(semanticInputs);

        // Select 20 random values
        List<String> randomSubList = semanticInputs.subList(0, 25);

        // API Calls
        int i = 1;
        for(String semanticInput: randomSubList){
            try {

                System.out.println(semanticInput);

                System.out.println("Iteration number " + i + "/" + maxCut);

                db_stations_eva(semanticInput, apiKey);      // MODIFY

                i++;
            }catch (Exception e){
                System.out.println(e);
            }

            TimeUnit.SECONDS.sleep(1);

        }


    }

    private static void setParameters(String propertyPath){
        OAISpecPath = readProperty(propertyPath, "oaispecpath");
        confPath = readProperty(propertyPath, "confpath");
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





    //  ------------------------------------------- OPERATIONS -----------------------------------------------
    // GET /stations
    // federalstate
    public static void db_stations_federalstate(String semanticInput, String apiKey) throws IOException {

        String url = baseUri + "/stations?federalstate=" + semanticInput;

        System.out.println(url);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
//        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");
    }

    // eva
    public static void db_stations_eva(String semanticInput, String apiKey) throws IOException {

        String url = baseUri + "/stations?eva=" + semanticInput;

        System.out.println(url);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");
    }

}

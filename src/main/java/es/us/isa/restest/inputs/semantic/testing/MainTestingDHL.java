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
import static es.us.isa.restest.util.PropertyManager.readProperty;


public class MainTestingDHL {

    // Parámetros a cambiar
    private static String propertiesPath = "/semantic/commercial/dhl.properties";
    private static String operationPath = "/find-by-address";
    private static String semanticParameterName = "countryCode";
    private static String baseUri = "https://api.dhl.com/location-finder/v1";
    private static Integer limit = Integer.MAX_VALUE;
    private static String apiKey = "----";


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

        Integer maxCut = (limit < 20) ? limit : 20;

        Collections.shuffle(semanticInputs);

        // Select 20 random values
        List<String> randomSubList = semanticInputs.subList(0, maxCut);

        // API Calls
        int i = 1;
        for(String semanticInput: randomSubList){
            try {

                System.out.println(semanticInput);

                System.out.println("Iteración número " + i + "/" + maxCut);

                dhl_findByAddress_countryCode(semanticInput, apiKey);      // TODO: MODIFY

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
                .getGenerator()
                .getGenParameters().get(0).getValues().get(0);
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
    // GET /find-by-geo
    // latitude
    public static void dhl_findByGeo_latitude(String semanticInput, String apiKey) throws IOException {

        String url = baseUri + "/find-by-geo?longitude=-122.4282&radius=15000&latitude=" + semanticInput;

        System.out.println(url);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("DHL-API-Key", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");
    }

    // longitude
    public static void dhl_findByGeo_longitude(String semanticInput, String apiKey) throws IOException {

        String url = baseUri + "/find-by-geo?latitude=37.7674&radius=15000&longitude=" + semanticInput;

        System.out.println(url);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("DHL-API-Key", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");
    }

    // GET /find-by-address
    // countryCode
    public static void dhl_findByAddress_countryCode(String semanticInput, String apiKey) throws IOException {

        String url = baseUri + "/find-by-address?postalCode=53113&countryCode=" + semanticInput;

        System.out.println(url);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("DHL-API-Key", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");
    }

    // postalCode
    public static void dhl_findByAddress_postalCode(String semanticInput, String apiKey) throws IOException {

        String url = baseUri + "/find-by-address?postalCode="+semanticInput+"&countryCode=DE";

        System.out.println(url);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("DHL-API-Key", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");
    }

    // addressLocality
    public static void dhl_findByAddress_addressLocality(String semanticInput, String apiKey) throws IOException {

        String uri = baseUri + "/find-by-address?countryCode=DE&addressLocality=" + semanticInput;

        System.out.println(uri);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(uri)
                .get()
                .addHeader("DHL-API-Key", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());
        System.out.println("\n");

        ///////////
        String uri2 = baseUri + "/find-by-address?countryCode=FR&addressLocality=" + semanticInput;

        System.out.println(uri2);

        OkHttpClient client2 = new OkHttpClient();

        Request request2 = new Request.Builder()
                .url(uri2)
                .get()
                .addHeader("DHL-API-Key", apiKey)
                .build();

        Response response2 = client2.newCall(request2).execute();

        System.out.println("RESPONSE CODE: " + response2.code());
        System.out.println(response2.body().string());
        System.out.println("\n");

        ///////////
        String uri3 = baseUri + "/find-by-address?countryCode=US&addressLocality=" + semanticInput;

        System.out.println(uri3);

        OkHttpClient client3 = new OkHttpClient();

        Request request3 = new Request.Builder()
                .url(uri3)
                .get()
                .addHeader("DHL-API-Key", apiKey)
                .build();

        Response response3 = client3.newCall(request3).execute();

        System.out.println("RESPONSE CODE: " + response3.code());
        System.out.println(response3.body().string());
        System.out.println("--------------------------------------------------------------------------------------");
    }

    // addressLocality
    public static void dhl_findByAddress_streetAddress(String semanticInput, String apiKey) throws IOException {

        String uri = baseUri + "/find-by-address?countryCode=DE&streetAddress=" + semanticInput;

        System.out.println(uri);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(uri)
                .get()
                .addHeader("DHL-API-Key", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());
        System.out.println("\n");

        ///////////
        String uri2 = baseUri + "/find-by-address?countryCode=FR&streetAddress=" + semanticInput;

        System.out.println(uri2);

        OkHttpClient client2 = new OkHttpClient();

        Request request2 = new Request.Builder()
                .url(uri2)
                .get()
                .addHeader("DHL-API-Key", apiKey)
                .build();

        Response response2 = client2.newCall(request2).execute();

        System.out.println("RESPONSE CODE: " + response2.code());
        System.out.println(response2.body().string());
        System.out.println("\n");

        ///////////
        String uri3 = baseUri + "/find-by-address?countryCode=US&streetAddress=" + semanticInput;

        System.out.println(uri3);

        OkHttpClient client3 = new OkHttpClient();

        Request request3 = new Request.Builder()
                .url(uri3)
                .get()
                .addHeader("DHL-API-Key", apiKey)
                .build();

        Response response3 = client3.newCall(request3).execute();

        System.out.println("RESPONSE CODE: " + response3.code());
        System.out.println(response3.body().string());
        System.out.println("\n");


        ///////////
        String uri4 = baseUri + "/find-by-address?countryCode=ES&streetAddress=" + semanticInput;

        System.out.println(uri4);

        OkHttpClient client4 = new OkHttpClient();

        Request request4 = new Request.Builder()
                .url(uri4)
                .get()
                .addHeader("DHL-API-Key", apiKey)
                .build();

        Response response4 = client4.newCall(request4).execute();

        System.out.println("RESPONSE CODE: " + response4.code());
        System.out.println(response4.body().string());
        System.out.println("--------------------------------------------------------------------------------------");


    }






}

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


public class MainTestingMarvel {

    // Parameters to change
    private static String propertiesPath = "src/test/resources/SemanticAPIs/CommercialAPIs/Marvel/marvel_saigen.properties";
    private static String operationPath = "/v1/public/characters/{characterId}/comics";
    private static String semanticParameterName = "title";
    private static String baseUri = "https://gateway.marvel.com";
    private static Integer limit = Integer.MAX_VALUE;
    private static String apiKey = "---";
    private static String hash = "----";
    private static String ts = "---";

    // &hash=1fe81640f1dc09272438d40e70867298&ts=1606780800&isbn=


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

                System.out.println("Iteration number " + i + "/" + maxCut);

                marvel_characterComics_title(semanticInput, apiKey, hash, ts);      // MODIFY

                i++;
            }catch (Exception e){
                System.out.println(e);
            }

            TimeUnit.SECONDS.sleep(1);

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





    //  ------------------------------------------- OPERATIONS -----------------------------------------------
    // GET /v1/public/characters/{characterId}/comics
    // isbn
    public static void marvel_characterComics_isbn(String semanticInput, String apiKey, String hash, String ts) throws IOException {

        String uri = baseUri + "/v1/public/characters/1011031/comics?apikey="+apiKey+"&hash=" + hash + "&ts=" + ts + "&isbn="+ semanticInput;

        System.out.println(uri);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(uri)
                .get()
//                .addHeader("Authorization", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");
    }

    // issn
    public static void marvel_characterComics_issn(String semanticInput, String apiKey, String hash, String ts) throws IOException {

        String uri = baseUri + "/v1/public/characters/1011031/comics?apikey="+apiKey+"&hash=" + hash + "&ts=" + ts + "&issn="+ semanticInput;

        System.out.println(uri);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(uri)
                .get()
//                .addHeader("Authorization", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");
    }

    // title
    public static void marvel_characterComics_title(String semanticInput, String apiKey, String hash, String ts) throws IOException {

        String uri = baseUri + "/v1/public/characters/1011031/comics?apikey="+apiKey+"&hash=" + hash + "&ts=" + ts + "&title="+ semanticInput;

        System.out.println(uri);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(uri)
                .get()
//                .addHeader("Authorization", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");
    }

    // upc
    public static void marvel_characterComics_upc(String semanticInput, String apiKey, String hash, String ts) throws IOException {

        String uri = baseUri + "/v1/public/characters/1011031/comics?apikey="+apiKey+"&hash=" + hash + "&ts=" + ts + "&upc="+ semanticInput;

        System.out.println(uri);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(uri)
                .get()
//                .addHeader("Authorization", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");
    }

    // ean
    public static void marvel_characterComics_ean(String semanticInput, String apiKey, String hash, String ts) throws IOException {

        String uri = baseUri + "/v1/public/characters/1011031/comics?apikey="+apiKey+"&hash=" + hash + "&ts=" + ts + "&ean="+ semanticInput;

        System.out.println(uri);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(uri)
                .get()
//                .addHeader("Authorization", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");
    }






}

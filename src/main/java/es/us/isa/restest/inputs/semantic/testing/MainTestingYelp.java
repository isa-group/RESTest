//package es.us.isa.restest.inputs.semantic.testing;
//
//import com.squareup.okhttp.OkHttpClient;
//import com.squareup.okhttp.Request;
//import com.squareup.okhttp.Response;
//import es.us.isa.restest.configuration.pojos.Operation;
//import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
//import es.us.isa.restest.specification.OpenAPISpecification;
//
//import java.io.BufferedReader;
//import java.io.FileReader;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//import java.util.concurrent.TimeUnit;
//
//import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
//import static es.us.isa.restest.inputs.semantic.testing.api.Asos.asos_categoriesList_country;
//import static es.us.isa.restest.util.PropertyManager.readProperty;
//
//
//public class MainTestingYelp {
//
//    // Parámetros a cambiar
//    private static String propertiesPath = "/semantic/commercial/yelp.properties";
//    private static String operationPath = "/businesses/matches";
//    private static String semanticParameterName = "name";
//    private static String baseUri = "https://api.yelp.com/v3";
//    private static Integer limit = Integer.MAX_VALUE;
//    private static String apiKey = "Bearer ----";
//
//    // Parámetros derivados
//    private static OpenAPISpecification spec;
//    private static String confPath;
//    private static String OAISpecPath;
//    private static Operation operation;
//    private static TestConfigurationObject conf;
//
//    public static void main(String[] args) throws IOException, InterruptedException {
//        setParameters(readProperty("evaluation.properties.dir") + propertiesPath);
//
//        String csvPath = getCsvPath();
//        List<String> semanticInputs = readCsv(csvPath);
//
//
//        System.out.println("Number of inputs " + semanticInputs.size());
//
//        Integer maxCut = (limit < 20) ? limit : 20;
//
//        Collections.shuffle(semanticInputs);
//
//        // Select 20 random values
//        List<String> randomSubList = semanticInputs.subList(0, maxCut);
//
//        // API Calls
//        int i = 1;
//        for(String semanticInput: randomSubList){
//            try {
//
//                System.out.println(semanticInput);
//
//                System.out.println("Iteración número " + i + "/" + maxCut);
//
//                yelp_transactionsSearch_longitude(semanticInput, apiKey);      // TODO: MODIFY
//
//
//                i++;
//            }catch (Exception e){
//                System.out.println(e);
//            }
//
//            TimeUnit.SECONDS.sleep(1);
//
//        }
//
//
//    }
//
//    private static void setParameters(String propertyPath){
//        OAISpecPath = readProperty(propertyPath, "oaispecpath");
//        confPath = readProperty(propertyPath, "confpath");
//        spec = new OpenAPISpecification(OAISpecPath);
//
//        conf = loadConfiguration(confPath, spec);
//
//        operation = conf.getTestConfiguration().getOperations().stream().filter(x -> x.getTestPath().equals(operationPath)).findFirst().get();
////        host = operation.getTestParameters().stream().filter(x-> x.getName().equals("X-RapidAPI-Host")).findFirst().get().getGenerator().getGenParameters().get(0).getValues().get(0);
//
//    }
//
//    private static String getCsvPath(){
//        return operation.getTestParameters().stream()
//                .filter(x-> x.getName().equals(semanticParameterName))
//                .findFirst().get()
//                .getGenerator()
//                .getGenParameters().get(0).getValues().get(0);
//    }
//
//    public static List<String> readCsv(String csvFile) {
//
//        List<String> res = new ArrayList<>();
//        try {
//            BufferedReader br = new BufferedReader(new FileReader(csvFile));
//            String line = "";
//            while((line = br.readLine()) != null) {
//                res.add(line);
//            }
//            br.close();
//        } catch(IOException ioe) {
//            ioe.printStackTrace();
//        }
//        return res;
//    }
//
//
//
//
//
//    //  ------------------------------------------- OPERATIONS -----------------------------------------------
//    // GET /businesses/search
//    // location
//    public static void yelp_businessesSearch_location(String semanticInput, String apiKey) throws IOException {
//
//        String url = baseUri + "/businesses/search?location=" + semanticInput;
//
//        System.out.println(url);
//
//        OkHttpClient client = new OkHttpClient();
//
//        Request request = new Request.Builder()
//                .url(url)
//                .get()
//                .addHeader("Authorization", apiKey)
//                .build();
//
//        Response response = client.newCall(request).execute();
//
//        System.out.println("RESPONSE CODE: " + response.code());
//        System.out.println(response.body().string());
//        System.out.println("--------------------------------------------------------------------------------------");
//    }
//
//    // latitude
//    public static void yelp_businessesSearch_latitude(String semanticInput, String apiKey) throws IOException {
//
//        String url = baseUri + "/businesses/search?radius=40000&longitude=-122.4282&latitude=" + semanticInput;
//
//        System.out.println(url);
//
//        OkHttpClient client = new OkHttpClient();
//
//        Request request = new Request.Builder()
//                .url(url)
//                .get()
//                .addHeader("Authorization", apiKey)
//                .build();
//
//        Response response = client.newCall(request).execute();
//
//        System.out.println("RESPONSE CODE: " + response.code());
//        System.out.println(response.body().string());
//        System.out.println("--------------------------------------------------------------------------------------");
//    }
//
//    // longitude
//    public static void yelp_businessesSearch_longitude(String semanticInput, String apiKey) throws IOException {
//
//        String url = baseUri + "/businesses/search?radius=40000&longitude="+semanticInput+"&latitude=37.7674";
//
//        System.out.println(url);
//
//        OkHttpClient client = new OkHttpClient();
//
//        Request request = new Request.Builder()
//                .url(url)
//                .get()
//                .addHeader("Authorization", apiKey)
//                .build();
//
//        Response response = client.newCall(request).execute();
//
//        System.out.println("RESPONSE CODE: " + response.code());
//        System.out.println(response.body().string());
//        System.out.println("--------------------------------------------------------------------------------------");
//    }
//
//    // term
//    public static void yelp_businessesSearch_term(String semanticInput, String apiKey) throws IOException {
//
//        String url = baseUri + "/businesses/search?location=europe&term=" + semanticInput;
//
//        System.out.println(url);
//
//        OkHttpClient client = new OkHttpClient();
//
//        Request request = new Request.Builder()
//                .url(url)
//                .get()
//                .addHeader("Authorization", apiKey)
//                .build();
//
//        Response response = client.newCall(request).execute();
//
//        System.out.println("RESPONSE CODE: " + response.code());
//        System.out.println(response.body().string() + "\n");
//
//        String url2 = baseUri + "/businesses/search?location=america&term=" + semanticInput;
//
//        System.out.println(url2);
//
//        OkHttpClient client2 = new OkHttpClient();
//
//        Request request2 = new Request.Builder()
//                .url(url2)
//                .get()
//                .addHeader("Authorization", apiKey)
//                .build();
//
//        Response response2 = client2.newCall(request2).execute();
//
//        System.out.println("RESPONSE CODE: " + response2.code());
//        System.out.println(response2.body().string());
//
//        System.out.println("--------------------------------------------------------------------------------------");
//    }
//
//
//    // locale
//    public static void yelp_businessesSearch_locale(String semanticInput, String apiKey) throws IOException {
//
//        String url = baseUri + "/businesses/search?location=europe&radius=40000&locale=" + semanticInput;
//
//        System.out.println(url);
//
//        OkHttpClient client = new OkHttpClient();
//
//        Request request = new Request.Builder()
//                .url(url)
//                .get()
//                .addHeader("Authorization", apiKey)
//                .build();
//
//        Response response = client.newCall(request).execute();
//
//        System.out.println("RESPONSE CODE: " + response.code());
//        System.out.println(response.body().string() + "\n");
//
//        String url2 = baseUri +  "/businesses/search?location=america&radius=40000&locale=" + semanticInput;
//
//        System.out.println(url2);
//
//        OkHttpClient client2 = new OkHttpClient();
//
//        Request request2 = new Request.Builder()
//                .url(url2)
//                .get()
//                .addHeader("Authorization", apiKey)
//                .build();
//
//        Response response2 = client2.newCall(request2).execute();
//
//        System.out.println("RESPONSE CODE: " + response2.code());
//        System.out.println(response2.body().string());
//
//        System.out.println("--------------------------------------------------------------------------------------");
//    }
//
//
//    // GET /transactions/{transaction_type}/search
//    // location
//    public static void yelp_transactionsSearch_location(String semanticInput, String apiKey) throws IOException {
//
//        String url = baseUri + "/transactions/delivery/search?location="+semanticInput ;
//
//        System.out.println(url);
//
//        OkHttpClient client = new OkHttpClient();
//
//        Request request = new Request.Builder()
//                .url(url)
//                .get()
//                .addHeader("Authorization", apiKey)
//                .build();
//
//        Response response = client.newCall(request).execute();
//
//        System.out.println("RESPONSE CODE: " + response.code());
//        System.out.println(response.body().string());
//        System.out.println("--------------------------------------------------------------------------------------");
//    }
//
//    // latitude
//    public static void yelp_transactionsSearch_latitude(String semanticInput, String apiKey) throws IOException {
//
//        String url = baseUri + "/transactions/delivery/search?longitude=-122.4282&latitude="+semanticInput ;
//
//        System.out.println(url);
//
//        OkHttpClient client = new OkHttpClient();
//
//        Request request = new Request.Builder()
//                .url(url)
//                .get()
//                .addHeader("Authorization", apiKey)
//                .build();
//
//        Response response = client.newCall(request).execute();
//
//        System.out.println("RESPONSE CODE: " + response.code());
//        System.out.println(response.body().string());
//        System.out.println("--------------------------------------------------------------------------------------");
//    }
//
//    // longitude
//    public static void yelp_transactionsSearch_longitude(String semanticInput, String apiKey) throws IOException {
//
//        String url = baseUri + "/transactions/delivery/search?longitude="+semanticInput+"&latitude=37.7674" ;
//
//        System.out.println(url);
//
//        OkHttpClient client = new OkHttpClient();
//
//        Request request = new Request.Builder()
//                .url(url)
//                .get()
//                .addHeader("Authorization", apiKey)
//                .build();
//
//        Response response = client.newCall(request).execute();
//
//        System.out.println("RESPONSE CODE: " + response.code());
//        System.out.println(response.body().string());
//        System.out.println("--------------------------------------------------------------------------------------");
//    }
//
//
//}

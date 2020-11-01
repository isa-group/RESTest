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


public class MainTesting {

    // Parámetros a cambiar
    private static String propertiesPath = "/semantic/apiBasketball.properties";
    private static String operationPath = "/standings";
    private static String semanticParameterName = "group";
    private static String baseUrl = "https://api-basketball.p.rapidapi.com";     //"https://climacell-microweather-v1.p.rapidapi.com"; // "https://rapidapi.p.rapidapi.com";
    private static Integer limit = Integer.MAX_VALUE;

    // Parámetros derivados
    private static OpenAPISpecification spec;
    private static String confPath;
    private static String OAISpecPath;
    private static Operation operation;
    private static String host;
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

                String query = "?season=2019-2020&league=12&group=" + semanticInput;         // TODO: Modify
                String url = baseUrl + operationPath + query;

                OkHttpClient client = new OkHttpClient();

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("x-rapidapi-host", host)
                        .addHeader("x-rapidapi-key", "6a615b46f4mshab392a25b2bc44dp16cee9jsn2bd2d62e5f69")  // TODO: Modify
                        .build();

                Response response = client.newCall(request).execute();

                System.out.println("Iteración número " + i + "/" + maxCut);

                System.out.println("RESPONSE CODE: " + response.code());
                System.out.println(response.body().string());
                System.out.println("--------------------------------------------------------------------------------------");


                i++;
            }catch (Exception e){
                System.out.println(e);
            }

            TimeUnit.SECONDS.sleep(5);

        }


    }

    private static void setParameters(String propertyPath){
        OAISpecPath = readProperty(propertyPath, "oaispecpath");
        confPath = readProperty(propertyPath, "confpath");
        spec = new OpenAPISpecification(OAISpecPath);

        conf = loadConfiguration(confPath, spec);

        operation = conf.getTestConfiguration().getOperations().stream().filter(x -> x.getTestPath().equals(operationPath)).findFirst().get();
        host = operation.getTestParameters().stream().filter(x-> x.getName().equals("X-RapidAPI-Host")).findFirst().get().getGenerator().getGenParameters().get(0).getValues().get(0);

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


}

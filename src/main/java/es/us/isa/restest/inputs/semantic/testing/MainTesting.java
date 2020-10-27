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
    private static String propertiesPath = "/semantic/flightData.properties";
    private static String operationPath = "/v2/prices/week-matrix";
    private static String semanticParameterName = "currency";
    private static Integer limit = Integer.MAX_VALUE;

    // Parámetros derivados
    private static OpenAPISpecification spec;
    private static String confPath;
    private static String OAISpecPath;
    private static String baseUrl = "https://rapidapi.p.rapidapi.com";     //"https://climacell-microweather-v1.p.rapidapi.com"; // "https://rapidapi.p.rapidapi.com";
    private static Operation operation;
    private static String host;
    private static TestConfigurationObject conf;

    public static void main(String[] args) throws IOException, InterruptedException {
        setParameters(readProperty("evaluation.properties.dir") + propertiesPath);

        String csvPath = getCsvPath();
        List<String> semanticInputs = readCsv(csvPath);

        Integer maxCut = (limit < 20) ? limit : 20;

        Collections.shuffle(semanticInputs);

        // Select 20 random values
        List<String> randomSubList = semanticInputs.subList(0, maxCut);

        // API Calls
        int i = 1;
        for(String semanticInput: randomSubList){
            try {

                System.out.println(semanticInput);
                String query = "?destination=MOW&origin=LED&currency=" + semanticInput;
//                String query = "?calendar_type=departure_date&destination=BCN&origin=MOW&currency=" + semanticInput + "&length=None";            // TODO: Modify
                String url = baseUrl + operationPath + query;
//                String url = baseUrl + "/2019/" + semanticInput;
                OkHttpClient client = new OkHttpClient();

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("x-access-token", "5b3ef0bdeca04643188c6610c30056f5")
                        .addHeader("x-rapidapi-host", host)
                        .addHeader("x-rapidapi-key", "69c700dd67msh57b046423099254p1d4b4cjsn1908598eb392")  // TODO: Modify
                        .build();

                Response response = client.newCall(request).execute();

                System.out.println("Iteración número " + i + "/" + maxCut);
//                if(response.code() != 404){
                System.out.println("RESPONSE CODE: " + response.code());
                System.out.println(response.body().string());
                System.out.println("--------------------------------------------------------------------------------------");
//                }

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

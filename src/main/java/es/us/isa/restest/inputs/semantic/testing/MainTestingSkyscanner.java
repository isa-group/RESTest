package es.us.isa.restest.inputs.semantic.testing;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;
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

public class MainTestingSkyscanner {

    // Parámetros a cambiar
    private static String propertiesPath = "/semantic/skyscanner.properties";
    private static String operationPath = "/apiservices/browseroutes/v1.0/{country}/{currency}/{locale}/{originplace}/{destinationplace}/{outboundpartialdate}/{inboundpartialdate}";
    private static String semanticParameterName = "locale";
    private static String baseUrl = "https://skyscanner-skyscanner-flight-search-v1.p.rapidapi.com";
    private static Integer limit = Integer.MAX_VALUE;

    // Parámetros derivados
    private static OpenAPISpecification spec;
    private static String confPath;
    private static String OAISpecPath;
    private static Operation operation;
    private static TestParameter testParameter;
    private static String host;
    private static TestConfigurationObject conf;

    public static void main(String[] args) throws IOException, InterruptedException {
        setParameters();

        String csvPath = getCsvPath();
        List<String> semanticInputs = readCsv(csvPath);


        System.out.println("Number of inputs " + semanticInputs.size());

        Integer maxCut = (limit < 10) ? limit : 10;

        Collections.shuffle(semanticInputs);

        // Select 20 random values
        List<String> randomSubList = semanticInputs.subList(0, maxCut);

        // API Calls
        int i = 1;
        for(String semanticInput: randomSubList){
            try {

                System.out.println(semanticInput);

//                String query = "?latitude=49.6848&longitude=" + semanticInput;
//                String url = baseUrl + operationPath + query;
                String url = baseUrl + "/apiservices/browseroutes/v1.0/US/USD/"+semanticInput+"/SFO-sky/JFK-sky/anytime/anytime";
//                                      /apiservices/browseroutes/v1.0/{country}/{currency}/{locale}/{originplace}/{destinationplace}/{outboundpartialdate}/{inboundpartialdate}


                // country                  US
                // currency                 USD
                // locale                   en-US
                // originplace              SFO-sky
                // destination place        JFK-sky
                // outboundpartialdate      2019-09-01
                //inboundpartialdate        2019-12-01


//                          /apiservices/referral/v1.0/{country}/{currency}/{locale}/{originplace}/{destinationplace}/{outboundpartialdate}/{inboundpartialdate}
//                          /apiservices/autosuggest/v1.0/{country}/{currency}/{locale}/
//                          /apiservices/browsequotes/v1.0/{country}/{currency}/{locale}/{origin}/{destination}/{outboundpartialdate}
//
//
//                          /apiservices/browseroutes/v1.0/{country}/{currency}/{locale}/{originplace}/{destinationplace}/{outboundpartialdate}
//                          /apiservices/browsedates/v1.0/{country}/{currency}/{locale}/{originplace}/{destinationplace}/{outboundpartialdate}
//                          /apiservices/browsedates/v1.0/{country}/{currency}/{locale}/{originplace}/{destinationplace}/{outboundpartialdate}/{inboundpartialdate}
//                          /apiservices/browsequotes/v1.0/{country}/{currency}/{locale}/{originplace}/{destinationplace}/{outboundpartialdate}/{inboundpartialdate}
//                          /apiservices/browseroutes/v1.0/{country}/{currency}/{locale}/{originplace}/{destinationplace}/{outboundpartialdate}/{inboundpartialdate}



                System.out.println(url);

                OkHttpClient client = new OkHttpClient();

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("x-rapidapi-key", "xxx")
                        .addHeader("x-rapidapi-host", "skyscanner-skyscanner-flight-search-v1.p.rapidapi.com")
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

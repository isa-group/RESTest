package es.us.isa.restest.inputs.semantic;
import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.util.PropertyManager.readProperty;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.specification.OpenAPISpecification;

import java.io.IOException;

public class MainTesting {

    private static OpenAPISpecification spec;

    private static String confPath;
    private static String OAISpecPath;

    private static String baseUrl = "https://rapidapi.p.rapidapi.com";


    public static void main(String[] args) throws IOException {
        setParameters(readProperty("evaluation.properties.dir") + "/semantic/airportinfo.properties");

        TestConfigurationObject conf = loadConfiguration(confPath, spec);

        System.out.println(spec);

        String operationPath = "/airport";
//        String host = spec.getSpecification().getComponents()

        Operation operation = conf.getTestConfiguration().getOperations().stream().filter(x -> x.getTestPath().equals(operationPath)).findFirst().get();

        for(TestParameter parameter: operation.getTestParameters()){
            String type = parameter.getGenerator().getType();
            GenParameter genParameter = parameter.getGenerator().getGenParameters().get(0);

            if(type.equals("RandomInputValue") && genParameter.getName().equals("csv")){

//                semanticParameters.add(parameter);

            }
        }

        // ---------------------------------------- API CALL ----------------------------------------
        String url = baseUrl + operationPath + "?iata=AAA&icao=FNK";
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .get()
//                .addHeader("x-rapidapi-host", host)
                .addHeader("x-rapidapi-key", "6a615b46f4mshab392a25b2bc44dp16cee9jsn2bd2d62e5f69")
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
//        System.out.println(response.headers());
        System.out.println(response.body().string());

        // -----------------------------------------------------------------------------------------



    }

    public static void setParameters(String propertyPath){
        OAISpecPath = readProperty(propertyPath, "oaispecpath");
        confPath = readProperty(propertyPath, "confpath");
        spec = new OpenAPISpecification(OAISpecPath);

    }
}

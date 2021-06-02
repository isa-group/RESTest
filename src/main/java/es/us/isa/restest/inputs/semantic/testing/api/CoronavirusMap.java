package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

public class CoronavirusMap {

    private static final String baseUri = "https://coronavirus-map.p.rapidapi.com";
    private static final String operationPath = "/v1/spots/year";

    public static Response coronavirusMap_region(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + operationPath + "?region=" + semanticInput;

        System.out.println(url);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("x-rapidapi-host", host)
                .addHeader("x-rapidapi-key", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());
//        System.out.println("--------------------------------------------------------------------------------------");

        return response;


    }
}

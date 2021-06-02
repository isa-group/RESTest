package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class Climacell {

    private static final String baseUrl = "https://climacell-microweather-v1.p.rapidapi.com";
    private static final String operationPath = "/weather/forecast/hourly";

    // Latitude
    public static void climacell_lat(String semanticInput, String apiKey, String host) throws IOException {
        String uri = baseUrl + operationPath + "?lat=" + semanticInput + "&lon=-74.0578";
        printResponse(uri);
    }

    // Longitude
    public static void climacell_lon(String semanticInput, String apiKey, String host) throws IOException {
        String uri = baseUrl + operationPath + "?lat=52.412&lon=" + semanticInput;
        printResponse(uri);

    }


}

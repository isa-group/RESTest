package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class OpenWeatherMap {

    private static final String baseUrl = "https://community-open-weather-map.p.rapidapi.com";
    private static final String operationPath = "/forecast";

    public static void openWeatherMap_forecast_zip(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUrl + "/forecast?zip=" + semanticInput;
        printResponse(url);
    }

    // Latitude
    // https://community-open-weather-map.p.rapidapi.com/find?lat=42.6461&lon=-1.50802
    public static void openWeatherMap_latitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUrl + operationPath + "?lat=" + semanticInput + "&lon=-1.50802";
        printResponse(url);
    }

    // Longitude
    // https://community-open-weather-map.p.rapidapi.com/find?lat=40.3513&lon=-74.4801
    public static void openWeatherMap_longitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUrl + operationPath + "??lat=40.3513&lon=" + semanticInput;
        printResponse(url);
    }
    
}

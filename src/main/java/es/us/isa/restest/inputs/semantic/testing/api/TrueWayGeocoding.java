package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class TrueWayGeocoding {

    private static final String baseUrl = "https://trueway-geocoding.p.rapidapi.com";

    // language
    public static void trueWayGeocoding_language(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUrl + "/Geocode?address=505 Howard St, San Francisco&language=" + semanticInput;
        printResponse(url);
    }

    // location
    public static void trueWayGeocoding_location(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUrl + "/ReverseGeocode?location=" + semanticInput;
        printResponse(url);
    }

    // address
    public static void trueWayGeocoding_address(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUrl + "/Geocode?address=" + semanticInput;
        printResponse(url);
    }

    // address
    public static void trueWayGeocoding_country(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUrl + "/Geocode?address=505 Howard St, San Francisco&country=" + semanticInput;
        printResponse(url);
    }

}

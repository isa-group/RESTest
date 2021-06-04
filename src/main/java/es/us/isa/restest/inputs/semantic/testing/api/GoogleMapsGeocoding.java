package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class GoogleMapsGeocoding {

    private static final String baseUri = "https://google-maps-geocoding.p.rapidapi.com";

    // /geocode/json        (Geocoding)
    public static void googleMapsGeocodingAddress(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/geocode/json?address=" + semanticInput;
        printResponse(url);
    }

    public static void googleMapsGeocodingLanguage(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/geocode/json?address=164%20Townsend%20St.%2C%20San%20Francisco%2C%20CA&language=" + semanticInput;
        printResponse(url);
    }


    // /geocode/json        (Reverse Geocoding)
    public static void googleMapsReverseGeocodingLatlng(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/geocode/json?latlng=" + semanticInput;
        printResponse(url);
    }

    public static void googleMapsReverseGeocodingLanguage(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/geocode/json?latlng=40.714224%2C-73.96145&language=" + semanticInput;
        printResponse(url);
    }

}

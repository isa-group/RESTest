package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class TrailAPI {

    private static final String baseUri = "https://trailapi-trailapi.p.rapidapi.com";


    // /trails/explore/ (Find Bike Trails)
    // lat
    public static void trailAPI_findBikeTrails_lat(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/trails/explore/?lon=-97.9883&lat=" + semanticInput;
        printResponse(url);
    }

    // lon
    public static void trailAPI_findBikeTrails_lon(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/trails/explore/?lon=" + semanticInput + "&lat=42.8931";
        printResponse(url);
    }


    // / (Outdoors, legacy)
    // lat
    public static void trailAPI_outdoors_lat(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/?lon=-105.2&lat=" + semanticInput;
        printResponse(url);
    }

    // lon
    public static void trailAPI_outdoors_lon(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/?lon=" + semanticInput + "&lat=34.1";
        printResponse(url);
    }

    // q-state_cont
    public static void trailAPI_outdoors_qStateCont(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/?q-state_cont=" + semanticInput;
        printResponse(url);
    }

    // q-country_cont
    public static void trailAPI_outdoors_qCountryCont(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/?q-country_cont=" + semanticInput;
        printResponse(url);
    }

    // q-city_cont
    public static void trailAPI_outdoors_qCityCont(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/?q-city_cont=" + semanticInput;
        printResponse(url);
    }



}

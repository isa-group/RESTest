package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class Spott {

    private static final String baseUri = "https://spott.p.rapidapi.com";

    //  /places
    public static void spottLatitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/places?longitude=-99.2159&latitude=" + semanticInput;
        printResponse(url);
    }

    public static void spottLongitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/places?latitude=51.5122&longitude=" + semanticInput;
        printResponse(url);
    }

    public static void spottCountry(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/places?country=" + semanticInput;
        printResponse(url);
    }

    public static void spottLanguage(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/places?language=" + semanticInput;
        printResponse(url);
    }

}

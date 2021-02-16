package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class AirportInfo {

    private static final String baseUri = "https://airport-info.p.rapidapi.com";

    public static void airportInfo_iata(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/airport?iata=" + semanticInput;
        printResponse(url);
    }

    public static void airportInfo_icao(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/airport?icao=" + semanticInput;
        printResponse(url);
    }

}

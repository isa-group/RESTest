package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class Astronomy {

    private static final String baseUri = "https://astronomy.p.rapidapi.com";

    //  /api/v2/bodies/positions/{body} (Get positions for body)
    public static void astronomyLatitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/api/v2/bodies/positions/venus?latitude=" + semanticInput + "&longitude=-84.39733&from_date=2017-12-20&to_date=2017-12-21&elevation=166&time=12%3A00%3A00";
        printResponse(url);
    }

    public static void astronomyLongitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/api/v2/bodies/positions/venus?latitude=33.775867&longitude=" + semanticInput + "&from_date=2017-12-20&to_date=2017-12-21&elevation=166&time=12%3A00%3A00";
        printResponse(url);
    }

    public static void astronomyBody(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/api/v2/bodies/positions/" + semanticInput + "?latitude=33.775867&longitude=-84.39733&from_date=2017-12-20&to_date=2017-12-21&elevation=166&time=12%3A00%3A00";
        printResponse(url);
    }

}

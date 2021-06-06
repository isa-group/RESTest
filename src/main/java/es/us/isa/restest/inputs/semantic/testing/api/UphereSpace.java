package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class UphereSpace {

    private static final String baseUri = "https://uphere-space1.p.rapidapi.com";

    //  /satellite/list (Satellite list)
    public static void uphereSpaceSatelliteListCountry(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/satellite/list?page=1&country=" + semanticInput;
        printResponse(url);
    }

    //  /satellite/{number}/details (Details)
    public static void uphereSpaceSateDetailsNumber(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/satellite/" + semanticInput + "/details";
        printResponse(url);
    }


    //  /user/visible (Visible satellites)
    public static void uphereSpaceSateVisibleSatellitesLat(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/user/visible?lat=" + semanticInput + "&lng=-117.9833";
        printResponse(url);
    }

    public static void uphereSpaceSateVisibleSatellitesLng(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/user/visible?lat=32.1433&lng=" + semanticInput;
        printResponse(url);
    }

}

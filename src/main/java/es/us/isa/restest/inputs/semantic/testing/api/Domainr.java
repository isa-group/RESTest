package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class Domainr {

    private static final String baseUri = "https://domainr.p.rapidapi.com";

    // /v2/search
    // registrar
    public static void domainr_search_registrar(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v2/search?mashape-key=" + apiKey + "&query=cafe&registrar=" + semanticInput;
        printResponse(url);
    }

    // location
    public static void domainr_search_location(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v2/search?mashape-key=" + apiKey + "&query=cafe&location=" + semanticInput;
        printResponse(url);
    }


    // /v2/status
    // domain
    public static void domainr_search_domain(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v2/status?mashape-key=6a615b46f4mshab392a25b2bc44dp16cee9jsn2bd2d62e5f69&domain=" + semanticInput;
        printResponse(url);
    }

}

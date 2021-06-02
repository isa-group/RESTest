package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class RentEstimate {

    private static final String baseUri = "https://realtymole-rental-estimate-v1.p.rapidapi.com";

    public static void rentalPriceLatitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/rentalPrice?longitude=-73.987703&latitude=" + semanticInput;
        printResponse(url);
    }

    public static void rentalPriceLongitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/rentalPrice?latitude=40.749798&longitude=" + semanticInput;
        printResponse(url);
    }

    public static void rentalPriceAddress(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/rentalPrice?address=" + semanticInput;
        printResponse(url);
    }



}

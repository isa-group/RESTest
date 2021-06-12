package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class RealtyMoleProperty {

    private static final String baseUri = "https://realty-mole-property-api.p.rapidapi.com";

    // /salePrice (Sale Price Estimate)
    // latitude
    public static void realtyMoleProperty_salePrice_latitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/salePrice?longitude=-122.619&latitude=" + semanticInput;
        printResponse(url);
    }

    // longitude
    public static void realtyMoleProperty_salePrice_longitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/salePrice?longitude=" + semanticInput + "&latitude=37.785";
        printResponse(url);
    }

    // address
    public static void realtyMoleProperty_salePrice_address(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/salePrice?address=" + semanticInput;
        printResponse(url);
    }


    // /saleListings (Sale Listings)
    // longitude
    public static void realtyMoleProperty_saleListings_longitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/saleListings?longitude=" + semanticInput + "&latitude=55.6595";
        printResponse(url);
    }

    // city
    public static void realtyMoleProperty_saleListings_city(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/saleListings?city=" + semanticInput;
        printResponse(url);
    }

    // state
    public static void realtyMoleProperty_saleListings_state(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/saleListings?state=" + semanticInput;
        printResponse(url);
    }

    // latitude
    public static void realtyMoleProperty_saleListings_latitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/saleListings?longitude=-122.619&latitude=" + semanticInput;
        printResponse(url);
    }

    // address
    public static void realtyMoleProperty_saleListings_address(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/saleListings?address=" + semanticInput;
        printResponse(url);
    }


    // /zipCodes/{zipCode} (Zip Code Rental Data)
    // zipCode
    public static void realtyMoleProperty_zipCodeRentalData_zipCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/zipCodes/" + semanticInput;
        printResponse(url);
    }

}

package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class AviationReferenceData {

    private static final String baseUri = "https://aviation-reference-data.p.rapidapi.com";

    // /countries/{code} (Country)
    // code
    public static void aviationReferenceData_country_code(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/countries/" + semanticInput;
        printResponse(url);
    }


    // /airports/search (Airport search by location coordinate)
    // lat
    public static void aviationReferenceData_airportSearchByLocationCoordinate_lat(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/airports/search?lat=" + semanticInput + "&lon=-68.315&radius=100";
        printResponse(url);
    }

    // lon
    public static void aviationReferenceData_airportSearchByLocationCoordinate_lon(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/airports/search?lat=-54.81&lon=" + semanticInput + "&radius=100";
        printResponse(url);
    }

    // /airports/{code} (Airport)
    // code
    public static void aviationReferenceData_airport_code(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/airports/" + semanticInput;
        printResponse(url);
    }

    // /airline/{code} (Airline)
    // code
    public static void aviationReferenceData_airline_code(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/airline/" + semanticInput;
        printResponse(url);
    }

    // /icaoType/{code} (Aircraft type)
    // code
    public static void aviationReferenceData_aircraftType_code(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/icaoType/" + semanticInput;
        printResponse(url);
    }
    
}

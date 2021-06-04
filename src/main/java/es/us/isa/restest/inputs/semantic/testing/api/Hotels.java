package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class Hotels {

    private static final String baseUri = "https://hotels4.p.rapidapi.com";

    //  /locations/search
    public static void hotelsLocationsSearchLocale(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/locations/search?query=new+york&locale=" + semanticInput;
        printResponse(url);
    }

    // /properties/list
    public static void hotelsPropertiesListLocale(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/properties/list?adults1=1&pageNumber=1&destinationId=1506246&pageSize=25&checkOut=2020-01-15&checkIn=2020-01-08&locale=" + semanticInput;
        printResponse(url);
    }

    public static void hotelsPropertiesListCurrency(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/properties/list?adults1=1&pageNumber=1&destinationId=1506246&pageSize=25&checkOut=2020-01-15&checkIn=2020-01-08&currency=" + semanticInput;
        printResponse(url);
    }

    //  /reviews/list
    public static void hotelsReviewsListLoc(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/reviews/list?id=1178275040&loc=" + semanticInput;
        printResponse(url);
    }

}

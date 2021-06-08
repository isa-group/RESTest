package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class TravelAdvisor {

    private static final String baseUri = "https://travel-advisor.p.rapidapi.com";

    // /locations/search
    // currency
    public static void travelAdvisor_locationsSearch_currency(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/locations/search?query=pattaya&currency=" + semanticInput;
        printResponse(url);
    }

    // lang
    public static void travelAdvisor_locationsSearch_lang(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/locations/search?query=pattaya&lang=" + semanticInput;
        printResponse(url);
    }


    // /hotels/list-by-latlng
    // latitude
    public static void travelAdvisor_listByLatLng_latitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/hotels/list-by-latlng?latitude=" + semanticInput + "&longitude=100.87808";
        printResponse(url);
    }

    // longitude
    public static void travelAdvisor_listByLatLng_longitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/hotels/list-by-latlng?latitude=12.91285&longitude=" + semanticInput;
        printResponse(url);
    }

    // /hotels/list-in-boundary
    // bl_latitude
    public static void travelAdvisor_hotelsListInBoundary_bllatitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/hotels/list-in-boundary?bl_latitude=" + semanticInput + "&bl_longitude=108.473209&tr_longitude=109.149359&tr_latitude=12.838442";
        printResponse(url);
    }

    // bl_longitude
    public static void travelAdvisor_hotelsListInBoundary_bllongitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/hotels/list-in-boundary?bl_latitude=11.847676&bl_longitude=" + semanticInput + "&tr_longitude=109.149359&tr_latitude=12.838442";
        printResponse(url);
    }

    // tr_longitude
    public static void travelAdvisor_hotelsListInBoundary_trlongitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/hotels/list-in-boundary?bl_latitude=11.847676&bl_longitude=108.473209&tr_longitude=" + semanticInput + "&tr_latitude=12.838442";
        printResponse(url);
    }

    // tr_latitude
    public static void travelAdvisor_hotelsListInBoundary_trlatitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/hotels/list-in-boundary?bl_latitude=11.847676&bl_longitude=108.473209&tr_longitude=109.149359&tr_latitude=" + semanticInput;
        printResponse(url);
    }

    
}

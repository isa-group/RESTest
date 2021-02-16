package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class UsRestaurantMenus {

    private static final String baseUri = "https://us-restaurant-menus.p.rapidapi.com";
    private static final String operationPath = "/restaurants/search/geo";

    // Test latitude (The operation name must be specified)
    public static void usRestaurantMenus_latitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + operationPath + "?lon=-73.992378&lat=" + semanticInput + "&distance=10000";
        printResponse(url);
    }

    // Test longitude (The operation name must be specified)
    public static void usRestaurantMenus_longitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + operationPath + "?lon=" + semanticInput + "&lat=40.68919&distance=10000";
        printResponse(url);
    }

    // Test distance (The operation name must be specified)
    public static void usRestaurantMenus_distance(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + operationPath + "?lon=-73.992378&lat=40.68919&distance=" + semanticInput;
        printResponse(url);
    }

    // GET /restaurants/zip_code/{zip_code}
    public static void usRestaurantMenus_restaurantsZipcode_zipCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/restaurants/zip_code/" + semanticInput;
        printResponse(url);
    }

//    GET /restaurants/state/{state}
    public static void usRestaurantMenus_restaurantsState_state(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/restaurants/state/" + semanticInput;
        printResponse(url);
    }

}

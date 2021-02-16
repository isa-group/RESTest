package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class UsWeatherByZipcode {

    private static final String baseUri = "https://us-weather-by-zip-code.p.rapidapi.com";

    // Zipcode
    public static void usRestaurantMenus_latitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/getweatherzipcode" + "?zip=" + semanticInput;
        printResponse(url);
    }

}

package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class PricelineCom {

    private static final String baseUri = "https://priceline-com.p.rapidapi.com";

    // /hotel/{id} (hotel details)
    // currency
    public static void pricelineCom_hotelDetails_currency(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/hotel/8794805?checkin_date=2021-01-22&checkout_date=2021-01-23&currency=" + semanticInput;
        printResponse(url);
    }

    // /hotels/city/nearby/{latitude}/{longitude} (Hotel city nearby)
    // latitude
    public static void pricelineCom_hotelCityNearby_latitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/hotels/city/nearby/" + semanticInput + "/-122.40057774847898";
        printResponse(url);
    }

    // longitude
    public static void pricelineCom_hotelCityNearby_longitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/hotels/city/nearby/37.788719679657554/" + semanticInput;
        printResponse(url);
    }

    // /flights/{origin}/{destination}/{departure_date} (Flights one way)
    // origin
    public static void pricelineCom_flightsOneWay_origin(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/flights/" + semanticInput + "/SFO/2021-02-17";
        printResponse(url);
    }

    // destination
    public static void pricelineCom_flightsOneWay_destination(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/flights/LAX/" + semanticInput + "/2021-02-17";
        printResponse(url);
    }


}

package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class PricelineComProvider {

    private static final String baseUri = "https://priceline-com-provider.p.rapidapi.com";

    // /v1/flights/search (Search flights)
    // location_arrival
    public static void pricelineComProvider_searchFlights_locationArrival(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/flights/search?location_arrival=" + semanticInput + "&location_departure=MOW&date_departure=2021-09-27&class_type=ECO&itinerary_type=ONE_WAY&sort_order=PRICE";
        printResponse(url);
    }

    // location_departure
    public static void pricelineComProvider_searchFlights_locationDeparture(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/flights/search?location_arrival=NYC&location_departure=" + semanticInput + "&date_departure=2021-09-27&class_type=ECO&itinerary_type=ONE_WAY&sort_order=PRICE";
        printResponse(url);
    }

    // /v1/hotels/locations-by-geo (Search locations by geolocation)
    // lat
    public static void pricelineComProvider_locationsByGeolocation_lat(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/hotels/locations-by-geo?longitude=14.418540&latitude=" + semanticInput;
        printResponse(url);
    }

    // lon
    public static void pricelineComProvider_locationsByGeolocation_lon(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/hotels/locations-by-geo?longitude=" + semanticInput + "&latitude=50.073658";
        printResponse(url);
    }

}

package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class HotelsComProvider {

    private static final String baseUri = "https://hotels-com-provider.p.rapidapi.com";

    // /v1/hotels/nearby (Search nearby hotels)
    // latitude
    public static void alphaVantage_searchNearbyHotels_latitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/hotels/nearby?sort_order=STAR_RATING_HIGHEST_FIRST&locale=en_US&checkin_date=2022-03-26&adults_number=1&currency=USD&latitude=" + semanticInput + "&longitude=-0.118092&checkout_date=2022-03-27";
        printResponse(url);
    }

    // longitude
    public static void alphaVantage_searchNearbyHotels_longitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/hotels/nearby?sort_order=STAR_RATING_HIGHEST_FIRST&locale=en_US&checkin_date=2022-03-26&adults_number=1&currency=USD&latitude=51.509865&longitude=" + semanticInput + "&checkout_date=2022-03-27";
        printResponse(url);
    }

}

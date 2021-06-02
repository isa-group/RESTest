package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;
import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponseFlightData;

public class FlightData {

    private static final String baseUrl = "https://travelpayouts-travelpayouts-flight-data-v1.p.rapidapi.com";
    private static final String apikeyFlightData = "5b3ef0bdeca04643188c6610c30056f5";
    private static final String operationPath = "/v2/prices/week-matrix";

    // GET /v1/city-directions
    // origin
    public static void flightData_cityDirections_origin(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUrl + "/v1/city-directions" + "?origin=" + semanticInput + "&currency=RUB";
        printResponseFlightData(url);
    }

    // currency
    public static void flightData_cityDirections_currency(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUrl + "/v1/city-directions" + "?origin=MOW&currency=" + semanticInput;
        printResponseFlightData(url);
    }

    // currency regex
    public static Response flightData_cityDirections_currency_regex(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUrl + "/v1/city-directions" + "?origin=MOW&currency=" + semanticInput;

        System.out.println(url);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("x-access-token", apikeyFlightData)
                .addHeader("x-rapidapi-host", host)
                .addHeader("x-rapidapi-key", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());
//        System.out.println("--------------------------------------------------------------------------------------");

        return response;

    }


    // GET /v1/prices/calendar
    // origin
    public static void flightData_pricesCalendar_origin(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUrl + "/v1/prices/calendar" + "?origin=" + semanticInput + "&destination=BCN&currency=RUB";
        printResponseFlightData(url);
    }

    // destination
    public static void flightData_pricesCalendar_destination(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUrl + "/v1/prices/calendar" + "?origin=MOW&destination=" + semanticInput + "&currency=RUB";
        printResponseFlightData(url);
    }

    // currency
    public static void flightData_pricesCalendar_currency(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUrl + "/v1/prices/calendar" + "?origin=MOW&destination=BCN&currency=" + semanticInput;
        printResponseFlightData(url);
    }


    // GET /v1/airline-directions
    // airline_code
    public static void flightData_airlineDirections_airlineCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUrl + "/v1/airline-directions" + "?airline_code=" + semanticInput;
        printResponseFlightData(url);
    }


    // OPERATIONS THAT RECEIVE ORIGIN, CURRENCY and DESTINATION
    // origin
    public static void flightData_pricesCheap_origin(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUrl + operationPath + "?origin=" + semanticInput + "&destination=BCN&currency=RUB";
        printResponseFlightData(url);
    }

    // currency
    public static void flightData_pricesCheap_currency(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUrl + operationPath + "?origin=MOW&destination=BCN&currency=" + semanticInput;
        printResponseFlightData(url);
    }

    // destination
    public static void flightData_pricesCheap_destination(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUrl + operationPath + "?origin=MOW&destination="+semanticInput+"&currency=RUB";
        printResponseFlightData(url);
    }



}

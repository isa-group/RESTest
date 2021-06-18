package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.sun.scenario.effect.impl.sw.sse.SSEBlend_SRC_OUTPeer;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    // Regex /saleListings (Sale Listings) state
    public static Response realtyMoleProperty_saleListings_state_regex(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/saleListings?state=" + semanticInput;

        System.out.println(url);

        OkHttpClient client = new OkHttpClient();

        // TODO: uncomment
//        client.setConnectTimeout(120, TimeUnit.SECONDS);
        client.setReadTimeout(20, TimeUnit.SECONDS);
//        client.setWriteTimeout(120, TimeUnit.SECONDS);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("x-rapidapi-host", host)
                .addHeader("x-rapidapi-key", apiKey)
                .build();

        try {
            Response response = client.newCall(request).execute();

            System.out.println("RESPONSE CODE: " + response.code());
            System.out.println(response.body().toString());
            System.out.println("--------------------------------------------------------------------------------------");

            return response;

        } catch (Exception e) {
            return null;
        }



    }

}

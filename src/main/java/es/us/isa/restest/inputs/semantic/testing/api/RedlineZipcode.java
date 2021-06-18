package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;
import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponsePost;

public class RedlineZipcode {

    private static final String baseUri = "https://redline-redline-zipcode.p.rapidapi.com";

//    /rest/multi-radius.json/{distance}/{units} (Multiple zip codes by radius)
    public static void redlineZipcodeByRadiusZip_codes(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/rest/multi-radius.json/100/mile";
        String body = "zip_codes=" + semanticInput;
        printResponsePost(url, body);
    }

    public static void redlineZipcodeByRadiusAddrs(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/rest/multi-radius.json/100/mile";
        String body = "addrs=" + semanticInput;
        printResponsePost(url, body);
    }

//    /rest/multi-info.json/{zipcodes}/{units} (Multiple zip codes to location information)
    public static void redlineZipcodeToLocationInfoZipcodes(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/rest/multi-info.json/" + semanticInput + "/degrees";
        printResponse(url);
    }

//    /rest/distance.json/{zipcode1}/{zipcode2}/{units} (Distance Between Zip Codes)
    public static void redlineDistanceBetweenZipcodesZipcode1(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/rest/distance.json/" + semanticInput + "/70117/mile";
        printResponse(url);
    }

    public static void redlineDistanceBetweenZipcodesZipcode2(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/rest/distance.json/94035/" + semanticInput + "/mile";
        printResponse(url);
    }

//    /rest/info.json/{zipcode}/{units} (Zip Codes within Radius)
    public static void redlineZipcodesInRadiusZipcode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/rest/radius.json/" + semanticInput + "/100/mile";
        printResponse(url);
    }

//    /rest/state-zips.json/{state} (State to Zip Codes)
    public static void redlineStateToZipcode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/rest/state-zips.json/" + semanticInput;
        printResponse(url);
    }

//    /rest/city-zips.json/{city}/{state} (Location to Zip Code)
    public static void redlineLocationToZipcodeState(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/Moorestown/" + semanticInput;
        printResponse(url);
    }

    public static void redlineLocationToZipcodeCity(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/"+semanticInput+"/NJ";
        printResponse(url);
    }


    // Regex (State to Zip Codes)
    public static Response redlineStateToZipcode_regex(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/rest/state-zips.json/" + semanticInput;

        System.out.println(url);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("x-rapidapi-host", host)
                .addHeader("x-rapidapi-key", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println("--------------------------------------------------------------------------------------");

        return response;

//        return response.body().string();

    }


}

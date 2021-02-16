package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

public class UsRestaurantMenus {

    private static final String baseUri = "https://us-restaurant-menus.p.rapidapi.com";
    private static final String operationPath = "/restaurants/search/geo";

    // Test latitude (The operation name must be specified)
    public static void usRestaurantMenus_latitude(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + operationPath + "?lon=-73.992378&lat=" + semanticInput + "&distance=10000";

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");
    }

    // Test longitude (The operation name must be specified)
    public static void usRestaurantMenus_longitude(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + operationPath + "?lon=" + semanticInput + "&lat=40.68919&distance=10000";

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");
    }

    // Test distance (The operation name must be specified)
    public static void usRestaurantMenus_distance(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + operationPath + "?lon=-73.992378&lat=40.68919&distance=" + semanticInput;

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");
    }


    // GET /restaurants/zip_code/{zip_code}
    public static void usRestaurantMenus_restaurantsZipcode_zipCode(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/restaurants/zip_code/" + semanticInput;

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");
    }



//    GET /restaurants/state/{state}
    public static void usRestaurantMenus_restaurantsState_state(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/restaurants/state/" + semanticInput;

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");
    }

}

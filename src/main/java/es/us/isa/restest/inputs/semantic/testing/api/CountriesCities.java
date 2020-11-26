package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

public class CountriesCities {

    private static final String baseUri = "https://countries-cities.p.rapidapi.com";

    // GET /location/country/{code}/geojson
    public static void countriesCities_locationCountryCodeGeojson_code(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/location/country/"+semanticInput+"/geojson";

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

    // GET /location/country/{code}
    public static void countriesCities_locationCountryCode_code(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/location/country/" + semanticInput;

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

    // GET /location/country/{code}/city/list
    public static void countriesCities_locationCountryCodeCityList_code(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/location/country/" + semanticInput + "/city/list";

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

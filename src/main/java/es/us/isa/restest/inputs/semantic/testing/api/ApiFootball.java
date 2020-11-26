package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

public class ApiFootball {

    private static final String baseUri = "https://api-football-v1.p.rapidapi.com";

    // GET /v2/leagues/country/{country_name}/{season}
    public static void apiFootball_leaguesCountryCountryNameSeason_countryName(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/v2/leagues/country/"+semanticInput;

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

    public static void apiFootball_leaguesCountryCountryNameSeason_season(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/v2/leagues/country/england/" + semanticInput;

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

    // GET /v2/leagues/current/{country}
    public static void apiFootball_leaguesCurrentCountry_country(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/v2/leagues/current/" + semanticInput;

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

    // GET /v2/leagues/type/{type}/{country}/{season}
    public static void apiFootball_leaguesTypteTypeCountrySeason_country(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/v2/leagues/type/league/"+semanticInput+"/2019";

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

    public static void apiFootball_leaguesTypteTypeCountrySeason_season(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/v2/leagues/type/league/england/"+semanticInput;

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

    // GET /v2/leagues/type/{type}/{country}
    public static void apiFootball_leaguesTypteTypeCountry_country(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/v2/leagues/type/league/"+semanticInput;

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

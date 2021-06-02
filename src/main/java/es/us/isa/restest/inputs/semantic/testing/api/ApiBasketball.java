package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.squareup.okhttp.Response;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class ApiBasketball {

    private static final String baseUri = "https://api-basketball.p.rapidapi.com";

    // GET /standings
    // season
    public static String apiBasketball_standings_season_regex(String semanticInput, String apiKey, String host) throws IOException, JSONException {

        String url = baseUri + "/standings?league=12&season=" + semanticInput;

        printResponse(url);
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

        return response.body().string();
    }

    // season
    public static void apiBasketball_standings_season(String semanticInput, String apiKey, String host) throws IOException, JSONException {
        String url = baseUri + "/standings?league=12&season=" + semanticInput;
        printResponse(url);
    }

    // stage
    public static void apiBasketball_standings_stage(String semanticInput, String apiKey, String host) throws IOException, JSONException {
        String url = baseUri + "/standings?league=12&season=2019&stage=" + semanticInput;
        printResponse(url);
    }

    // group
    public static void apiBasketball_standings_group(String semanticInput, String apiKey, String host) throws IOException, JSONException {
        String url = baseUri + "/standings?league=12&season=2019&group=" + semanticInput;
        printResponse(url);
    }

    // GET /standings/groups
    public static void apiBasketball_standingsGroups_season(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/standings/groups?league=12&season=" + semanticInput;
        printResponse(url);
    }

    // GET /standing/stages
    public static void apiBasketball_standingsStages_season(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/standings/stages?league=12&season=" + semanticInput;
        printResponse(url);
    }

    // GET /leagues
    // season
    public static void apiBasketball_leagues_season(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/leagues?season=" + semanticInput;
        printResponse(url);
    }

    // name
    public static void apiBasketball_leagues_name(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/leagues?name=" + semanticInput;
        printResponse(url);
    }

    // country
    public static void apiBasketball_leagues_country(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/leagues?country=" + semanticInput;
        printResponse(url);
    }

    // code
    public static void apiBasketball_leagues_code(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/leagues?code=" + semanticInput;
        printResponse(url);
    }

    // GET /countries
    public static void apiBasketball_countries_code(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/countries?code=" + semanticInput;
        printResponse(url);
    }

    public static void apiBasketball_countries_name(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/countries?name=" + semanticInput;
        printResponse(url);
    }


}

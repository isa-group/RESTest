package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

public class WeatherForecast14Days {

    private static final String baseUri = "https://weather-forecast-14-days.p.rapidapi.com";

    // GET /api/countrycitylist
    public static void weatherForecast14Days_apiCountryCityList_country(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/api/countrycitylist?COUNTRY=" + semanticInput;

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

    // GET /api/countrycitylist
    public static void weatherForecast14Days_getForecastData_LON(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/api/getforecastdata?LON=" + semanticInput + "&LAT=42.6306";

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

    public static void weatherForecast14Days_getForecastData_LAT(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/api/getforecastdata?LON=-74.6594&LAT=" + semanticInput;

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

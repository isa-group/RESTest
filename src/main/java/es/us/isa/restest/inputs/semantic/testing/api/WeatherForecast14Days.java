package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class WeatherForecast14Days {

    private static final String baseUri = "https://weather-forecast-14-days.p.rapidapi.com";

    // GET /api/countrycitylist
    public static void weatherForecast14Days_apiCountryCityList_country(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/api/countrycitylist?COUNTRY=" + semanticInput;
        printResponse(url);
    }

    // GET /api/getforecastdata
    // LON
    public static void weatherForecast14Days_getForecastData_LON(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/api/getforecastdata?LON=" + semanticInput + "&LAT=42.6306";
        printResponse(url);
    }

    // LAT
    public static void weatherForecast14Days_getForecastData_LAT(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/api/getforecastdata?LON=-74.6594&LAT=" + semanticInput;
        printResponse(url);
    }


    // GET /api/getlocation
    // city
    public static void weatherForecast14Days_getLocation_city(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/api/getlocation?city=" + semanticInput;
        printResponse(url);
    }

    // zipcode
    public static void weatherForecast14Days_getLocation_zipcode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/api/getlocation?ZIPCODE=" + semanticInput;
        printResponse(url);
    }

}

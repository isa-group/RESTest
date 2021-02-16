package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class CountriesCities {

    private static final String baseUri = "https://countries-cities.p.rapidapi.com";

     // GET /location/city/nearby
    // latitude
    public static void countriesCities_locationCityNearby_latitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/location/city/nearby?latitude=" + semanticInput + "&longitude=-74.0697";
        printResponse(url);
    }

    // longitude
    // https://countries-cities.p.rapidapi.com/location/city/nearby?latitude=49.6848&longitude=-66.3292
    public static void countriesCities_locationCityNearby_longitude(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/location/city/nearby?latitude=49.6848&longitude=" + semanticInput;
        printResponse(url);
    }

    // GET /location/country/{code}/geojson
    public static void countriesCities_locationCountryCodeGeojson_code(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/location/country/"+semanticInput+"/geojson";
        printResponse(url);
    }

    // GET /location/country/{code}
    public static void countriesCities_locationCountryCode_code(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/location/country/" + semanticInput;
        printResponse(url);

    }

    // GET /location/country/{code}/city/list
    // code
    public static void countriesCities_locationCountryCodeCityList_code(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/location/country/" + semanticInput + "/city/list";
        printResponse(url);

    }

    // population
    public static void countriesCities_locationCountryCodeCityList_population(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/location/country/US/city/list?population=" + semanticInput;
        printResponse(url);
    }
}

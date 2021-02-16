package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class PublicHoliday{

    private static final String baseUrl = "https://public-holiday.p.rapidapi.com";

    public static void publicHoliday_countryCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUrl + "/2019/" + semanticInput;
        printResponse(url);
    }

    public static void publicHoliday_year(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUrl + "/"+semanticInput+"/US";
        printResponse(url);
    }


}

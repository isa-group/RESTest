package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class CarbonFootprint {

    private static final String baseUri = "https://carbonfootprint1.p.rapidapi.com";

    public static void carbonFootprint_PM(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/AirQualityHealthIndex?O3=10&NO2=10&PM=" + semanticInput;
        printResponse(url);
    }

}

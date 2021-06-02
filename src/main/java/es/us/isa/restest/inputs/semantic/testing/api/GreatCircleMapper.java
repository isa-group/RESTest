package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class GreatCircleMapper {

    private static String baseUri = "https://greatcirclemapper.p.rapidapi.com";
    private static String operationPath = "/airports/search/";

    public static void greatCircleMapper_iataIcao(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + operationPath + semanticInput;
        printResponse(url);
    }

}

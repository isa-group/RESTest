package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class SimilarWeb {

    private static final String baseUri = "https://similarweb2.p.rapidapi.com";
    private static final String operationPath = "/trafficoverview";

    public static void similarweb_website(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + operationPath + "?website=" + semanticInput;
        printResponse(url);
    }
}

package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class FootballPrediction {

    private static final String baseUri = "https://football-prediction-api.p.rapidapi.com";

    // /api/v2/performance-stats (Performance stats for past predictions)
    public static void footballPredictionFederation(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/api/v2/performance-stats?market=classic&federation=" + semanticInput;
        printResponse(url);
    }

}

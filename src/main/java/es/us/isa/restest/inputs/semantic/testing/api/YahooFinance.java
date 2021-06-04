package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class YahooFinance {

    private static final String baseUri = "https://yahoo-finance15.p.rapidapi.com";

    //  /api/yahoo/qu/quote/{symbol}/financial-data
    public static void yahooFinanceGetFinancialData(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/api/yahoo/qu/quote/" + semanticInput + "/financial-data";
        printResponse(url);
    }


}

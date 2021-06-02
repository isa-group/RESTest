package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class CurrencyConverter {

    private static final String baseUri = "https://currency-converter5.p.rapidapi.com";

    // /currency/convert
    public static void currencyConverterFrom(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/currency/convert?from=" + semanticInput + "&to=EUR&amount=1";
        printResponse(url);
    }

    public static void currencyConverterTo(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/currency/convert?from=EUR&to=" + semanticInput + "&amount=1";
        printResponse(url);
    }

    // /currency/historical/{date}
    public static void currencyHistoricalFrom(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/currency/historical/2020-01-20?from=" + semanticInput + "&amount=1&to=GBP";
        printResponse(url);
    }

    public static void currencyHistoricalTo(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/currency/historical/2020-01-20?from=EUR&amount=1&to=" + semanticInput;
        printResponse(url);
    }




}

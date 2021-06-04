package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class FixerCurrency {

    private static final String baseUri = "https://fixer-fixer-currency-v1.p.rapidapi.com";

    // /convert (Default convert)
    public static void fixerCurrencyConvertFrom(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/convert?amount=12&to=EUR&from=" + semanticInput;
        printResponse(url);
    }

    public static void fixerCurrencyConvertTo(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/convert?amount=12&from=EUR&to=" + semanticInput;
        printResponse(url);
    }


//    /latest (Latests rates)
    public static void fixerCurrencyLatestSymbols(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/latest?base=USD&symbols=" + semanticInput;
        printResponse(url);
    }

    public static void fixerCurrencyLatestBase(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/latest?base="+semanticInput+"&symbols=EUR";
        printResponse(url);
    }

//    /{date} (Historical rates)
    public static void fixerCurrencyHistoricalSymbols(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/2013-12-24?base=USD&symbols=" + semanticInput;
        printResponse(url);
    }

    public static void fixerCurrencyHistoricalBase(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/2013-12-24?base="+semanticInput+"&symbols=EUR";
        printResponse(url);
    }






}

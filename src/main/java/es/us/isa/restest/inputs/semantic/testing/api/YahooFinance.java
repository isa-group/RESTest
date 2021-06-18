package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class YahooFinance {

    private static final String baseUri = "https://yahoo-finance15.p.rapidapi.com";

    //  /api/yahoo/qu/quote/{symbol}/financial-data
    public static void yahooFinanceGetFinancialData(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/api/yahoo/qu/quote/" + semanticInput + "/financial-data";
        printResponse(url);
    }


    // REGEX
    //  /api/yahoo/qu/quote/{symbol}/financial-data
    public static String yahooFinanceGetFinancialData_regex(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/api/yahoo/qu/quote/" + semanticInput + "/financial-data";

        System.out.println(url);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("x-rapidapi-host", host)
                .addHeader("x-rapidapi-key", apiKey)
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println("--------------------------------------------------------------------------------------");

        return response.body().string();

    }


}

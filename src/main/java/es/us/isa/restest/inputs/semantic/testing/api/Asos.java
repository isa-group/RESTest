package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class Asos {

    private static final String baseUri = "https://asos2.p.rapidapi.com";

//    GET /v2/auto-complete
    // country
    public static void asos_v2AutoComplete_country(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v2/auto-complete" + "?q=bikini&country=" + semanticInput;
        printResponse(url);
    }

    // store
    public static void asos_v2AutoComplete_store(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v2/auto-complete" + "?q=bikini&store=" + semanticInput;
        printResponse(url);
    }

    // currency
    public static void asos_v2AutoComplete_currency(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v2/auto-complete" + "?q=bikini&currency=" + semanticInput;
        printResponse(url);
    }

    // lang
    public static void asos_v2AutoComplete_lang(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v2/auto-complete" + "?q=bikini&lang=" + semanticInput;
        printResponse(url);
    }

//    /products/v3/list-similarities
    // currency
    public static void asos_productv3ListSimilarities_currency(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/products/v3/list-similarities" + "?id=9851612&currency=" + semanticInput;
        printResponse(url);
    }

    // store
    public static void asos_productv3ListSimilarities_store(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/products/v3/list-similarities" + "?id=9851612&store=" + semanticInput;
        printResponse(url);
    }

    // lang
    public static void asos_productv3ListSimilarities_lang(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/products/v3/list-similarities" + "?id=9851612&lang=" + semanticInput;
        printResponse(url);
    }


//    GET /products/v3/detail
    // lang
    public static void asos_productv3Detail_lang(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/products/v3/detail" + "?id=9851612&lang=" + semanticInput;
        printResponse(url);
    }

    // currency
    public static void asos_productv3Detail_currency(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/products/v3/detail" + "?id=9851612&currency=" + semanticInput;
        printResponse(url);
    }

    // store
    public static void asos_productv3Detail_store(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/products/v3/detail" + "?id=9851612&store=" + semanticInput;
        printResponse(url);
    }

    // GET /categories/list
    // country
    public static void asos_categoriesList_country(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/categories/list?country=" + semanticInput;
        printResponse(url);
    }

    // lang
    public static void asos_categoriesList_lang(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/categories/list?lang=" + semanticInput;
        printResponse(url);
    }





}

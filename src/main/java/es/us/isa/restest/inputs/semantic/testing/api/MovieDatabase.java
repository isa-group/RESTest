package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class MovieDatabase {

    private static final String baseUri = "https://movie-database-imdb-alternative.p.rapidapi.com";

    public static void movieDatabase_y(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/?i=tt0201567&y=" + semanticInput;
        printResponse(url);
    }


    public static void movieDatabase_s(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/?s=" + semanticInput;
        printResponse(url);
    }

}

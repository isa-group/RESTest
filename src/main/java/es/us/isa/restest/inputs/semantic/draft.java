package es.us.isa.restest.inputs.semantic;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;


public class draft {

    public static void main(String[] args) throws IOException {

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://us-weather-by-city.p.rapidapi.com/getweather?city=San%20Francisco&state=CA")
                .get()
                .addHeader("x-rapidapi-host", "us-weather-by-city.p.rapidapi.com")
                .addHeader("x-rapidapi-key", "6a615b46f4mshab392a25b2bc44dp16cee9jsn2bd2d62e5f69")
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");

    }
}

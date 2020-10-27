package es.us.isa.restest.inputs.semantic;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;


public class draft {

    public static void main(String[] args) throws IOException {

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://rapidapi.p.rapidapi.com/v2/prices/nearest-places-matrix?destination=MOW&origin=LED&currency=RUB")
                .get()
                .addHeader("x-access-token", "5b3ef0bdeca04643188c6610c30056f5")
                .addHeader("x-rapidapi-key", "69c700dd67msh57b046423099254p1d4b4cjsn1908598eb392")
                .addHeader("x-rapidapi-host", "travelpayouts-travelpayouts-flight-data-v1.p.rapidapi.com")
                .build();

        Response response = client.newCall(request).execute();

        System.out.println("RESPONSE CODE: " + response.code());
        System.out.println(response.body().string());

    }
}

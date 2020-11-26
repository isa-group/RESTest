package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

public class Skyscanner {

    private static final String baseUri = "https://skyscanner-skyscanner-flight-search-v1.p.rapidapi.com";

//    GET /apiservices/autosuggest/v1.0/{country}/{currency}/{locale}/

    public static void skyscanner_firstOp_country(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/apiservices/autosuggest/v1.0/"+semanticInput+"/GBP/en-GB/?query=Stockholm";

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");


    }

    // /apiservices/autosuggest/v1.0/UK/GBP/en-GB/

    public static void skyscanner_firstOp_currency(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/apiservices/autosuggest/v1.0/UK/"+semanticInput+"/en-GB/?query=Stockholm";

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");


    }

//    GET /apiservices/browseroutes/v1.0/{country}/{currency}/{locale}/{originplace}/{destinationplace}/{outboundpartialdate}


    public static void skyscanner_secondOp_country(String semanticInput, String apiKey, String host) throws IOException {

            String url = baseUri + "/apiservices/browseroutes/v1.0/"+semanticInput+"/USD/en-US/SFO-sky/JFK-sky/anytime";

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");


    }

    public static void skyscanner_secondOp_currency(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/apiservices/browseroutes/v1.0/US/"+semanticInput+"/en-US/SFO-sky/JFK-sky/anytime";

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");


    }


    //    GET /apiservices/browsequotes/v1.0/{country}/{currency}/{locale}/{originplace}/{destinationplace}/{outboundpartialdate}

    public static void skyscanner_thirdOp_country(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/apiservices/browsequotes/v1.0/"+semanticInput+"/USD/en-US/SFO-sky/JFK-sky/anytime";

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");


    }

    public static void skyscanner_thirdOp_currency(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/apiservices/browsequotes/v1.0/US/"+semanticInput+"/en-US/SFO-sky/JFK-sky/anytime";

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");

    }

    //    GET /apiservices/browsedates/v1.0/{country}/{currency}/{locale}/{originplace}/{destinationplace}/{outboundpartialdate}

    public static void skyscanner_fourthOp_country(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/apiservices/browsedates/v1.0/"+semanticInput+"/USD/en-US/SFO-sky/JFK-sky/anytime";

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");


    }

    public static void skyscanner_fourthOp_currency(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/apiservices/browsedates/v1.0/US/"+semanticInput+"/en-US/SFO-sky/JFK-sky/anytime";

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");

    }

    //    /apiservices/browsedates/v1.0/{country}/{currency}/{locale}/{originplace}/{destinationplace}/{outboundpartialdate}/{inboundpartialdate}

    public static void skyscanner_fithOp_country(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/apiservices/browsedates/v1.0/"+semanticInput+"/USD/en-US/SFO-sky/JFK-sky/anytime/anytime";

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");


    }

    public static void skyscanner_fithOp_currency(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/apiservices/browsedates/v1.0/US/"+semanticInput+"/en-US/SFO-sky/JFK-sky/anytime/anytime";

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");

    }

//    GET /apiservices/browsequotes/v1.0/{country}/{currency}/{locale}/{originplace}/{destinationplace}/{outboundpartialdate}/{inboundpartialdate}

    public static void skyscanner_sixthOp_country(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/apiservices/browsequotes/v1.0/"+semanticInput+"/USD/en-US/SFO-sky/JFK-sky/anytime/anytime";

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");


    }

    public static void skyscanner_sixthOp_currency(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/apiservices/browsequotes/v1.0/US/"+semanticInput+"/en-US/SFO-sky/JFK-sky/anytime/anytime";

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");

    }

    //    GET /apiservices/browseroutes/v1.0/{country}/{currency}/{locale}/{originplace}/{destinationplace}/{outboundpartialdate}/{inboundpartialdate}

    public static void skyscanner_seventhOp_country(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/apiservices/browseroutes/v1.0/"+semanticInput+"/USD/en-US/SFO-sky/JFK-sky/anytime/anytime";

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");


    }

    public static void skyscanner_seventhOp_currency(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/apiservices/browseroutes/v1.0/US/"+semanticInput+"/en-US/SFO-sky/JFK-sky/anytime/anytime";

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");

    }

    //    GET /apiservices/browsedates/v1.0/{country}/{currency}/{locale}/{originplace}/{destinationplace}/{outboundpartialdate}/{inboundpartialdate}

    public static void skyscanner_eightOp_country(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/apiservices/browsedates/v1.0/"+semanticInput+"/USD/en-US/SFO-sky/JFK-sky/anytime/anytime";

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");


    }

    public static void skyscanner_eightOp_currency(String semanticInput, String apiKey, String host) throws IOException {

        String url = baseUri + "/apiservices/browsedates/v1.0/US/"+semanticInput+"/en-US/SFO-sky/JFK-sky/anytime/anytime";

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
        System.out.println(response.body().string());
        System.out.println("--------------------------------------------------------------------------------------");

    }

}

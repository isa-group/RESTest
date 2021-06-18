package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class AirportIX {

    private static final String baseUri = "https://airportix.p.rapidapi.com";

    // /airport/delay_index/{code}/ (Airports delay index)
    // code
    public static void airportix_delayIndex_code(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/airport/delay_index/" + semanticInput + "/";
        printResponse(url);
    }

    // /airport/city_code/{cityCode}/ (Airports by city code)
    // cityCode
    public static void airportix_airportsByCityCode_cityCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/airport/city_code/" + semanticInput + "/";
        printResponse(url);
    }

    // /airport/country_code/{isoCode}/{classification} (Airports by country code)
    // isoCode
    public static void airportix_airportsByCountryCode_isoCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/airport/country_code/" + semanticInput + "/1";
        printResponse(url);
    }

    // /airport/code/{code}/ (Airports by IATA or ICAO code)
    // code
    public static void airportix_airportsByIataOrIcaoCode_code(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/airport/code/" + semanticInput + "/";
        printResponse(url);
    }

    // /plane/bynumber/{number}/ (Aircraft by MSN or Registration number)
    // number
    public static void airportix_aircraftByMSN_number(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/plane/bynumber/" + semanticInput + "/";
        printResponse(url);
    }


    // /plane/directory/{code}/ (Aircraft directory by IATA code)
    // code
    public static void airportix_aircraftDirectoryByIataCode_code(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/plane/directory/" + semanticInput + "/";
        printResponse(url);
    }

    // REGEX /airport/delay_index/{code}/ (Airports delay index)
    // code
    public static String airportIX_delayIndex_code_regex(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/airport/delay_index/" + semanticInput + "/";

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

    // REGEX /airport/city_code/{cityCode}/ (Airports by city code)
    // cityCode
    public static String airportix_airportsByCityCode_cityCode_regex(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/airport/city_code/" + semanticInput + "/";

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

    // /airport/code/{code}/ (Airports by IATA or ICAO code)
    // code
    public static String airportix_airportsByIataOrIcaoCode_code_regex(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/airport/code/" + semanticInput + "/";

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

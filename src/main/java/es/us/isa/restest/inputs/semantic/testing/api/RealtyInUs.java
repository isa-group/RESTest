package es.us.isa.restest.inputs.semantic.testing.api;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import org.json.JSONException;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class RealtyInUs {

    private static final String baseUri = "https://realty-in-us.p.rapidapi.com";

    // /schools/list-nearby
    // lon
    public static void realtyInUs_listNearby_lon(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/schools/list-nearby?lon=" + semanticInput + "&lat=35.129431";
        printResponse(url);
    }

    // lat
    public static void realtyInUs_listNearby_lat(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/schools/list-nearby?lon=-117.937021&lat=" + semanticInput;
        printResponse(url);
    }


    // /properties/v2/list-sold
    // city
    public static void realtyInUs_propertiesV2ListSold_city(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/properties/v2/list-sold?offset=0&limit=200&city=" + semanticInput + "&state_code=NY";
        printResponse(url);
    }

    // state_code
    public static void realtyInUs_propertiesV2ListSold_stateCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/properties/v2/list-sold?offset=0&limit=200&city=New%20York%20City&state_code=" + semanticInput;
        printResponse(url);
    }

    // postal_code
    public static void realtyInUs_propertiesV2ListSold_postalCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/properties/v2/list-sold?postal_code=" + semanticInput;
        printResponse(url);
    }

    // /mortgage/check-equity-rates
    // zip
    public static void realtyInUs_mortageCheckEquityRates_zip(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/mortgage/check-equity-rates?loanAmount=70000&mortgageBalance=150000&propertyValue=300000&zip=" + semanticInput + "&state=CA";
        printResponse(url);
    }

    // state
    public static void realtyInUs_mortageCheckEquityRates_state(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/mortgage/check-equity-rates?loanAmount=70000&mortgageBalance=150000&propertyValue=300000&zip=93505&state=" + semanticInput;
        printResponse(url);
    }

    public static String realtyInUs_mortageCheckEquitityRates_state_regex(String semanticInput, String apiKey, String host) throws IOException, JSONException {
        String url = baseUri + "/mortgage/check-equity-rates?loanAmount=70000&mortgageBalance=150000&propertyValue=300000&zip=93505&state=" + semanticInput;

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


    // /finance/rates
    // loc
    public static void realtyInUs_financeRates_loc(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/finance/rates?loc=" + semanticInput;
        printResponse(url);
    }


}

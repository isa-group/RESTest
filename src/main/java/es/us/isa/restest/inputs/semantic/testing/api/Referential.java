package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class Referential {

    private static final String baseUri = "https://referential.p.rapidapi.com";

    // /v1/city (Cities)
    // iso_a2
    public static void referential_v1City_isoA2(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/city?iso_a2=" + semanticInput;
        printResponse(url);
    }

    // timezone
    public static void referential_v1City_timezone(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/city?timezone=" + semanticInput;
        printResponse(url);
    }

    // lang
    public static void referential_v1City_lang(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/city?lang=" + semanticInput;
        printResponse(url);
    }

    // state_code
    public static void referential_v1City_stateCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/city?state_code=" + semanticInput;
        printResponse(url);
    }


    // /v1/state (State)
    // iso_3166_2
    public static void referential_v1State_iso31662(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/state?iso_3166_2=" + semanticInput;
        printResponse(url);
    }

    // iso_a2
    public static void referential_v1State_isoA2(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/state?iso_a2=" + semanticInput;
        printResponse(url);
    }

    // lang
    public static void referential_v1State_lang(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/state?lang=" + semanticInput;
        printResponse(url);
    }


    // /v1/continent (Continent)
    // continent_code
    public static void referential_v1Continent_continentCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/continent?continent_code=" + semanticInput;
        printResponse(url);
    }

    // lang
    public static void referential_v1Continent_lang(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/continent?lang=" + semanticInput;
        printResponse(url);
    }


    // /v1/continent/{id} (Continent by id)
    // code
    public static void referential_v1ContinentById_code(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/continent/" + semanticInput;
        printResponse(url);
    }

    // lang
    public static void referential_v1ContinentById_lang(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/continent/EU?lang=" + semanticInput;
        printResponse(url);
    }

    // continent_code
    public static void referential_v1ContinentById_continentCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/continent/EU?continent_code=" + semanticInput;
        printResponse(url);
    }


    // /v1/country (Countries)
    // currency_code
    public static void referential_v1Countries_currencyCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/country?currency_code=" + semanticInput;
        printResponse(url);
    }

    // currency_num_code
    public static void referential_v1Countries_currencyNumCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/country?currency_num_code=" + semanticInput;
        printResponse(url);
    }

    // continent_code
    public static void referential_v1Countries_continentCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/country?continent_code=" + semanticInput;
        printResponse(url);
    }

    // iso_a2
    public static void referential_v1Countries_isoA2(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/country?iso_a2=" + semanticInput;
        printResponse(url);
    }

    // dial_code
    public static void referential_v1Countries_dialCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/country?dial_code=" + semanticInput;
        printResponse(url);
    }

    // lang
    public static void referential_v1Countries_lang(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/country?lang=" + semanticInput;
        printResponse(url);
    }

    // iso_a3
    public static void referential_v1Countries_isoA3(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/country?iso_a3=" + semanticInput;
        printResponse(url);
    }

    // currency (name)
    public static void referential_v1Countries_currency(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/country?currency=" + semanticInput;
        printResponse(url);
    }


    // /v1/country/{iso_code} (Country by iso code)
    // iso_code
    public static void referential_countryByIsoCode_isoCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/country/" + semanticInput;
        printResponse(url);
    }

    // lang
    public static void referential_countryByIsoCode_lang(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/country/US?lang=" + semanticInput;
        printResponse(url);
    }


    // /v1/timezone (Timezones)
    // code
    public static void referential_timezones_code(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/timezone?code=" + semanticInput;
        printResponse(url);
    }

    // daylights_code
    public static void referential_timezones_daylightsCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/timezone?daylights_code=" + semanticInput;
        printResponse(url);
    }

    // timezone
    public static void referential_timezones_timezone(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/timezone?timezone=" + semanticInput;
        printResponse(url);
    }


    // /v1/lang/{lang} (Language by id)
    // lang
    public static void referential_languageById_lang(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/lang/" + semanticInput;
        printResponse(url);
    }

    // lang_3
    public static void referential_languageById_lang3(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/lang/es?lang_3=" + semanticInput;
        printResponse(url);
    }

    // iso_a2
    public static void referential_languageById_isoA2(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/lang/es?iso_a2=" + semanticInput;
        printResponse(url);
    }

}

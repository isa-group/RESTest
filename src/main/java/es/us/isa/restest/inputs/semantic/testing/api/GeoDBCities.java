package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class GeoDBCities {

    private static final String baseUri = "https://wft-geo-db.p.rapidapi.com";

    // /v1/geo/adminDivisions (Administrative Divisions)
    // location
    public static void geoDbCities_administrativeDivisions_location(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/geo/adminDivisions?location=" + semanticInput;
        printResponse(url);
    }

    // timeZoneIds
    public static void geoDbCities_administrativeDivisions_timeZoneIds(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/geo/adminDivisions?timeZoneIds=" + semanticInput;
        printResponse(url);
    }

    // languageCode
    public static void geoDbCities_administrativeDivisions_languageCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/geo/adminDivisions?languageCode=" + semanticInput;
        printResponse(url);
    }

    // countryIds
    public static void geoDbCities_administrativeDivisions_countryIds(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/geo/adminDivisions?countryIds=" + semanticInput;
        printResponse(url);
    }

    // excludedCountryIds
    public static void geoDbCities_administrativeDivisions_excludedCountryIds(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/geo/adminDivisions?excludedCountryIds=" + semanticInput;
        printResponse(url);
    }


    // /v1/geo/countries (Countries)
    // currencyCode
    public static void geoDbCities_countries_currencyCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/geo/countries?currencyCode=" + semanticInput;
        printResponse(url);
    }


    // /v1/geo/countries/{countryid}/regions (Country regions)
    // countryid
    public static void geoDbCities_countryRegions_countryId(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/geo/countries/" + semanticInput + "/regions";
        printResponse(url);
    }

    // /v1/geo/countries/{countryid}/regions/{regioncode} (Country Region details)
    // countryId
    public static void geoDbCities_countryRegionDetails_countryId(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/geo/countries/" + semanticInput + "/regions/CA";
        printResponse(url);
    }

    // regioncode
    public static void geoDbCities_countryRegionDetails_regioncode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/v1/geo/countries/US/regions/" + semanticInput;
        printResponse(url);
    }

}

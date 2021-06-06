package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class AeroDataBox {

    private static final String baseUri = "https://aerodatabox.p.rapidapi.com";


    // /aircrafts/{searchBy}/{searchParam} (Aircraft by reg. / hex ICAO 24-bit address)
    // searchParam
    public static void aeroDataBoxAircraftByRegIcao24(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/aircrafts/reg/" + semanticInput;
        printResponse(url);

        url = baseUri + "/aircrafts/icao24/" + semanticInput;
        printResponse(url);
    }

    // /aircrafts/reg/{reg}/image/beta (Aircraft image by registration)
    // reg
    public static void aeroDataBoxAircraftImageByRegistration(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/aircrafts/reg/" + semanticInput + "/image/beta";
        printResponse(url);
    }

    // /airports/search/location/{lat}/{lon}/km/{radiusKm}/{limit}
    // lat
    public static void aeroDataBoxSearchAirportsByLocationLat(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/airports/search/location/" + semanticInput + "/-0.103869/km/100/16?withFlightInfoOnly=0";
        printResponse(url);
    }

    // lon
    public static void aeroDataBoxSearchAirportsByLocationLon(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/airports/search/location/51.511142/" + semanticInput + "/km/100/16?withFlightInfoOnly=0";
        printResponse(url);
    }

    // /airports/{codeType}/{code} (Aiport by IATA/ICAO code)
    //code
    public static void aeroDataBoxAirportByIataIcaoCodeCode(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/airports/iata/" + semanticInput;
        printResponse(url);

        url = baseUri + "/airports/icao/" + semanticInput;
        printResponse(url);
    }


    // /airports/{codeType}/{codeFrom}/distance-time/{codeTo} (Distance / Flight time to another airport by IATA/ICAO-codes)
    // codeFrom
    public static void aeroDataBox_FlightTimeToAnotherAirportByIataIcao_codeFrom(String semanticInput, String apiKey, String host) throws IOException {
        // IATA
        String url = baseUri + "/airports/iata/" + semanticInput + "/distance-time/AMS";
        printResponse(url);

        // ICAO
        url = baseUri + "/airports/icao/" + semanticInput + "/distance-time/UUEE";
        printResponse(url);
    }

    // codeTo
    public static void aeroDataBox_FlightTimeToAnotherAirportByIataIcao_codeTo(String semanticInput, String apiKey, String host) throws IOException {
        // IATA
        String url = baseUri + "/airports/iata/AMS/distance-time/" + semanticInput;
        printResponse(url);

        // ICAO
        url = baseUri + "/airports/icao/UUEE/distance-time/" + semanticInput;
        printResponse(url);
    }

    // /airports/icao/{icao}/runways (Airport runways by ICAO code)
    // icao
    public static void aeroDataBox_airportRunwaysByIcaoCode_icao(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/airports/icao/" + semanticInput + "/runways";
        printResponse(url);
    }



}

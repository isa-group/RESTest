package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class NavitimeRouteTotalNavi {

    private static final String baseUri = "https://navitime-route-totalnavi.p.rapidapi.com";

    // /shape_transit
    // start
    public static void navitimeRouteCar_shapeTransit_start(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/shape_transit?start=" + semanticInput + "&goal=35.661971%2C139.703795&start_time=2020-08-19T10%3A00%3A00";
        printResponse(url);
    }


    // goal
    public static void navitimeRouteCar_shapeTransit_goal(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/shape_transit?start=35.665251%2C139.712092&goal=" + semanticInput + "&start_time=2020-08-19T10%3A00%3A00";
        printResponse(url);
    }
    
}

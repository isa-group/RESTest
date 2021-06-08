package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class NavitimeRouteCar {

    private static final String baseUri = "https://navitime-route-car.p.rapidapi.com";

    // /ic (ic)
    // coord
    public static void navitimeRouteCar_ic_coord(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/ic?coord=" + semanticInput;
        printResponse(url);
    }


    // /shape_car (shape_car)
    // start
    public static void navitimeRouteCar_shapeCar_start(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/shape_car?start=" + semanticInput + "&goal=35.661971%2C139.703795";
        printResponse(url);
    }


    // goal
    public static void navitimeRouteCar_shapeCar_goal(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/shape_car?start=35.665251%2C139.712092&goal=" + semanticInput;
        printResponse(url);
    }
    
}

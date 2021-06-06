package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class PeriodicTableOfElements {

    private static final String baseUri = "https://periodic-table-of-elements.p.rapidapi.com";

    //  /element/name/{name}
    public static void periodicTableOfElementsName(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/element/name/" + semanticInput;
        printResponse(url);
    }

}

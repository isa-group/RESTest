package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class SubtitlesForYoutube {

    private static final String baseUri = "https://subtitles-for-youtube.p.rapidapi.com";

    public static void getSubtitleJson(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/subtitles/XbGs_qK2PQA?lang=" + semanticInput;
        printResponse(url);
    }


}

package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;
import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponsePost;

public class FaceDetection {

    private static final String baseUri = "https://face-detection6.p.rapidapi.co";

    // /img/face (Face Detection)
    // url
    public static void faceDetection_url(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/img/face";
        String body = "url=" + semanticInput;
        printResponsePost(url, body);
    }



}

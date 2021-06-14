package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;
import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponsePost;

public class RestbAiWatermarkDetection {

    private static final String baseUri = "https://restb-ai-watermark-detection.p.rapidapi.com";

    // /wmdetect (wmdetect)
    // image_url
    public static void restbAiWatermarkDetection_imageurl(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/wmdetect";
        String body = "image_url=http%3A%2F%2Fwww.wizards.com%2Fdnd%2Fimages%2FMM35_gallery%2FMM35_PG130.jpg";// + semanticInput;
        printResponsePost(url, body);
    }

    
}

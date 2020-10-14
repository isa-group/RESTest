package es.us.isa.restest.inputs.semantic;

import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.apache.commons.text.similarity.LevenshteinDistance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class NLPUtils {

    private static String stopwordsPath = "src/main/java/es/us/isa/restest/inputs/semantic/englishStopWords.txt";

    // With Comparator
    public static List<String> posTagging(String description, String name){
        String res = description.toLowerCase().trim();
        Properties props = new Properties();

        props.setProperty("annotators","tokenize,ssplit,pos,lemma");

        //Build pipeline
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        CoreDocument document = pipeline.processToCoreDocument(res);

        pipeline.annotate(document);

        List<String> stopWords = getStopWords();

        LevenshteinDistance leven = new LevenshteinDistance();

        //        FW	Foreign word
        //        NN	Noun, singular or mass
        //        NNS	Noun, plural
        //        NNP	Proper noun, singular
        //        NNPS	Proper noun, plural
        List<String> names = document.tokens().stream()
                .filter(x -> (x.tag().equals("FW") || x.tag().equals("NN") ||
                        x.tag().equals("NNS") || x.tag().equals("NNP") || x.tag().equals("NNPS"))
                        &&  (!stopWords.contains(x.lemma())))
                .map(x -> x.lemma())
                .filter(x -> x.charAt(0) == name.charAt(0))
                .sorted(Comparator.comparing(x->leven.apply(name, x)))
                .collect(Collectors.toList());


        return names;
    }

    // Without Comparator
    public static List<String> posTagging(String description){
        String res = description.toLowerCase().trim();
        Properties props = new Properties();

        props.setProperty("annotators","tokenize,ssplit,pos,lemma");

        //Build pipeline
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        CoreDocument document = pipeline.processToCoreDocument(res);

        pipeline.annotate(document);

        List<String> stopWords = getStopWords();

        LevenshteinDistance leven = new LevenshteinDistance();

        //        FW	Foreign word
        //        NN	Noun, singular or mass
        //        NNS	Noun, plural
        //        NNP	Proper noun, singular
        //        NNPS	Proper noun, plural
        List<String> names = document.tokens().stream()
                .filter(x -> (x.tag().equals("FW") || x.tag().equals("NN") ||
                        x.tag().equals("NNS") || x.tag().equals("NNP") || x.tag().equals("NNPS"))
                        &&  (!stopWords.contains(x.lemma())))
                .map(x -> x.lemma())
                .collect(Collectors.toList());


        return names;
    }



    private static List<String> getStopWords(){
        List<String> lines = Collections.emptyList();
        try{
            lines = Files.readAllLines(Paths.get(stopwordsPath), StandardCharsets.UTF_8);
        }catch (IOException e){
            e.printStackTrace();
        }
        return lines;
    }

    public static String splitCamelAndSnakeCase(String s) {
        return s.replaceAll("_"," ").replaceAll(
                String.format("%s|%s|%s",
                        "(?<=[A-Z])(?=[A-Z][a-z])",
                        "(?<=[^A-Z])(?=[A-Z])",
                        "(?<=[A-Za-z])(?=[^A-Za-z])"
                ),
                " "
        );
    }

}

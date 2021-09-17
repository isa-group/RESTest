package es.us.isa.restest.inputs.semantic;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.tokensregex.CoreMapExpressionExtractor;
import edu.stanford.nlp.ling.tokensregex.Env;
import edu.stanford.nlp.ling.tokensregex.MatchedExpression;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class NLPUtils {

    private NLPUtils() {
        throw new IllegalStateException("Utility class");
    }

    private static final Logger log = LogManager.getLogger(NLPUtils.class);

    private static String stopwordsPath = "src/main/resources/arte/englishStopWords.txt";
    private static String rules = "src/main/resources/arte/rules.txt";

    private static String annotatorName = "annotators";
    private static String annotatorValue = "tokenize,ssplit,pos,lemma";

    // With Comparator
    public static List<String> posTagging(String description, String name){
        String res = description.toLowerCase().trim();
        Properties props = new Properties();

        props.setProperty(annotatorName, annotatorValue);

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
        // Return the list of names
        return document.tokens().stream()
                .filter(x -> (x.tag().equals("FW") || x.tag().equals("NN") ||
                        x.tag().equals("NNS") || x.tag().equals("NNP") || x.tag().equals("NNPS") || x.tag().equals("JJ"))
                        &&  (!stopWords.contains(x.lemma())))
                .map(x -> x.lemma())
                .filter(x -> x.charAt(0) == name.charAt(0))
                .sorted(Comparator.comparing(x->leven.apply(name, x)))
                .collect(Collectors.toList());

    }

    // Without Comparator
    public static List<String> posTagging(String description){
        String res = description.toLowerCase().trim();
        Properties props = new Properties();

        props.setProperty(annotatorName, annotatorValue);

        //Build pipeline
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        CoreDocument document = pipeline.processToCoreDocument(res);

        pipeline.annotate(document);

        List<String> stopWords = getStopWords();

        //        FW	Foreign word
        //        NN	Noun, singular or mass
        //        NNS	Noun, plural
        //        NNP	Proper noun, singular
        //        NNPS	Proper noun, plural
        // Return names
        return document.tokens().stream()
                .filter(x -> (x.tag().equals("FW") || x.tag().equals("NN") ||
                        x.tag().equals("NNS") || x.tag().equals("NNP") || x.tag().equals("NNPS") || x.tag().equals("JJ"))
                        &&  (!stopWords.contains(x.lemma())))
                .map(x -> x.lemma())
                .collect(Collectors.toList());

    }



    private static List<String> getStopWords(){
        List<String> lines = Collections.emptyList();
        try{
            lines = Files.readAllLines(Paths.get(stopwordsPath), StandardCharsets.UTF_8);
        }catch (IOException e){
            log.error(e.getMessage());
        }
        return lines;
    }


    public static Map<Double, Set<String>> extractPredicateCandidatesFromDescription(String name, String description){

        Map<Double, Set<String>> res = new HashMap<>();

        // Remove html tags
        description = description.replaceAll("\\<.*?\\>", "");

        Env env = TokenSequencePattern.getNewEnv();
        env.bind("$NAME_RULE1", "/(?i)"+name+"/" );
        env.bind("$NAME_RULE2", "/(?i)"+name+".*/" );
        CoreMapExpressionExtractor<MatchedExpression> extractor = CoreMapExpressionExtractor
                .createExtractorFromFiles(env, rules);

        StanfordCoreNLP pipeline = new StanfordCoreNLP(PropertiesUtils.asProperties(annotatorName, annotatorValue));

        description = String.join(" ", posTagging(description));

        Annotation annotation = new Annotation(description);

        pipeline.annotate(annotation);

        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);

        for (CoreMap sentence : sentences) {
            List<MatchedExpression> matchedExpressionsInSentence = extractor.extractExpressions(sentence);
            for (MatchedExpression matched: matchedExpressionsInSentence){
                Double priority = matched.getPriority();

                Set<String> match = new HashSet<>();

                if(priority == 2.0){
                    String[] array = matched.getValue().get().toString().split(" ");

                    log.info("Added {} and {}{} to candidates list", array[0], name, array[1]);

                    match.add(array[0]);
                    match.add(name + array[1]);
                }else{
                    String candidate = matched.getValue().get().toString();
                    log.info("Added {} to candidates list",candidate );
                    match.add(candidate);
                }

                if(res.keySet().contains(priority)){
                    res.get(priority).addAll(match);
                }else{
                    res.put(priority, match);
                }
            }

        }
        return res;
    }



}

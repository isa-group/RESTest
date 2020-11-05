package es.us.isa.restest.inputs.semantic;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.MultiPatternMatcher;
import edu.stanford.nlp.ling.tokensregex.SequenceMatchResult;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DescriptionProcessing {

    public static void main(String[] args) throws IOException, Exception {

        List<String> names = readData("src/main/java/es/us/isa/restest/inputs/semantic/names.txt");
        List<String> descriptions = readData("src/main/java/es/us/isa/restest/inputs/semantic/descriptions.txt");

        if(names.size()!= descriptions.size()){
            throw new Exception("Tamaños distintos");
        }

        for(int i=0; i<names.size(); i++){

            System.out.println("\n--------------------------------------------------");
            System.out.println("Iteración número " + i + "\n");

            String name = names.get(i);
            String description = descriptions.get(i);

            // Remove html tags
            description = description.replaceAll("\\<.*?\\>", "");

            System.out.println("Parameter name: " + name);
            System.out.println("Parameter description: " + description);
            System.out.println("\n");

            // Preprocessing pipeline
            StanfordCoreNLP pipeline = new StanfordCoreNLP(PropertiesUtils.asProperties("annotators", "tokenize,ssplit,pos,lemma"));


            Annotation annotation = new Annotation(description);

            pipeline.annotate(annotation);
            List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);

            // List of patterns (rules)
            List<TokenSequencePattern> tokenSequencePatterns = new ArrayList<>();
            // TODO: Añadir action
            // TODO: Hacer que rule2 devuelva parámetro con y sin cortar
            // TODO: Múltiples resultados (Iteración 42, 48) y borrar repeticiones (Iteración 43, 44)
            String[] patterns = {
                    "(?$rule1 [ {word: /(?i)" + name + "/} ] [{word: /(?i)code|id/}] )",
                    "(?$rule2 [ {word: /(?i)" + name + ".*/ }] [{word: /(?i)code|id/}] )",
                    "(?$rule3  [{pos: FW} | {pos: NN} | {pos: NNS} | {pos: NNP} | {pos: NNPS}] [{word: /(?i)code|id/}]  )"      // TODO REGLA 3: Palabra != parámetro
            };

            for (String line : patterns) {
                TokenSequencePattern pattern = TokenSequencePattern.compile(line);
                tokenSequencePatterns.add(pattern);
            }

            // Match all the rules in a single iteration
            MultiPatternMatcher<CoreMap> multiMatcher = TokenSequencePattern.getMultiPatternMatcher(tokenSequencePatterns);

            int j = 0;
            for (CoreMap sentence : sentences) {
                List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
                System.out.println("Sentence #" + ++j);
                System.out.print("  Tokens:");
                for (CoreLabel token : tokens) {
                    System.out.print(' ');
                    System.out.print(token.toShortString("Text", "PartOfSpeech", "NamedEntityTag"));
                }
                System.out.println();

                List<SequenceMatchResult<CoreMap>> answers = multiMatcher.findNonOverlapping(tokens);
                int k = 0;
                for (SequenceMatchResult<CoreMap> matched : answers) {
                    System.out.println("  Match #" + ++k);
                    System.out.println("    match: " + matched.group(0));
                    System.out.println("      rule1: " + matched.group("$rule1"));
                    System.out.println("      rule2: " + matched.group("$rule2"));
                    System.out.println("      rule3: " + matched.group("$rule3"));

                }
            }




        }

    }

    public static List<String> readData(String path) throws IOException {
        List<String> content = new ArrayList<String>();
        try(BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = "";
            while ((line = br.readLine()) != null) {
                content.add(line);
            }
        } catch (FileNotFoundException e) {
            System.out.println(e);
        }
        return content;
    }

}

package es.us.isa.restest.testcases.diversity;

import org.apache.commons.text.similarity.JaccardSimilarity;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.apache.commons.text.similarity.LevenshteinDistance;

public class SimilarityMeter {

    private METRIC similarityMetric;
    private JaccardSimilarity jaccardMeter;
    private JaroWinklerSimilarity jaroWinklerMeter;
    private LevenshteinDistance levenshteinMeter;

    public SimilarityMeter(METRIC similarityMetric) {
        this.similarityMetric = similarityMetric;

        switch (similarityMetric) {
            case JACCARD:
                jaccardMeter = new JaccardSimilarity();
                break;
            case JARO_WINKLER:
                jaroWinklerMeter = new JaroWinklerSimilarity();
                break;
            case LEVENSHTEIN:
                levenshteinMeter = new LevenshteinDistance();
                break;
            default:
                throw new IllegalArgumentException("The similarity metric " + similarityMetric + " is not supported");
        }
    }

    public Double apply (CharSequence left, CharSequence right) {
        switch (similarityMetric) {
            case JACCARD:
                return jaccardMeter.apply(left, right);
            case JARO_WINKLER:
                return jaroWinklerMeter.apply(left, right);
            case LEVENSHTEIN: // Computed as: 1 - (distance / maxlength(left, right))
                double maxStringLength = Math.max(left.length(), right.length());
                if (maxStringLength != 0)
                    return 1 - (double)levenshteinMeter.apply(left, right) / maxStringLength;
                else
                    return 1d;
            default:
                throw new IllegalArgumentException("The similarity metric " + similarityMetric + " is not supported");
        }
    }

    public METRIC getSimilarityMetric() {
        return similarityMetric;
    }

    public enum METRIC {
        JACCARD,
        JARO_WINKLER,
        LEVENSHTEIN
    }

}
package es.us.isa.restest.inputs.stateful;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.stanford.nlp.process.Morphology;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Utility class used by BodyGenerator and ParameterGenerator. Given a dictionary
 * of values and a parameter name, it extracts from the dictionary a potentially
 * valid value for that parameter.
 */
public class DataMatching {

    private static final Morphology m = new Morphology();

    public static JsonNode getParameterValue(ObjectNode dict, String operationMethod, String operationPath, String paramName) {
        JsonNode paramValue = null;
        ObjectNode operationDict = (ObjectNode) dict.get(operationMethod + operationPath);

        if ("id".equalsIgnoreCase(paramName)) {
            paramValue = getParameterValue(dict, operationMethod, operationPath, getIdParameterName(paramName, operationPath));
            if (paramValue != null)
                return paramValue;
        }

        // 1st option: Original operation, same parameter name
        paramValue = getValueFromOperationDict(operationDict, paramName);

        // 2nd option: Other operations, same parameter name
        if (paramValue == null) {
            Iterator<JsonNode> dictIterator = dict.iterator();
            while (dictIterator.hasNext() && paramValue == null) {
                paramValue = getValueFromOperationDict((ObjectNode) dictIterator.next(), paramName);
            }
        }

        // 3rd option: Original operation, similar parameter name
        if (paramValue == null) {
            List<JsonNode> paramValues = getValuesOfSimilarParameterNames(operationDict, paramName);
            if (!paramValues.isEmpty())
                paramValue = paramValues.get(ThreadLocalRandom.current().nextInt(paramValues.size()));
        }

        // 4th option: Other operations, similar parameter name
        if (paramValue == null) {
            List<JsonNode> paramValues = new ArrayList<>();
            Iterator<JsonNode> dictIterator = dict.iterator();
            while (dictIterator.hasNext())
                paramValues.addAll(getValuesOfSimilarParameterNames((ObjectNode) dictIterator.next(), paramName));
            if (!paramValues.isEmpty())
                paramValue = paramValues.get(ThreadLocalRandom.current().nextInt(paramValues.size()));
        }

        // 5th option: Repeat whole process with sub-property name (e.g., "data.comment.id" -> "comment.id")
        if (paramValue == null && paramName.contains("."))
            paramValue = getParameterValue(dict, operationMethod, operationPath, paramName.substring(paramName.indexOf('.')+1));

        return paramValue;
    }

    private static JsonNode getValueFromOperationDict(ObjectNode operationDict, String paramName) {
        JsonNode paramValue = null;
        if (operationDict != null) {
            ArrayNode paramDict = ((ArrayNode) operationDict.get(paramName));
            if (paramDict != null) {
                paramValue = paramDict.get(ThreadLocalRandom.current().nextInt(paramDict.size()));
            }
        }
        return paramValue;
    }

    private static List<JsonNode> getValuesOfSimilarParameterNames(ObjectNode operationDict, String paramName) {
        List<JsonNode> validValues = new ArrayList<>();

        if (operationDict != null) {
            String processedParamName = processParameterName(paramName);

            operationDict.fields().forEachRemaining(f -> {
                String processedFieldName = processParameterName(f.getKey());
                if (processedParamName.matches(".*" + processedFieldName + "$")
                        || processedFieldName.matches(".*" + processedParamName + "$"))
                    f.getValue().elements().forEachRemaining(validValues::add);
            });
        }

        return validValues;
    }

    private static String processParameterName(String paramName) {
        return Arrays.stream(paramName.toLowerCase().split("[^a-z\\d]"))
                .map(m::stem)
                .filter(Objects::nonNull)
                .collect(Collectors.joining());
    }

    private static String getIdParameterName(String paramName, String operationPath) {
        return m.stem(operationPath
                .substring(operationPath.lastIndexOf('/') + 1)
                .replaceAll("^([gG]et|[sS]et|[pP]ost|[pP]ut|[dD]elete|[pP]atch|[oO]btain|[rR]etrieve|[cC]reate|[uU]pdate|[rR]emove)([A-Z])", "$2"))
                + ("ID".equals(paramName) ? "ID" : "Id");
    }
}

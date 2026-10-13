/*
 * Copyright 2026 ISA Research Group, Universidad de Sevilla.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.restest.gen;

import io.restest.core.json.JsonException;
import io.restest.core.json.JsonValue;
import io.restest.core.json.YamlText;
import io.restest.core.model.HttpMethod;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads a plan out of the text of a file.
 *
 * <p>Written in YAML, like the lists of values it names and like the specifications both sit
 * beside. A file this version cannot read is refused by name rather than half-read: a plan that
 * silently lost one of its strategies would send a quarter of its requests somewhere nobody asked
 * for, and nothing in the output would say so.
 *
 * <pre>{@code
 * version: 2
 * strategies:
 *   - name: nominal
 *     share: 75
 *     kind: nominal
 *     sources:
 *       - source: enum
 *       - weighted: {example: 60, random: 40}
 *   - name: mutation
 *     share: 15
 *     kind: mutation
 *     sources:
 *       - source: enum
 *       - source: random
 *   - name: sequences
 *     share: 10
 *     kind: sequences
 *     sources:
 *       - dictionary: pet-clinic
 *       - source: random
 * operations:
 *   methods: [GET, POST]
 * }</pre>
 *
 * <p>Every thing a plan says has one way to be written. A strategy says what it does in its
 * {@code kind}, one word. A source is one of RESTest's own, by a word, or one of the lists somebody
 * handed over, by its name; a group to choose between is a map from each source to its weight.
 * What a strategy of the kind that pushes at the API draws on first - the list of awkward values
 * RESTest carries - comes with the kind, and is not written among its sources.
 */
final class CampaignDocument {

    /** The version of the file format this build writes and reads. */
    static final long VERSION = 2;

    /** The members a plan may have. Anything else is a typo, and a typo is worth saying. */
    private static final List<String> MEMBERS = List.of("version", "strategies", "operations");

    /** The members one strategy may have, every one of them required. */
    private static final List<String> STRATEGY_MEMBERS = List.of("name", "share", "kind", "sources");

    /** What a strategy can be, each a different way of building its requests. */
    private static final List<String> KINDS = List.of("nominal", "fuzzing", "mutation", "sequences");

    /** The members the operation filter may have. */
    private static final List<String> OPERATIONS_MEMBERS = List.of("methods", "only");

    /** How a step naming one of the tool's own sources is written. */
    private static final String SOURCE = "source";

    /** How a step naming one list of values is written, and the key that names lists in a group. */
    private static final String DICTIONARY = "dictionary";

    /** How a step naming several sources to choose between is written. */
    private static final String WEIGHTED = "weighted";

    /** The word for every list this run was handed, which is one of the tool's own sources. */
    private static final String EVERY_LIST_GIVEN = "dictionaries";

    /**
     * The words that name a source of the tool's own, and the order they always take inside a
     * group - the order the plan RESTest carries writes them in, with the closed list of accepted
     * values last because it is rarely chosen among at all. A group's members are drawn in this
     * order whatever order a file wrote them in, so a file rewritten by a tool that sorts the keys
     * of a map sends the same requests from the same seed.
     */
    private static final List<String> SOURCE_WORDS =
            List.of("example", "random", "observed", EVERY_LIST_GIVEN, "default", "enum");

    private CampaignDocument() {
    }

    /**
     * The plan a file's text describes.
     *
     * @param text the file's contents
     * @param describedAs where it came from, for saying so when it cannot be read
     * @return the plan
     * @throws JsonException if the text is not a plan this version can read
     */
    static Campaign read(String text, String describedAs) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(describedAs, "describedAs");
        try {
            return readOrRefuse(text, describedAs);
        } catch (IllegalArgumentException notAPlan) {
            // The records a plan is made of validate themselves, and what they throw for is a
            // person's typo rather than a fault in the tool: shares that do not add up, a list
            // named by the empty string. Said the way every other thing wrong with a file is said,
            // so the run answers "your file" rather than "RESTest broke".
            throw new JsonException(describedAs + ": " + notAPlan.getMessage());
        }
    }

    private static Campaign readOrRefuse(String text, String describedAs) {
        JsonValue.JsonObject document =
                YamlText.asObject(YamlText.read(text, describedAs), "a plan", describedAs);
        // The version is read before anything else is judged, for the reason a dictionary reads
        // its own first: a file written in another version of the format will have members this
        // build does not know, and telling its author they have misspelt something would be the
        // one message that is certainly wrong about it.
        long version = YamlText.asWholeNumber(required(document, "version", describedAs),
                "'version'", describedAs);
        if (version != VERSION) {
            throw new JsonException(describedAs + " is a plan written in version " + version
                    + " of the format, and this version of RESTest reads version " + VERSION
                    + "; 'restest run --print-campaign' prints a plan it reads");
        }
        rejectAnythingUnrecognised(document, MEMBERS, "a plan", describedAs);

        List<Campaign.PlannedStrategy> strategies = new ArrayList<>();
        for (JsonValue held : YamlText.asList(required(document, "strategies", describedAs),
                "'strategies'", describedAs)) {
            strategies.add(strategy(YamlText.asObject(held, "a strategy", describedAs),
                    describedAs));
        }
        WhichOperations operations = document.member("operations")
                .map(stated -> operations(YamlText.asObject(stated, "'operations'", describedAs),
                        describedAs))
                .orElseGet(WhichOperations::everything);
        return new Campaign(strategies, operations);
    }

    private static Campaign.PlannedStrategy strategy(JsonValue.JsonObject stated,
            String describedAs) {
        rejectAnythingUnrecognised(stated, STRATEGY_MEMBERS, "a strategy", describedAs);
        String name = YamlText.asText(required(stated, "name", describedAs),
                "a strategy's 'name'", describedAs);
        String where = "the strategy called '" + name + "'";
        int share = asShare(YamlText.asWholeNumber(required(stated, "share", describedAs),
                where + "'s 'share'", describedAs), where + "'s 'share'", describedAs);
        String kind = YamlText.asText(stated.member("kind").orElseThrow(() -> new JsonException(
                describedAs + ": " + where + " says no 'kind', and every strategy says which it is: "
                        + String.join(", ", KINDS))), where + "'s 'kind'", describedAs);
        if (!KINDS.contains(kind)) {
            throw new JsonException(describedAs + ": " + where + " is of kind '" + kind
                    + "', and a strategy's kind is one of " + String.join(", ", KINDS));
        }
        List<Campaign.Entry> sources = new ArrayList<>();
        if ("fuzzing".equals(kind)) {
            // What makes a strategy one that pushes: the list of awkward values RESTest carries,
            // asked before anything the strategy names, which is there for the values that list
            // has nothing sendable for.
            sources.add(new Campaign.Entry.Single(
                    new Campaign.Source.OneList(RandomTestCaseGenerator.PUSHES_AT_THE_API)));
        }
        List<JsonValue> written = YamlText.asList(required(stated, "sources", describedAs),
                where + "'s 'sources'", describedAs);
        if (written.isEmpty()) {
            throw new JsonException(describedAs + ": " + where + " has no sources, and every "
                    + "strategy needs at least one: it is where values come from, and for a "
                    + "strategy of kind fuzzing it is what is asked where RESTest's own awkward "
                    + "values have nothing to send");
        }
        for (JsonValue held : written) {
            sources.add(entry(YamlText.asObject(held, "a source of " + where, describedAs),
                    where, describedAs));
        }
        return new Campaign.PlannedStrategy(name, share, sources, "mutation".equals(kind),
                "sequences".equals(kind));
    }

    /**
     * One step of a strategy's list: a single source, or a group to choose between.
     *
     * <p>Which of the three it is comes from the word it is written under, so that none has to be
     * worked out from its shape.
     */
    private static Campaign.Entry entry(JsonValue.JsonObject stated, String where,
            String describedAs) {
        if (stated.member("weight").isPresent()) {
            throw new JsonException(describedAs + ": a source of " + where + " carries a "
                    + "'weight', and a weight only goes inside a '" + WEIGHTED + "' group, as "
                    + WEIGHTED + ": {example: 50, random: 50}");
        }
        rejectAnythingUnrecognised(stated, List.of(SOURCE, DICTIONARY, WEIGHTED), "a source",
                describedAs);
        List<String> ways = List.of(SOURCE, DICTIONARY, WEIGHTED).stream()
                .filter(way -> stated.member(way).isPresent()).toList();
        if (ways.isEmpty()) {
            throw new JsonException(describedAs + ": a source of " + where + " says where its "
                    + "values come from, with '" + SOURCE + "' for one of RESTest's own, '"
                    + DICTIONARY + "' for one of your dictionaries by its name, or '" + WEIGHTED
                    + "' for a group to choose between. This one says none of them");
        }
        if (ways.size() > 1) {
            throw new JsonException(describedAs + ": a source of " + where + " is named one way, "
                    + "and this one is named " + ways.size() + " ways at once ("
                    + String.join(", ", ways) + ")");
        }
        JsonValue said = stated.member(ways.getFirst()).orElseThrow();
        return switch (ways.getFirst()) {
            case SOURCE -> new Campaign.Entry.Single(ownSource(
                    YamlText.asText(said, "a '" + SOURCE + "'", describedAs), describedAs));
            case DICTIONARY -> {
                if (said instanceof JsonValue.JsonObject) {
                    throw new JsonException(describedAs + ": a source of " + where + " gives '"
                            + DICTIONARY + "' a map of weights, and weights only go inside a '"
                            + WEIGHTED + "' group; on its own, one of your dictionaries is named "
                            + "as " + DICTIONARY + ": <its name>");
                }
                yield new Campaign.Entry.Single(oneList(
                        YamlText.asText(said, "a '" + DICTIONARY + "'", describedAs), describedAs));
            }
            default -> group(said, where, describedAs);
        };
    }

    /**
     * A group to choose between: a map from each source to its weight, with the lists somebody
     * handed over under {@code dictionary}, by name.
     */
    private static Campaign.Entry group(JsonValue said, String where, String describedAs) {
        String what = "a '" + WEIGHTED + "' group of " + where;
        if (!(said instanceof JsonValue.JsonObject map)) {
            throw new JsonException(describedAs + ": " + what + " is a map from each source to its "
                    + "weight, such as " + WEIGHTED + ": {example: 50, random: 50}");
        }
        Map<String, Integer> own = new TreeMap<>(java.util.Comparator.comparingInt(
                SOURCE_WORDS::indexOf));
        Map<String, Integer> lists = new TreeMap<>();
        map.members().forEach((key, weight) -> {
            if (DICTIONARY.equals(key)) {
                lists.putAll(weightedLists(weight, what, describedAs));
                return;
            }
            if (!SOURCE_WORDS.contains(key)) {
                throw new JsonException(describedAs + ": " + what + " has '" + key + "', "
                        + whatIsNotASource(key) + "; a group holds RESTest's own sources ("
                        + String.join(", ", SOURCE_WORDS) + ") and your dictionaries under '"
                        + DICTIONARY + "'");
            }
            own.put(key, weight(weight, "'" + key + "' in " + what, describedAs));
        });
        List<Campaign.Share> among = new ArrayList<>();
        own.forEach((word, weight) -> among.add(new Campaign.Share(ownSource(word, describedAs),
                weight)));
        lists.forEach((name, weight) -> among.add(new Campaign.Share(oneList(name, describedAs),
                weight)));
        if (among.size() < 2) {
            throw new JsonException(describedAs + ": " + what + " has one source in it, and a "
                    + "group is for choosing between several; one on its own is written on its "
                    + "own, as " + SOURCE + ": random");
        }
        return new Campaign.Entry.Group(among);
    }

    /** The lists somebody handed over, by name, with the weight each gets in a group. */
    private static Map<String, Integer> weightedLists(JsonValue said, String what,
            String describedAs) {
        if (!(said instanceof JsonValue.JsonObject names) || names.members().isEmpty()) {
            throw new JsonException(describedAs + ": '" + DICTIONARY + "' in " + what + " names "
                    + "your dictionaries and the weight of each, as " + DICTIONARY
                    + ": {pet-clinic: 40}" + (said instanceof JsonValue.JsonNumber
                            ? "; for every dictionary handed over at once, write "
                                    + EVERY_LIST_GIVEN + ": " + ((JsonValue.JsonNumber) said)
                                            .value().toPlainString()
                            : ", and this one names none"));
        }
        Map<String, Integer> weights = new TreeMap<>();
        names.members().forEach((name, weight) -> weights.put(name,
                weight(weight, "'" + name + "' in " + what, describedAs)));
        return weights;
    }

    private static int weight(JsonValue said, String what, String describedAs) {
        return asShare(YamlText.asWholeNumber(said, what, describedAs), what, describedAs);
    }

    /** One of the tool's own sources, by the word that names it. */
    private static Campaign.Source ownSource(String word, String describedAs) {
        if (EVERY_LIST_GIVEN.equals(word)) {
            return new Campaign.Source.EveryListGiven();
        }
        return Campaign.Builtin.named(word).<Campaign.Source>map(Campaign.Source.Builtin::new)
                .orElseThrow(() -> new JsonException(describedAs + ": '" + word + "' "
                        + whatIsNotASource(word) + "; RESTest's own sources are "
                        + String.join(", ", SOURCE_WORDS) + ", and one of your dictionaries is "
                        + "named with '" + DICTIONARY + "' instead"));
    }

    /** One list somebody handed over, by its name. */
    private static Campaign.Source oneList(String name, String describedAs) {
        if (RandomTestCaseGenerator.PUSHES_AT_THE_API.equals(name)) {
            throw new JsonException(describedAs + ": " + whatFuzzingIs());
        }
        return new Campaign.Source.OneList(name);
    }

    /**
     * What a word that names none of the tool's sources most likely meant, said after it.
     *
     * <p>Two near misses are worth naming. The list of awkward values RESTest carries is not a
     * source a plan names, since it comes with the kind that pushes; and {@code dictionary} and
     * {@code dictionaries} are one letter apart and mean different things.
     */
    private static String whatIsNotASource(String word) {
        if (RandomTestCaseGenerator.PUSHES_AT_THE_API.equals(word)) {
            return "which is not a source: " + whatFuzzingIs();
        }
        if (DICTIONARY.equals(word)) {
            return "which is not one of RESTest's own sources: for every dictionary handed over, "
                    + "write '" + EVERY_LIST_GIVEN + "'";
        }
        return "which is not one of RESTest's own sources";
    }

    private static String whatFuzzingIs() {
        return "'" + RandomTestCaseGenerator.PUSHES_AT_THE_API + "' is RESTest's own list of "
                + "values to push with, which a strategy of kind fuzzing asks before its own "
                + "sources, and is not named as a source";
    }

    private static WhichOperations operations(JsonValue.JsonObject stated, String describedAs) {
        rejectAnythingUnrecognised(stated, OPERATIONS_MEMBERS, "'operations'", describedAs);
        Set<HttpMethod> methods = new LinkedHashSet<>();
        for (JsonValue held : stated.member("methods")
                .map(value -> YamlText.asList(value, "'methods'", describedAs))
                .orElse(List.of())) {
            String written = YamlText.asText(held, "a method", describedAs);
            methods.add(method(written, describedAs));
        }
        Set<String> only = new LinkedHashSet<>();
        for (JsonValue held : stated.member("only")
                .map(value -> YamlText.asList(value, "'only'", describedAs))
                .orElse(List.of())) {
            only.add(YamlText.asText(held, "an operation under 'only'", describedAs));
        }
        return WhichOperations.of(methods, only);
    }

    /**
     * The HTTP method a word names.
     *
     * <p>Read in either case, because a document writes {@code get} and a person writing a plan
     * writes {@code GET}, and refusing one of the two would be pedantry about a thing with no
     * ambiguity in it.
     */
    private static HttpMethod method(String written, String describedAs) {
        for (HttpMethod known : HttpMethod.values()) {
            if (known.name().equalsIgnoreCase(written)) {
                return known;
            }
        }
        throw new JsonException(describedAs + ": '" + written + "' is not an HTTP method. They are "
                + java.util.Arrays.stream(HttpMethod.values()).map(HttpMethod::name)
                        .reduce((a, b) -> a + ", " + b).orElseThrow()
                .toUpperCase(Locale.ROOT));
    }

    /**
     * A share or a weight, which is a number between nothing and a hundred.
     *
     * <p>Checked before it is narrowed to the size the rest of the tool holds it in. Narrowing
     * first is how {@code 4294967396} became {@code 100} and passed the check that shares add up.
     */
    private static int asShare(long stated, String what, String describedAs) {
        if (stated < 0 || stated > Campaign.WHOLE) {
            throw new JsonException(describedAs + ": " + what + " is " + stated
                    + ", and a share of something is between 0 and " + Campaign.WHOLE);
        }
        return (int) stated;
    }

    private static JsonValue required(JsonValue.JsonObject document, String member,
            String describedAs) {
        return document.member(member).orElseThrow(() -> new JsonException(
                describedAs + " has no '" + member + "' in it"));
    }

    /**
     * Refuses a file with a member nobody here recognises.
     *
     * <p>Strict on purpose, and easier to be strict now than later, which is the bargain the
     * dictionary format already made. A misspelt {@code sources} would otherwise leave a plan that
     * loads without complaint and then builds requests from nothing at all. Accepting unknown
     * members can be allowed later without breaking anybody's file; refusing them later cannot.
     */
    private static void rejectAnythingUnrecognised(JsonValue.JsonObject document,
            List<String> allowed, String what, String describedAs) {
        List<String> unrecognised = document.members().keySet().stream()
                .filter(member -> !allowed.contains(member))
                .toList();
        if (!unrecognised.isEmpty()) {
            throw new JsonException(describedAs + ": " + what + " has " + unrecognised.size()
                    + " thing(s) in it this version does not recognise ("
                    + String.join(", ", unrecognised) + "), which is usually a misspelling. What "
                    + "belongs there is " + String.join(", ", allowed));
        }
    }
}

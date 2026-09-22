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
import java.util.Objects;
import java.util.Set;

/**
 * Reads a plan out of the text of a file.
 *
 * <p>Written in YAML, like the lists of values it names and like the specifications both sit
 * beside. A file this version cannot read is refused by name rather than half-read: a plan that
 * silently lost one of its strategies would send a quarter of its requests somewhere nobody asked
 * for, and nothing in the output would say so.
 *
 * <pre>{@code
 * version: 1
 * strategies:
 *   - name: nominal
 *     share: 75
 *     sources:
 *       - source: enum
 *       - weighted:
 *           - source: example
 *             weight: 60
 *           - source: random
 *             weight: 40
 * operations:
 *   methods: [GET, POST]
 * }</pre>
 */
final class CampaignDocument {

    /** The version of the file format this build writes and reads. */
    static final long VERSION = 1;

    /** The members a plan may have. Anything else is a typo, and a typo is worth saying. */
    private static final List<String> MEMBERS = List.of("version", "strategies", "operations");

    /** The members one strategy may have. */
    private static final List<String> STRATEGY_MEMBERS = List.of("name", "share", "sources");

    /** The members the operation filter may have. */
    private static final List<String> OPERATIONS_MEMBERS = List.of("methods", "only");

    /** How a step naming one of the tool's own sources is written. */
    private static final String SOURCE = "source";

    /** How a step naming one list of values is written. */
    private static final String DICTIONARY = "dictionary";

    /** How a step naming every list the run was handed is written, and the one word it takes. */
    private static final String DICTIONARIES = "dictionaries";
    private static final String GIVEN = "given";

    /** How a step naming several sources to choose between is written. */
    private static final String WEIGHTED = "weighted";

    /** What one source inside such a group says about how often it should be chosen. */
    private static final String WEIGHT = "weight";

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
            // person's typo rather than a fault in the tool: a weight of nought, a list named by
            // the empty string. Said the way every other thing wrong with a file is said, so the
            // run answers "your file" rather than "RESTest broke".
            throw new JsonException(describedAs + ": " + notAPlan.getMessage());
        }
    }

    private static Campaign readOrRefuse(String text, String describedAs) {
        JsonValue.JsonObject document =
                YamlText.asObject(YamlText.read(text, describedAs), "a plan", describedAs);
        // The version is read before anything else is judged, for the reason a dictionary reads
        // its own first: a file written in a later version of the format will have members this
        // build does not know, and telling its author they have misspelt something would be the
        // one message that is certainly wrong about it.
        long version = YamlText.asWholeNumber(required(document, "version", describedAs),
                "'version'", describedAs);
        if (version != VERSION) {
            throw new JsonException(describedAs + " is a plan written in version " + version
                    + " of the format, and this version of RESTest reads version " + VERSION);
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
        List<Campaign.Entry> sources = new ArrayList<>();
        for (JsonValue held : YamlText.asList(required(stated, "sources", describedAs),
                where + "'s 'sources'", describedAs)) {
            sources.add(entry(YamlText.asObject(held, "a source of " + where, describedAs),
                    where, describedAs));
        }
        return new Campaign.PlannedStrategy(name, share, sources);
    }

    /**
     * One step of a strategy's list: a single source, or a group to choose between.
     *
     * <p>Which of the two it is comes from the word it is written under, so that neither has to be
     * worked out from its shape. A step that uses none of the four words is a step nobody can act
     * on, and saying which four they are is more use than saying it was not understood.
     */
    private static Campaign.Entry entry(JsonValue.JsonObject stated, String where,
            String describedAs) {
        if (stated.member(WEIGHTED).isPresent()) {
            rejectAnythingUnrecognised(stated, List.of(WEIGHTED), "a group of sources",
                    describedAs);
            List<Campaign.Share> among = new ArrayList<>();
            for (JsonValue held : YamlText.asList(stated.member(WEIGHTED).orElseThrow(),
                    "a '" + WEIGHTED + "' group of " + where, describedAs)) {
                JsonValue.JsonObject one =
                        YamlText.asObject(held, "a source inside a group", describedAs);
                int weight = asShare(YamlText.asWholeNumber(
                        one.member(WEIGHT).orElseThrow(() -> new JsonException(describedAs
                                + ": a source inside a '" + WEIGHTED + "' group says how much of "
                                + "the choice it gets, and this one has no '" + WEIGHT + "'")),
                        "a '" + WEIGHT + "'", describedAs),
                        "a '" + WEIGHT + "'", describedAs);
                among.add(new Campaign.Share(source(one, List.of(WEIGHT), describedAs), weight));
            }
            return new Campaign.Entry.Group(among);
        }
        if (stated.member(WEIGHT).isPresent()) {
            throw new JsonException(describedAs + ": a source of " + where + " carries a '" + WEIGHT
                    + "', but it is not inside a '" + WEIGHTED + "' group - and on its own a source "
                    + "is simply asked, so there is no choice for a weight to divide");
        }
        return new Campaign.Entry.Single(source(stated, List.of(), describedAs));
    }

    /**
     * Which source one step names, out of the three ways of naming one.
     *
     * @param stated the step
     * @param besides members that belong to the step rather than to the source, such as a weight
     */
    private static Campaign.Source source(JsonValue.JsonObject stated, List<String> besides,
            String describedAs) {
        List<String> allowed = new ArrayList<>(besides);
        allowed.add(SOURCE);
        allowed.add(DICTIONARY);
        allowed.add(DICTIONARIES);
        rejectAnythingUnrecognised(stated, allowed, "a source", describedAs);

        List<String> ways = List.of(SOURCE, DICTIONARY, DICTIONARIES).stream()
                .filter(way -> stated.member(way).isPresent()).toList();
        if (ways.size() > 1) {
            throw new JsonException(describedAs + ": a source is named one way, and this one is "
                    + "named " + ways.size() + " ways at once (" + String.join(", ", ways) + ")");
        }
        if (stated.member(SOURCE).isPresent()) {
            String word = YamlText.asText(stated.member(SOURCE).orElseThrow(),
                    "a '" + SOURCE + "'", describedAs);
            return new Campaign.Source.Builtin(Campaign.Builtin.named(word)
                    .orElseThrow(() -> new JsonException(describedAs + ": '" + word + "' is not "
                            + "one of the sources RESTest has of its own, which are "
                            + Campaign.Builtin.allOfThem() + ". A list of values is named with '"
                            + DICTIONARY + "' instead")));
        }
        if (stated.member(DICTIONARY).isPresent()) {
            return new Campaign.Source.OneList(YamlText.asText(
                    stated.member(DICTIONARY).orElseThrow(), "a '" + DICTIONARY + "'",
                    describedAs));
        }
        if (stated.member(DICTIONARIES).isPresent()) {
            String word = YamlText.asText(stated.member(DICTIONARIES).orElseThrow(),
                    "a '" + DICTIONARIES + "'", describedAs);
            if (!GIVEN.equals(word)) {
                throw new JsonException(describedAs + ": '" + DICTIONARIES + "' says which lists "
                        + "are meant, and the only thing it can say is '" + GIVEN + "' - every list "
                        + "this run was handed. To name one list, write '" + DICTIONARY + "'");
            }
            return new Campaign.Source.EveryListGiven();
        }
        throw new JsonException(describedAs + ": a source says where its values come from, with '"
                + SOURCE + "' for one of RESTest's own, '" + DICTIONARY + "' for one list by name, "
                + "or '" + DICTIONARIES + ": " + GIVEN + "' for every list this run was handed. "
                + "This one says none of them");
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

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
package io.restest.core.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One thing about the tool that somebody can change, and everything a person needs to know to
 * change it: which group it belongs to, what it is called, what kind of value it takes, and what it
 * does.
 *
 * <p>The list of them - {@link #all()} - is the whole of what can be configured. It is what
 * {@code --print-settings} prints, what a misspelt key is measured against when the tool offers the
 * name it thinks was meant, and what a test compares against {@link Settings} itself, so that a
 * number added to the tool without a key here is caught rather than being quietly unreachable.
 *
 * <p>Every key is written the same way everywhere it can be typed. {@code engine.maxConcurrency} in
 * a file and after {@code --set}; {@code RESTEST_ENGINE_MAX_CONCURRENCY} in the environment, where
 * only capitals and underscores are usual. A person learns one name.
 *
 * @param group which part of the tool this belongs to
 * @param name what it is called inside that group
 * @param kind what sort of value it takes
 * @param meaning what it does, in one line, as printed beside it
 */
public record SettingKey(String group, String name, SettingKind kind, String meaning) {

    /** How every environment variable naming a setting begins. */
    public static final String ENVIRONMENT_PREFIX = "RESTEST_";

    public SettingKey {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(meaning, "meaning");
    }

    /** How this key is written in a file and after {@code --set}. */
    public String fullName() {
        return group + "." + name;
    }

    /**
     * How this key is written as an environment variable.
     *
     * <p>The same words, in the spelling environments use: capitals, and an underscore wherever the
     * name has a capital letter of its own. {@code engine.maxConcurrency} becomes
     * {@code RESTEST_ENGINE_MAX_CONCURRENCY}.
     *
     * @return the variable's name
     */
    public String environmentName() {
        StringBuilder written = new StringBuilder(ENVIRONMENT_PREFIX)
                .append(group.toUpperCase(Locale.ROOT))
                .append('_');
        for (int at = 0; at < name.length(); at++) {
            char letter = name.charAt(at);
            if (Character.isUpperCase(letter) && at > 0) {
                written.append('_');
            }
            written.append(Character.toUpperCase(letter));
        }
        return written.toString();
    }

    /** Every setting there is, in the order {@code --print-settings} prints them. */
    public static List<SettingKey> all() {
        return ALL;
    }

    /** The groups there are, in printing order. */
    public static List<String> groups() {
        return List.of("engine", "schedule", "generation", "memory", "document", "report");
    }

    /**
     * The setting with this name.
     *
     * @param fullName the name as it is written in a file or after {@code --set}
     * @return that setting, or nothing if there is none
     */
    public static Optional<SettingKey> named(String fullName) {
        return Optional.ofNullable(BY_NAME.get(fullName));
    }

    /**
     * The setting whose name this one most looks like, for telling somebody who misspelt one what
     * they probably meant.
     *
     * <p>Nothing is offered when nothing is close. A suggestion that is wrong is worse than none:
     * somebody who typed {@code engine.threads} and is told to try {@code report.writeUpsInTotal}
     * learns that the tool is guessing, and stops reading what it says.
     *
     * @param fullName what was typed
     * @return the nearest real name, when one is near enough to be worth offering
     */
    public static Optional<SettingKey> nearestTo(String fullName) {
        String typed = fullName == null ? "" : fullName.trim().toLowerCase(Locale.ROOT);
        // A quarter of what was typed, so a short name has to be nearly right and a long one may be
        // a few letters out. Below one, nothing is ever offered for an empty or one-letter guess.
        int asFarAsIsWorthIt = Math.max(1, typed.length() / 4);
        SettingKey nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (SettingKey key : ALL) {
            int distance = distanceBetween(typed, key.fullName().toLowerCase(Locale.ROOT));
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = key;
            }
        }
        return nearestDistance <= asFarAsIsWorthIt ? Optional.ofNullable(nearest) : Optional.empty();
    }

    /**
     * How many single-letter changes turn one piece of text into the other.
     *
     * <p>The ordinary edit distance, written out rather than taken from a library, because this is
     * the only place in the tool that needs one and it is a dozen lines.
     */
    private static int distanceBetween(String one, String other) {
        int[] previous = new int[other.length() + 1];
        int[] current = new int[other.length() + 1];
        for (int column = 0; column <= other.length(); column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= one.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= other.length(); column++) {
                int substitution = previous[column - 1]
                        + (one.charAt(row - 1) == other.charAt(column - 1) ? 0 : 1);
                current[column] = Math.min(substitution,
                        Math.min(previous[column] + 1, current[column - 1] + 1));
            }
            int[] swapped = previous;
            previous = current;
            current = swapped;
        }
        return previous[other.length()];
    }

    private static SettingKey key(String group, String name, SettingKind kind, String meaning) {
        return new SettingKey(group, name, kind, meaning);
    }

    private static final List<SettingKey> ALL = List.of(
            key("engine", "connectTimeout", SettingKind.LENGTH_OF_TIME,
                    "how long to wait for the API to accept a connection at all"),
            key("engine", "readTimeout", SettingKind.LENGTH_OF_TIME,
                    "how long to wait for the API to answer once connected"),
            key("engine", "writeTimeout", SettingKind.LENGTH_OF_TIME,
                    "how long to wait while sending a request body"),
            key("engine", "minConcurrency", SettingKind.WHOLE_NUMBER,
                    "the fewest requests kept in flight, however badly the API behaves"),
            key("engine", "initialConcurrency", SettingKind.WHOLE_NUMBER,
                    "how many requests are in flight before anything is known about the API"),
            key("engine", "maxConcurrency", SettingKind.WHOLE_NUMBER,
                    "the most requests ever in flight at once. 1 for a fragile API"),
            key("engine", "slowdownFactor", SettingKind.NUMBER,
                    "how much slower than its best answer so far counts as the API struggling"),
            key("engine", "maxRetainedResponseBytes", SettingKind.WHOLE_NUMBER,
                    "how much of a reply body is kept in memory"),
            key("engine", "followRedirects", SettingKind.YES_OR_NO,
                    "whether a redirection is followed instead of being reported"),
            key("engine", "userAgent", SettingKind.TEXT,
                    "what the tool calls itself in the User-Agent header"),

            key("schedule", "workAheadFactor", SettingKind.WHOLE_NUMBER,
                    "how many requests may await an answer, as a multiple of maxConcurrency"),
            key("schedule", "announcementsAllowedToPileUp", SettingKind.WHOLE_NUMBER,
                    "how many announcements may await the reports before the run pauses"),
            key("schedule", "stragglerGrace", SettingKind.LENGTH_OF_TIME,
                    "how long past the deadline to wait for answers already asked for"),
            key("schedule", "openingLap", SettingKind.YES_OR_NO,
                    "whether a run starts by sending every operation once, the request likeliest to work"),
            key("schedule", "openingLapPatience", SettingKind.LENGTH_OF_TIME,
                    "how long each step of that opening lap waits for the answers to the one before"),

            key("generation", "optionalNestingDepth", SettingKind.WHOLE_NUMBER,
                    "below this depth, only what the description insists on is built"),
            key("generation", "hardNestingDepth", SettingKind.WHOLE_NUMBER,
                    "where building stops, however insistent the description is"),
            key("generation", "usualLongestString", SettingKind.WHOLE_NUMBER,
                    "the longest word invented when the description does not demand more"),
            key("generation", "longestString", SettingKind.WHOLE_NUMBER,
                    "beyond this, a demanded length is declined rather than built"),
            key("generation", "lowestNumber", SettingKind.NUMBER,
                    "where an invented number starts, when the description states no bottom"),
            key("generation", "roomAboveIt", SettingKind.NUMBER,
                    "how far above that it may go, when the description states no top"),
            key("generation", "decimalPlaces", SettingKind.WHOLE_NUMBER,
                    "decimal places for a number allowed to have them"),
            key("generation", "usualMostItems", SettingKind.WHOLE_NUMBER,
                    "the most items put in a list when the description does not demand more"),
            key("generation", "mostItems", SettingKind.WHOLE_NUMBER,
                    "beyond this, a demanded number of items is declined rather than built"),
            key("generation", "optionalPropertyChance", SettingKind.NUMBER,
                    "how often an optional property is included anyway, between 0 and 1"),
            key("generation", "optionalBodyChance", SettingKind.NUMBER,
                    "how often a request that merely accepts a body sends it anyway, between 0 "
                            + "and 1; a GET or a HEAD never does"),
            key("generation", "optionalParameterContinueChance", SettingKind.NUMBER,
                    "how often one more optional parameter is added on top of the ones already "
                            + "chosen, between 0 and 1"),
            key("generation", "nullInOneIn", SettingKind.WHOLE_NUMBER,
                    "one time in this many, a value allowed to be absent is sent as nothing"),
            key("generation", "uniqueAttempts", SettingKind.WHOLE_NUMBER,
                    "how many times a fresh element is attempted for a list of distinct items"),
            key("generation", "sendableAttempts", SettingKind.WHOLE_NUMBER,
                    "how many times a value is invented again after an unsendable one"),
            key("generation", "writableBodyAttempts", SettingKind.WHOLE_NUMBER,
                    "how many bodies are drawn while looking for one its media type can carry"),

            key("memory", "mostValuesUnderOneName", SettingKind.WHOLE_NUMBER,
                    "how many values the run remembers under any one name. 0 remembers none"),
            key("memory", "mostNames", SettingKind.WHOLE_NUMBER,
                    "how many different names are remembered at all"),
            key("memory", "longestValueKept", SettingKind.WHOLE_NUMBER,
                    "how long one remembered word or number may be, written out"),
            key("memory", "longestReplyRead", SettingKind.WHOLE_NUMBER,
                    "the largest reply the run reads looking for values to remember"),
            key("memory", "asDeepAsAReplyIsRead", SettingKind.WHOLE_NUMBER,
                    "how far into a reply that search goes"),

            key("document", "fetchTimeout", SettingKind.LENGTH_OF_TIME,
                    "how long to wait for a description fetched over the network"),
            key("document", "mostBytesRead", SettingKind.WHOLE_NUMBER,
                    "the largest description that is read at all"),

            key("report", "writeUpsPerOperationAndKind", SettingKind.WHOLE_NUMBER,
                    "how many faults of one kind, on one operation, are written out whole"),
            key("report", "writeUpsInTotal", SettingKind.WHOLE_NUMBER,
                    "and how many in the whole file"),
            key("report", "mostBodyBytesKept", SettingKind.WHOLE_NUMBER,
                    "how much of any one body the report quotes"),
            key("report", "faultsShownOnTheConsole", SettingKind.WHOLE_NUMBER,
                    "how many faults are printed in full before the screen stops being the place"),
            key("report", "skippedOperationsShownOnTheConsole", SettingKind.WHOLE_NUMBER,
                    "how many operations that could not be tested are named on the screen"));

    private static final Map<String, SettingKey> BY_NAME = byName();

    private static Map<String, SettingKey> byName() {
        Map<String, SettingKey> named = new LinkedHashMap<>();
        for (SettingKey key : ALL) {
            if (named.put(key.fullName(), key) != null) {
                throw new IllegalStateException("two settings are both called " + key.fullName());
            }
        }
        return Map.copyOf(named);
    }
}

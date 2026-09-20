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

import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonException;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Finds the dictionaries a run should use, and says what went wrong with the ones it could not.
 *
 * <p>Two kinds arrive. One travels with the tool - today that is the list of values to push at an
 * API with - and is always there. The rest are files somebody points the tool at, typically kept next
 * to the specification they belong to so that the good values somebody worked out for an API are
 * committed alongside it rather than living in one person's head.
 *
 * <p>One dictionary per file. A file says once what decides which of its values apply, and letting
 * several share a file would make that statement meaningless. Pointing at a directory reads every
 * {@code .yaml}, {@code .yml} and {@code .json} file in it, which is the comfortable way to keep
 * several.
 *
 * <p>Nothing here ends a run. A file that cannot be read costs the values in it and is reported, and
 * the run carries on with the others - the same bargain the tool makes with a specification it
 * cannot fully read.
 */
public final class Dictionaries {

    /** The list of values to push at an API with that travels with the tool. */
    private static final String SHIPPED_FUZZING = "fuzzing-dictionary.yaml";

    /** How the shipped dictionary is named when something is wrong with it, which would be our bug. */
    private static final String SHIPPED_DESCRIPTION =
            "the list of values to push with that is built into RESTest";

    /** How many of the entries under one reason a message names before it stops listing them. */
    private static final int NAMED_IN_FULL = 3;

    /** What an entry is guilty of when the document has no such operation at all. */
    private static final String NO_SUCH_OPERATION = "no such operation in this API";

    private Dictionaries() {
    }

    /**
     * What was found when the dictionaries were gathered.
     *
     * @param shipped the list RESTest carries, absent only when this build cannot read its own
     * @param fromTheUser the ones somebody handed over, which is a different question from which
     *     ones there are: a message about a file nobody wrote is a message nobody can act on
     * @param problems what went wrong, in the words a person should read, empty when nothing did
     */
    public record Found(Optional<Dictionary> shipped, List<Dictionary> fromTheUser,
            List<String> problems) {

        public Found {
            Objects.requireNonNull(shipped, "shipped");
            fromTheUser = List.copyOf(Objects.requireNonNull(fromTheUser, "fromTheUser"));
            problems = List.copyOf(Objects.requireNonNull(problems, "problems"));
        }

        /**
         * Every list this run should use, the one RESTest carries first.
         *
         * <p>Worked out from the two halves rather than held beside them, so that "which lists are
         * there" and "which of them were handed over" cannot come to disagree. Which they would,
         * eventually: this answer has had to learn the difference twice already.
         *
         * @return the lists
         */
        public List<Dictionary> dictionaries() {
            List<Dictionary> all = new ArrayList<>(shipped.map(List::of).orElse(List.of()));
            all.addAll(fromTheUser);
            return List.copyOf(all);
        }

        /** The names of the lists somebody handed over, as against the one RESTest carries. */
        public java.util.Set<String> namesFromTheUser() {
            return fromTheUser.stream().map(Dictionary::name)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    /**
     * The dictionary of awkward values that travels with the tool.
     *
     * @return it, or nothing at all if this build's own copy cannot be read
     */
    public static Optional<Dictionary> fuzzing() {
        try {
            return Optional.of(shipped());
        } catch (IOException | JsonException beyondHelp) {
            // Our own file, so this is a fault in this build rather than in anything the user did.
            // The run goes on without it: no awkward values is a worse run, not a broken one - and
            // whoever asked through gather is told why, which is the only way anybody would notice.
            return Optional.empty();
        }
    }

    /**
     * The shipped dictionary, or the reason it could not be read.
     *
     * <p>Separate from the method above because the reason matters. A file of ours that silently
     * fails to load leaves the tool quietly doing less than it says it does, and nothing in the
     * output would ever mention it. A test reads it through here, so a mistake in that file breaks
     * a build rather than a user's run.
     *
     * @return the dictionary
     * @throws IOException if this build does not carry the file at all
     * @throws JsonException if it carries one it cannot read
     */
    static Dictionary shipped() throws IOException {
        try (InputStream stream = Dictionaries.class.getResourceAsStream(SHIPPED_FUZZING)) {
            if (stream == null) {
                throw new IOException("this build of RESTest does not carry " + SHIPPED_FUZZING);
            }
            return DictionaryDocument.read(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8), SHIPPED_DESCRIPTION);
        }
    }

    /**
     * Every dictionary a run should use: the shipped one, then whatever the user pointed at.
     *
     * @param locations the files and directories named on the command line, in the order given
     * @param model the API being tested, so that a dictionary naming operations it does not have can
     *     be reported as the stale file it probably is
     * @return what was found, and what went wrong
     */
    public static Found gather(List<Path> locations, ApiModel model) {
        Objects.requireNonNull(locations, "locations");
        Objects.requireNonNull(model, "model");
        List<String> problems = new ArrayList<>();
        Optional<Dictionary> carried;
        try {
            carried = Optional.of(shipped());
        } catch (IOException | JsonException beyondHelp) {
            carried = Optional.empty();
            problems.add("RESTest's own list of values to push with could not be read, so nothing "
                    + "will be pushed at the API. This is a fault in this build of the tool, not in "
                    + "anything you did: " + beyondHelp.getMessage());
        }

        List<Dictionary> fromTheUser = new ArrayList<>();
        Map<Dictionary, Path> whereEachCameFrom = new java.util.LinkedHashMap<>();
        for (Path location : locations) {
            for (Path file : filesUnder(location, problems)) {
                read(file, problems).ifPresent(dictionary -> {
                    Dictionary named = underTheNamesTheRunPrints(dictionary, model, file, problems);
                    fromTheUser.add(named);
                    whereEachCameFrom.put(named, file);
                });
            }
        }
        // Judged once every file has been read. Which operations are given a body whole is a fact
        // about the lists this run holds, not about the file a particular entry was written in.
        Set<String> givenAWholeBody = operationsGivenAWholeBody(fromTheUser);
        whereEachCameFrom.forEach((dictionary, file) ->
                reportEntriesNothingWouldUse(dictionary, model, file, givenAWholeBody, problems));
        reportNamesUsedTwice(fromTheUser, problems);
        return new Found(carried, fromTheUser, problems);
    }

    /**
     * Says so when two lists are called the same thing.
     *
     * <p>A name is not decoration. It is how a plan says which lists a kind of request draws on,
     * and it is what a report prints beside a value to say where it came from - so two lists
     * answering to one name leave a plan with nothing to point at and a report unable to tell them
     * apart.
     *
     * <p>Only the lists somebody handed over are counted. Giving one of your own the same name as
     * the list RESTest carries is the documented way to have it pushed at an API, so that collision
     * is somebody following instructions rather than making a mistake. It costs what every shared
     * name costs - a value from either reads as having come from the same place - and the plan that
     * names each list separately is what ends it.
     */
    private static void reportNamesUsedTwice(List<Dictionary> found, List<String> problems) {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (Dictionary dictionary : found) {
            if (!seen.add(dictionary.name())) {
                problems.add("more than one list of values is called '" + dictionary.name()
                        + "', so nothing can say which of them a value came from, and a plan naming "
                        + "it would have two things to point at");
            }
        }
    }

    private static Optional<Dictionary> read(Path file, List<String> problems) {
        try {
            return Optional.of(DictionaryDocument.read(Files.readString(file), file.toString()));
        } catch (IOException unreadable) {
            problems.add(file + " could not be read: " + unreadable.getMessage());
        } catch (JsonException notADictionary) {
            problems.add(notADictionary.getMessage());
        }
        return Optional.empty();
    }

    /** Whether a file in a directory is one to try reading as a dictionary. */
    private static boolean looksLikeADictionary(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json");
    }

    /**
     * The files to read at one place the user named: the file itself, or every dictionary in a
     * directory.
     */
    private static List<Path> filesUnder(Path location, List<String> problems) {
        if (Files.isDirectory(location)) {
            try (Stream<Path> inIt = Files.list(location)) {
                List<Path> files = inIt
                        .filter(Files::isRegularFile)
                        .filter(Dictionaries::looksLikeADictionary)
                        .sorted()
                        .toList();
                if (files.isEmpty()) {
                    problems.add(location + " holds no .yaml, .yml or .json dictionary");
                }
                return files;
            } catch (IOException | UncheckedIOException unreadable) {
                problems.add(location + " could not be listed: " + unreadable.getMessage());
                return List.of();
            }
        }
        if (!Files.isReadable(location)) {
            problems.add("there is no dictionary at " + location);
            return List.of();
        }
        return List.of(location);
    }

    /**
     * The same dictionary with every operation under the name the run prints for it.
     *
     * <p>A file may name an operation either way: by the identifier the document declares, or by
     * the method and path, which anybody can read off the document without having to check whether
     * an identifier is there at all. The second is turned into the first here, once, so that
     * everything after this point has one name per operation to think about.
     */
    private static Dictionary underTheNamesTheRunPrints(Dictionary dictionary, ApiModel model,
            Path file, List<String> problems) {
        if (!(dictionary instanceof ValueDictionary values)
                || values.keyedBy() != ValueDictionary.Keying.OPERATION_AND_PARAMETER) {
            return dictionary;
        }
        Set<String> declared = model.operations().stream()
                .map(operation -> operation.id().value())
                .collect(java.util.stream.Collectors.toSet());
        Map<String, String> renaming = new java.util.LinkedHashMap<>();
        for (Operation operation : model.operations()) {
            String methodAndPath = operation.method() + " " + operation.path();
            // Nothing to rename when the document declares no identifier: the two names are then
            // the same string already.
            if (!methodAndPath.equals(operation.id().value())
                    && !declared.contains(methodAndPath)) {
                renaming.put(methodAndPath, operation.id().value());
            }
        }
        reportOperationsWrittenBothWays(values, renaming, file, problems);
        return values.withOperationsRenamed(renaming);
    }

    /**
     * Says so when one file writes one operation under both of the names it answers to.
     *
     * <p>Two ways of writing the same thing is what makes a file easy to produce from a document,
     * and this is what it costs: a file can say the same thing twice without the duplicate-key
     * check seeing it, because the two keys are different strings. Where an entry is written under
     * both names the one under the operation's own identifier is the one used, and the other is
     * quietly lost - so it is not left quiet.
     */
    private static void reportOperationsWrittenBothWays(ValueDictionary values,
            Map<String, String> renaming, Path file, List<String> problems) {
        Set<String> written = values.entriesByOperation().keySet();
        List<String> both = renaming.entrySet().stream()
                .filter(naming -> written.contains(naming.getKey())
                        && written.contains(naming.getValue()))
                .map(naming -> naming.getValue() + " (also written as " + naming.getKey() + ")")
                .toList();
        if (!both.isEmpty()) {
            problems.add(file + ": " + both.size() + (both.size() == 1
                    ? " operation is written under both of the names it answers to ("
                    : " operations are written under both of the names they answer to (")
                    + String.join(", ", both.subList(0, Math.min(NAMED_IN_FULL, both.size())))
                    + (both.size() > NAMED_IN_FULL ? ", …" : "") + "), and where the two give "
                    + "values for the same place the one under the identifier is used");
        }
    }

    /** The operations some list this run holds gives a whole body for. */
    private static Set<String> operationsGivenAWholeBody(List<Dictionary> dictionaries) {
        Set<String> given = new java.util.LinkedHashSet<>();
        for (Dictionary dictionary : dictionaries) {
            if (dictionary instanceof ValueDictionary values
                    && values.keyedBy() == ValueDictionary.Keying.OPERATION_AND_PARAMETER) {
                values.entriesByOperation().forEach((operation, entries) -> {
                    if (entries.containsKey(ValueRequest.THE_BODY)) {
                        given.add(operation);
                    }
                });
            }
        }
        return given;
    }

    /**
     * Says so when entries in a dictionary name places this API does not have, or places nothing
     * would ever take a value from.
     *
     * <p>Worth the line because the alternative is a file that loads without complaint and then
     * quietly does nothing - which is what this format goes out of its way to prevent everywhere
     * else. All of it is knowable from the document before a single request is sent, so it is said
     * then rather than after a run has been spent.
     *
     * <p>Four things put an entry here. It names an operation the document does not have, which is
     * usually a file that has fallen behind the document it was written for. It names a parameter
     * or a piece of a body that operation does not have, which is usually a misspelling - or a
     * property written under its own name where it should carry the way down to it. It is for a
     * place the document settles by itself: a parameter whose whole list of allowed values it
     * declares, or a property it says the API only ever sends back. Or it names a piece of a body
     * in a file that also supplies that whole body, which is what gets sent.
     *
     * <p>Where the document runs out - a shape written in a way the parser could not read, one that
     * contains itself - nothing below that point is judged: being unable to name something is not
     * the same as knowing it is wrong.
     */
    private static void reportEntriesNothingWouldUse(Dictionary dictionary, ApiModel model,
            Path file, Set<String> givenAWholeBody, List<String> problems) {
        if (!(dictionary instanceof ValueDictionary values)
                || values.keyedBy() != ValueDictionary.Keying.OPERATION_AND_PARAMETER) {
            return;
        }
        Map<String, Operation> operations = new java.util.LinkedHashMap<>();
        model.operations().forEach(operation -> operations.put(operation.id().value(), operation));

        Map<String, List<String>> byReason = new java.util.LinkedHashMap<>();
        int entries = 0;
        for (Map.Entry<String, Map<String, List<JsonValue>>> named
                : values.entriesByOperation().entrySet()) {
            entries += named.getValue().size();
            Operation operation = operations.get(named.getKey());
            if (operation == null) {
                // Every entry under it is dead, not one: the operation is the only part of the key
                // that is wrong, and counting it once would under-report a whole block of them.
                named.getValue().keySet().forEach(place ->
                        byReason.computeIfAbsent(NO_SUCH_OPERATION, ignored -> new ArrayList<>())
                                .add(place + " in " + named.getKey()));
                continue;
            }
            WhereAValueCanGo places = WhereAValueCanGo.in(operation, model);
            boolean theWholeBodyIsGiven = givenAWholeBody.contains(named.getKey());
            for (String place : named.getValue().keySet()) {
                whyNothingWouldUseIt(place, places, theWholeBodyIsGiven).ifPresent(why ->
                        byReason.computeIfAbsent(why, ignored -> new ArrayList<>())
                                .add(place + " in " + named.getKey()));
            }
        }
        if (byReason.isEmpty()) {
            return;
        }
        int unusable = byReason.values().stream().mapToInt(List::size).sum();
        StringBuilder said = new StringBuilder(file + ": " + unusable + " of its " + entries
                + " entries will never be used:");
        String between = " ";
        for (Map.Entry<String, List<String>> reason : byReason.entrySet()) {
            List<String> where = reason.getValue();
            said.append(between).append(where.size()).append(" for ").append(reason.getKey())
                    .append(" (").append(String.join(", ",
                            where.subList(0, Math.min(NAMED_IN_FULL, where.size()))))
                    .append(where.size() > NAMED_IN_FULL ? ", …)" : ")");
            between = ", ";
        }
        problems.add(said.toString());
    }

    /** Why nothing would take a value from an entry for this place, when nothing would. */
    private static Optional<String> whyNothingWouldUseIt(String place, WhereAValueCanGo places,
            boolean theWholeBodyIsGiven) {
        if (!places.has(place)) {
            return Optional.of("no such parameter or piece of a body in that operation");
        }
        if (theWholeBodyIsGiven && place.startsWith(ValueRequest.THE_BODY + ValueRequest.STEP)) {
            return Optional.of("a piece of a body that is supplied whole, which is sent instead");
        }
        return places.whyNothingWouldUseIt(place);
    }
}

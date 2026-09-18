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

    private Dictionaries() {
    }

    /**
     * What was found when the dictionaries were gathered.
     *
     * @param dictionaries every one that could be read, the one RESTest carries first
     * @param fromTheUser the ones somebody handed over, which is a different question from which
     *     ones there are: a message about a file nobody wrote is a message nobody can act on
     * @param problems what went wrong, in the words a person should read, empty when nothing did
     */
    public record Found(List<Dictionary> dictionaries, List<Dictionary> fromTheUser,
            List<String> problems) {

        public Found {
            dictionaries = List.copyOf(Objects.requireNonNull(dictionaries, "dictionaries"));
            fromTheUser = List.copyOf(Objects.requireNonNull(fromTheUser, "fromTheUser"));
            problems = List.copyOf(Objects.requireNonNull(problems, "problems"));
        }

        /** The names of the lists somebody handed over, as against the one RESTest carries. */
        public java.util.Set<String> namesFromTheUser() {
            return fromTheUser.stream().map(Dictionary::name)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        /** The one named, if it was among those found. */
        public Optional<Dictionary> named(String name) {
            return dictionaries.stream().filter(held -> held.name().equals(name)).findFirst();
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
        List<Dictionary> found = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        try {
            found.add(shipped());
        } catch (IOException | JsonException beyondHelp) {
            problems.add("RESTest's own list of values to push with could not be read, so nothing "
                    + "will be pushed at the API. This is a fault in this build of the tool, not in "
                    + "anything you did: " + beyondHelp.getMessage());
        }

        List<Dictionary> fromTheUser = new ArrayList<>();
        for (Path location : locations) {
            for (Path file : filesUnder(location, problems)) {
                read(file, problems).ifPresent(dictionary -> {
                    found.add(dictionary);
                    fromTheUser.add(dictionary);
                    reportKeysThatMatchNothing(dictionary, model, file, problems);
                });
            }
        }
        reportNamesUsedTwice(fromTheUser, problems);
        return new Found(found, fromTheUser, problems);
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
     * Says so when a dictionary names operations this API does not have.
     *
     * <p>Almost always a file that has fallen behind the document it was written for. The likeliest
     * cause is worth knowing: an operation with no identifier of its own is named after its method
     * and path, so the day somebody adds an {@code operationId} to the specification, every entry
     * written against the old name stops matching - silently, if nobody says this.
     */
    private static void reportKeysThatMatchNothing(Dictionary dictionary, ApiModel model, Path file,
            List<String> problems) {
        if (!(dictionary instanceof ValueDictionary values)
                || values.keyedBy() != ValueDictionary.Keying.OPERATION_AND_PARAMETER) {
            return;
        }
        Set<String> known = model.operations().stream()
                .map(Operation::id)
                .map(io.restest.core.model.OperationId::value)
                .collect(java.util.stream.Collectors.toSet());
        List<String> unknown = values.keys().stream().filter(key -> !known.contains(key)).toList();
        if (!unknown.isEmpty()) {
            problems.add(file + " has values for " + unknown.size() + " operation"
                    + (unknown.size() == 1 ? "" : "s") + " this API does not have ("
                    + String.join(", ", unknown.subList(0, Math.min(3, unknown.size())))
                    + (unknown.size() > 3 ? ", …" : "") + "), so those values will never be used");
        }
    }
}

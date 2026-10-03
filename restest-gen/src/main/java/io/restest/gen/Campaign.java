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

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The plan a run follows: where its values come from, and which operations it may touch.
 *
 * <p>Until now this was written into the tool. How much of a run went on ordinary requests and how
 * much on requests meant to push at the API, and which sources of values were preferred over which,
 * were decisions taken once by whoever wrote the generator and the same for everybody. This is
 * those decisions moved into a file, so that somebody testing their own API can take them
 * differently without touching the tool.
 *
 * <p>A plan is a handful of <b>strategies</b>. Each has a name, a share of the run's time, and an
 * ordered list of the <b>sources</b> its values come from. The sources are asked in turn and the
 * first answer is taken - so the order is a statement about which source knows best - except where
 * several are grouped together, in which case they are all asked and one of their answers is
 * chosen by chance, in proportion to weights the plan gives them.
 *
 * <p>Nothing here holds any values or knows how to invent one. It is the arrangement only; the
 * sources it names are built elsewhere, out of the lists the run was given and the document it is
 * testing.
 *
 * @param strategies the ways of building a request, and how much of the time each gets
 * @param operations which of the API's operations the run may touch
 */
public record Campaign(List<PlannedStrategy> strategies, WhichOperations operations) {

    /** What the shares of a plan's strategies, and the weights within any one group, add up to. */
    public static final int WHOLE = 100;

    public Campaign {
        Objects.requireNonNull(operations, "operations");
        strategies = List.copyOf(Objects.requireNonNull(strategies, "strategies"));
        if (strategies.isEmpty()) {
            throw new IllegalArgumentException("a plan with no strategies in it would build no "
                    + "requests at all");
        }
        List<String> named = strategies.stream().map(PlannedStrategy::name).toList();
        if (named.size() != java.util.Set.copyOf(named).size()) {
            throw new IllegalArgumentException("two strategies of a plan cannot share a name, "
                    + "because a report names them and a reader could not tell which is which: "
                    + named);
        }
        int shares = strategies.stream().mapToInt(PlannedStrategy::share).sum();
        if (shares != WHOLE) {
            throw new IllegalArgumentException("the shares of a plan's strategies are how it "
                    + "divides the run's time between them, so they add up to " + WHOLE
                    + "; these add up to " + shares);
        }
    }

    /**
     * One way of building a request, and how much of the run's time it gets.
     *
     * <p>Called a <em>planned</em> strategy because it is the arrangement rather than the working
     * thing: {@link Strategy} is what this becomes once the sources it names have been found and
     * built. This one can be read out of a file; that one cannot.
     *
     * <p>Most strategies build every request from nothing. One that <em>mutates</em> builds its
     * requests by taking one the API has already accepted and changing exactly one thing in it; its
     * sources are then what it falls back on, for an operation the API has not accepted anything
     * for yet, or whose accepted request has nothing in it that can be changed.
     *
     * <p>One that <em>sends series</em> turns a creation into the first request of a short series
     * about the thing created - read it, delete it, read it again - built from its own sources
     * throughout; on any operation that creates nothing, it builds the request from those sources
     * the ordinary way. It cannot also change accepted requests, since each of the two says what
     * its requests are about, and it cannot push at the API: a series is only worth sending when
     * the thing it is about was created with values meant to work.
     *
     * @param name what it is called, as a report would print it
     * @param share how often it builds the request of an ordinary turn, out of a hundred. The
     *     later requests of a series one of its creations starts are sent in turns of their own,
     *     ahead of the ordinary ones, so a strategy that sends series takes more of the run's time
     *     than its share says
     * @param sources where its values come from, asked in the order written
     * @param mutatesAccepted whether its requests are made by changing one thing in a request the
     *     API accepted, its sources being used only when that cannot be done
     * @param sendsSequences whether a creation it builds starts a series of requests about the
     *     thing created
     */
    public record PlannedStrategy(String name, int share, List<Entry> sources,
            boolean mutatesAccepted, boolean sendsSequences) {

        public PlannedStrategy {
            Objects.requireNonNull(name, "name");
            sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
            if (name.isBlank()) {
                throw new IllegalArgumentException("a strategy has a name, which a report prints");
            }
            if (share <= 0) {
                throw new IllegalArgumentException("a strategy given none of the time would never "
                        + "run, so leaving it out is the way to say that: " + name + " asked for "
                        + share);
            }
            if (sources.isEmpty()) {
                throw new IllegalArgumentException("a strategy with no sources of values could "
                        + "not fill in a single request: " + name);
            }
            if (mutatesAccepted && sendsSequences) {
                throw new IllegalArgumentException("a strategy either changes accepted requests or "
                        + "sends series of requests, not both: " + name);
            }
            if (sendsSequences && drawsOnTheListToPushWith(sources)) {
                throw new IllegalArgumentException("a strategy that sends series builds them from "
                        + "values meant to work, so it cannot draw on the list of values called '"
                        + RandomTestCaseGenerator.PUSHES_AT_THE_API + "': " + name);
            }
        }

        /**
         * A strategy that builds its requests from nothing, or by changing accepted ones.
         *
         * @param name what it is called, as a report would print it
         * @param share how much of the run's time it gets, out of a hundred
         * @param sources where its values come from, asked in the order written
         * @param mutatesAccepted whether its requests are made by changing one thing in a request
         *     the API accepted
         */
        public PlannedStrategy(String name, int share, List<Entry> sources,
                boolean mutatesAccepted) {
            this(name, share, sources, mutatesAccepted, false);
        }

        /**
         * A strategy that builds every one of its requests from nothing.
         *
         * @param name what it is called, as a report would print it
         * @param share how much of the run's time it gets, out of a hundred
         * @param sources where its values come from, asked in the order written
         */
        public PlannedStrategy(String name, int share, List<Entry> sources) {
            this(name, share, sources, false);
        }

        /**
         * Whether this strategy's requests are built to push at the API.
         *
         * <p>Worked out from what it draws on rather than stated: a strategy that draws on the list
         * of values to push with is pushing, because that is what those values are. Deriving it
         * means a plan somebody wrote by hand gets the same treatment as the one RESTest ships,
         * without a word in the file whose only job is to be true.
         *
         * @return whether it draws on the list of values to push with
         */
        public boolean pushesAtTheApi() {
            return drawsOnTheListToPushWith(sources);
        }

        /**
         * Whether these sources include the list of values to push with. Asked of the list itself
         * rather than of the strategy, so that the strategy can ask it before it exists.
         */
        private static boolean drawsOnTheListToPushWith(List<Entry> sources) {
            return sources.stream().flatMap(entry -> entry.sources().stream())
                    .anyMatch(source -> source instanceof Source.OneList list
                            && list.name().equals(RandomTestCaseGenerator.PUSHES_AT_THE_API));
        }
    }

    /**
     * One step in a strategy's list of sources: either a single source, or several grouped.
     *
     * <p>The list is closed, which is what lets everything that reads a plan handle both kinds and
     * be told by the compiler if a third is ever added.
     */
    public sealed interface Entry {

        /** The sources this step names, which is one of them or all of a group's. */
        List<Source> sources();

        /** One source, asked on its own. */
        record Single(Source source) implements Entry {

            public Single {
                Objects.requireNonNull(source, "source");
            }

            @Override
            public List<Source> sources() {
                return List.of(source);
            }
        }

        /** Several sources, all asked, one of their answers chosen in proportion to its weight. */
        record Group(List<Share> among) implements Entry {

            public Group {
                among = List.copyOf(Objects.requireNonNull(among, "among"));
                if (among.size() < 2) {
                    throw new IllegalArgumentException("a group of sources to choose between has "
                            + "at least two in it; one on its own is written as one on its own");
                }
                int weights = among.stream().mapToInt(Share::weight).sum();
                if (weights != WHOLE) {
                    throw new IllegalArgumentException("the weights in a group are how the choice "
                            + "between its sources is divided, so they add up to " + WHOLE
                            + "; these add up to " + weights);
                }
            }

            @Override
            public List<Source> sources() {
                return among.stream().map(Share::source).toList();
            }
        }
    }

    /**
     * One source inside a group, and how much of the choice it gets when it answers.
     *
     * @param source the source
     * @param weight its share of the choice, out of a hundred
     */
    public record Share(Source source, int weight) {

        public Share {
            Objects.requireNonNull(source, "source");
            if (weight <= 0) {
                throw new IllegalArgumentException("a source given no weight would never be "
                        + "chosen, so leaving it out is the way to say that: asked for " + weight);
            }
        }
    }

    /**
     * Where a plan says some of its values should come from.
     *
     * <p>Three kinds, and a plan says which it means rather than leaving it to be guessed. The
     * tool's own sources are named by one of a small fixed set of words. A list of values is named
     * by the name the list carries. And a plan may say "every list this run was handed", which is
     * how the plan RESTest ships can speak about lists whose names it cannot know.
     */
    public sealed interface Source {

        /** One of the tool's own sources, named by a word this version knows. */
        record Builtin(Campaign.Builtin which) implements Source {

            public Builtin {
                Objects.requireNonNull(which, "which");
            }
        }

        /** One list of values, by the name it carries. */
        record OneList(String name) implements Source {

            public OneList {
                Objects.requireNonNull(name, "name");
                if (name.isBlank()) {
                    throw new IllegalArgumentException("a list of values is named by its name");
                }
            }
        }

        /** Every list of values this run was handed, whatever they are called. */
        record EveryListGiven() implements Source {
        }
    }

    /**
     * The sources that are part of the tool rather than a file somebody wrote.
     *
     * <p>Each is one statement a document can make about a value, except the last two: what the API
     * has already been seen to send, and what is invented when nothing else has anything to say.
     */
    public enum Builtin {

        /** The closed list of values the document says it accepts. */
        ENUM,
        /** The sample values the document's author wrote down. */
        EXAMPLE,
        /** The value the document says the API uses when the caller sends nothing. */
        DEFAULT,
        /**
         * What the API itself has already sent back, earlier in the same run.
         *
         * <p>The one source with a memory. Everything else here reads the document, which says the
         * same thing whoever is asked and whenever; this one knows that a pet with identifier 7
         * exists because the API said so a moment ago. The price is that a run drawing on it is not
         * repeated by starting it again with the same number - what it sends depends on what the
         * API answered - so the same number gets a similar run rather than the same one, and what
         * was kept of the run that did happen is what somebody goes back to.
         */
        OBSERVED,
        /** A value invented to fit the shape, when nothing better is on offer. */
        RANDOM;

        /** How this is written in a plan. */
        public String inAPlan() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * The source a plan means by this word, if this version has one.
         *
         * @param word the word written in the plan
         * @return the source, or nothing when no source answers to it
         */
        public static Optional<Builtin> named(String word) {
            Objects.requireNonNull(word, "word");
            for (Builtin known : values()) {
                if (known.inAPlan().equals(word)) {
                    return Optional.of(known);
                }
            }
            return Optional.empty();
        }

        /** Every word a plan may use, in the order they are written here, for saying so. */
        public static String allOfThem() {
            return java.util.Arrays.stream(values()).map(Builtin::inAPlan)
                    .reduce((a, b) -> a + ", " + b).orElseThrow();
        }
    }
}

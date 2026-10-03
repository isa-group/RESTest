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

/**
 * Finds the plan a run should follow, and says what looks wrong with it.
 *
 * <p>Either the file somebody pointed the tool at, or the one RESTest carries. The carried one is
 * a real file rather than something written into the tool, so that anybody can print it, copy it,
 * change a line and hand it back - which is the whole reason the format exists.
 *
 * <p>Nothing here ends a run. A plan that cannot be read costs the run whatever the author meant by
 * it and falls back to the one RESTest carries, saying so; a plan naming an operation this API does
 * not have, or a list of values nobody handed over, is reported and otherwise carried on with. That
 * is the same bargain the tool already makes with a specification it cannot fully read.
 */
public final class Campaigns {

    /** The plan that travels with the tool. */
    static final String SHIPPED = "default-campaign.yaml";

    /** How the shipped plan is named when something is wrong with it, which would be our bug. */
    private static final String SHIPPED_DESCRIPTION = "the plan built into RESTest";

    private Campaigns() {
    }

    /**
     * The plan a run will follow, and what looks wrong with it.
     *
     * @param campaign the plan
     * @param problems what to tell the person running it, in the words they should read, empty when
     *     there is nothing to say
     */
    public record Found(Campaign campaign, List<String> problems) {

        public Found {
            Objects.requireNonNull(campaign, "campaign");
            problems = List.copyOf(Objects.requireNonNull(problems, "problems"));
        }
    }

    /**
     * The plan RESTest carries.
     *
     * @return it
     * @throws IOException if this build does not carry the file at all
     * @throws JsonException if it carries one it cannot read
     */
    public static Campaign shipped() throws IOException {
        try (InputStream stream = Campaigns.class.getResourceAsStream(SHIPPED)) {
            if (stream == null) {
                throw new IOException("this build of RESTest does not carry " + SHIPPED);
            }
            return CampaignDocument.read(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8), SHIPPED_DESCRIPTION);
        }
    }

    /**
     * The text of the plan RESTest carries, for printing so that somebody can start from it.
     *
     * @return the file, comments and all
     * @throws IOException if this build does not carry it
     */
    public static String shippedText() throws IOException {
        try (InputStream stream = Campaigns.class.getResourceAsStream(SHIPPED)) {
            if (stream == null) {
                throw new IOException("this build of RESTest does not carry " + SHIPPED);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * The plan for this run: the file named, or the one RESTest carries.
     *
     * @param file the plan somebody pointed the tool at, if they did
     * @param model the API being tested, so a plan naming operations it does not have can be
     *     reported as the stale file it probably is
     * @param listsHandedOver the names of the lists of values this run was given, so a plan naming
     *     one nobody handed over can be reported too
     * @return the plan, and what to say about it
     * @throws JsonException if a plan was named and cannot be read, which ends the run rather
     *     than quietly running a different one
     */
    public static Found gather(Optional<Path> file, ApiModel model, Set<String> listsHandedOver) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(listsHandedOver, "listsHandedOver");
        List<String> problems = new ArrayList<>();

        Campaign campaign = file.map(Campaigns::read).orElseGet(() -> carried(problems));
        report(campaign, model, listsHandedOver, problems);
        return new Found(campaign, List.copyOf(problems));
    }

    /**
     * Refused when a plan somebody named cannot be read.
     *
     * <p>Not the bargain the rest of the tool makes, and deliberately not. A list of values that
     * cannot be read costs a run some of its values; a plan that cannot be read costs it whatever
     * the plan was for - and one of the things a plan is for is keeping a run to the operations
     * HTTP calls safe. Falling back to the plan RESTest carries, which narrows nothing, would
     * answer a request to touch fourteen operations by touching thirty-five and writing to all of
     * them. So this ends the run before it starts, and says why.
     */
    private static Campaign read(Path file) {
        try {
            return CampaignDocument.read(Files.readString(file), file.toString());
        } catch (IOException | UncheckedIOException cannotOpen) {
            throw new JsonException(file + " could not be read: " + reasonFor(cannotOpen));
        }
    }

    /** What went wrong, for the messages that give only a path when a file is not there. */
    private static String reasonFor(Exception thrown) {
        String said = thrown.getMessage();
        if (thrown instanceof java.nio.file.NoSuchFileException) {
            return "there is no such file";
        }
        return said == null || said.isBlank() ? thrown.getClass().getSimpleName() : said;
    }

    /**
     * The plan RESTest carries, falling back to the plainest one if this build cannot read it.
     *
     * <p>For the callers that want a plan and have nowhere to report a problem to. Everything a
     * person should be told about is told through {@link #gather}.
     *
     * @return the plan
     */
    public static Campaign carried() {
        return carried(new ArrayList<>());
    }

    /**
     * The carried plan, or a last resort when this build cannot read its own.
     *
     * <p>A missing list of values costs a run some of its values. A missing plan would cost it
     * every request, so there is one written here as well - deliberately the plainest thing that
     * still tests an API, and never reached unless this build is broken.
     */
    private static Campaign carried(List<String> problems) {
        try {
            return shipped();
        } catch (IOException | JsonException beyondHelp) {
            problems.add("RESTest's own plan could not be read, so this run follows the plainest "
                    + "one there is. This is a fault in this build of the tool, not in anything "
                    + "you did: " + beyondHelp.getMessage());
            return plainest();
        }
    }

    /** Every source RESTest has, asked in turn, for everything. */
    static Campaign plainest() {
        List<Campaign.Entry> sources = new ArrayList<>();
        for (Campaign.Builtin source : Campaign.Builtin.values()) {
            sources.add(new Campaign.Entry.Single(new Campaign.Source.Builtin(source)));
        }
        return new Campaign(
                List.of(new Campaign.PlannedStrategy("nominal", Campaign.WHOLE, sources)),
                WhichOperations.everything());
    }

    /**
     * Everything about this plan that is worth saying before a request is sent.
     *
     * <p>None of it stops the run. All of it is the same kind of thing: something the plan asks for
     * that will not happen, which nobody would otherwise find out about, because a run that quietly
     * does less than its plan says looks exactly like one that did what it was told.
     */
    private static void report(Campaign campaign, ApiModel model, Set<String> listsHandedOver,
            List<String> problems) {
        List<String> stale = campaign.operations().namesNothingMatches(model);
        if (!stale.isEmpty()) {
            problems.add("the plan keeps this run to " + stale.size() + " operation(s) this API "
                    + "does not have (" + String.join(", ", stale) + "), which usually means the "
                    + "plan was written against an older version of the document");
        }
        for (Campaign.PlannedStrategy strategy : campaign.strategies()) {
            strategy.sources().stream()
                    .flatMap(entry -> entry.sources().stream())
                    .filter(source -> source instanceof Campaign.Source.OneList list
                            && !listsHandedOver.contains(list.name()))
                    .map(source -> ((Campaign.Source.OneList) source).name())
                    .distinct()
                    .forEach(missing -> problems.add("the strategy called '" + strategy.name()
                            + "' draws on a list of values called '" + missing + "', and no list of "
                            + "that name was handed over - so nothing will come from it"));
            reportAnythingUnreachable(strategy, problems);
        }
    }

    /**
     * Says so when a strategy lists a source below one that answers for nearly everything.
     *
     * <p>Invention fits a value to the shape it was asked about, so it answers where anything
     * could, and a source written below it is asked for almost nothing. That is a line whose author
     * believed they were choosing something, and reporting it is cheaper than the afternoon spent
     * working out why a list made no difference.
     *
     * <p>Said rather than refused, because "nearly everything" is not everything: a shape nothing
     * satisfies leaves invention with no answer either, and a plan is not wrong for relying on
     * that.
     */
    private static void reportAnythingUnreachable(Campaign.PlannedStrategy strategy,
            List<String> problems) {
        List<Campaign.Entry> entries = strategy.sources();
        for (int at = 0; at < entries.size() - 1; at++) {
            boolean invents = entries.get(at).sources().stream()
                    .anyMatch(source -> source instanceof Campaign.Source.Builtin builtin
                            && builtin.which() == Campaign.Builtin.RANDOM);
            if (invents) {
                problems.add("the strategy called '" + strategy.name() + "' asks for '"
                        + Campaign.Builtin.RANDOM.inAPlan() + "' before "
                        + (entries.size() - at - 1) + " other source(s). Invention fits a value to "
                        + "whatever it is asked about, so it answers where anything could, and "
                        + "what comes after it will hardly ever be reached");
                return;
            }
        }
    }
}

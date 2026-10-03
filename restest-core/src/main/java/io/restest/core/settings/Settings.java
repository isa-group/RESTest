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

import io.restest.core.exec.EngineSettings;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Every number somebody decided about how RESTest behaves, in one place a run can be handed.
 *
 * <p>These are decisions about the <em>tool</em> - how hard it pushes, how much it keeps, how deep
 * it goes, how long it waits - as against decisions about the API, which live in a plan (see the
 * campaign file). The difference is worth holding on to: a setting would mean the same thing
 * against a different API on the same machine, and a plan would mean the same thing on a different
 * machine against the same API. So settings travel with a machine or with an experiment, and plans
 * travel with an API.
 *
 * <p>Nothing here is read from anywhere. There is no place a part of the tool can reach out and ask
 * what a setting is; the command line builds one of these, once, and hands it to whatever needs it.
 * That is what lets two runs with different settings happen in the same program without either
 * noticing the other, and it is why {@code System.getenv} is forbidden everywhere but the command
 * line.
 *
 * <p>Every value has a name a person can type - {@code engine.maxConcurrency} - and those names are
 * listed in {@link SettingKey}. {@link #from(Map)} turns a set of them into one of these, refusing a
 * name that does not exist and a value that could not be used, and {@link #written(SettingKey)}
 * reads any of them back out as the text a person would have typed.
 *
 * @param engine how requests are sent
 * @param schedule how far ahead of the API the run works
 * @param generation what an invented value may look like
 * @param mutation which changes may be made to requests the API accepted, and how large
 * @param sequences which series of requests may be sent around a thing the run created itself
 * @param memory how much of what the API said is remembered
 * @param document what is accepted when a description is fetched
 * @param report how much of what was found is written out
 */
public record Settings(
        EngineSettings engine,
        ScheduleSettings schedule,
        GenerationSettings generation,
        MutationSettings mutation,
        SequenceSettings sequences,
        MemorySettings memory,
        DocumentSettings document,
        ReportSettings report) {

    private static final Settings DEFAULTS = new Settings(
            EngineSettings.defaults(),
            ScheduleSettings.defaults(),
            GenerationSettings.defaults(),
            MutationSettings.defaults(),
            SequenceSettings.defaults(),
            MemorySettings.defaults(),
            DocumentSettings.defaults(),
            ReportSettings.defaults());

    public Settings {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(sequences, "sequences");
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(report, "report");
    }

    /** What RESTest does when nobody has said otherwise, which is a usable run against any API. */
    public static Settings defaults() {
        return DEFAULTS;
    }

    /**
     * Settings built from values somebody named, with everything else left as it is.
     *
     * <p>Every value is applied before any of them is checked against the others, so the order they
     * were typed in never decides whether they are accepted. Raising the fewest requests in flight
     * above the current most, while also raising the most, works - which it would not if each were
     * applied and checked on its own.
     *
     * @param given the values, keyed the way they are written in a file or after {@code --set}
     * @return the settings those values describe
     * @throws SettingsException if a name is not a setting, or a value is not one it can take
     */
    public static Settings from(Map<String, String> given) {
        Objects.requireNonNull(given, "given");
        given.keySet().forEach(Settings::mustExist);
        Given typed = new Given(given);
        int fewest = typed.wholeNumber("engine.minConcurrency", DEFAULTS.engine.minConcurrency());
        int most = typed.wholeNumber("engine.maxConcurrency", DEFAULTS.engine.maxConcurrency());
        Optional<Duration> wholeRequestNamed = given.containsKey("engine.callTimeout")
                ? Optional.of(typed.lengthOfTime("engine.callTimeout",
                        DEFAULTS.engine.callTimeout()))
                : Optional.empty();
        Duration connect = typed.lengthOfTime("engine.connectTimeout",
                fitting(DEFAULTS.engine.connectTimeout(), wholeRequestNamed));
        Duration read = typed.lengthOfTime("engine.readTimeout",
                fitting(DEFAULTS.engine.readTimeout(), wholeRequestNamed));
        Duration write = typed.lengthOfTime("engine.writeTimeout",
                fitting(DEFAULTS.engine.writeTimeout(), wholeRequestNamed));
        Duration wholeRequest =
                wholeRequestNamed.orElseGet(() -> twiceTheLongest(connect, read, write));
        EngineSettings engine = group("engine", () -> new EngineSettings(
                connect,
                read,
                write,
                wholeRequest,
                fewest,
                typed.wholeNumber("engine.initialConcurrency", whereToStartWithin(fewest, most)),
                most,
                typed.number("engine.slowdownFactor",
                        BigDecimal.valueOf(DEFAULTS.engine.slowdownFactor())).doubleValue(),
                typed.count("engine.maxRetainedResponseBytes",
                        DEFAULTS.engine.maxRetainedResponseBytes()),
                typed.yesOrNo("engine.followRedirects", DEFAULTS.engine.followRedirects()),
                typed.text("engine.userAgent", DEFAULTS.engine.userAgent())));
        ScheduleSettings schedule = group("schedule", () -> new ScheduleSettings(
                typed.wholeNumber("schedule.workAheadFactor",
                        DEFAULTS.schedule.workAheadFactor()),
                typed.wholeNumber("schedule.announcementsAllowedToPileUp",
                        DEFAULTS.schedule.announcementsAllowedToPileUp()),
                typed.lengthOfTime("schedule.stragglerGrace",
                        DEFAULTS.schedule.stragglerGrace()),
                typed.lengthOfTime("schedule.interruptGrace",
                        DEFAULTS.schedule.interruptGrace()),
                typed.yesOrNo("schedule.openingLap", DEFAULTS.schedule.openingLap()),
                typed.lengthOfTime("schedule.openingLapPatience",
                        DEFAULTS.schedule.openingLapPatience())));
        GenerationSettings generation = group("generation", () -> new GenerationSettings(
                typed.wholeNumber("generation.optionalNestingDepth",
                        DEFAULTS.generation.optionalNestingDepth()),
                typed.wholeNumber("generation.hardNestingDepth",
                        DEFAULTS.generation.hardNestingDepth()),
                typed.wholeNumber("generation.usualLongestString",
                        DEFAULTS.generation.usualLongestString()),
                typed.wholeNumber("generation.longestString",
                        DEFAULTS.generation.longestString()),
                typed.number("generation.lowestNumber", DEFAULTS.generation.lowestNumber()),
                typed.number("generation.roomAboveIt", DEFAULTS.generation.roomAboveIt()),
                typed.wholeNumber("generation.decimalPlaces",
                        DEFAULTS.generation.decimalPlaces()),
                typed.wholeNumber("generation.usualMostItems",
                        DEFAULTS.generation.usualMostItems()),
                typed.wholeNumber("generation.mostItems", DEFAULTS.generation.mostItems()),
                typed.number("generation.optionalPropertyChance",
                        BigDecimal.valueOf(DEFAULTS.generation.optionalPropertyChance()))
                        .doubleValue(),
                typed.number("generation.optionalBodyChance",
                        BigDecimal.valueOf(DEFAULTS.generation.optionalBodyChance()))
                        .doubleValue(),
                typed.number("generation.optionalParameterContinueChance",
                        BigDecimal.valueOf(DEFAULTS.generation.optionalParameterContinueChance()))
                        .doubleValue(),
                typed.yesOrNo("generation.optionalParametersBySize",
                        DEFAULTS.generation.optionalParametersBySize()),
                typed.wholeNumber("generation.nullInOneIn", DEFAULTS.generation.nullInOneIn()),
                typed.wholeNumber("generation.uniqueAttempts",
                        DEFAULTS.generation.uniqueAttempts()),
                typed.wholeNumber("generation.sendableAttempts",
                        DEFAULTS.generation.sendableAttempts()),
                typed.wholeNumber("generation.writableBodyAttempts",
                        DEFAULTS.generation.writableBodyAttempts()),
                typed.yesOrNo("generation.impliedFormats", DEFAULTS.generation.impliedFormats()),
                typed.number("generation.impliedFormatChance",
                        BigDecimal.valueOf(DEFAULTS.generation.impliedFormatChance()))
                        .doubleValue()));
        MutationSettings mutation = group("mutation", () -> new MutationSettings(
                typed.yesOrNo("mutation.violations", DEFAULTS.mutation.violations()),
                typed.yesOrNo("mutation.dropRequired", DEFAULTS.mutation.dropRequired()),
                typed.yesOrNo("mutation.wrongLocation", DEFAULTS.mutation.wrongLocation()),
                typed.yesOrNo("mutation.wrongType", DEFAULTS.mutation.wrongType()),
                typed.yesOrNo("mutation.outsideABound", DEFAULTS.mutation.outsideABound()),
                typed.yesOrNo("mutation.breakAnEnumeration",
                        DEFAULTS.mutation.breakAnEnumeration()),
                typed.yesOrNo("mutation.breakAPattern", DEFAULTS.mutation.breakAPattern()),
                typed.yesOrNo("mutation.breakAFormat", DEFAULTS.mutation.breakAFormat()),
                typed.yesOrNo("mutation.sendNull", DEFAULTS.mutation.sendNull()),
                typed.yesOrNo("mutation.sendEmpty", DEFAULTS.mutation.sendEmpty()),
                typed.yesOrNo("mutation.oversize", DEFAULTS.mutation.oversize()),
                typed.yesOrNo("mutation.wrongRoot", DEFAULTS.mutation.wrongRoot()),
                typed.yesOrNo("mutation.emptyBody", DEFAULTS.mutation.emptyBody()),
                typed.yesOrNo("mutation.notJson", DEFAULTS.mutation.notJson()),
                typed.yesOrNo("mutation.wrongContentType",
                        DEFAULTS.mutation.wrongContentType()),
                typed.yesOrNo("mutation.beyondItsWidth", DEFAULTS.mutation.beyondItsWidth()),
                typed.wholeNumber("mutation.acceptedKept", DEFAULTS.mutation.acceptedKept()),
                typed.wholeNumber("mutation.oversizedLength",
                        DEFAULTS.mutation.oversizedLength()),
                typed.wholeNumber("mutation.oversizedItems",
                        DEFAULTS.mutation.oversizedItems())));
        SequenceSettings sequences = group("sequences", () -> new SequenceSettings(
                typed.yesOrNo("sequences.readAfterDelete", DEFAULTS.sequences.readAfterDelete()),
                typed.yesOrNo("sequences.deleteTwice", DEFAULTS.sequences.deleteTwice()),
                typed.yesOrNo("sequences.writeUnderDeleted",
                        DEFAULTS.sequences.writeUnderDeleted()),
                typed.yesOrNo("sequences.putTwice", DEFAULTS.sequences.putTwice()),
                typed.yesOrNo("sequences.safeGet", DEFAULTS.sequences.safeGet()),
                typed.yesOrNo("sequences.createTwice", DEFAULTS.sequences.createTwice())));
        MemorySettings memory = group("memory", () -> new MemorySettings(
                typed.wholeNumber("memory.mostValuesUnderOneName",
                        DEFAULTS.memory.mostValuesUnderOneName()),
                typed.wholeNumber("memory.mostNames", DEFAULTS.memory.mostNames()),
                typed.wholeNumber("memory.longestValueKept", DEFAULTS.memory.longestValueKept()),
                typed.wholeNumber("memory.longestReplyRead", DEFAULTS.memory.longestReplyRead()),
                typed.wholeNumber("memory.asDeepAsAReplyIsRead",
                        DEFAULTS.memory.asDeepAsAReplyIsRead()),
                typed.yesOrNo("memory.identifiersByResource",
                        DEFAULTS.memory.identifiersByResource()),
                typed.yesOrNo("memory.identifiersByResourceFirst",
                        DEFAULTS.memory.identifiersByResourceFirst()),
                typed.yesOrNo("memory.rememberAcceptedRequests",
                        DEFAULTS.memory.rememberAcceptedRequests())));
        DocumentSettings document = group("document", () -> new DocumentSettings(
                typed.lengthOfTime("document.fetchTimeout", DEFAULTS.document.fetchTimeout()),
                typed.wholeNumber("document.mostBytesRead", DEFAULTS.document.mostBytesRead())));
        ReportSettings report = group("report", () -> new ReportSettings(
                typed.wholeNumber("report.writeUpsPerOperationAndKind",
                        DEFAULTS.report.writeUpsPerOperationAndKind()),
                typed.wholeNumber("report.writeUpsInTotal", DEFAULTS.report.writeUpsInTotal()),
                typed.count("report.mostBodyBytesKept", DEFAULTS.report.mostBodyBytesKept()),
                typed.wholeNumber("report.faultsShownOnTheConsole",
                        DEFAULTS.report.faultsShownOnTheConsole()),
                typed.wholeNumber("report.skippedOperationsShownOnTheConsole",
                        DEFAULTS.report.skippedOperationsShownOnTheConsole())));
        return new Settings(engine, schedule, generation, mutation, sequences, memory, document,
                report);
    }

    /**
     * Whether a setting nobody names is worked out from others rather than fixed: where the engine
     * starts, which follows the concurrency range, and how long a whole request may take, which
     * follows the waits inside it.
     *
     * <p>A printed file leaves such a setting to go on following them, unless somebody gave it, so
     * that changing one of the others in the file moves it the way {@code --set} would.
     *
     * @param key the setting
     * @return whether its value, when nobody names it, comes from other settings
     */
    static boolean followsOthers(SettingKey key) {
        return FOLLOWING_OTHERS.contains(key.fullName());
    }

    private static final Set<String> FOLLOWING_OTHERS =
            Set.of("engine.initialConcurrency", "engine.callTimeout");

    /**
     * Where the engine starts, when somebody moved the range and said nothing about the start.
     *
     * <p>{@code --set engine.maxConcurrency=1} is the answer to "my API falls over when asked two
     * things at once", and it has to work as typed. The starting number is where to begin inside
     * the range rather than a number of its own, so starting outside the range somebody has just
     * asked for means nothing - and refusing the line would make the simplest thing anybody wants
     * from these settings take three options instead of one.
     *
     * <p>This is a default being worked out, not a value being quietly overruled. A start named
     * outright and outside the range is still refused, saying what the range is.
     */
    private static int whereToStartWithin(int fewest, int most) {
        return Math.max(fewest, Math.min(DEFAULTS.engine.initialConcurrency(), most));
    }

    /**
     * How long one of the three waits inside a request is, when somebody said how long the whole
     * request may take and said nothing about that wait.
     *
     * <p>{@code --set engine.callTimeout=5s} asks for no request to take longer than five seconds,
     * and it has to work as typed: refusing it because the read timeout RESTest uses by default is
     * longer would make the one thing it asks take four options instead of one. So a wait nobody
     * named is shortened to fit, and recorded as worked out. A wait named outright that is longer
     * than a whole request somebody also named is still refused, saying which one.
     */
    private static Duration fitting(Duration usual, Optional<Duration> wholeRequest) {
        return wholeRequest.filter(whole -> usual.compareTo(whole) > 0).orElse(usual);
    }

    /**
     * How long a whole request may take, when nobody said: twice the longest of the three waits
     * inside it, which by default is twice the read timeout.
     *
     * <p>A reply that has started arriving gets as long again to finish. Raising the read timeout
     * for a slow API therefore raises this with it - {@code engine.readTimeout: 2m} works as typed,
     * rather than being cut short by a limit its author never heard of - and the value is recorded as
     * worked out. Twice is never more than the engine can wait for: past that, it is the longest
     * the engine can wait.
     */
    private static Duration twiceTheLongest(Duration connect, Duration read, Duration write) {
        Duration longest = Collections.max(List.of(connect, read, write));
        if (longest.compareTo(EngineSettings.LONGEST_WAIT) >= 0) {
            return longest;
        }
        Duration twice = longest.multipliedBy(2);
        return twice.compareTo(EngineSettings.LONGEST_WAIT) > 0 ? EngineSettings.LONGEST_WAIT : twice;
    }

    /**
     * What one setting's value is here, written the way somebody would have typed it.
     *
     * @param key the setting
     * @return its value, as text
     */
    public String written(SettingKey key) {
        Objects.requireNonNull(key, "key");
        return switch (key.fullName()) {
            case "engine.connectTimeout" -> LengthOfTime.written(engine.connectTimeout());
            case "engine.readTimeout" -> LengthOfTime.written(engine.readTimeout());
            case "engine.writeTimeout" -> LengthOfTime.written(engine.writeTimeout());
            case "engine.callTimeout" -> LengthOfTime.written(engine.callTimeout());
            case "engine.minConcurrency" -> String.valueOf(engine.minConcurrency());
            case "engine.initialConcurrency" -> String.valueOf(engine.initialConcurrency());
            case "engine.maxConcurrency" -> String.valueOf(engine.maxConcurrency());
            case "engine.slowdownFactor" -> written(engine.slowdownFactor());
            case "engine.maxRetainedResponseBytes" ->
                    String.valueOf(engine.maxRetainedResponseBytes());
            case "engine.followRedirects" -> String.valueOf(engine.followRedirects());
            case "engine.userAgent" -> engine.userAgent();

            case "schedule.workAheadFactor" -> String.valueOf(schedule.workAheadFactor());
            case "schedule.announcementsAllowedToPileUp" ->
                    String.valueOf(schedule.announcementsAllowedToPileUp());
            case "schedule.stragglerGrace" -> LengthOfTime.written(schedule.stragglerGrace());
            case "schedule.interruptGrace" -> LengthOfTime.written(schedule.interruptGrace());
            case "schedule.openingLap" -> String.valueOf(schedule.openingLap());
            case "schedule.openingLapPatience" ->
                    LengthOfTime.written(schedule.openingLapPatience());

            case "generation.optionalNestingDepth" ->
                    String.valueOf(generation.optionalNestingDepth());
            case "generation.hardNestingDepth" -> String.valueOf(generation.hardNestingDepth());
            case "generation.usualLongestString" ->
                    String.valueOf(generation.usualLongestString());
            case "generation.longestString" -> String.valueOf(generation.longestString());
            case "generation.lowestNumber" -> written(generation.lowestNumber());
            case "generation.roomAboveIt" -> written(generation.roomAboveIt());
            case "generation.decimalPlaces" -> String.valueOf(generation.decimalPlaces());
            case "generation.usualMostItems" -> String.valueOf(generation.usualMostItems());
            case "generation.mostItems" -> String.valueOf(generation.mostItems());
            case "generation.optionalPropertyChance" ->
                    written(generation.optionalPropertyChance());
            case "generation.optionalBodyChance" -> written(generation.optionalBodyChance());
            case "generation.optionalParameterContinueChance" ->
                    written(generation.optionalParameterContinueChance());
            case "generation.optionalParametersBySize" ->
                    String.valueOf(generation.optionalParametersBySize());
            case "generation.nullInOneIn" -> String.valueOf(generation.nullInOneIn());
            case "generation.uniqueAttempts" -> String.valueOf(generation.uniqueAttempts());
            case "generation.sendableAttempts" -> String.valueOf(generation.sendableAttempts());
            case "generation.writableBodyAttempts" ->
                    String.valueOf(generation.writableBodyAttempts());
            case "generation.impliedFormats" -> String.valueOf(generation.impliedFormats());
            case "generation.impliedFormatChance" -> written(generation.impliedFormatChance());

            case "mutation.violations" -> String.valueOf(mutation.violations());
            case "mutation.dropRequired" -> String.valueOf(mutation.dropRequired());
            case "mutation.wrongLocation" -> String.valueOf(mutation.wrongLocation());
            case "mutation.wrongType" -> String.valueOf(mutation.wrongType());
            case "mutation.outsideABound" -> String.valueOf(mutation.outsideABound());
            case "mutation.breakAnEnumeration" -> String.valueOf(mutation.breakAnEnumeration());
            case "mutation.breakAPattern" -> String.valueOf(mutation.breakAPattern());
            case "mutation.breakAFormat" -> String.valueOf(mutation.breakAFormat());
            case "mutation.sendNull" -> String.valueOf(mutation.sendNull());
            case "mutation.sendEmpty" -> String.valueOf(mutation.sendEmpty());
            case "mutation.oversize" -> String.valueOf(mutation.oversize());
            case "mutation.wrongRoot" -> String.valueOf(mutation.wrongRoot());
            case "mutation.emptyBody" -> String.valueOf(mutation.emptyBody());
            case "mutation.notJson" -> String.valueOf(mutation.notJson());
            case "mutation.wrongContentType" -> String.valueOf(mutation.wrongContentType());
            case "mutation.beyondItsWidth" -> String.valueOf(mutation.beyondItsWidth());
            case "mutation.acceptedKept" -> String.valueOf(mutation.acceptedKept());
            case "mutation.oversizedLength" -> String.valueOf(mutation.oversizedLength());
            case "mutation.oversizedItems" -> String.valueOf(mutation.oversizedItems());

            case "sequences.readAfterDelete" -> String.valueOf(sequences.readAfterDelete());
            case "sequences.deleteTwice" -> String.valueOf(sequences.deleteTwice());
            case "sequences.writeUnderDeleted" -> String.valueOf(sequences.writeUnderDeleted());
            case "sequences.putTwice" -> String.valueOf(sequences.putTwice());
            case "sequences.safeGet" -> String.valueOf(sequences.safeGet());
            case "sequences.createTwice" -> String.valueOf(sequences.createTwice());

            case "memory.mostValuesUnderOneName" ->
                    String.valueOf(memory.mostValuesUnderOneName());
            case "memory.mostNames" -> String.valueOf(memory.mostNames());
            case "memory.longestValueKept" -> String.valueOf(memory.longestValueKept());
            case "memory.longestReplyRead" -> String.valueOf(memory.longestReplyRead());
            case "memory.asDeepAsAReplyIsRead" -> String.valueOf(memory.asDeepAsAReplyIsRead());
            case "memory.identifiersByResource" -> String.valueOf(memory.identifiersByResource());
            case "memory.identifiersByResourceFirst" ->
                    String.valueOf(memory.identifiersByResourceFirst());
            case "memory.rememberAcceptedRequests" ->
                    String.valueOf(memory.rememberAcceptedRequests());

            case "document.fetchTimeout" -> LengthOfTime.written(document.fetchTimeout());
            case "document.mostBytesRead" -> String.valueOf(document.mostBytesRead());

            case "report.writeUpsPerOperationAndKind" ->
                    String.valueOf(report.writeUpsPerOperationAndKind());
            case "report.writeUpsInTotal" -> String.valueOf(report.writeUpsInTotal());
            case "report.mostBodyBytesKept" -> String.valueOf(report.mostBodyBytesKept());
            case "report.faultsShownOnTheConsole" ->
                    String.valueOf(report.faultsShownOnTheConsole());
            case "report.skippedOperationsShownOnTheConsole" ->
                    String.valueOf(report.skippedOperationsShownOnTheConsole());

            // Unreachable while every key in the catalogue is answered above, which a test in this
            // module checks by asking for every one of them. It is here so that adding a key and
            // forgetting this switch fails loudly rather than printing nothing.
            default -> throw new IllegalStateException("nothing here knows what " + key.fullName()
                    + " is set to, although it is listed as a setting");
        };
    }

    /** These settings with the engine's changed. */
    public Settings withEngine(EngineSettings value) {
        return new Settings(value, schedule, generation, mutation, sequences, memory, document,
                report);
    }

    /** These settings with the schedule's changed. */
    public Settings withSchedule(ScheduleSettings value) {
        return new Settings(engine, value, generation, mutation, sequences, memory, document,
                report);
    }

    /** These settings with generation's changed. */
    public Settings withGeneration(GenerationSettings value) {
        return new Settings(engine, schedule, value, mutation, sequences, memory, document,
                report);
    }

    /** These settings with what may be changed in accepted requests changed. */
    public Settings withMutation(MutationSettings value) {
        return new Settings(engine, schedule, generation, value, sequences, memory, document,
                report);
    }

    /** These settings with which series of requests may be sent changed. */
    public Settings withSequences(SequenceSettings value) {
        return new Settings(engine, schedule, generation, mutation, value, memory, document,
                report);
    }

    /** These settings with the memory's changed. */
    public Settings withMemory(MemorySettings value) {
        return new Settings(engine, schedule, generation, mutation, sequences, value, document,
                report);
    }

    /** These settings with the document's changed. */
    public Settings withDocument(DocumentSettings value) {
        return new Settings(engine, schedule, generation, mutation, sequences, memory, value,
                report);
    }

    /** These settings with the report's changed. */
    public Settings withReport(ReportSettings value) {
        return new Settings(engine, schedule, generation, mutation, sequences, memory, document,
                value);
    }

    /** A number, without the exponent Java would otherwise print for a small or large one. */
    private static String written(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /**
     * A number, written the way a person writes one rather than with an exponent.
     *
     * <p>Safe to write plainly, and safe to read back, because a number these settings hold has
     * already had its trailing zeros stripped: {@code 1E+2} and {@code 100} arrive here as one
     * value, so writing either of them out and reading it again gives that same value.
     */
    private static String written(BigDecimal value) {
        return value.toPlainString();
    }


    private static void mustExist(String name) {
        if (SettingKey.named(name).isEmpty()) {
            throw new SettingsException("there is no setting called '" + name + "'"
                    + SettingKey.nearestTo(name)
                            .map(near -> ". Did you mean '" + near.fullName() + "'?")
                            .orElse(". Run 'restest run --print-settings' for the ones there are"));
        }
    }

    /**
     * One group built, with anything its own compact constructor refuses said in that group's name.
     *
     * <p>The group's own refusals are the ones worth printing - they know the range, and they know
     * which other value the refused one disagrees with - so they are passed on as they are, with
     * only the group's name put in front of them so a reader knows where to look.
     */
    private static <T> T group(String name, java.util.function.Supplier<T> built) {
        try {
            return built.get();
        } catch (IllegalArgumentException | NullPointerException refused) {
            throw new SettingsException(name + ": " + refused.getMessage());
        }
    }

    /** The values somebody named, read as the kinds of thing the settings that take them are. */
    private record Given(Map<String, String> values) {

        int wholeNumber(String key, int fallback) {
            String text = values.get(key);
            if (text == null) {
                return fallback;
            }
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException notANumber) {
                throw refuse(key, text, SettingKind.WHOLE_NUMBER);
            }
        }

        long count(String key, long fallback) {
            String text = values.get(key);
            if (text == null) {
                return fallback;
            }
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException notANumber) {
                throw refuse(key, text, SettingKind.WHOLE_NUMBER);
            }
        }

        BigDecimal number(String key, BigDecimal fallback) {
            String text = values.get(key);
            if (text == null) {
                return fallback;
            }
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException notANumber) {
                throw refuse(key, text, SettingKind.NUMBER);
            }
        }

        Duration lengthOfTime(String key, Duration fallback) {
            String text = values.get(key);
            if (text == null) {
                return fallback;
            }
            try {
                return LengthOfTime.parse(text);
            } catch (IllegalArgumentException notALength) {
                throw new SettingsException(key + ": " + notALength.getMessage());
            }
        }

        boolean yesOrNo(String key, boolean fallback) {
            String text = values.get(key);
            if (text == null) {
                return fallback;
            }
            String said = text.trim();
            if ("true".equalsIgnoreCase(said)) {
                return true;
            }
            if ("false".equalsIgnoreCase(said)) {
                return false;
            }
            throw refuse(key, text, SettingKind.YES_OR_NO);
        }

        String text(String key, String fallback) {
            String text = values.get(key);
            return text == null ? fallback : text;
        }

        private static SettingsException refuse(String key, String text, SettingKind kind) {
            return new SettingsException(key + " takes " + kind.described() + ", and '" + text
                    + "' is not one");
        }
    }
}

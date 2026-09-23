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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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
 * @param memory how much of what the API said is remembered
 * @param document what is accepted when a description is fetched
 * @param report how much of what was found is written out
 */
public record Settings(
        EngineSettings engine,
        ScheduleSettings schedule,
        GenerationSettings generation,
        MemorySettings memory,
        DocumentSettings document,
        ReportSettings report) {

    private static final Settings DEFAULTS = new Settings(
            EngineSettings.defaults(),
            ScheduleSettings.defaults(),
            GenerationSettings.defaults(),
            MemorySettings.defaults(),
            DocumentSettings.defaults(),
            ReportSettings.defaults());

    public Settings {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(generation, "generation");
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
        EngineSettings engine = group("engine", () -> new EngineSettings(
                typed.lengthOfTime("engine.connectTimeout", DEFAULTS.engine.connectTimeout()),
                typed.lengthOfTime("engine.readTimeout", DEFAULTS.engine.readTimeout()),
                typed.lengthOfTime("engine.writeTimeout", DEFAULTS.engine.writeTimeout()),
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
                        DEFAULTS.schedule.stragglerGrace())));
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
                typed.wholeNumber("generation.nullInOneIn", DEFAULTS.generation.nullInOneIn()),
                typed.wholeNumber("generation.uniqueAttempts",
                        DEFAULTS.generation.uniqueAttempts()),
                typed.wholeNumber("generation.sendableAttempts",
                        DEFAULTS.generation.sendableAttempts()),
                typed.wholeNumber("generation.writableBodyAttempts",
                        DEFAULTS.generation.writableBodyAttempts())));
        MemorySettings memory = group("memory", () -> new MemorySettings(
                typed.wholeNumber("memory.mostValuesUnderOneName",
                        DEFAULTS.memory.mostValuesUnderOneName()),
                typed.wholeNumber("memory.mostNames", DEFAULTS.memory.mostNames()),
                typed.wholeNumber("memory.longestValueKept", DEFAULTS.memory.longestValueKept()),
                typed.wholeNumber("memory.longestReplyRead", DEFAULTS.memory.longestReplyRead()),
                typed.wholeNumber("memory.asDeepAsAReplyIsRead",
                        DEFAULTS.memory.asDeepAsAReplyIsRead())));
        DocumentSettings document = group("document", () -> new DocumentSettings(
                typed.lengthOfTime("document.fetchTimeout", DEFAULTS.document.fetchTimeout()),
                typed.wholeNumber("document.mostBytesRead", DEFAULTS.document.mostBytesRead())));
        ReportSettings report = group("report", () -> new ReportSettings(
                typed.wholeNumber("report.writeUpsPerOperationAndKind",
                        DEFAULTS.report.writeUpsPerOperationAndKind()),
                typed.wholeNumber("report.writeUpsInTotal", DEFAULTS.report.writeUpsInTotal()),
                typed.count("report.mostBodyBytesKept", DEFAULTS.report.mostBodyBytesKept()),
                typed.wholeNumber("report.faultsShownOnTheConsole",
                        DEFAULTS.report.faultsShownOnTheConsole())));
        return new Settings(engine, schedule, generation, memory, document, report);
    }

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
            case "generation.nullInOneIn" -> String.valueOf(generation.nullInOneIn());
            case "generation.uniqueAttempts" -> String.valueOf(generation.uniqueAttempts());
            case "generation.sendableAttempts" -> String.valueOf(generation.sendableAttempts());
            case "generation.writableBodyAttempts" ->
                    String.valueOf(generation.writableBodyAttempts());

            case "memory.mostValuesUnderOneName" ->
                    String.valueOf(memory.mostValuesUnderOneName());
            case "memory.mostNames" -> String.valueOf(memory.mostNames());
            case "memory.longestValueKept" -> String.valueOf(memory.longestValueKept());
            case "memory.longestReplyRead" -> String.valueOf(memory.longestReplyRead());
            case "memory.asDeepAsAReplyIsRead" -> String.valueOf(memory.asDeepAsAReplyIsRead());

            case "document.fetchTimeout" -> LengthOfTime.written(document.fetchTimeout());
            case "document.mostBytesRead" -> String.valueOf(document.mostBytesRead());

            case "report.writeUpsPerOperationAndKind" ->
                    String.valueOf(report.writeUpsPerOperationAndKind());
            case "report.writeUpsInTotal" -> String.valueOf(report.writeUpsInTotal());
            case "report.mostBodyBytesKept" -> String.valueOf(report.mostBodyBytesKept());
            case "report.faultsShownOnTheConsole" ->
                    String.valueOf(report.faultsShownOnTheConsole());

            // Unreachable while every key in the catalogue is answered above, which a test in this
            // module checks by asking for every one of them. It is here so that adding a key and
            // forgetting this switch fails loudly rather than printing nothing.
            default -> throw new IllegalStateException("nothing here knows what " + key.fullName()
                    + " is set to, although it is listed as a setting");
        };
    }

    /** These settings with the engine's changed. */
    public Settings withEngine(EngineSettings value) {
        return new Settings(value, schedule, generation, memory, document, report);
    }

    /** These settings with the schedule's changed. */
    public Settings withSchedule(ScheduleSettings value) {
        return new Settings(engine, value, generation, memory, document, report);
    }

    /** These settings with generation's changed. */
    public Settings withGeneration(GenerationSettings value) {
        return new Settings(engine, schedule, value, memory, document, report);
    }

    /** These settings with the memory's changed. */
    public Settings withMemory(MemorySettings value) {
        return new Settings(engine, schedule, generation, value, document, report);
    }

    /** These settings with the document's changed. */
    public Settings withDocument(DocumentSettings value) {
        return new Settings(engine, schedule, generation, memory, value, report);
    }

    /** These settings with the report's changed. */
    public Settings withReport(ReportSettings value) {
        return new Settings(engine, schedule, generation, memory, document, value);
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

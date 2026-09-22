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
package io.restest.cli;

import io.restest.core.json.JsonException;
import io.restest.core.json.JsonValue;
import io.restest.core.json.YamlText;
import io.restest.core.settings.SettingKey;
import io.restest.core.settings.SettingSource;
import io.restest.core.settings.Settings;
import io.restest.core.settings.SettingsException;
import io.restest.core.settings.SettingsInEffect;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Gathers a run's settings from the four places they can be given, and remembers which place each
 * one came from.
 *
 * <p>The four, each winning over the one before it:
 *
 * <ol>
 *   <li>what RESTest does when nobody has said otherwise;
 *   <li>a file named with {@code --settings}, in YAML, grouped the way {@code --print-settings}
 *       prints it;
 *   <li>environment variables, {@code RESTEST_ENGINE_MAX_CONCURRENCY} and the like, which is how a
 *       container is configured;
 *   <li>{@code --set engine.maxConcurrency=8}, repeated as often as wanted.
 * </ol>
 *
 * <p>No file is looked for anywhere unless it was named. Starting the tool with nothing beside it
 * has to behave the same way in every directory, and a file found by accident is a run nobody can
 * explain afterwards.
 *
 * <p>This is the only part of RESTest that reads the environment, which an architecture test keeps
 * true. Everything else is handed the settings it needs, so two runs in the same program can have
 * different ones without either noticing the other.
 */
final class SettingsFromEverywhere {

    private SettingsFromEverywhere() {
    }

    /**
     * The settings this run should use, and where each of their values came from.
     *
     * @param file a file of settings, if one was named
     * @param environment the environment the tool was started in
     * @param typed what was given after {@code --set}, in the order it was typed
     * @return the settings, with each value's origin
     * @throws SettingsException if a name is not a setting, or a value is not one it can take
     */
    static SettingsInEffect gather(Optional<Path> file, Map<String, String> environment,
            List<String> typed) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(typed, "typed");

        Map<String, String> values = new LinkedHashMap<>();
        Map<String, SettingSource> sources = new LinkedHashMap<>();
        file.ifPresent(named ->
                take(fromTheFile(named), SettingSource.FILE, values, sources));
        take(fromTheEnvironment(environment), SettingSource.ENVIRONMENT, values, sources);
        take(fromTheCommandLine(typed), SettingSource.COMMAND_LINE, values, sources);
        return new SettingsInEffect(Settings.from(values), sources);
    }

    private static void take(Map<String, String> given, SettingSource source,
            Map<String, String> values, Map<String, SettingSource> sources) {
        given.forEach((key, value) -> {
            values.put(key, value);
            sources.put(key, source);
        });
    }

    /**
     * What a file of settings says.
     *
     * <p>Two levels: a group, and the settings inside it. That is what {@code --print-settings}
     * writes, and writing one setting per line as {@code engine.maxConcurrency: 8} is accepted too,
     * because it is what somebody who has only ever seen the command line will write.
     */
    private static Map<String, String> fromTheFile(Path file) {
        String text;
        try {
            text = Files.readString(file);
        } catch (IOException cannotRead) {
            throw new SettingsException("the settings in " + file + " could not be read: "
                    + cannotRead.getMessage());
        }
        JsonValue read;
        try {
            read = YamlText.read(text, "the settings in " + file);
        } catch (JsonException notReadable) {
            throw new SettingsException(notReadable.getMessage());
        }
        if (read instanceof JsonValue.JsonNull) {
            // An empty file asks for nothing, which is a thing somebody might genuinely hand over.
            return Map.of();
        }
        if (!(read instanceof JsonValue.JsonObject groups)) {
            throw new SettingsException(file + " does not hold settings: a file of settings is a "
                    + "list of groups, each holding the settings in it, the way "
                    + "'restest run --print-settings' writes one out");
        }
        Map<String, String> values = new LinkedHashMap<>();
        groups.members().forEach((name, held) -> {
            if (held instanceof JsonValue.JsonObject inside) {
                inside.members().forEach((key, value) ->
                        values.put(name + "." + key, written(name + "." + key, value, file)));
            } else {
                values.put(name, written(name, held, file));
            }
        });
        return values;
    }

    /**
     * One value out of a file, as the text every layer speaks in.
     *
     * <p>A file can say {@code maxConcurrency: 8} or {@code maxConcurrency: "8"} and mean the same
     * thing, so what a reader made of it is turned straight back into text and read again as the
     * kind of thing the setting takes. That keeps one answer to "what values does this setting
     * accept", whichever of the four places a value arrived from.
     */
    private static String written(String key, JsonValue value, Path file) {
        return switch (value) {
            case JsonValue.JsonString text -> text.value();
            case JsonValue.JsonNumber number -> number.value().toPlainString();
            case JsonValue.JsonBoolean yesOrNo -> String.valueOf(yesOrNo.value());
            case JsonValue.JsonNull ignored -> throw new SettingsException(file + ": " + key
                    + " has no value. Leave the line out to keep what RESTest does by default");
            case JsonValue.JsonArray ignored -> throw new SettingsException(file + ": " + key
                    + " is a list, and no setting takes one");
            case JsonValue.JsonObject ignored -> throw new SettingsException(file + ": " + key
                    + " holds a group of its own, and settings are only ever grouped once");
        };
    }

    /**
     * The settings named by environment variables.
     *
     * <p>Only names that are settings are looked at, and they are looked for rather than scanned
     * for: the tool asks the environment about each of the settings it has, so a variable that
     * begins {@code RESTEST_} and is not a setting - one belonging to a wrapper script, or to some
     * later version of the tool - is left alone rather than refused.
     */
    private static Map<String, String> fromTheEnvironment(Map<String, String> environment) {
        Map<String, String> values = new LinkedHashMap<>();
        SettingKey.all().forEach(key -> {
            String said = environment.get(key.environmentName());
            if (said != null) {
                values.put(key.fullName(), said);
            }
        });
        return values;
    }

    /** What was typed after {@code --set}, which is {@code group.key=value}. */
    private static Map<String, String> fromTheCommandLine(List<String> typed) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String said : typed) {
            int equals = said.indexOf('=');
            if (equals < 1) {
                throw new SettingsException("--set takes a setting and the value to give it, "
                        + "written together as engine.maxConcurrency=8, and '" + said
                        + "' is not that");
            }
            values.put(said.substring(0, equals).trim(), said.substring(equals + 1));
        }
        return values;
    }
}

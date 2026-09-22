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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The settings a run is using, together with where each one of them came from.
 *
 * <p>A run is configured from four places at once and the later ones win, so "what is the
 * concurrency" is only half a question: the other half is which of the four decided it. This holds
 * both answers. It is what {@code --print-settings} prints and what every run writes into its
 * report, so that a directory of results carries the configuration that produced it rather than
 * leaving somebody to reconstruct it from a shell history.
 *
 * <p>What it prints is a file the tool will read back. That is deliberate: the way to change one
 * number is to print these, change the line, and hand the file back with {@code --settings}, the
 * same way a plan is printed, changed and handed back.
 *
 * @param settings the values in force
 * @param sources where each of them came from, by the name a person types. A setting missing from
 *     here came from nobody, which means it is what RESTest does when nobody has said otherwise
 */
public record SettingsInEffect(Settings settings, Map<String, SettingSource> sources) {

    /** How wide the value column is before the note saying where a value came from. */
    private static final int VALUE_COLUMN = 30;

    public SettingsInEffect {
        Objects.requireNonNull(settings, "settings");
        sources = Map.copyOf(Objects.requireNonNull(sources, "sources"));
    }

    /** Settings nobody has changed, every one of them straight from the code. */
    public static SettingsInEffect of(Settings settings) {
        return new SettingsInEffect(settings, Map.of());
    }

    /**
     * Where one setting's value came from.
     *
     * @param key the setting
     * @return the place it came from, which is the code itself when nobody named it
     */
    public SettingSource sourceOf(SettingKey key) {
        Objects.requireNonNull(key, "key");
        SettingSource named = sources.get(key.fullName());
        if (named != null) {
            return named;
        }
        // Nobody named it, and it is still not what the code says by default - so it was worked out
        // from something that was named. Where the engine starts is the one that does this today:
        // it is a place inside the concurrency range, so moving the range moves it. Saying
        // "default" here would put two different values under the same word in two results
        // directories, with nothing in either to explain the difference.
        return settings.written(key).equals(Settings.defaults().written(key))
                ? SettingSource.DEFAULT
                : SettingSource.WORKED_OUT;
    }

    /**
     * One row for every setting there is: its name, its value, and where the value came from.
     *
     * @return the rows, in the order settings are printed
     */
    public List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        SettingKey.all().forEach(key ->
                rows.add(new Row(key, settings.written(key), sourceOf(key))));
        return List.copyOf(rows);
    }

    /**
     * One setting, as a report or a printed file states it.
     *
     * @param key which setting
     * @param value its value, written the way somebody would type it
     * @param source where that value came from
     */
    public record Row(SettingKey key, String value, SettingSource source) {

        public Row {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(source, "source");
        }
    }

    /**
     * These settings as a file somebody can save, change and hand back with {@code --settings}.
     *
     * <p>Every setting is written, not only the ones somebody changed, because the file is also the
     * list of what can be changed at all - each with a line saying what it does and a note saying
     * where its current value came from. Notes are comments, so handing the file straight back
     * changes nothing.
     *
     * @return the file's text, ending in a line break
     */
    public String asAFile() {
        StringBuilder written = new StringBuilder("""
                # The settings this run uses, and where each of its values came from.
                #
                # Save this, change a line, and hand it back with --settings <file>. The same
                # values can be given as --set engine.maxConcurrency=8 on the command line, or as
                # RESTEST_ENGINE_MAX_CONCURRENCY=8 in the environment. What is typed on the command
                # line wins over the environment, which wins over a file, which wins over what
                # RESTest does when nobody has said otherwise.
                #
                # These are settings of the tool. Where a run's values come from, and which
                # operations it may touch, is a plan instead: --print-campaign writes one out.
                """);
        Map<String, List<Row>> byGroup = new LinkedHashMap<>();
        rows().forEach(row ->
                byGroup.computeIfAbsent(row.key().group(), group -> new ArrayList<>()).add(row));
        byGroup.forEach((group, rows) -> {
            written.append('\n').append(group).append(":\n");
            rows.forEach(row -> {
                written.append("  # ").append(row.key().meaning()).append('\n');
                String stated = "  " + row.key().name() + ": " + quoted(row);
                written.append(stated);
                written.append(" ".repeat(Math.max(1, VALUE_COLUMN - stated.length())));
                written.append("# ").append(row.source().written()).append('\n');
            });
        });
        return written.toString();
    }

    /**
     * A value written so that reading the file back gives the same value.
     *
     * <p>Text is quoted; everything else is a number, a length of time or a yes-or-no. A length of
     * time is quoted as well, because {@code 30s} unquoted is a word and {@code 10} unquoted is a
     * number, and only one of the two would survive the trip through a reader that does not know
     * which setting it is reading.
     *
     * <p>Inside the quotes, a backslash and a quotation mark are escaped for the obvious reason,
     * and so is every character that would otherwise end the line or be invisible. A line break in
     * a value is the case worth naming: written as itself it would split one setting across two
     * lines, and the file would come back with the break silently turned into a space.
     */
    private static String quoted(Row row) {
        return switch (row.key().kind()) {
            case TEXT, LENGTH_OF_TIME -> "\"" + escaped(row.value()) + "\"";
            case WHOLE_NUMBER, NUMBER, YES_OR_NO -> row.value();
        };
    }

    /** One piece of text, written so that a reader hands back exactly what went in. */
    private static String escaped(String value) {
        StringBuilder written = new StringBuilder(value.length() + 2);
        value.codePoints().forEach(letter -> {
            switch (letter) {
                case '\\' -> written.append("\\\\");
                case '"' -> written.append("\\\"");
                case '\n' -> written.append("\\n");
                case '\r' -> written.append("\\r");
                case '\t' -> written.append("\\t");
                default -> {
                    if (Character.isISOControl(letter)) {
                        written.append(String.format("\\x%02x", letter));
                    } else {
                        written.appendCodePoint(letter);
                    }
                }
            }
        });
        return written.toString();
    }

}

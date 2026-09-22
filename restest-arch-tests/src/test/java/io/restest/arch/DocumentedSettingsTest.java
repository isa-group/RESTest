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
package io.restest.arch;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.settings.SettingKey;
import io.restest.core.settings.Settings;
import io.restest.core.settings.SettingsInEffect;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Keeps the page that documents the settings in step with the settings themselves.
 *
 * <p>The page carries two things a person relies on without running anything: the template the tool
 * writes out, and the list of every setting with what it does and what it is by default. Both are
 * copies, and a copy of something that changes is a promise somebody will break by accident - a
 * setting added and not written down, or written down and then renamed, leaves a page that is
 * confidently wrong.
 *
 * <p>So the copies are checked rather than trusted. Nothing here has an opinion about what the
 * settings should be; it only insists that what the page says and what the tool does are the same
 * thing.
 */
class DocumentedSettingsTest {

    /** The fenced block on the page holding the template, which is YAML. */
    private static final Pattern TEMPLATE =
            Pattern.compile("## The template, in full.*?```yaml\\n(.*?)```", Pattern.DOTALL);

    /** One row of the page's list of settings: its name and what it is when nobody says otherwise. */
    private static final Pattern ROW =
            Pattern.compile("^\\| `([A-Za-z]+)` \\| `([^`]*)` \\| (.*?) \\|$", Pattern.MULTILINE);

    private static String page;

    @BeforeAll
    static void readThePage() throws IOException {
        // Line endings taken out on the way in. A checkout on Windows turns every line of a
        // document into a carriage return and a line feed, and this project's rules about line
        // endings are deliberately narrow - they cover the files a shell has to execute and
        // nothing else, because a blanket rule rewrites the whole repository the day it fires.
        // What is being compared here is what the page says, which is the same either way.
        page = Files.readString(settingsPage()).replace("\r\n", "\n");
    }

    @Test
    @DisplayName("the template on the page is the one the tool writes out")
    void the_template_is_the_one_the_tool_writes() {
        Matcher found = TEMPLATE.matcher(page);

        assertThat(found.find())
                .describedAs("the page is supposed to show what --print-settings writes, under a "
                        + "heading naming it, so that somebody who has not built the tool can see "
                        + "what a file of settings looks like")
                .isTrue();
        assertThat(found.group(1))
                .describedAs("the page and the tool disagree about what --print-settings writes. "
                        + "Run 'restest run --print-settings' and paste the result back in")
                .isEqualTo(SettingsInEffect.of(Settings.defaults()).asAFile());
    }

    @Test
    @DisplayName("every setting there is has a row on the page, so none is undocumented")
    void every_setting_is_written_down() {
        List<String> documented = rows().stream().map(Row::name).toList();

        assertThat(SettingKey.all()).allSatisfy(key ->
                assertThat(documented)
                        .describedAs("%s can be set and is nowhere on the page", key.fullName())
                        .contains(key.name()));
    }

    @Test
    @DisplayName("and every row on the page is a setting that exists, so none is stale")
    void nothing_written_down_has_gone_away() {
        List<String> real = SettingKey.all().stream().map(SettingKey::name).toList();

        assertThat(rows()).allSatisfy(row ->
                assertThat(real)
                        .describedAs("the page lists '%s', which is not a setting", row.name())
                        .contains(row.name()));
    }

    @Test
    @DisplayName("every row states the value the tool actually uses, and says what the setting does")
    void every_row_says_what_is_true() {
        Settings defaults = Settings.defaults();

        assertThat(rows()).allSatisfy(row -> {
            SettingKey key = SettingKey.all().stream()
                    .filter(known -> known.name().equals(row.name()))
                    .findFirst()
                    .orElseThrow();
            assertThat(row.value())
                    .describedAs("the page says %s is %s, and it is %s",
                            key.fullName(), row.value(), defaults.written(key))
                    .isEqualTo(defaults.written(key));
            assertThat(row.meaning())
                    .describedAs("the page explains %s differently from the tool, so one of the "
                            + "two is the explanation somebody will read and not act on",
                            key.fullName())
                    .isEqualTo(key.meaning());
        });
    }

    /** One setting as the page states it. */
    private record Row(String name, String value, String meaning) {
    }

    private static List<Row> rows() {
        return ROW.matcher(page).results()
                .map(found -> new Row(found.group(1), found.group(2), found.group(3)))
                .toList();
    }

    private static Path settingsPage() {
        Path page = RepositoryRoot.locate().resolve("docs").resolve("settings.md");
        assertThat(page)
                .describedAs("the settings have a page, and these rules read it")
                .isRegularFile();
        return page;
    }
}
